package app.noctorium.android.screenshots

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.Clocks
import app.noctorium.android.Heartbeat
import app.noctorium.android.KeepAlive
import app.noctorium.android.KeepAliveHost
import app.noctorium.android.KeepAliveNotification
import app.noctorium.android.KeepAliveService
import app.noctorium.android.KeepAliveStart
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.PlaybackHost
import app.noctorium.android.PlaybackService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A player that only does what it is told: a list of songs by their titles, one of them current, playing or not. What
 * the media session is built around in place of the application's ExoPlayer, so Media3's notification can be watched
 * following it.
 */
@OptIn(UnstableApi::class)
class StillPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var titles = emptyList<String>()
    private var index = 0
    private var playing = false
    private var positionMs = 0L
    private var loaded = false

    /** The songs in the player but not loaded to play: idle, with the song there, as a song is before it is prepared. */
    fun hold(songs: List<String>) {
        titles = songs
        index = 0
        playing = false
        loaded = false
        invalidateState()
    }

    /** The songs loaded, from the first, [atMs] in, and playing: what picking a song up does. */
    fun start(songs: List<String>, atMs: Long) {
        titles = songs
        index = 0
        positionMs = atMs
        playing = true
        loaded = true
        invalidateState()
    }

    override fun getState(): State {
        val state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (titles.isEmpty()) return state.setPlaybackState(Player.STATE_IDLE).build()
        return state
            .setPlaylist(
                titles.mapIndexed { at, title ->
                    MediaItemData.Builder("song-$at")
                        .setMediaItem(
                            MediaItem.Builder().setMediaId("song-$at").setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build(),
                        )
                        .setDurationUs(200_000_000)
                        .build()
                },
            )
            .setCurrentMediaItemIndex(index)
            .setContentPositionMs(positionMs)
            .setPlaybackState(if (loaded) Player.STATE_READY else Player.STATE_IDLE)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playing = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) index = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0 else positionMs
        return Futures.immediateVoidFuture()
    }
}

/** An application that keeps Noctorium alive around [StillPlayer], and picks a song up by starting it. */
class StickyRestartApplication : Application(), KeepAliveHost, PlaybackHost {
    val player by lazy { StillPlayer() }
    val pickedUp = mutableListOf<Pair<String, Long>>()
    override var keepAliveOn = true
    override val reachableOverConnect = false

    override fun turnOffKeepAlive() {
        keepAliveOn = false
    }

    override fun sessionPlayer(): Player = player

    override fun playingHere(): Boolean = PlaybackService.runsInForegroundByItself(player)

    override fun pickUp(queueKey: String, positionMs: Long): Boolean {
        pickedUp += queueKey to positionMs
        player.start(listOf("Willow Creek Morning", "Moon"), positionMs)
        return true
    }
}

/**
 * What a phone did after its power manager killed Noctorium in the middle of a song: Android started both services
 * again by themselves, with nothing bound to either, keep-alive picked the song up -- and Media3's notification was
 * never posted by the new process, so the quiet one stayed beside the dead process's, which went on showing the song it
 * died on whatever played. Here both are restarted that way, the song picked up and then skipped, and there has to be
 * one notification, the music's, and it has to follow the song.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = StickyRestartApplication::class)
class StickyRestartTest {
    private val app: StickyRestartApplication get() = ApplicationProvider.getApplicationContext()
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Before
    fun nothingHeld() {
        KeepAlive.heldByPlayback = false
        KeepAliveStore(app).heartbeat = null
    }

    // Every service a test builds is ended after it, whatever happened in it: a session left unreleased would be in the
    // way of the next test's, and a service left running would read as keep-alive already held.
    private val services = mutableListOf<ServiceController<*>>()

    private fun <T : ServiceController<*>> built(controller: T): T = controller.also { services += it }

    @After
    fun endServices() {
        services.forEach { runCatching { it.destroy() } }
        services.clear()
    }

    /** Services asked to stop, which Android then ends: gone, as far as what shows goes. */
    private val stopped = mutableSetOf<String>()

    /**
     * The notifications the foreground services hold, as Android shows them: one for each id, from the services still
     * running and still in the foreground. Robolectric keeps every notification a service ever put up; Android takes
     * one down when its service moves the foreground to another, or ends.
     */
    private fun shown(vararg services: Service): List<Int> {
        generateSequence { shadowOf(app).nextStoppedService }.forEach { stopped += it.component!!.className }
        return services.filter { service ->
            val shadow = shadowOf(service)
            service.javaClass.name !in stopped && !shadow.isStoppedBySelf && !shadow.isForegroundStopped && shadow.lastForegroundNotificationId != 0
        }.map { shadowOf(it).lastForegroundNotificationId }.distinct()
    }

    /**
     * Android putting the killed process's music notification back up as it starts the service again, with the service
     * still counted in the foreground: what it does for a foreground service it restarts (ActiveServices, on starting a
     * service, posts the notification it was holding). Done before the service is created, as Android does it.
     */
    private fun carriedOver(playback: ServiceController<PlaybackService>, title: String) {
        val old = NotificationCompat.Builder(app, "media").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).build()
        playback.get().startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, old, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    @Test
    fun `restarted by Android mid-song, the song is picked up with one notification, which follows it`() {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 41)
        val clocks = Clocks.now(app)
        KeepAliveStore(app).heartbeat = Heartbeat("SOUNDCLOUD:willow", 38_760, clocks.elapsedMs - 5_000, clocks.bootCount, clocks.bootedAtMs)
        KeepAlive.processStarted(app, on = true)

        // Android starts both services again by itself: no intent, and nothing binds to either.
        val playback = built(Robolectric.buildService(PlaybackService::class.java)).create()
        playback.get().onStartCommand(null, 0, 1)
        val keepAlive = built(Robolectric.buildService(KeepAliveService::class.java)).create()
        keepAlive.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(listOf("SOUNDCLOUD:willow" to 38_760L), app.pickedUp, "the song picked up where it was")
        val service = shadowOf(playback.get())
        assertEquals(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, service.lastForegroundNotificationId, "the music's notification holds the foreground")
        assertEquals("Willow Creek Morning", service.lastForegroundNotification.title())

        // And the quiet one is asked to stand down, which Android does.
        val stopped = generateSequence { shadowOf(app).nextStoppedService }.toList()
        assertTrue(stopped.any { it.component?.className == KeepAliveService::class.java.name }, "the quiet notification's service stopped")
        keepAlive.destroy()
        settle()
        assertEquals(listOf(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID), notifications.activeNotifications.map { it.id }, "one notification")

        // Skipped, as from the lock screen: the notification follows.
        app.player.seekToNextMediaItem()
        settle()
        assertEquals("Moon", service.lastForegroundNotification.title())
        assertEquals(listOf("Moon"), notifications.activeNotifications.map { it.notification.title() })
        assertTrue(KeepAliveNotification.ID !in notifications.activeNotifications.map { it.id })

        playback.destroy()
    }

    @Test
    fun `restarted by Android with nothing playing, only the quiet notification shows`() {
        KeepAliveStore(app).heartbeat = null
        KeepAlive.processStarted(app, on = true)

        val playback = built(Robolectric.buildService(PlaybackService::class.java)).create()
        playback.get().onStartCommand(null, 0, 1)
        val keepAlive = built(Robolectric.buildService(KeepAliveService::class.java)).create()
        keepAlive.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(emptyList(), app.pickedUp, "nothing was playing, so nothing starts")
        assertEquals(listOf(KeepAliveNotification.ID), notifications.activeNotifications.map { it.id })
        assertEquals("Keep Noctorium alive", notifications.activeNotifications.single().notification.title())

        keepAlive.destroy()
        playback.destroy()
    }

    /**
     * Killed while paused: back silent, with its song back in the queue -- here even sitting in the player, idle -- and
     * the old process's music notification put back up by Android, in the foreground. There has to be one notification,
     * the quiet one, held by the media session service in the old one's place; and once the song plays, the music's.
     */
    @Test
    fun `restarted by Android after being killed while paused, there is one notification until the song plays, and one after`() {
        app.player.hold(listOf("took a loss", "Moon"))
        KeepAlive.processStarted(app, on = true)

        val playback = built(Robolectric.buildService(PlaybackService::class.java))
        carriedOver(playback, "took a loss")
        playback.create()
        playback.get().onStartCommand(null, 0, 1)
        // And keep-alive's own service, restarted by Android as well.
        val keepAlive = built(Robolectric.buildService(KeepAliveService::class.java)).create()
        keepAlive.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(emptyList(), app.pickedUp, "paused, so nothing starts")
        assertEquals(listOf(KeepAliveNotification.ID), shown(playback.get(), keepAlive.get()), "one notification, the quiet one")
        assertEquals("Keep Noctorium alive", shadowOf(playback.get()).lastForegroundNotification.title())
        assertTrue(KeepAlive.heldByPlayback, "held by the media session service, which never left the foreground")

        // Played: the music's notification, and still one.
        app.player.start(listOf("took a loss", "Moon"), 0)
        settle()
        assertEquals(listOf(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID), shown(playback.get(), keepAlive.get()))
        assertEquals("took a loss", shadowOf(playback.get()).lastForegroundNotification.title())

        keepAlive.destroy()
        playback.destroy()
    }

    @Test
    fun `the same with keep-alive's own service restarted first, which stands down`() {
        KeepAlive.processStarted(app, on = true)
        val keepAlive = built(Robolectric.buildService(KeepAliveService::class.java)).create()
        keepAlive.get().onStartCommand(null, 0, 1)
        val playback = built(Robolectric.buildService(PlaybackService::class.java))
        carriedOver(playback, "took a loss")
        playback.create()
        playback.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(listOf(KeepAliveNotification.ID), shown(playback.get(), keepAlive.get()))
        assertTrue(KeepAliveService::class.java.name in stopped, "asked to stand down")

        keepAlive.destroy()
        playback.destroy()
    }

    /** Killed mid-song, with the old notification carried over: picked up by the media session service itself. */
    @Test
    fun `restarted by Android mid-song with its notification carried over, the song is picked up there, with one notification following it`() {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 41)
        val clocks = Clocks.now(app)
        KeepAliveStore(app).heartbeat = Heartbeat("SOUNDCLOUD:willow", 38_760, clocks.elapsedMs - 5_000, clocks.bootCount, clocks.bootedAtMs)
        KeepAlive.processStarted(app, on = true)

        val playback = built(Robolectric.buildService(PlaybackService::class.java))
        carriedOver(playback, "Willow Creek Morning")
        playback.create()
        playback.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(listOf("SOUNDCLOUD:willow" to 38_760L), app.pickedUp)
        val asked = generateSequence { shadowOf(app).nextStartedService }.toList()
        assertTrue(asked.none { it.component?.className == KeepAliveService::class.java.name }, "no second service for it")
        assertEquals(listOf(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID), shown(playback.get()))
        assertEquals("Willow Creek Morning", shadowOf(playback.get()).lastForegroundNotification.title())
        app.player.seekToNextMediaItem()
        settle()
        assertEquals("Moon", shadowOf(playback.get()).lastForegroundNotification.title())

        playback.destroy()
    }

    /** Opened by the listener on a kept queue that has not played yet: the quiet notification, and only that. */
    @Test
    fun `opened with a kept queue and nothing played, there is one notification, the quiet one`() {
        app.player.hold(listOf("took a loss"))
        // The screen binds to the media session service, which is created with nothing carried over; and opening
        // Noctorium starts keep-alive.
        val playback = built(Robolectric.buildService(PlaybackService::class.java)).create()
        assertTrue(KeepAlive.start(app, KeepAliveStart.APP))
        val asked = generateSequence { shadowOf(app).nextStartedService }.first { it.component?.className == KeepAliveService::class.java.name }
        val keepAlive = built(Robolectric.buildService(KeepAliveService::class.java, asked)).create().startCommand(0, 1)
        settle()

        assertEquals(listOf(KeepAliveNotification.ID), shown(playback.get(), keepAlive.get()))
        assertEquals(listOf(KeepAliveNotification.ID), notifications.activeNotifications.map { it.id })

        keepAlive.destroy()
        playback.destroy()
    }

    /** With keep-alive off, the notification Android carried over for a song this process never loaded just goes. */
    @Test
    fun `with keep-alive off, the carried-over notification goes`() {
        app.keepAliveOn = false
        val playback = built(Robolectric.buildService(PlaybackService::class.java))
        carriedOver(playback, "took a loss")
        playback.create()
        playback.get().onStartCommand(null, 0, 1)
        settle()
        assertEquals(emptyList(), shown(playback.get()))
        playback.destroy()
    }
}
