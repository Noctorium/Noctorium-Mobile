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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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
            else -> Log.w(TAG, "Unknown: ${intent.action}")
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

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun serviceFile(context: Context) = File(context.filesDir, "debug/service-url")

        /** The service this debug build was pointed at, or null for the real one. */
        fun serviceUrl(context: Context): String? =
            runCatching { serviceFile(context).readText().trim() }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
