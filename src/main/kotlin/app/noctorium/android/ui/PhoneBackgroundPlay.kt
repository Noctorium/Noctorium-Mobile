package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.noctorium.android.BackgroundPlayback
import app.noctorium.android.Interrupted
import app.noctorium.android.Interruption
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.timesInWords
import app.noctorium.settings.ThemeSkin

/*
 * Playing on in the background, said to the listener: a warning wherever they will see it while the phone restricts
 * Noctorium in the background -- which stops the music the moment they switch apps -- and, when they come back after
 * Android took the music out of the foreground, what happened and the setting that stops it.
 */

/** What the warning says. */
internal const val RESTRICTED_WORDS = "Your phone restricts Noctorium in the background, so the music stops when you switch apps."

/**
 * The screenshots' made-up phone: whether it restricts Noctorium in the background, and what happened lately while
 * nobody was looking. Null in the app, where the phone is asked.
 */
@Immutable
internal data class BackgroundPlayPicture(
    val restricted: Boolean,
    val unseen: List<Interrupted> = emptyList(),
    /** Whether the word said as the music starts on a restricted phone is still to be said. */
    val toldAsPlaybackStarts: Boolean = false,
)

internal val LocalBackgroundPlayPicture = staticCompositionLocalOf<BackgroundPlayPicture?> { null }

/** Whether the phone restricts Noctorium in the background, asked again each time Noctorium comes back to the front. */
@Composable
internal fun rememberBackgroundRestricted(): Boolean {
    LocalBackgroundPlayPicture.current?.let { return it.restricted }
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BackgroundPlayback.restrictedInBackground(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = BackgroundPlayback.restrictedInBackground(context) }
    return restricted
}

/**
 * The warning, while the phone restricts Noctorium in the background: above the player bar, where the listener looks
 * when the music is what they are thinking about, with the way to lift it. It goes by itself once lifted.
 */
@Composable
internal fun BackgroundRestrictedBanner(modifier: Modifier = Modifier) {
    if (!rememberBackgroundRestricted()) return
    val context = LocalContext.current
    val skin = LocalSkin.current
    val win98 = Win98
    val ink = when (skin) {
        ThemeSkin.STANDARD -> MaterialTheme.colorScheme.onErrorContainer
        ThemeSkin.WINDOWS_98 -> win98.Text
        ThemeSkin.WINDOWS_XP -> Luna.Text
    }
    val face = when (skin) {
        ThemeSkin.STANDARD -> Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
        ThemeSkin.WINDOWS_98 -> Modifier
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .background(win98.Face)
            .drawWithContent {
                drawContent()
                drawEdge98(Edge98.RAISED, win98)
            }
        ThemeSkin.WINDOWS_XP -> Modifier
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .background(Luna.Window)
            .border(1.dp, Luna.GroupEdge)
    }
    Row(
        modifier.fillMaxWidth().then(face).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyph.WARNING.image, null, Modifier.size(18.dp), tint = if (skin == ThemeSkin.STANDARD) ink else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Text(RESTRICTED_WORDS, Modifier.weight(1f), color = ink, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(6.dp))
        SkinnedTextButton({ BackgroundPlayback.openBackgroundActivity(context) }) { Text("Allow") }
    }
}

/** What, of what happened while nobody was looking, is worth saying: Android having taken the foreground and kept it. */
internal fun worthSaying(unseen: List<Interrupted>): List<Interrupted> = unseen.filter { it.why != Interruption.TAKEN }

/** What the explanation says, for [said]: the restriction when it was behind any of it, and otherwise the refusal. */
internal fun interruptionWords(said: List<Interrupted>): String {
    val times = if (said.size > 1) " ${timesInWords(said.size)}" else ""
    return if (said.any { it.why == Interruption.RESTRICTED }) {
        "Android stopped Noctorium's music in the background$times, because your phone restricts it there. Allow " +
            "background activity, and the music keeps playing when you switch apps."
    } else {
        "Android took Noctorium's music out of the foreground$times while you were in another app, where the phone " +
            "can stop it. Letting Noctorium run in the background keeps it playing."
    }
}

/** Whether the one-off word about the restriction, as the music starts, has been said since Noctorium started. */
private var toldAsPlaybackStarted = false

/**
 * What playing in the background needs said, as a dialog, once each: when the listener comes back after Android
 * stopped the music in the background, what happened and the fix; and when music starts while the phone restricts
 * Noctorium, that it will stop when they switch apps.
 */
@Composable
internal fun BackgroundPlayNotices(playing: Boolean) {
    val context = LocalContext.current
    val picture = LocalBackgroundPlayPicture.current
    val restricted = rememberBackgroundRestricted()
    var unseen by remember { mutableStateOf(picture?.unseen ?: emptyList()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (picture == null) {
            val store = KeepAliveStore(context)
            unseen = worthSaying(store.interruptions.filter { it.atMs > store.interruptionsSeenAt })
        }
    }
    var warnNow by remember { mutableStateOf(false) }
    LaunchedEffect(playing, restricted) {
        if (picture != null) {
            warnNow = picture.toldAsPlaybackStarts
        } else if (playing && restricted && !toldAsPlaybackStarted) {
            toldAsPlaybackStarted = true
            warnNow = true
        }
    }

    val said = worthSaying(unseen)
    if (said.isNotEmpty()) {
        val done = {
            if (picture == null) KeepAliveStore(context).interruptionsSeenAt = System.currentTimeMillis()
            unseen = emptyList()
            // Said already, with the same fix: not again as the music starts.
            warnNow = false
        }
        val restrictedThen = said.any { it.why == Interruption.RESTRICTED }
        SkinnedAlertDialog(
            onDismissRequest = done,
            title = "The music was stopped in the background",
            text = { Text(interruptionWords(said)) },
            confirmButton = {
                SkinnedTextButton({
                    done()
                    if (restrictedThen) BackgroundPlayback.openBackgroundActivity(context) else BackgroundPlayback.ask(context)
                }) { Text(if (restrictedThen) "Allow background activity" else "Allow") }
            },
            dismissButton = { SkinnedTextButton(done) { Text("Not now") } },
        )
        return
    }
    if (warnNow && restricted) {
        val done = { warnNow = false }
        SkinnedAlertDialog(
            onDismissRequest = done,
            title = "The music will stop when you switch apps",
            text = {
                Column {
                    Text(RESTRICTED_WORDS)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Allow background activity in Noctorium's battery usage, and it keeps playing.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                SkinnedTextButton({
                    done()
                    BackgroundPlayback.openBackgroundActivity(context)
                }) { Text("Allow background activity") }
            },
            dismissButton = { SkinnedTextButton(done) { Text("Not now") } },
        )
    }
}
