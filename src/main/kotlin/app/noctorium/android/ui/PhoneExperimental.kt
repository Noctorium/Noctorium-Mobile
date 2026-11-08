package app.noctorium.android.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import app.noctorium.core.AppState
import app.noctorium.settings.ShadowPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Settings › About › Experimental on the phone: a box for codes, which do things not yet worth a setting of their
 * own -- and, for now, one easter egg. The desktop's page, at a phone's size.
 */

/** The codes there are, by what is typed for them. Typed in any case, with spaces either side or not. */
internal enum class ExperimentalCode(val code: String) {
    /** Closes Noctorium. Nothing actually crashes. */
    CRASH("crash"),

    /** The secret theme, kept from then on like any, until another is chosen. */
    SHADOW("sh@d0w"),
    SHADOW_CHAOS("sh@d0w2"),
    ;

    companion object {
        fun find(typed: String): ExperimentalCode? = entries.firstOrNull { it.code.equals(typed.trim(), ignoreCase = true) }
    }
}

@Composable
internal fun ExperimentalCard(state: AppState) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var takeover by remember { mutableStateOf(false) }
    val enter: () -> Unit = {
        when (ExperimentalCode.find(typed)) {
            // Closed as a phone closes an app: off the screen and out of the recent apps, the music with it.
            ExperimentalCode.CRASH -> {
                (context as? Activity)?.finishAndRemoveTask()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ kotlin.system.exitProcess(0) }, 300)
            }
            // The theme is saved at once, under the takeover, and is there when Noctorium is opened again.
            ExperimentalCode.SHADOW -> {
                state.unlockShadowTheme()
                takeover = true
            }
            ExperimentalCode.SHADOW_CHAOS -> {
                state.unlockShadowChaosTheme()
                takeover = true
            }
            null -> answer = if (typed.isBlank()) null else "Nothing happens. Not yet, anyway."
        }
        typed = ""
    }
    if (takeover) {
        ShadowTakeover {
            (context as? Activity)?.finishAndRemoveTask()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ kotlin.system.exitProcess(0) }, 300)
        }
    }
    SettingsCardShell(anchor = SettingIds.EXPERIMENTAL) {
        CardHeading(Glyph.TERMINAL.image, "Codes")
        Spacer(Modifier.height(6.dp))
        Text(
            "Things that are not settings yet. Type a code and press Enter.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                typed,
                { typed = it.take(64); answer = null },
                Modifier.weight(1f),
                label = { Text("Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { enter() }),
            )
            Spacer(Modifier.width(8.dp))
            SkinnedTextButton(enter, enabled = typed.isNotBlank()) { Text("Enter") }
        }
        answer?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** The picture behind the Shadow theme: one of the listener's own, kept on this phone and nowhere else. */
@Composable
internal fun ShadowPictureCard(picture: String, state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refused by remember { mutableStateOf(false) }
    // Android's own photo picker: no permission asked for, and only the one picture chosen is lent.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val kept = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytesCompat(ShadowPicture.LARGEST + 1)
                        ShadowPicture.keep(bytes)
                    }
                }.getOrNull()
            }
            refused = kept == null
            if (kept != null) state.setShadowPicture(kept.toString())
        }
    }
    SettingsCardShell(anchor = "shadow-picture") {
        CardHeading(Glyph.PALETTE.image, "Your picture")
        Spacer(Modifier.height(6.dp))
        Text(
            "Put a picture of your own behind this theme. It is copied into Noctorium on this phone and goes nowhere else.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinnedTextButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (picture.isBlank()) "Add a picture" else "Change picture")
            }
            if (picture.isNotBlank()) {
                SkinnedTextButton({
                    ShadowPicture.forget()
                    state.setShadowPicture("")
                    refused = false
                }) { Text("Remove") }
            }
        }
        if (refused) {
            Spacer(Modifier.height(8.dp))
            Text(
                "That is not a picture Noctorium can use: a PNG, JPEG, WebP or GIF of 25 MB or less.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
    }
}

/** Up to [limit] bytes of this stream: enough to tell a picture that is too big from one that is not. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
