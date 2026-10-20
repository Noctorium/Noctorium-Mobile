package app.noctorium.android.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.noctorium.android.BackgroundPlayback
import app.noctorium.android.ExitSummary
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.LATELY_MS
import app.noctorium.android.MakerPageKind
import app.noctorium.android.PhoneMaker
import app.noctorium.android.crashesInWords
import app.noctorium.android.dontKillMyAppPage
import app.noctorium.android.recentExits
import app.noctorium.android.stoppedInWords
import app.noctorium.android.summarise
import app.noctorium.android.timesInWords
import app.noctorium.core.AppState
import app.noctorium.settings.SettingsState

/**
 * What the keep-alive section says about this phone: how often it stopped Noctorium lately, how often Android refused
 * to let keep-alive start again, which of the phone's own settings are seen to, and who made it.
 *
 * Read from the phone in the app, and again each time Noctorium comes back to the front -- which is how a choice made
 * in the phone's settings shows up here at once. The screenshots hand the section a made-up one instead.
 */
@Immutable
internal data class KeepAliveFacts(
    /** Null before Android 11, which keeps no record of why an app stopped. */
    val exits: ExitSummary? = null,
    /** The times in the last week Android would not let keep-alive start again from the background. */
    val refusals: Int = 0,
    val unrestricted: Boolean = true,
    val notificationsAllowed: Boolean = true,
    /** Android 13 and later, where notifications have to be allowed before any show at all. */
    val asksForNotifications: Boolean = true,
    val maker: PhoneMaker? = null,
    /** What the phone says it is by, for dontkillmyapp.com's page about a maker that has no entry here. */
    val manufacturer: String = "",
    val hintDismissed: Boolean = false,
    /** The moment these were read, which "today at 14:02" is said from. */
    val nowMs: Long = 0,
) {
    companion object {
        fun read(context: Context): KeepAliveFacts {
            val now = System.currentTimeMillis()
            val store = KeepAliveStore(context)
            return KeepAliveFacts(
                exits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) summarise(recentExits(context), now) else null,
                refusals = store.refusals.count { it.atMs in (now - LATELY_MS)..now },
                unrestricted = BackgroundPlayback.unrestricted(context),
                notificationsAllowed = BackgroundPlayback.notificationsAllowed(context),
                asksForNotifications = Build.VERSION.SDK_INT >= 33,
                maker = PhoneMaker.thisPhone,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                hintDismissed = store.hintDismissed,
                nowMs = now,
            )
        }
    }
}

/** The screenshots' made-up phone; null in the app, where the phone itself is read. */
internal val LocalKeepAlivePicture = staticCompositionLocalOf<KeepAliveFacts?> { null }

@Composable
private fun rememberKeepAliveFacts(): KeepAliveFacts {
    LocalKeepAlivePicture.current?.let { return it }
    val context = LocalContext.current
    var facts by remember { mutableStateOf(KeepAliveFacts.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { facts = KeepAliveFacts.read(context) }
    return facts
}

/**
 * "Keep Noctorium alive": the switch, what this phone has done to Noctorium lately, and -- while it is on -- what the
 * phone has to be told for it to work, each with the way to tell it, and what nothing can get round.
 */
@Composable
internal fun KeepAliveCard(settings: SettingsState, state: AppState) {
    val context = LocalContext.current
    val facts = rememberKeepAliveFacts()
    val on = settings.preferences.phone.keepAlive

    SettingsCardShell(anchor = SettingIds.KEEP_ALIVE) {
        CardHeading(Glyph.MONITOR_HEART.image, "Keep Noctorium alive")
        Spacer(Modifier.height(6.dp))
        Toggle(
            "Keep it running in the background",
            "For phones that close apps in the background. Noctorium stays running with a quiet notification, starts " +
                "again after the phone restarts, and carries on with a song it was stopped in the middle of.",
            on,
        ) { state.updatePhone { copy(keepAlive = it) } }

        // What the phone has done lately, from Android's own record.
        facts.exits?.let { exits ->
            Spacer(Modifier.height(4.dp))
            val stopped = stoppedInWords(exits, facts.nowMs)
            if (stopped != null) {
                Line(Glyph.BATTERY_ALERT, stopped, warning = true)
            } else {
                Line(Glyph.CHECK_CIRCLE, "Not stopped in the background in the last week.")
            }
            crashesInWords(exits)?.let { Line(Glyph.ERROR_OUTLINE, it) }
        }
        if (on && facts.refusals > 0) {
            Line(
                Glyph.WARNING,
                "Android would not let it start again by itself ${timesInWords(facts.refusals)} in the last week. " +
                    "Allowing it to run in the background, below, lets it.",
                warning = !facts.unrestricted,
            )
        }

        if (on) {
            Spacer(Modifier.height(10.dp))
            Text("What this phone needs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Step(
                done = facts.unrestricted,
                glyph = Glyph.BATTERY_FULL,
                title = if (facts.unrestricted) "Allowed to run in the background" else "Not yet allowed to run in the background",
                detail = if (facts.maker?.needsBackgroundExemption == true) {
                    "Android's battery optimisation. Without it, Android will not let Noctorium start itself again once it is stopped."
                } else {
                    "Android's battery optimisation. Allowed, Noctorium may also start itself again after being stopped."
                },
                button = "Allow".takeUnless { facts.unrestricted },
                needed = facts.maker?.needsBackgroundExemption == true,
            ) { BackgroundPlayback.ask(context) }
            if (facts.asksForNotifications) {
                Step(
                    done = facts.notificationsAllowed,
                    glyph = Glyph.CHECK_CIRCLE,
                    title = if (facts.notificationsAllowed) "Notifications allowed" else "Notifications not allowed",
                    detail = "So the quiet notification can show. Android's settings can hide just that one, by its name.",
                    button = "Allow".takeUnless { facts.notificationsAllowed },
                ) { BackgroundPlayback.openNotificationSettings(context) }
            }
            val pages = facts.maker?.pages.orEmpty()
            pages.forEach { page ->
                Step(
                    done = null,
                    glyph = if (page.kind == MakerPageKind.BATTERY) Glyph.BATTERY_FULL else Glyph.PHONE_ANDROID,
                    title = page.title,
                    detail = page.detail,
                    button = "Open",
                    needed = page.needed,
                ) { BackgroundPlayback.openMakerPage(context, page) }
            }
            if (pages.isEmpty()) {
                Step(
                    done = null,
                    glyph = Glyph.PHONE_ANDROID,
                    title = "Battery, on Noctorium's app info page",
                    detail = "Where this phone keeps anything more about running in the background.",
                    button = "Open",
                ) { BackgroundPlayback.openAppInfo(context) }
            }
            Step(
                done = null,
                glyph = Glyph.PUBLIC,
                title = "How to, on dontkillmyapp.com",
                detail = facts.maker?.let { "What else ${it.displayName} phones need, step by step." }
                    ?: "What else phones like this one need, step by step.",
                button = "Open",
            ) { BackgroundPlayback.openWebPage(context, dontKillMyAppPage(facts.maker, facts.manufacturer)) }
            facts.maker?.tip?.let { Step(done = null, glyph = Glyph.LOCK, title = it, detail = null, button = null) {} }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "A force stop, from Android's app settings or a phone maker's clean-up that force-stops, keeps any app off " +
                "until it is opened again. Nothing can get round that; keep-alive comes back the next time Noctorium opens.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

/** A line of what the phone has done: an icon and the words, in the error colour for what is worth fixing. */
@Composable
private fun Line(glyph: Glyph, text: String, warning: Boolean = false) {
    val colour = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(glyph.image, null, Modifier.padding(top = 1.dp).size(16.dp), tint = colour)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (warning) colour else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * One thing the phone has to be told, with the way to tell it. [done] is ticked when Noctorium can tell it has been,
 * marked when it can tell it has not, and null -- the line's own icon -- where the phone gives no way of knowing.
 * [needed] says, above the rest, that keep-alive does not work on this phone without it: for a page that cannot be
 * read back, so that a line with no tick is not taken for one that does not matter.
 */
@Composable
private fun Step(
    done: Boolean?,
    glyph: Glyph,
    title: String,
    detail: String?,
    button: String?,
    needed: Boolean = false,
    onClick: () -> Unit,
) {
    val (icon, tint) = when (done) {
        true -> Glyph.CHECK_CIRCLE to MaterialTheme.colorScheme.primary
        false -> Glyph.ERROR_OUTLINE to MaterialTheme.colorScheme.error
        null -> glyph to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon.image, if (done == true) "Done" else null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (needed) {
                Text(
                    "Needed on this phone",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(title, fontSize = 12.sp, lineHeight = 16.sp, color = if (done == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            detail?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (button != null) {
            Spacer(Modifier.width(6.dp))
            SkinnedTextButton(onClick) { Text(button) }
        }
    }
}

/**
 * Asked once, in Settings, on a phone that has stopped Noctorium in the background lately or whose maker is known to:
 * whether to keep it alive. Put away for good by either answer, and not shown while keep-alive is on.
 */
@Composable
internal fun KeepAliveHint(settings: SettingsState, state: AppState, open: () -> Unit) {
    val context = LocalContext.current
    val picture = LocalKeepAlivePicture.current
    val facts = rememberKeepAliveFacts()
    var dismissed by remember { mutableStateOf(facts.hintDismissed) }
    val stopped = facts.exits?.stopped ?: 0
    if (settings.preferences.phone.keepAlive || dismissed || facts.hintDismissed) return
    if (stopped == 0 && facts.maker == null) return

    val put = {
        dismissed = true
        // A picture's made-up phone has nowhere to put it away.
        if (picture == null) KeepAliveStore(context).hintDismissed = true
    }
    SettingsCardShell {
        CardHeading(Glyph.MONITOR_HEART.image, "Keep Noctorium alive?")
        Spacer(Modifier.height(6.dp))
        Text(
            (if (stopped > 0) {
                "This phone stopped Noctorium in the background ${timesInWords(stopped)} in the last week. "
            } else {
                "${facts.maker?.displayName}'s phones often close apps in the background. "
            }) + "Keep Noctorium alive keeps it running with a quiet notification, and brings it back when it is stopped.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinnedButton({
                put()
                state.updatePhone { copy(keepAlive = true) }
                open()
            }) { Text("Turn on") }
            SkinnedTextButton(put) { Text("Not now") }
        }
    }
}

