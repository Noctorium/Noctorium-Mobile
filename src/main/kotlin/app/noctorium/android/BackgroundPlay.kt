package app.noctorium.android

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/*
 * Music in the background, kept where Android lets it play: in the foreground, with its notification.
 *
 * Android can take a music app out of the foreground without a word to it -- a phone that restricts the app in the
 * background does it the moment the app is left, and stops its service soon after -- and the music then goes on as a
 * plain background process, which a phone that freezes cached apps freezes, mid-song, until the app is opened again.
 * Nothing tells the app. Media3 goes into the foreground when its notification changes, and never asks whether it is
 * still there; so once it had been taken away, it stayed away, song after song.
 *
 * So whenever this phone plays, Noctorium checks, and takes the foreground back where it has gone: on each change of
 * what the player is doing, every ten seconds while it plays, and whenever Noctorium comes to the front. With
 * keep-alive on or off -- this is only playing music in the background. Where Android refuses, or restricts Noctorium
 * in the background, it is noted, and said to the listener when they come back, with the setting that stops it.
 */

/** Why the music lost the foreground in the background, as far as Noctorium can tell. */
internal enum class Interruption {
    /** The phone restricts Noctorium in the background ("Allow background activity" off, or Android's Restricted). */
    RESTRICTED,

    /** Android would not let Noctorium back into the foreground from the background. */
    REFUSED,

    /** Android stopped the media session service while the music played. */
    STOPPED,

    /** Android took the foreground away, and Noctorium took it back. */
    TAKEN,
}

/** One time it happened. */
internal data class Interrupted(val why: Interruption, val atMs: Long)

/** What sets off a check. */
internal enum class ForegroundCheck { EVENT, HEARTBEAT, APP }

/** How often, while this phone plays, the foreground is checked on. */
internal const val FOREGROUND_CHECK_EVERY_MS = 10_000L

/** Why a loss of the foreground is put down to: the restriction when there is one, else whether it was refused. */
internal fun interruptionFor(restricted: Boolean, refused: Boolean, regained: Boolean, stopped: Boolean = false): Interruption = when {
    restricted -> Interruption.RESTRICTED
    stopped -> Interruption.STOPPED
    refused || !regained -> Interruption.REFUSED
    else -> Interruption.TAKEN
}

private const val LOG_TAG = "NoctoriumBackgroundPlay"

internal object BackgroundPlay {

    /** Whether Noctorium is on the screen: set by MainActivity. Android always lets an app on the screen into the foreground. */
    @Volatile
    var visible = false

    /** A connection of the application's own to the media session, made when this phone plays with no service for it. */
    private var connection: ListenableFuture<MediaController>? = null

    /** The application [connection] was made for: there is one, but a test makes a new one each time. */
    private var connectedFor: Context? = null

    /**
     * Makes sure the music has the foreground while this phone plays: the media session service takes it back if it
     * has lost it, and where there is no service at all, one is brought up the way the screen brings it up, by
     * connecting to it -- which Media3 answers by putting the music's notification in the foreground itself.
     */
    fun ensure(context: Context, check: ForegroundCheck) {
        val service = PlaybackService.current()
        if (service != null) {
            service.winForegroundBack(check)
            return
        }
        val host = context.applicationContext as? PlaybackHost ?: return
        if (!host.playingHere()) return
        val application = context.applicationContext
        // Still connecting, or connected: the service is on its way. Let go of when the service it reached was stopped.
        val live = connectedFor === application && connection?.let { future ->
            !future.isDone || runCatching { future.get().isConnected }.getOrDefault(false)
        } == true
        if (live) return
        connection?.let(MediaController::releaseFuture)
        Log.i(LOG_TAG, "Playing with no media session service ($check); connecting to it again")
        connectedFor = application
        connection = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java)))
            .buildAsync()
    }

    /** Notes a loss of the foreground, when nobody was looking: what the next visit to Noctorium explains. */
    fun interrupted(context: Context, why: Interruption) {
        if (visible) return
        Log.w(LOG_TAG, "The music lost the foreground in the background: $why")
        KeepAliveStore(context).interrupted(Interrupted(why, System.currentTimeMillis()))
    }

    /** Checks on the foreground on each change of what this phone plays, and every [FOREGROUND_CHECK_EVERY_MS] while it plays. */
    fun follow(context: Context, playback: StateFlow<PlaybackState>, scope: CoroutineScope) {
        scope.launch {
            playback.map { it.status }.distinctUntilChanged().collectLatest { status ->
                if (status != PlaybackStatus.PLAYING && status != PlaybackStatus.RESOLVING) return@collectLatest
                // After a while rather than at once: the music going into the foreground as it starts is Media3's, a moment off.
                while (true) {
                    delay(FOREGROUND_CHECK_EVERY_MS)
                    ensure(context, ForegroundCheck.HEARTBEAT)
                }
            }
        }
    }
}
