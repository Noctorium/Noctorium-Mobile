package app.noctorium.android

import android.content.Intent
import android.content.pm.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Keep-alive's decisions, which are the whole of what it does apart from asking Android: when a comeback carries on
 * with a song, what the receiver starts, which foreground the media session service holds, and the type the quiet
 * notification is shown under.
 */
class KeepAliveTest {

    /** The phone ten minutes after starting up, by both clocks, on its 41st start. */
    private val now = Clocks(elapsedMs = 600_000, wallMs = 1_760_000_600_000, bootCount = 41)

    /** A heartbeat [ago] milliseconds before [now], in the same boot. */
    private fun beat(ago: Long, at: Clocks = now) = Heartbeat(
        queueKey = "YOUTUBE_MUSIC:abc",
        positionMs = 83_000,
        atElapsedMs = at.elapsedMs - ago,
        bootCount = at.bootCount,
        bootedAtMs = at.bootedAtMs,
    )

    @Test
    fun `stopped mid-song and brought back by Android or the watchdog within ninety seconds, it carries on`() {
        assertTrue(resumes(beat(ago = 5_000), now, KeepAliveStart.STICKY))
        assertTrue(resumes(beat(ago = 45_000), now, KeepAliveStart.WATCHDOG))
        assertTrue(resumes(beat(ago = RESUME_WITHIN_MS), now, KeepAliveStart.STICKY), "ninety seconds exactly still counts")
    }

    @Test
    fun `brought back any later, it waits to be asked`() {
        assertFalse(resumes(beat(ago = RESUME_WITHIN_MS + 1), now, KeepAliveStart.STICKY))
        assertFalse(resumes(beat(ago = 15 * 60_000), now, KeepAliveStart.WATCHDOG))
    }

    @Test
    fun `with no heartbeat -- paused, stopped, or swiped away while paused -- nothing is started again`() {
        // The heartbeat is cleared the moment the music pauses or stops, so each of those leaves none.
        assertFalse(resumes(null, now, KeepAliveStart.STICKY))
        assertFalse(resumes(null, now, KeepAliveStart.WATCHDOG))
    }

    @Test
    fun `never after the phone restarted`() {
        // Android's count of starts moved on.
        assertFalse(resumes(beat(ago = 5_000).copy(bootCount = 40), now, KeepAliveStart.STICKY))
        // A phone that keeps no count: the clock since starting is back near nothing, and the start was later by the
        // wall clock, though the heartbeat looks a moment old by the other clock.
        val restarted = Clocks(elapsedMs = 70_000, wallMs = now.wallMs + 120_000, bootCount = -1)
        val before = Heartbeat("YOUTUBE_MUSIC:abc", 83_000, atElapsedMs = 50_000, bootCount = -1, bootedAtMs = now.bootedAtMs)
        assertFalse(resumes(before, restarted, KeepAliveStart.STICKY))
        // And a heartbeat from later than now, by the clock since starting, can only be from before a restart.
        assertFalse(resumes(beat(ago = -5_000), now, KeepAliveStart.STICKY))
    }

    @Test
    fun `the wall clock set by the network a few seconds is still the same boot`() {
        val nudged = now.copy(wallMs = now.wallMs + 3_000)
        assertTrue(resumes(beat(ago = 5_000), nudged, KeepAliveStart.WATCHDOG))
    }

    @Test
    fun `opened by the listener, after an update or a restart, or turned on, it never starts a song by itself`() {
        for (start in listOf(KeepAliveStart.APP, KeepAliveStart.BOOT, KeepAliveStart.UPDATE, KeepAliveStart.SETTING, KeepAliveStart.MUSIC)) {
            assertFalse(resumes(beat(ago = 5_000), now, start), start.name)
        }
    }

    @Test
    fun `the receiver starts keep-alive after a restart, an update and the watchdog's alarm, and only while it is on`() {
        assertEquals(KeepAliveStart.BOOT, receiverStart(Intent.ACTION_BOOT_COMPLETED, on = true))
        assertEquals(KeepAliveStart.UPDATE, receiverStart(Intent.ACTION_MY_PACKAGE_REPLACED, on = true))
        assertEquals(KeepAliveStart.WATCHDOG, receiverStart(ACTION_WATCHDOG, on = true))
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_WATCHDOG)) {
            assertNull(receiverStart(action, on = false), "$action while off")
        }
        assertNull(receiverStart("android.intent.action.SOMETHING_ELSE", on = true))
        assertNull(receiverStart(null, on = true))
    }

    @Test
    fun `off, the media session service is in the foreground as Media3 has it, and lets go of the quiet notification`() {
        for (song in listOf(true, false)) {
            assertEquals(ForegroundPlan.AS_MEDIA3, foregroundPlan(on = false, song = song, holding = Holding.NOTHING))
            assertEquals(ForegroundPlan.AS_MEDIA3, foregroundPlan(on = false, song = song, holding = Holding.SONG))
            assertEquals(ForegroundPlan.LET_GO_OF_READY, foregroundPlan(on = false, song = song, holding = Holding.READY))
        }
    }

    @Test
    fun `on, a song loaded is held in the foreground whether it plays or not, by the music's own notification`() {
        for (holding in Holding.entries) {
            assertEquals(ForegroundPlan.SONG_KEPT, foregroundPlan(on = true, song = true, holding = holding), holding.name)
        }
    }

    @Test
    fun `on, with the song gone, the quiet notification takes its place without leaving the foreground, or elsewhere`() {
        assertEquals(ForegroundPlan.READY_HERE, foregroundPlan(on = true, song = false, holding = Holding.SONG))
        assertEquals(ForegroundPlan.READY_HERE, foregroundPlan(on = true, song = false, holding = Holding.READY))
        // Not in the foreground already: coming into it here is no surer than the quiet notification's own service.
        assertEquals(ForegroundPlan.READY_ELSEWHERE, foregroundPlan(on = true, song = false, holding = Holding.NOTHING))
    }

    @Test
    fun `the quiet notification is a special use foreground service from Android 14, and untyped before`() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, readyForegroundType(34))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, readyForegroundType(35))
        for (sdk in 26..33) assertEquals(0, readyForegroundType(sdk), "Android API $sdk")
        // Never the media type, which Android 15 will not start from the phone starting up.
        assertTrue((26..36).none { readyForegroundType(it) and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0 })
    }

    @Test
    fun `the quiet notification says what it is, and whether Connect can reach this phone`() {
        assertEquals("Ready to play, and reachable over Noctorium Connect", KeepAliveNotification.text(reachable = true))
        assertEquals("Ready to play", KeepAliveNotification.text(reachable = false))
    }

    @Test
    fun `every way it is started says so in words`() {
        assertEquals(KeepAliveStart.entries.size, KeepAliveStart.entries.map { it.words }.toSet().size)
        assertTrue(KeepAliveStart.entries.all { it.words.isNotBlank() })
    }
}
