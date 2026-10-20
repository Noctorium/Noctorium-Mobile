package app.noctorium.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import java.lang.ref.WeakReference

/**
 * What keeps the music going when Noctorium is not on screen.
 *
 * On Android, audio that continues past the activity has to belong to a foreground service, and a
 * `MediaSessionService` is the one that also gets the lock-screen controls, the notification and the
 * Bluetooth and headset buttons for free. Media3 builds all of that from the session below; there is no
 * notification code here because writing one by hand is how they end up inconsistent with the system's.
 *
 * The player is the application's, not this service's. There is exactly one, and two would mean two things
 * making sound.
 *
 * With "Keep Noctorium alive" on, this is also what holds Noctorium in the foreground whenever a song is loaded.
 * Media3 takes a paused player out of the foreground, which on a phone with a strict power manager is the moment it
 * gets closed, so with keep-alive on it is told to stay, paused or not. And when the song goes -- the queue cleared,
 * the music moved to another device -- this shows keep-alive's quiet notification in place of the music's without
 * ever leaving the foreground, since coming back into it from the background is what Android does not always allow.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    /** What this is in the foreground for, if anything; see [foregroundPlan]. */
    private var holding = Holding.NOTHING
        set(value) {
            field = value
            KeepAlive.heldByPlayback = value != Holding.NOTHING
            showingReady = value == Holding.READY
        }

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        running = WeakReference(this)
        // Media3's own notification, except that one rebuilt once its cover has arrived comes back through
        // onUpdateNotification below like every other change; see KeptNotifications.
        setMediaNotificationProvider(KeptNotifications(DefaultMediaNotificationProvider.Builder(this).build()))
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                KeepAlive.refused(this@PlaybackService, KeepAliveStart.MUSIC)
            }
        })
        val application = application as NoctoriumApplication
        val state = application.state
        // Wrapped, so that skipping from the lock screen reaches the queue that actually holds the tracks.
        // See QueueAwarePlayer: the bare ExoPlayer only ever has one item and reports, correctly and
        // uselessly, that there is nothing to skip to.
        val player = QueueAwarePlayer(
            player = application.player.mediaPlayer,
            goNext = state::next,
            goPrevious = state::previous,
            // The queue the player shows: this phone's, or the one on the device it is the remote control of.
            canGoNext = { state.shownQueue.value.hasNext },
            // A song playing can always go back to its start, so the first in the queue keeps the button too.
            canGoPrevious = { state.canGoPrevious || state.connect.value.target != null },
            repeatMode = { state.shownQueue.value.repeatMode },
            setRepeat = state.queue::setRepeat,
            remote = {
                when {
                    state.connect.value.target == null -> null
                    state.shownPlayback.value.isPlaying -> QueueAwarePlayer.RemotePlaying.PLAYING
                    else -> QueueAwarePlayer.RemotePlaying.PAUSED
                }
            },
            togglePlayback = state::togglePlayback,
        )
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val asked = super.onStartCommand(intent, flags, startId)
        // No intent is Android starting this again by itself after the process was killed, Media3's being sticky. The
        // quiet notification's service takes it from there, and picks up the song if one was playing a moment ago.
        if (intent == null && holding == Holding.NOTHING) KeepAlive.start(this, KeepAliveStart.STICKY)
        return asked
    }

    /**
     * Media3's notification, and whether this is in the foreground for it, by [foregroundPlan]: Media3's own way while
     * keep-alive is off, and in the foreground throughout while it is on -- with the music's notification while a
     * song is loaded, and keep-alive's quiet one when none is.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        when (foregroundPlan(KeepAlive.on(this), showsSong(session.player), holding)) {
            ForegroundPlan.AS_MEDIA3 -> {
                super.onUpdateNotification(session, startInForegroundRequired)
                holding = if (isPlaybackOngoing) Holding.SONG else Holding.NOTHING
            }
            ForegroundPlan.LET_GO_OF_READY -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                holding = Holding.NOTHING
                super.onUpdateNotification(session, startInForegroundRequired)
                holding = if (isPlaybackOngoing) Holding.SONG else Holding.NOTHING
            }
            ForegroundPlan.SONG_KEPT -> {
                // Throws when Android refuses the foreground, which Media3 catches and tells the listener set above.
                super.onUpdateNotification(session, true)
                if (isPlaybackOngoing) {
                    holding = Holding.SONG
                    // The music's notification holds the foreground now; the quiet one is not wanted beside it.
                    KeepAliveService.standDown(this)
                }
            }
            ForegroundPlan.READY_HERE -> showReady()
            ForegroundPlan.READY_ELSEWHERE -> {
                super.onUpdateNotification(session, startInForegroundRequired)
                KeepAlive.start(this, KeepAliveStart.MUSIC)
            }
        }
    }

    /** Keep-alive's quiet notification in place of the music's, without leaving the foreground. */
    private fun showReady() {
        try {
            ServiceCompat.startForeground(this, KeepAliveNotification.ID, KeepAliveNotification.build(this), readyForegroundType(Build.VERSION.SDK_INT))
            holding = Holding.READY
        } catch (refusal: IllegalStateException) {
            KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
            holding = Holding.NOTHING
        } catch (refusal: SecurityException) {
            KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
            holding = Holding.NOTHING
        }
    }

    /** The same as Media3's own changes, for one that is not: keep-alive switched, or a cover that arrived late. */
    private fun updateAgain() {
        main.post {
            val current = session ?: return@post
            try {
                onUpdateNotification(current, runsInForegroundByItself(current.player))
            } catch (refusal: IllegalStateException) {
                // Android refusing the foreground: Media3 catches that on its own calls, and this is not one of them.
                KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
            }
        }
    }

    /**
     * Stops when the listener swipes the app away *and* nothing is playing.
     *
     * Android's default is to leave the service running, which for a music app is right while music is
     * playing and wrong when it is not — a paused player sitting in the notification shade after the app
     * was dismissed reads as something that would not go away. With keep-alive on, staying is what was asked for.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (KeepAlive.on(this)) return
        val player = session?.player
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = null
        val held = holding != Holding.NOTHING
        holding = Holding.NOTHING
        // The session is released; the player is not. It belongs to the application, which may still be
        // alive and may be asked to play again without this service ever being recreated.
        session?.release()
        session = null
        super.onDestroy()
        // Keep-alive was being held here: the quiet notification's service holds it from now on.
        if (held) KeepAlive.start(applicationContext, KeepAliveStart.MUSIC)
    }

    /**
     * Media3's own notifications, but one rebuilt by itself once its cover has arrived goes back through
     * [onUpdateNotification] while keep-alive is on and the player is not playing. Handed straight to Media3, that
     * one would take a paused player out of the foreground, which is what keep-alive is there to stop.
     */
    private inner class KeptNotifications(private val media3: MediaNotification.Provider) : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback,
        ): MediaNotification = media3.createNotification(mediaSession, mediaButtonPreferences, actionFactory) { changed ->
            if (KeepAlive.on(this@PlaybackService) && !runsInForegroundByItself(mediaSession.player)) {
                updateAgain()
            } else {
                onNotificationChangedCallback.onNotificationChanged(changed)
            }
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
            media3.handleCustomCommand(session, action, extras)
    }

    companion object {
        private var running: WeakReference<PlaybackService>? = null

        /** Whether keep-alive's quiet notification is showing from here. */
        @Volatile
        var showingReady = false
            private set

        /** Keep-alive switched on or off: the service, if it is running, settles its notification again. */
        fun reconsider() {
            running?.get()?.updateAgain()
        }

        /** Whether the service is running with a song loaded, playing or paused. */
        fun hasSong(): Boolean = running?.get()?.session?.player?.let(::showsSong) == true

        /** Whether Media3 has a song to show: something loaded, and not stopped. Media3's own test, read the same way. */
        internal fun showsSong(player: Player): Boolean =
            !player.currentTimeline.isEmpty && player.playbackState != Player.STATE_IDLE

        /** Whether Media3, left to itself, keeps the service in the foreground: told to play, and able to. */
        internal fun runsInForegroundByItself(player: Player): Boolean =
            player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
    }
}
