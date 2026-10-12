package app.noctorium.android

import androidx.media3.common.Player
import app.noctorium.android.QueueAwarePlayer.RemotePlaying
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The media session's player while the music is on another device: a headset's button, a car's, or a notification
 * left over from before drives the device the music is on, through the application's own play button, and starts
 * nothing here.
 */
class QueueAwarePlayerTest {

    /** What reached the player underneath, by name. */
    private val reached = mutableListOf<String>()

    /** A player that does nothing but note what it was asked. */
    private val underneath: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        reached += method.name
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            else -> null
        }
    } as Player

    private var toggled = 0

    private fun player(remote: RemotePlaying?) = QueueAwarePlayer(
        player = underneath,
        goNext = {},
        goPrevious = {},
        canGoNext = { true },
        canGoPrevious = { true },
        remote = { remote },
        togglePlayback = { toggled++ },
    )

    @Test
    fun `with the music here, play and pause are the player's own`() {
        val player = player(remote = null)
        player.prepare()
        player.play()
        player.pause()
        assertEquals(listOf("prepare", "play", "pause"), reached)
        assertEquals(0, toggled)
    }

    @Test
    fun `with the music paused on another device, play starts it there and nothing here`() {
        val player = player(RemotePlaying.PAUSED)
        player.prepare()
        player.play()
        player.pause()
        player.setPlayWhenReady(true)
        assertEquals(emptyList(), reached, "nothing was loaded or started here")
        assertEquals(2, toggled, "play, and play when ready, each pressed the other device's play")
    }

    @Test
    fun `with the music playing on another device, pause pauses it there`() {
        val player = player(RemotePlaying.PLAYING)
        player.play()
        player.pause()
        player.setPlayWhenReady(false)
        assertEquals(emptyList(), reached)
        assertEquals(2, toggled)
    }
}
