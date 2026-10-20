package app.noctorium.android

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
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
 * What the media session service plays: the application's one player, as the session is to see it. The application
 * is it (see NoctoriumApplication.sessionPlayer); a test hands the service a player of its own.
 */
interface PlaybackHost {
    fun sessionPlayer(): Player

    /** Whether this phone is playing, or about to, by the player itself: what the music needs the foreground for. */
    fun playingHere(): Boolean = false
}

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
                // Refused while music plays here: the music is out of the foreground, where Android may freeze it.
                if (session?.player?.let(::runsInForegroundByItself) == true) {
                    BackgroundPlay.interrupted(
                        this@PlaybackService,
                        interruptionFor(BackgroundPlayback.restrictedInBackground(this@PlaybackService), refused = true, regained = false),
                    )
                }
            }
        })
        val player = (application as PlaybackHost).sessionPlayer()
        player.addListener(watch)
        // A notification of Media3's left over with nothing loaded is from before: a process Android killed, whose
        // session is gone, so the controls on it answer nothing. The one wanted is posted afresh below.
        if (!showsSong(player)) NotificationManagerCompat.from(this).cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        session = MediaSession.Builder(this, player).build().also { built ->
            /*
             * Watched from the moment it exists, not from when something first binds to the service.
             *
             * Media3 builds the notification from a session it has been handed, and it is handed one when a controller
             * connects -- the app's own screen, as a rule. Started by Android itself after the process was killed,
             * nothing connects: keep-alive brought the song back and it played with no notification of its own, the
             * dead process's left showing the song it died on, frozen, and the quiet notification never stood down,
             * since nothing ever said a song was loaded.
             */
            addSession(built)
        }
        settleCarriedOver(player)
    }

    /**
     * The foreground the process before this one left behind, when Android starts the service again by itself.
     *
     * Android keeps a foreground service that it means to restart in the foreground, and puts the old process's
     * notification back up as it restarts it: the music's, for the song that was loaded then, with that process's dead
     * session on it, so it shows a song this process has not loaded and its buttons answer nothing. Media3 only takes
     * a service out of the foreground when something changes, and with nothing loaded here nothing does -- so it stayed,
     * beside the quiet notification, for as long as nothing played.
     *
     * Nothing loaded is the only way a service starts: a song loaded already belongs to this process, and Media3 has
     * it. With keep-alive on, the quiet notification takes the old one's place here without leaving the foreground,
     * which spares asking Android to let Noctorium into it again from the background; with it off, the old one goes.
     * Before Android 10 there is no asking whether a service is in the foreground, so it is let go of as if it were.
     */
    private fun settleCarriedOver(player: Player) {
        if (showsSong(player)) return
        val carried = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        if (!carried) return
        if (KeepAlive.on(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) showReady()
        // Off, before Android 10, or refused the quiet notification in its place: the old one goes all the same, and the
        // quiet notification's own service is started for it below.
        if (holding == Holding.NOTHING) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val asked = super.onStartCommand(intent, flags, startId)
        // No intent is Android starting this again by itself after the process was killed, Media3's being sticky.
        // Already holding the foreground -- the old one, settled above -- this picks up a song that was playing a
        // moment ago itself; otherwise the quiet notification's service takes it from there, and picks it up.
        if (intent == null) {
            if (holding != Holding.NOTHING) KeepAlive.cameBack(this, KeepAliveStart.STICKY) else KeepAlive.start(this, KeepAliveStart.STICKY)
        }
        return asked
    }

    /**
     * Media3's notification, and whether this is in the foreground for it, by [foregroundPlan]: Media3's own way while
     * keep-alive is off, and in the foreground throughout while it is on -- with the music's notification while a
     * song is loaded, and keep-alive's quiet one when none is.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        resync()
        when (foregroundPlan(KeepAlive.on(this), showsSong(session.player), holding)) {
            ForegroundPlan.AS_MEDIA3 -> {
                super.onUpdateNotification(session, startInForegroundRequired)
                holding = if (heldByMedia3()) Holding.SONG else Holding.NOTHING
            }
            ForegroundPlan.LET_GO_OF_READY -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                holding = Holding.NOTHING
                super.onUpdateNotification(session, startInForegroundRequired)
                holding = if (heldByMedia3()) Holding.SONG else Holding.NOTHING
            }
            ForegroundPlan.SONG_KEPT -> {
                // Throws when Android refuses the foreground, which Media3 catches and tells the listener set above.
                super.onUpdateNotification(session, true)
                if (heldByMedia3()) {
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

    /**
     * Keep-alive's quiet notification in place of the music's, without leaving the foreground. Held here, it is not
     * wanted from the quiet notification's own service as well: the two share its id, so it showed once, but the
     * other service has nothing left to do.
     */
    private fun showReady() {
        try {
            ServiceCompat.startForeground(this, KeepAliveNotification.ID, KeepAliveNotification.build(this), readyForegroundType(Build.VERSION.SDK_INT))
            holding = Holding.READY
            KeepAliveService.standDown(this)
        } catch (refusal: IllegalStateException) {
            KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
            holding = Holding.NOTHING
        } catch (refusal: SecurityException) {
            KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
            holding = Holding.NOTHING
        }
    }

    /**
     * Whether Android has this service in the foreground now. From Android 10, Android itself is asked: it takes a
     * service out of the foreground without a word to it -- a phone restricting Noctorium in the background does it
     * the moment Noctorium is left -- and nothing here would otherwise know. Before 10, what this did last is all
     * there is to go on.
     */
    private fun inForeground(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE else holding != Holding.NOTHING

    /** Whether Media3 put the music's notification in the foreground just now: by Android's word, where it can be had. */
    private fun heldByMedia3(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) inForeground() else isPlaybackOngoing

    /**
     * [holding] put right where Android took the foreground away. Left as it was, it went on saying the music held the
     * foreground after Android had taken it -- so keep-alive did not start its own service, believing the music held
     * it, and nothing here thought anything was wrong.
     */
    private fun resync() {
        if (holding != Holding.NOTHING && !inForeground()) holding = Holding.NOTHING
    }

    /** Whether this held the foreground at some point while the music has been playing: a loss of it is Android's doing. */
    private var heldWhilePlaying = false

    /** Whether the loss going on now has been noted, so that a long one is noted once rather than every ten seconds. */
    private var lossNoted = false

    /** A moment after the player changes, once Media3 has had its own go at the notification: see [winForegroundBack]. */
    private val afterChange = Runnable { winForegroundBack(ForegroundCheck.EVENT) }

    /** What the player does, watched for [winForegroundBack]. */
    private val watch = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                main.removeCallbacks(afterChange)
                main.postDelayed(afterChange, AFTER_CHANGE_MS)
            }
        }
    }

    /**
     * While the music plays here, makes sure it has the foreground, and takes it back if Android has taken it.
     *
     * Media3 goes into the foreground when its notification changes, and never asks whether it is still there; so once
     * Android had taken the foreground away, the music went on without it, song after song, as a plain background
     * process -- which a phone that freezes cached apps freezes. This asks Android, after each change of what the
     * player does, every ten seconds while it plays, and whenever Noctorium comes to the front (see BackgroundPlay),
     * and puts the music's notification back in the foreground where it has gone, as Media3 would for a song starting.
     * Android may refuse that from the background; the refusal, or the restriction behind it, is noted for the listener.
     */
    internal fun winForegroundBack(check: ForegroundCheck) {
        val current = session ?: return
        resync()
        if (!runsInForegroundByItself(current.player)) {
            heldWhilePlaying = false
            lossNoted = false
            return
        }
        if (inForeground()) {
            heldWhilePlaying = true
            lossNoted = false
            return
        }
        val wasHeld = heldWhilePlaying
        // Media3 lets go of a session whose controller went away, and would then show nothing for it.
        addSession(current)
        var refused = false
        try {
            onUpdateNotification(current, true)
        } catch (refusal: IllegalStateException) {
            refused = true
            KeepAlive.refused(this, KeepAliveStart.MUSIC, refusal)
        } catch (refusal: SecurityException) {
            refused = true
        }
        val regained = inForeground()
        if (regained) heldWhilePlaying = true
        android.util.Log.i(LOG_TAG, "Playing out of the foreground ($check); ${if (regained) "taken back" else "not taken back"}")
        // Noted when Android took it, or when it could not be had at all; not for the moment before Media3 has it.
        if ((wasHeld || !regained) && !lossNoted) {
            BackgroundPlay.interrupted(this, interruptionFor(BackgroundPlayback.restrictedInBackground(this), refused, regained))
        }
        lossNoted = !regained
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
        main.removeCallbacks(afterChange)
        // Stopped by Android while the music plays on: noted, and the application brings a service up again for it.
        session?.player?.let { player ->
            player.removeListener(watch)
            if (runsInForegroundByItself(player)) {
                BackgroundPlay.interrupted(this, interruptionFor(BackgroundPlayback.restrictedInBackground(this), refused = false, regained = false, stopped = true))
            }
        }
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
        private const val LOG_TAG = "NoctoriumPlayback"

        /** How long after the player changes the foreground is checked on: Media3 sees the change a moment later. */
        private const val AFTER_CHANGE_MS = 1_500L

        private var running: WeakReference<PlaybackService>? = null

        /** The service, if it is running in this process. */
        internal fun current(): PlaybackService? = running?.get()

        /** What the service holds, put right where Android has taken the foreground away: see [resync]. */
        internal fun resyncHeld() {
            current()?.resync()
        }

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
