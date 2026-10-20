package app.noctorium.android

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Why Noctorium stopped, as Android remembers it.
 *
 * From Android 11, Android keeps a short record of every time an app's process ended and why: it crashed, the phone
 * ran short of memory, something killed it, the listener force-stopped it. That is the honest answer to "does this
 * phone close Noctorium in the background?", so the keep-alive section counts from it -- the times the phone stopped
 * Noctorium while it was doing something, not the times the listener did, and not the ordinary tidying away of an
 * app nobody was using. Crashes are Noctorium's own doing and are said apart.
 */

/** One time Noctorium's process ended: an [ApplicationExitInfo], kept to what is read from it. */
internal data class Exit(
    /** An [ApplicationExitInfo] reason. */
    val reason: Int,
    /** How much the process mattered to Android at the time: an [ActivityManager.RunningAppProcessInfo] importance. */
    val importance: Int,
    val atMs: Long,
    /** Android's own note, or the phone maker's, such as "remove task" for a swipe from the recent apps. */
    val description: String? = null,
)

/** What an exit was, as the section counts it. */
internal enum class ExitKind {
    /** Android, or the phone maker's power manager, stopped Noctorium while it was doing something in the background. */
    STOPPED,

    /** Noctorium crashed, or stopped answering. */
    CRASHED,

    /** The listener's own doing, an update, or the ordinary end of an app nobody was using: not counted. */
    NOT_COUNTED,
}

/**
 * Words in Android's or a maker's note that say a power manager did it, for a force stop that would otherwise read as
 * the listener's own. The notes are not documented, and differ between phones and versions: what these catch is
 * counted, and a force stop that names none of them is taken to be the listener's.
 */
private val POWER_MANAGER_NOTES = listOf(
    "powerkeeper", "power keeper", "smartpower", "greeze", "securitycenter", "powergenie", "hwpfw", "athena",
    "phonemaster", "batterysaver", "battery saver", "autostart", "app freeze", "deep clean", "oneclean", "one key clean",
)

/** Android's notes for the listener's own swipe from the recent apps. */
private val SWIPE_NOTES = listOf("remove task", "removetask", "swipe")

/** Whether [exit] counts as the phone stopping Noctorium, as Noctorium crashing, or not at all. */
internal fun kindOf(exit: Exit): ExitKind {
    val note = exit.description.orEmpty().lowercase()
    return when (exit.reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> ExitKind.CRASHED
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_FREEZER,
        ApplicationExitInfo.REASON_OTHER ->
            if (inTheBackgroundDoingSomething(exit.importance) && SWIPE_NOTES.none(note::contains)) ExitKind.STOPPED else ExitKind.NOT_COUNTED
        // A force stop: the listener's, from Android's app settings or a swipe on phones whose swipe force-stops,
        // unless the note names a power manager.
        ApplicationExitInfo.REASON_USER_REQUESTED ->
            if (POWER_MANAGER_NOTES.any(note::contains)) ExitKind.STOPPED else ExitKind.NOT_COUNTED
        // Its own exit, a permission the listener took back, an update, the phone's user switched: none the phone's doing.
        else -> ExitKind.NOT_COUNTED
    }
}

/**
 * Not on the screen, and not an app nobody was using: playing, kept alive, or otherwise at work in the background.
 *
 * On the screen, a process is the one thing a phone keeps. Cached -- in the background with nothing running -- is an
 * app Android may tidy away whenever it likes, every phone does, and nothing is lost when it does.
 */
private fun inTheBackgroundDoingSomething(importance: Int): Boolean =
    importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
        importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED

/** Why the phone stopped Noctorium, in a few words: what follows "the last time, ... :". */
internal fun whyInWords(exit: Exit): String {
    val note = exit.description.orEmpty().lowercase()
    val byMaker = POWER_MANAGER_NOTES.any(note::contains)
    return when (exit.reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "the phone ran short of memory"
        ApplicationExitInfo.REASON_SIGNALED -> if (byMaker) "killed by the phone maker's power manager" else "killed by the system or the phone maker's power manager"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "closed for using too much of the battery in the background"
        ApplicationExitInfo.REASON_FREEZER -> "closed while Android held it frozen"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "force-stopped by the phone maker's power manager"
        ApplicationExitInfo.REASON_OTHER -> if (byMaker) "closed by the phone maker's power manager" else "closed by the system"
        ApplicationExitInfo.REASON_ANR -> "it stopped responding"
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "it crashed"
        else -> "closed"
    }
}

/** What the section says about the last while: how often the phone stopped Noctorium, and the last time and why. */
internal data class ExitSummary(
    val stopped: Int = 0,
    val lastStopped: Exit? = null,
    val crashed: Int = 0,
)

/** How far back "lately" goes. Android keeps only the last few exits, and a week is about what they cover. */
internal const val LATELY_MS = 7L * 24 * 60 * 60 * 1_000

/** The exits since [LATELY_MS] before [nowMs], counted. */
internal fun summarise(exits: List<Exit>, nowMs: Long): ExitSummary {
    val lately = exits.filter { it.atMs in (nowMs - LATELY_MS)..nowMs }
    val stopped = lately.filter { kindOf(it) == ExitKind.STOPPED }
    return ExitSummary(
        stopped = stopped.size,
        lastStopped = stopped.maxByOrNull(Exit::atMs),
        crashed = lately.count { kindOf(it) == ExitKind.CRASHED },
    )
}

/** "Once", "twice", "3 times". */
internal fun timesInWords(count: Int): String = when (count) {
    1 -> "once"
    2 -> "twice"
    else -> "$count times"
}

/** When [atMs] was, said from [nowMs]: "today at 14:02", "yesterday at 09:15", "on Monday at 22:40", "on 3 October at 08:00". */
internal fun whenInWords(atMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val at = Instant.ofEpochMilli(atMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    val day: LocalDate = at.toLocalDate()
    return when {
        day == today -> "today at $time"
        day == today.minusDays(1) -> "yesterday at $time"
        day.isAfter(today.minusDays(7)) -> "on ${at.format(DateTimeFormatter.ofPattern("EEEE", locale))} at $time"
        else -> "on ${at.format(DateTimeFormatter.ofPattern("d MMMM", locale))} at $time"
    }
}

/** The section's line about it, or null with nothing to say. */
internal fun stoppedInWords(summary: ExitSummary, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
    val last = summary.lastStopped ?: return null
    return "Stopped in the background ${timesInWords(summary.stopped)} in the last week. The last time, " +
        "${whenInWords(last.atMs, nowMs, zone, locale)}: ${whyInWords(last)}."
}

/** The section's line about crashes, said apart, since they are not the phone's doing. */
internal fun crashesInWords(summary: ExitSummary): String? = summary.crashed.takeIf { it > 0 }?.let {
    "Noctorium also crashed ${timesInWords(it)} in the last week. That was Noctorium, not the phone."
}

/** Android's record of how Noctorium's process ended lately; nothing before Android 11, which keeps none. */
internal fun recentExits(context: Context): List<Exit> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
    val activities = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
    return runCatching {
        activities.getHistoricalProcessExitReasons(context.packageName, 0, 0).map {
            Exit(it.reason, it.importance, it.timestamp, it.description)
        }
    }.getOrDefault(emptyList())
}
