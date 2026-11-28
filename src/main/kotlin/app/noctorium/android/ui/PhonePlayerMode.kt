package app.noctorium.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Player mode's home-screen activity, by name: the phone build has it, the TV build does not. */
private const val PLAYER_HOME = "app.noctorium.android.LauncherActivity"

/** The warning Player mode is turned on past, kept with the launcher's own (Noctorium-Mp3-Like-Player-Android-Launcher-). */
internal const val PLAYER_MODE_WARNING =
    "Player mode makes Noctorium this phone's home screen: the phone becomes a music player, and your other apps are " +
        "no longer on its home screen. Use it on a phone you keep for music, not on the phone you use every day. " +
        "You can leave Player mode at any time by holding the clock, or from Android's Settings, under Default apps."

private fun Context.playerHome() = ComponentName(packageName, PLAYER_HOME)

/** Whether this build has Player mode at all. */
internal fun Context.hasPlayerMode(): Boolean =
    runCatching { packageManager.getActivityInfo(playerHome(), PackageManager.MATCH_DISABLED_COMPONENTS) }.isSuccess

/** Whether Player mode's home screen is switched on, so Android offers it as a home screen. */
internal fun Context.playerModeOn(): Boolean =
    packageManager.getComponentEnabledSetting(playerHome()) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

/**
 * Switches Player mode's home screen on or off, then opens Android's choice of home screen so it can be picked, or the
 * phone's own picked back. Off is the default in the manifest, so no phone is asked unless its owner asked for it.
 */
internal fun Context.setPlayerMode(on: Boolean) {
    packageManager.setComponentEnabledSetting(
        playerHome(),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
    val choose = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(choose) }.onFailure {
        // A phone without that page: Home asks which home screen to use instead.
        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** A cover filling [modifier], for Player mode's screen, through the app's own image loader. */
@Composable
fun PlayerModeArtwork(url: String?, modifier: Modifier) {
    coil.compose.AsyncImage(url, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

/** Settings › On this phone: Player mode, the phone as a music player, behind a warning that says whose phone it is for. */
@Composable
internal fun PlayerModeCard() {
    val context = LocalContext.current
    if (!context.hasPlayerMode()) return
    var on by remember { mutableStateOf(context.playerModeOn()) }
    var asking by remember { mutableStateOf(false) }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Use a spare phone") },
            text = { Text(PLAYER_MODE_WARNING) },
            confirmButton = {
                TextButton({
                    asking = false
                    context.setPlayerMode(true)
                    on = true
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton({ asking = false }) { Text("Not on this phone") } },
        )
    }

    SettingsCardShell(anchor = SettingIds.PLAYER_MODE) {
        CardHeading(Glyph.PHONE_ANDROID.image, "Player mode")
        Spacer(Modifier.height(6.dp))
        Text(
            "Turns this phone into a music player: Noctorium becomes its home screen, with the clock, the song playing " +
                "and big buttons, and nothing else. For a phone kept for music, not the one you use every day.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            if (on) {
                Text("On", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                Spacer(Modifier.width(12.dp))
                OutlinedButton({ context.setPlayerMode(false); on = false }) { Text("Turn off") }
            } else {
                Button({ asking = true }) { Text("Turn on…") }
            }
        }
    }
}
