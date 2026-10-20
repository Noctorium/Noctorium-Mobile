package app.noctorium.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * "Keep Noctorium alive": staying in the background on phones whose power managers close apps Android itself would
 * leave running, and coming back when they close it anyway.
 *
 * Off unless the listener turns it on. On, something always holds Noctorium in the foreground, which is what Android
 * asks of an app that means to keep running: the music's own notification while a song is loaded, playing or paused,
 * and a quiet one of its own -- "Ready to play" -- while nothing is (see KeepAliveService). Never both at once.
 *
 * And it comes back: Android starts a foreground service again by itself after the process is killed (START_STICKY),
 * a receiver starts it after the phone restarts or Noctorium is updated, and a watchdog alarm checks on it every
 * quarter of an hour, and half a minute after the last heartbeat while a song plays. Brought back within a minute and
 * a half of being stopped mid-song, it carries on with the song where it was.
 *
 * What none of it can get round is a force stop: Android keeps a force-stopped app off -- no alarms, no restarts, no
 * receivers -- until it is opened again. That is said plainly where the setting is.
 */

/** What brought keep-alive on: for the record of the times Android refused, and for whether a song is picked up. */
internal enum class KeepAliveStart(val words: String) {
    APP("when Noctorium was opened"),
    SETTING("when keep-alive was turned on"),
    BOOT("after the phone restarted"),
    UPDATE("after Noctorium was updated"),
    WATCHDOG("when its check found it gone"),
    STICKY("when Android started it again"),
    MUSIC("as the music started or stopped"),
}

/** What keep-alive needs of the application: the setting, whether Connect can be reached, and a song to pick up. */
interface KeepAliveHost {
    val keepAliveOn: Boolean

    /** The notification's Turn off. */
    fun turnOffKeepAlive()

    /** Whether the listener's other devices can find this phone over Noctorium Connect: on, and signed in. */
    val reachableOverConnect: Boolean

    /** Plays the kept queue's song from [positionMs], if [queueKey] is the song it is on and nothing has played since. */
    fun pickUp(queueKey: String, positionMs: Long): Boolean
}

/** How often, while a song plays, which one and how far in is noted. */
internal const val HEARTBEAT_EVERY_MS = 10_000L

/** How soon after being stopped mid-song a comeback still carries the song on, rather than waiting to be asked. */
internal const val RESUME_WITHIN_MS = 90_000L

/** How far the wall clock's idea of when the phone started may wander in one boot: a clock set by the network. */
private const val SAME_BOOT_SLACK_MS = 10_000L

/**
 * Whether coming back by [start] at [now] carries on with the song in [beat].
 *
 * Only when Android, or the watchdog, brought Noctorium back -- not when the listener opened it, and never after a
 * restart -- and only within [RESUME_WITHIN_MS] of the last note. The note is cleared the moment the music pauses or
 * stops, so a song the listener paused, stopped, or swiped away while paused has none, and is never started again.
 */
internal fun resumes(beat: Heartbeat?, now: Clocks, start: KeepAliveStart): Boolean {
    if (beat == null) return false
    if (start != KeepAliveStart.STICKY && start != KeepAliveStart.WATCHDOG) return false
    // The same time since the phone started: by Android's count of starts, and by when this one began.
    if (beat.bootCount != now.bootCount) return false
    if (abs(beat.bootedAtMs - now.bootedAtMs) > SAME_BOOT_SLACK_MS) return false
    return now.elapsedMs - beat.atElapsedMs in 0..RESUME_WITHIN_MS
}

/** The watchdog's alarm, and the notification's Turn off, both sent to [KeepAliveReceiver]. */
internal const val ACTION_WATCHDOG = "app.noctorium.android.action.KEEP_ALIVE_WATCHDOG"
internal const val ACTION_TURN_OFF = "app.noctorium.android.action.KEEP_ALIVE_TURN_OFF"

/** What [KeepAliveReceiver] starts for [action]: nothing at all while keep-alive is off, nor for what it does not know. */
internal fun receiverStart(action: String?, on: Boolean): KeepAliveStart? = if (!on) null else when (action) {
    Intent.ACTION_BOOT_COMPLETED -> KeepAliveStart.BOOT
    Intent.ACTION_MY_PACKAGE_REPLACED -> KeepAliveStart.UPDATE
    ACTION_WATCHDOG -> KeepAliveStart.WATCHDOG
    else -> null
}

/**
 * The foreground service type the quiet notification is shown under: `specialUse`, from Android 14, where types are
 * checked; none before, where they are not.
 *
 * Not `mediaPlayback`: nothing is playing, and Android 15 will not start a media service from the phone starting up,
 * which is one of the ways this comes back. Not `connectedDevice` either, though Connect is part of what it is kept
 * for: that type is for talking to a device, which this is only while somebody is, and keep-alive is wanted with
 * Connect off too. `specialUse` is Android's type for a reason none of the others names, said in the manifest, and is
 * one of the types Android 15 still lets start from the phone starting up.
 */
internal fun readyForegroundType(sdk: Int): Int = if (sdk >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

/** What the media session service is in the foreground for, if anything. */
internal enum class Holding { NOTHING, SONG, READY }

/** What the media session service does when Media3 says its notification has changed. See PlaybackService. */
internal enum class ForegroundPlan {
    /** Keep-alive is off: Media3's own way, in the foreground while playing and out of it when paused. */
    AS_MEDIA3,

    /** Keep-alive was just turned off while the quiet notification was here: it goes, then Media3's own way. */
    LET_GO_OF_READY,

    /** A song is loaded: Media3's notification, and in the foreground whether it plays or not. */
    SONG_KEPT,

    /** Nothing loaded, and already in the foreground: the quiet notification, here, without ever leaving it. */
    READY_HERE,

    /** Nothing loaded, and not in the foreground: KeepAliveService holds it instead. */
    READY_ELSEWHERE,
}

internal fun foregroundPlan(on: Boolean, song: Boolean, holding: Holding): ForegroundPlan = when {
    !on && holding == Holding.READY -> ForegroundPlan.LET_GO_OF_READY
    !on -> ForegroundPlan.AS_MEDIA3
    song -> ForegroundPlan.SONG_KEPT
    holding != Holding.NOTHING -> ForegroundPlan.READY_HERE
    else -> ForegroundPlan.READY_ELSEWHERE
}

private const val LOG_TAG = "NoctoriumKeepAlive"

/** Keep-alive's one place for the whole process: what holds the foreground, the comeback, and the heartbeat. */
internal object KeepAlive {

    /** Whether the media session service is in the foreground, and so is what keeps Noctorium alive. */
    @Volatile
    var heldByPlayback = false

    /** The heartbeat left by the process before this one, read before anything can clear it: what a comeback picks up. */
    @Volatile
    private var lastBeat: Heartbeat? = null

    fun host(context: Context): KeepAliveHost? = context.applicationContext as? KeepAliveHost

    fun on(context: Context): Boolean = host(context)?.keepAliveOn == true

    /** At the start of the process, before anything plays or is cleared: the heartbeat, and the watchdog while on. */
    fun processStarted(context: Context, on: Boolean) {
        lastBeat = KeepAliveStore(context).heartbeat
        if (on) KeepAliveWatchdog.resting(context)
    }

    /** The setting changed. */
    fun switched(context: Context, on: Boolean) {
        if (on) {
            // Found by now, whether by the hint or not: the hint suggesting it is not wanted again.
            KeepAliveStore(context).hintDismissed = true
            KeepAliveWatchdog.resting(context)
            // A song paused in the media session service is held there, by the music's own notification, once the
            // service has settled it again below; the quiet one would only flash up beside it first.
            if (!PlaybackService.hasSong()) start(context, KeepAliveStart.SETTING)
        } else {
            KeepAliveWatchdog.cancel(context)
            KeepAliveStore(context).heartbeat = null
            KeepAliveService.standDown(context)
        }
        PlaybackService.reconsider()
    }

    /**
     * Makes sure something holds Noctorium in the foreground: the music's notification, or the quiet one.
     *
     * From the background, Android allows that only some of the time -- after the phone starts up, with Noctorium
     * left out of battery optimisation, and a few other cases -- and refuses otherwise. A refusal is not an error to
     * show: it is noted, for the section to say how often it happens, and false is returned.
     */
    fun start(context: Context, start: KeepAliveStart): Boolean {
        if (!on(context)) return false
        if (heldByPlayback || KeepAliveService.running) return true
        return try {
            ContextCompat.startForegroundService(context, KeepAliveService.intent(context, start))
            true
        } catch (refusal: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException, from Android 12, is one of these.
            refused(context, start, refusal)
            false
        } catch (refusal: SecurityException) {
            refused(context, start, refusal)
            false
        }
    }

    fun refused(context: Context, start: KeepAliveStart, why: Throwable? = null) {
        Log.w(LOG_TAG, "Android would not let keep-alive start ${start.words}", why)
        KeepAliveStore(context).refused(Refusal(start, System.currentTimeMillis()))
    }

    /** Back after the phone stopped Noctorium: the song it was in the middle of, carried on, if it was a moment ago. */
    fun cameBack(context: Context, start: KeepAliveStart) {
        val beat = lastBeat ?: return
        lastBeat = null
        if (!resumes(beat, Clocks.now(context), start)) return
        val picked = host(context)?.pickUp(beat.queueKey, beat.positionMs) == true
        Log.i(LOG_TAG, "Back ${start.words}, ${if (picked) "carrying on with" else "not carrying on with"} ${beat.queueKey} at ${beat.positionMs}ms")
    }

    /**
     * Notes which song this phone is playing and how far in, every [HEARTBEAT_EVERY_MS] while it plays, and clears the
     * note the moment it pauses or stops. Each note also puts the watchdog's next look half a minute off, so a phone
     * that stops Noctorium mid-song is checked on well inside [RESUME_WITHIN_MS].
     *
     * Between songs, and while a song's address is found again, it is still meant to be playing: the last note stands.
     */
    fun follow(context: Context, playback: StateFlow<PlaybackState>, on: Flow<Boolean>, scope: CoroutineScope) {
        val store = KeepAliveStore(context)
        scope.launch {
            combine(on.distinctUntilChanged(), playback.map { it.status }.distinctUntilChanged()) { keep, status -> keep to status }
                .collectLatest { (keep, status) ->
                    when {
                        !keep -> Unit
                        status == PlaybackStatus.PLAYING -> while (true) {
                            val now = playback.value
                            val song = now.track
                            if (song != null && now.status == PlaybackStatus.PLAYING) {
                                val clocks = Clocks.now(context)
                                store.heartbeat = Heartbeat(song.queueKey, now.positionMs, clocks.elapsedMs, clocks.bootCount, clocks.bootedAtMs)
                                KeepAliveWatchdog.playing(context)
                            }
                            delay(HEARTBEAT_EVERY_MS)
                        }
                        status == PlaybackStatus.RESOLVING -> Unit
                        store.heartbeat != null -> {
                            store.heartbeat = null
                            KeepAliveWatchdog.resting(context)
                        }
                    }
                }
        }
    }
}

/**
 * The watchdog: an alarm that wakes [KeepAliveReceiver] to bring keep-alive back if it has gone.
 *
 * Inexact, which needs no permission and lets Android fold it in with everything else it wakes for: every quarter of
 * an hour at rest, and half a minute after the last heartbeat while a song plays -- pushed back by every heartbeat, so
 * it only ever goes off once they have stopped coming. One alarm, either way: setting one replaces the other.
 */
internal object KeepAliveWatchdog {
    const val RESTING_EVERY_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES
    const val PLAYING_WITHIN_MS = 30_000L

    fun resting(context: Context) {
        val alarms = alarms(context) ?: return
        runCatching {
            alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + RESTING_EVERY_MS,
                RESTING_EVERY_MS,
                alarm(context),
            )
        }
    }

    fun playing(context: Context) {
        val alarms = alarms(context) ?: return
        // Allowed while the phone dozes, where an ordinary alarm would wait for the next window: a song stopped in a
        // pocket is the case this is for.
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + PLAYING_WITHIN_MS, alarm(context)) }
    }

    fun cancel(context: Context) {
        runCatching { alarms(context)?.cancel(alarm(context)) }
    }

    private fun alarms(context: Context): AlarmManager? = context.getSystemService(AlarmManager::class.java)

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_WATCHDOG),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
