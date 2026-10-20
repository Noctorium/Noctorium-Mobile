package app.noctorium.android

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which of the times Noctorium's process ended count as the phone stopping it, which as crashes, and which not at all;
 * and the words the keep-alive section says them in.
 */
class ExitReasonsTest {
    private val zone = ZoneId.of("Europe/London")
    private val locale = Locale.UK
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Tuesday 20 October 2026, a quarter past six in the evening. */
    private val now = at(20, 18, 15)

    private val playing = RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    private val onScreen = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    private val cached = RunningAppProcessInfo.IMPORTANCE_CACHED

    private fun exit(reason: Int, importance: Int = playing, atMs: Long = now - 60_000, note: String? = null) = Exit(reason, importance, atMs, note)

    @Test
    fun `the phone running short of memory, a kill, and Android's own closing count, while it was at work in the background`() {
        for (reason in listOf(
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_FREEZER,
        )) {
            assertEquals(ExitKind.STOPPED, kindOf(exit(reason)), "reason $reason")
            assertEquals(ExitKind.STOPPED, kindOf(exit(reason, RunningAppProcessInfo.IMPORTANCE_SERVICE)), "reason $reason, a service")
            assertEquals(ExitKind.STOPPED, kindOf(exit(reason, RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE)), "reason $reason, perceptible")
        }
    }

    @Test
    fun `an app nobody was using tidied away, or one on the screen, does not`() {
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_LOW_MEMORY, cached)))
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_OTHER, cached, note = "empty for 1800s")))
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_SIGNALED, onScreen)))
    }

    @Test
    fun `the listener's own force stop and swipe do not`() {
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_USER_REQUESTED, note = "stop app.noctorium due to from pid 1234")))
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_USER_REQUESTED)))
        assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(ApplicationExitInfo.REASON_OTHER, note = "remove task")))
    }

    @Test
    fun `a force stop that names a phone maker's power manager does`() {
        assertEquals(ExitKind.STOPPED, kindOf(exit(ApplicationExitInfo.REASON_USER_REQUESTED, note = "com.miui.powerkeeper: kill background")))
        assertEquals("force-stopped by the phone maker's power manager", whyInWords(exit(ApplicationExitInfo.REASON_USER_REQUESTED, note = "Powerkeeper")))
    }

    @Test
    fun `crashes and stopping answering are said apart`() {
        for (reason in listOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR)) {
            assertEquals(ExitKind.CRASHED, kindOf(exit(reason)), "reason $reason")
            assertEquals(ExitKind.CRASHED, kindOf(exit(reason, onScreen)), "reason $reason, on the screen")
        }
    }

    @Test
    fun `its own exit, a permission taken back, an update and the rest are none of the phone's doing`() {
        for (reason in listOf(
            ApplicationExitInfo.REASON_UNKNOWN,
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_PERMISSION_CHANGE,
            ApplicationExitInfo.REASON_USER_STOPPED,
            ApplicationExitInfo.REASON_DEPENDENCY_DIED,
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        )) {
            assertEquals(ExitKind.NOT_COUNTED, kindOf(exit(reason)), "reason $reason")
        }
    }

    @Test
    fun `why, in words`() {
        assertEquals("the phone ran short of memory", whyInWords(exit(ApplicationExitInfo.REASON_LOW_MEMORY)))
        assertEquals("killed by the system or the phone maker's power manager", whyInWords(exit(ApplicationExitInfo.REASON_SIGNALED)))
        assertEquals("killed by the phone maker's power manager", whyInWords(exit(ApplicationExitInfo.REASON_SIGNALED, note = "SmartPower kill")))
        assertEquals("closed by the system", whyInWords(exit(ApplicationExitInfo.REASON_OTHER, note = "kill background")))
        assertEquals("closed by the phone maker's power manager", whyInWords(exit(ApplicationExitInfo.REASON_OTHER, note = "AutoPowerKill by powerkeeper")))
        assertEquals("closed for using too much of the battery in the background", whyInWords(exit(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)))
        assertEquals("closed while Android held it frozen", whyInWords(exit(ApplicationExitInfo.REASON_FREEZER)))
    }

    @Test
    fun `when, in words, from the moment they are read`() {
        assertEquals("today at 14:02", whenInWords(at(20, 14, 2), now, zone, locale))
        assertEquals("yesterday at 09:15", whenInWords(at(19, 9, 15), now, zone, locale))
        assertEquals("on Saturday at 22:40", whenInWords(at(17, 22, 40), now, zone, locale))
        assertEquals("on 12 October at 08:00", whenInWords(at(12, 8, 0), now, zone, locale))
    }

    @Test
    fun `the last week is counted, the last of it said, and crashes apart`() {
        val exits = listOf(
            exit(ApplicationExitInfo.REASON_SIGNALED, atMs = at(20, 14, 2)),
            exit(ApplicationExitInfo.REASON_LOW_MEMORY, atMs = at(18, 3, 30)),
            exit(ApplicationExitInfo.REASON_OTHER, atMs = at(16, 23, 0)),
            // Not the phone's doing, or too long ago, or the listener's.
            exit(ApplicationExitInfo.REASON_LOW_MEMORY, cached, atMs = at(19, 12, 0)),
            exit(ApplicationExitInfo.REASON_SIGNALED, atMs = at(10, 12, 0)),
            exit(ApplicationExitInfo.REASON_USER_REQUESTED, atMs = at(20, 9, 0)),
            exit(ApplicationExitInfo.REASON_CRASH, onScreen, atMs = at(19, 20, 0)),
        )
        val summary = summarise(exits, now)
        assertEquals(3, summary.stopped)
        assertEquals(at(20, 14, 2), summary.lastStopped?.atMs)
        assertEquals(1, summary.crashed)
        assertEquals(
            "Stopped in the background 3 times in the last week. The last time, today at 14:02: killed by the system or " +
                "the phone maker's power manager.",
            stoppedInWords(summary, now, zone, locale),
        )
        assertEquals("Noctorium also crashed once in the last week. That was Noctorium, not the phone.", crashesInWords(summary))
    }

    @Test
    fun `nothing in the last week says nothing`() {
        val summary = summarise(emptyList(), now)
        assertEquals(ExitSummary(), summary)
        assertNull(stoppedInWords(summary, now, zone, locale))
        assertNull(crashesInWords(summary))
    }

    @Test
    fun `once, twice, and then a number`() {
        assertEquals("once", timesInWords(1))
        assertEquals("twice", timesInWords(2))
        assertEquals("3 times", timesInWords(3))
        assertEquals("12 times", timesInWords(12))
    }
}
