package app.noctorium.android

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Which song was playing and how far in, noted every few seconds while one plays: what Noctorium picks up from when
 * it comes back after the phone stopped it mid-song.
 */
internal data class Heartbeat(
    val queueKey: String,
    val positionMs: Long,
    /** When, by the clock that counts from the phone starting: what "a moment ago" is measured on. */
    val atElapsedMs: Long,
    /** Which time since the phone started it was: Android's count of starts, and when this one began by the wall clock. */
    val bootCount: Int,
    val bootedAtMs: Long,
)

/** The phone's clocks at one moment: the one that counts from its start, the wall clock, and its count of starts. */
internal data class Clocks(val elapsedMs: Long, val wallMs: Long, val bootCount: Int) {
    /** When the phone started, by the wall clock: the same, within a few seconds, for as long as it stays on. */
    val bootedAtMs: Long get() = wallMs - elapsedMs

    companion object {
        fun now(context: Context): Clocks = Clocks(
            SystemClock.elapsedRealtime(),
            System.currentTimeMillis(),
            // Android's own count, where the phone keeps one; -1 on the few that do not, and then the start time decides.
            runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
        )
    }
}

/** A time Android would not let Noctorium keep itself alive from the background. */
internal data class Refusal(val start: KeepAliveStart, val atMs: Long)

/**
 * What keep-alive remembers on this phone and nowhere else: the heartbeat, the times Android refused it, and whether
 * the hint suggesting it was put away.
 *
 * Not in Noctorium's settings file, which is the listener's choices; these are notes the phone takes. A shared
 * preferences file, since the heartbeat is written every few seconds while music plays, and that is what it is for.
 */
internal class KeepAliveStore(context: Context) {
    private val notes = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var heartbeat: Heartbeat?
        get() = runCatching {
            val song = notes.getString(SONG, null) ?: return null
            Heartbeat(song, notes.getLong(POSITION, 0), notes.getLong(AT, 0), notes.getInt(BOOT_COUNT, -1), notes.getLong(BOOTED_AT, 0))
        }.getOrNull()
        set(beat) {
            val edit = notes.edit()
            if (beat == null) {
                listOf(SONG, POSITION, AT, BOOT_COUNT, BOOTED_AT).forEach(edit::remove)
            } else {
                edit.putString(SONG, beat.queueKey)
                    .putLong(POSITION, beat.positionMs)
                    .putLong(AT, beat.atElapsedMs)
                    .putInt(BOOT_COUNT, beat.bootCount)
                    .putLong(BOOTED_AT, beat.bootedAtMs)
            }
            edit.apply()
        }

    /** The times Android refused, newest last; only the last few are kept. */
    val refusals: List<Refusal>
        get() = notes.getString(REFUSALS, null).orEmpty().split(',').mapNotNull { entry ->
            val (start, at) = entry.split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
            val kind = runCatching { KeepAliveStart.valueOf(start) }.getOrNull() ?: return@mapNotNull null
            at.toLongOrNull()?.let { Refusal(kind, it) }
        }

    fun refused(refusal: Refusal) {
        val kept = (refusals + refusal).takeLast(KEPT_REFUSALS)
        notes.edit().putString(REFUSALS, kept.joinToString(",") { "${it.start.name}@${it.atMs}" }).apply()
    }

    /** Whether the one-time hint suggesting keep-alive was put away, or acted on. */
    var hintDismissed: Boolean
        get() = notes.getBoolean(HINT_DISMISSED, false)
        set(value) = notes.edit().putBoolean(HINT_DISMISSED, value).apply()

    /** The times the music lost the foreground in the background, newest last; only the last few are kept. */
    val interruptions: List<Interrupted>
        get() = notes.getString(INTERRUPTIONS, null).orEmpty().split(',').mapNotNull { entry ->
            val (why, at) = entry.split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
            val kind = runCatching { Interruption.valueOf(why) }.getOrNull() ?: return@mapNotNull null
            at.toLongOrNull()?.let { Interrupted(kind, it) }
        }

    fun interrupted(interruption: Interrupted) {
        val kept = (interruptions + interruption).takeLast(KEPT_REFUSALS)
        notes.edit().putString(INTERRUPTIONS, kept.joinToString(",") { "${it.why.name}@${it.atMs}" }).apply()
    }

    /** Up to when the listener has been told about [interruptions]. */
    var interruptionsSeenAt: Long
        get() = notes.getLong(INTERRUPTIONS_SEEN, 0)
        set(value) = notes.edit().putLong(INTERRUPTIONS_SEEN, value).apply()

    private companion object {
        const val FILE = "keep-alive"
        const val SONG = "heartbeat.song"
        const val POSITION = "heartbeat.position"
        const val AT = "heartbeat.at"
        const val BOOT_COUNT = "heartbeat.bootCount"
        const val BOOTED_AT = "heartbeat.bootedAt"
        const val REFUSALS = "refusals"
        const val HINT_DISMISSED = "hintDismissed"
        const val INTERRUPTIONS = "interruptions"
        const val INTERRUPTIONS_SEEN = "interruptionsSeenAt"
        const val KEPT_REFUSALS = 20
    }
}
