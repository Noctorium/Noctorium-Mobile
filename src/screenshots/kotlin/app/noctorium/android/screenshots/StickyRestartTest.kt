package app.noctorium.android.screenshots

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
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
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.PlaybackHost
import app.noctorium.android.PlaybackService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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

    /** The songs loaded, from the first, [atMs] in, and playing: what picking a song up does. */
    fun start(songs: List<String>, atMs: Long) {
        titles = songs
        index = 0
        positionMs = atMs
        playing = true
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
            .setPlaybackState(Player.STATE_READY)
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

    @Test
    fun `restarted by Android mid-song, the song is picked up with one notification, which follows it`() {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 41)
        val clocks = Clocks.now(app)
        KeepAliveStore(app).heartbeat = Heartbeat("SOUNDCLOUD:willow", 38_760, clocks.elapsedMs - 5_000, clocks.bootCount, clocks.bootedAtMs)
        KeepAlive.processStarted(app, on = true)

        // Android starts both services again by itself: no intent, and nothing binds to either.
        val playback = Robolectric.buildService(PlaybackService::class.java).create()
        playback.get().onStartCommand(null, 0, 1)
        val keepAlive = Robolectric.buildService(KeepAliveService::class.java).create()
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

        val playback = Robolectric.buildService(PlaybackService::class.java).create()
        playback.get().onStartCommand(null, 0, 1)
        val keepAlive = Robolectric.buildService(KeepAliveService::class.java).create()
        keepAlive.get().onStartCommand(null, 0, 1)
        settle()

        assertEquals(emptyList(), app.pickedUp, "nothing was playing, so nothing starts")
        assertEquals(listOf(KeepAliveNotification.ID), notifications.activeNotifications.map { it.id })
        assertEquals("Keep Noctorium alive", notifications.activeNotifications.single().notification.title())

        keepAlive.destroy()
        playback.destroy()
    }
}
