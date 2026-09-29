package app.noctorium.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.noctorium.android.BackgroundPlayback
import app.noctorium.core.AppState

/**
 * Whether the phone lets Noctorium play on once the screen locks, read again every time Noctorium comes back
 * to the front -- which is how the answer to Android's own dialog, or a change made in the phone's settings,
 * shows up without a restart.
 */
@Composable
internal fun rememberBackgroundUnrestricted(): Boolean {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(BackgroundPlayback.unrestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = BackgroundPlayback.unrestricted(context) }
    return unrestricted
}

/** Keeping the music going with the screen locked: where it stands, and the phone's own ways to allow it. */
@Composable
internal fun KeepPlayingCard() {
    val context = LocalContext.current
    val unrestricted = rememberBackgroundUnrestricted()
    val strict = BackgroundPlayback.strictManufacturer

    SettingsCardShell {
        CardHeading(
            if (unrestricted) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
            "Keep playing when the phone locks",
            tint = if (unrestricted) null else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                unrestricted && strict ->
                    "Android lets Noctorium run in the background. On this phone, also set Noctorium's battery " +
                        "saver to No restrictions, or it can still be stopped when the screen locks."
                unrestricted -> "Android lets Noctorium run in the background, so the music carries on with the screen off."
                else ->
                    "Android can stop Noctorium to save battery once the screen locks, and the music stops with it. " +
                        "Allow it to run in the background and it will not."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!unrestricted) Button({ BackgroundPlayback.ask(context) }) { Text("Allow") }
            if (strict || !unrestricted) {
                OutlinedButton({ BackgroundPlayback.openBatteryPage(context) }) {
                    Text(if (strict) "Battery saver" else "Battery settings")
                }
            }
        }
    }
}

/**
 * Asked once, the first time something plays on a phone that could stop it at the lock screen.
 *
 * At that moment rather than at launch, because that is when it means something: somebody who has just
 * pressed play understands "keep the music playing", and somebody opening the app for the first time is
 * being asked about a problem they have not had.
 */
@Composable
internal fun KeepPlayingPrompt(state: AppState, playing: Boolean) {
    val context = LocalContext.current
    val unrestricted = rememberBackgroundUnrestricted()
    val settings by state.settings.collectAsState()
    val asked = settings.preferences.phone.askedAboutBackground
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, unrestricted, asked) {
        if (playing && !unrestricted && !asked) showing = true
    }
    if (!showing) return

    val done = {
        showing = false
        state.updatePhone { copy(askedAboutBackground = true) }
    }
    AlertDialog(
        onDismissRequest = done,
        title = { Text("Keep the music playing when your phone locks?") },
        text = {
            Column {
                Text(
                    "Your phone can close Noctorium to save battery once the screen locks, and the music stops with " +
                        "it. Allowing Noctorium to run in the background stops that.",
                )
                if (BackgroundPlayback.strictManufacturer) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On this phone, also set Noctorium's battery saver to No restrictions. Settings › On this " +
                            "phone has a button for it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton({
                done()
                BackgroundPlayback.ask(context)
            }) { Text("Allow") }
        },
        dismissButton = { TextButton(done) { Text("Not now") } },
    )
}
