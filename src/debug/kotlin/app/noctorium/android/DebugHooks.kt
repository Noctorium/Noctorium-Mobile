package app.noctorium.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.noctorium.connect.ConnectClient
import app.noctorium.connect.ConnectManager
import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.DeviceAnnouncement
import app.noctorium.connect.DeviceKind
import app.noctorium.net.Http
import app.noctorium.playback.Loudness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.log10

/**
 * Ways into the debug build from adb, for what cannot be reached by tapping.
 *
 * Only in the debug build: this file and its entry in the manifest live in src/debug, and a release build has
 * neither (src/release has the one function the application asks for, answering "nothing"). The receiver is
 * guarded by DUMP, which the adb shell holds and no installed app can.
 *
 * Testing Connect against a computer over USB needs three things a phone on its own would never do: talk to
 * a Noctorium service running on the computer, be signed in to a made-up account there, and be told where
 * the computer is, since announcements do not travel through adb.
 *
 *     H="-n app.noctorium.debug/app.noctorium.android.DebugHooks"
 *     adb reverse tcp:3000 tcp:3000
 *     adb shell am broadcast $H -a app.noctorium.debug.SERVICE --es url http://127.0.0.1:3000   # then restart the app
 *     adb shell am broadcast $H -a app.noctorium.debug.SESSION --es token <a session on that service>
 *     adb shell am broadcast $H -a app.noctorium.debug.CONNECT_ADD --es address 127.0.0.1 --ei port 47700
 *     adb shell am broadcast $H -a app.noctorium.debug.CONNECT_PROBE --es address 192.168.1.20 --ei port 41234
 *     adb shell am broadcast $H -a app.noctorium.debug.SERVICE                                  # back to the real one
 *
 * The service is only ever one reached through 127.0.0.1, which is to say the computer at the other end of
 * the cable: the one address this application may already speak plain HTTP to. What each hook did is written
 * to logcat under "NoctoriumDebug".
 *
 * Send them while the application is open. MIUI does not start a stopped application for a broadcast, and
 * one sent then is dropped without a word.
 *
 * The booster is measured on the phone the same way, from the sound itself rather than by ear: a WAV put in the
 * debug folder is played through the real player, muted, at each level in turn, and what went into the booster
 * and what came out of it are measured in LUFS ([Loudness]) and written to logcat. The booster works before the
 * player's volume and the phone's, so it measures the same at media volume 0.
 *
 *     adb push loud.wav /data/local/tmp/ && adb shell "cat /data/local/tmp/loud.wav | run-as app.noctorium.debug sh -c 'mkdir -p files/debug && cat > files/debug/loud.wav'"
 *     adb shell am broadcast $H -a app.noctorium.debug.BOOST_MEASURE --es file loud.wav --es levels 100,200,400,1000 --ef volume 1
 */
class DebugHooks : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as NoctoriumApplication
        when (intent.action) {
            SERVICE -> useService(context, intent.getStringExtra("url"))
            SESSION -> intent.getStringExtra("token")?.takeIf(String::isNotBlank)?.let { token ->
                Log.i(TAG, "Signing in with the session handed over")
                application.state.useNoctoriumSession(token)
            } ?: Log.w(TAG, "SESSION needs --es token")
            CONNECT_ADD -> withAddress(intent) { address, port ->
                Log.i(TAG, "Looking for a Connect device at $address:$port")
                application.state.addConnectAddress(address, port)
            }
            CONNECT_PROBE -> withAddress(intent) { address, port ->
                val pending = goAsync()
                scope.launch {
                    try {
                        probe(application, address, port)
                    } finally {
                        pending.finish()
                    }
                }
            }
            BOOST_MEASURE -> {
                val name = intent.getStringExtra("file")?.takeIf { it.isNotBlank() && '/' !in it }
                val file = name?.let { File(context.filesDir, "debug/$it") }
                if (file == null || !file.isFile) {
                    Log.w(TAG, "BOOST_MEASURE needs --es file <a WAV in files/debug>")
                } else {
                    val levels = intent.getStringExtra("levels")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                        ?: listOf(100, 200, 400, 1_000)
                    val volume = intent.getFloatExtra("volume", 1f)
                    val seconds = intent.getIntExtra("seconds", 8)
                    boostMeasuring?.cancel()
                    boostMeasuring = main.launch { measureBoost(application, file, levels, volume, seconds) }
                }
            }
            else -> Log.w(TAG, "Unknown: ${intent.action}")
        }
    }

    /**
     * Plays [file] through the real player, muted, once for each of [levels] at [volume], and logs how loud the
     * booster's input and output were over [seconds] of it, a second in: integrated LUFS, and the loudest sample.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun measureBoost(application: NoctoriumApplication, file: File, levels: List<Int>, volume: Float, seconds: Int) {
        val engine = application.player
        val player = engine.mediaPlayer
        application.state.setVolume(volume)
        if (!application.state.playback.value.isMuted) application.state.toggleMute()
        try {
            for (level in levels) {
                application.state.setVolumeBoostPercent(level)
                delay(500)
                val heard = BoostRecording(skipSeconds = 1, keepSeconds = seconds)
                engine.boost.tap = heard
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
                player.prepare()
                player.play()
                while (!heard.full) {
                    if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) break
                    delay(200)
                }
                engine.boost.tap = null
                player.stop()
                Log.i(TAG, "BOOST $level% at volume $volume: ${heard.describe()}; muted ${application.state.playback.value.isMuted}")
            }
        } finally {
            engine.boost.tap = null
        }
    }

    /** What went into the booster and what came out, kept for a while, measured afterwards. */
    private class BoostRecording(private val skipSeconds: Int, private val keepSeconds: Int) : BoostTap {
        private var input = FloatArray(0)
        private var output = FloatArray(0)
        private var kept = 0
        private var skipped = 0
        private var channels = 2
        private var rate = 48_000
        @Volatile var full = false
            private set

        override fun heard(input: FloatArray, output: FloatArray, samples: Int, channels: Int, sampleRate: Int) {
            if (full) return
            if (this.input.isEmpty()) {
                this.channels = channels
                rate = sampleRate
                this.input = FloatArray(sampleRate * channels * keepSeconds)
                this.output = FloatArray(this.input.size)
            }
            var from = 0
            val skip = rate * channels * skipSeconds
            if (skipped < skip) {
                from = minOf(samples, skip - skipped)
                skipped += from
            }
            val count = minOf(samples - from, this.input.size - kept)
            if (count <= 0) return
            System.arraycopy(input, from, this.input, kept, count)
            System.arraycopy(output, from, this.output, kept, count)
            kept += count
            if (kept == this.input.size) full = true
        }

        fun describe(): String {
            val inLufs = Loudness.integrated(input.copyOf(kept), channels, rate)
            val outLufs = Loudness.integrated(output.copyOf(kept), channels, rate)
            val peak = (0 until kept).maxOfOrNull { abs(output[it]) } ?: 0f
            return String.format(
                java.util.Locale.ROOT, "in %.1f LUFS, out %.1f LUFS (%+.1f LU), out peak %.2f dBFS, %.1f s at %d Hz",
                inLufs, outLufs, outLufs - inLufs, 20 * log10(peak.toDouble()), kept.toDouble() / channels / rate, rate,
            )
        }
    }

    private fun withAddress(intent: Intent, then: (String, Int) -> Unit) {
        val address = intent.getStringExtra("address")?.trim()
        val port = intent.getIntExtra("port", 0)
        if (address.isNullOrBlank() || port !in 1..65535) {
            Log.w(TAG, "${intent.action} needs --es address and --ei port")
            return
        }
        then(address, port)
    }

    private fun useService(context: Context, url: String?) {
        val file = serviceFile(context)
        val wanted = url?.trim()?.trimEnd('/')
        if (wanted.isNullOrBlank()) {
            file.delete()
            Log.i(TAG, "Back to the real Noctorium service from the next launch")
            return
        }
        if (!Regex("^http://127\\.0\\.0\\.1:\\d+$").matches(wanted)) {
            Log.w(TAG, "Only a service through adb reverse, as http://127.0.0.1:<port>; not $wanted")
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(wanted)
        Log.i(TAG, "The Noctorium service is $wanted from the next launch")
    }

    /**
     * One request to [address] the way the rest of the application sends requests, and one the way Connect does.
     *
     * The first is OkHttp, which asks Android first and is refused plain HTTP to anything but 127.0.0.1: that
     * is the rule keeping everything else https, and it should go on refusing. The second is Connect's own,
     * signed with the account's key, which a device on the same account answers.
     */
    private suspend fun probe(application: NoctoriumApplication, address: String, port: Int) {
        val where = if (':' in address) "[$address]:$port" else "$address:$port"
        val plain = Http().send("http://$where/state", timeoutSeconds = 4)
        Log.i(TAG, "OkHttp to http://$where/state: ${plain.status} ${plain.body.take(160).replace('\n', ' ')}")

        val key = runCatching { application.secrets.get(ConnectManager.CONNECT_KEY) }.getOrNull()
        if (key == null) Log.w(TAG, "No connect key stored: sign in first, or the answer can only be a refusal")
        val from = DeviceAnnouncement(id = "debug-probe", name = "Debug probe", kind = DeviceKind.PHONE, port = 1, stamp = 0, tag = "")
        val peer = ConnectPeer("probe", where, DeviceKind.PHONE, address, port, 0)
        val state = ConnectClient().state(peer, key ?: "no-key", from)
        Log.i(
            TAG,
            "Connect to $where: " +
                (state?.let { "answered as ${it.deviceName} (${it.kind}), playing=${it.playing}, \"${it.track?.title ?: "-"}\"" }
                    ?: "no answer"),
        )
    }

    companion object {
        private const val TAG = "NoctoriumDebug"
        private const val SERVICE = "app.noctorium.debug.SERVICE"
        private const val SESSION = "app.noctorium.debug.SESSION"
        private const val CONNECT_ADD = "app.noctorium.debug.CONNECT_ADD"
        private const val CONNECT_PROBE = "app.noctorium.debug.CONNECT_PROBE"
        private const val BOOST_MEASURE = "app.noctorium.debug.BOOST_MEASURE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Where the player is touched from: its own thread, the main one. */
        private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /** The measurement running, so a second broadcast replaces it rather than playing over it. */
        private var boostMeasuring: Job? = null

        private fun serviceFile(context: Context) = File(context.filesDir, "debug/service-url")

        /** The service this debug build was pointed at, or null for the real one. */
        fun serviceUrl(context: Context): String? =
            runCatching { serviceFile(context).readText().trim() }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
