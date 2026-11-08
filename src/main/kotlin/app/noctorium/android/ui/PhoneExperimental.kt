package app.noctorium.android.ui

import android.app.Activity
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
    ;

    companion object {
        fun find(typed: String): ExperimentalCode? = entries.firstOrNull { it.code.equals(typed.trim(), ignoreCase = true) }
    }
}

@Composable
internal fun ExperimentalCard() {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    val enter: () -> Unit = {
        when (ExperimentalCode.find(typed)) {
            // Closed as a phone closes an app: off the screen and out of the recent apps, the music with it.
            ExperimentalCode.CRASH -> {
                (context as? Activity)?.finishAndRemoveTask()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ kotlin.system.exitProcess(0) }, 300)
            }
            null -> answer = if (typed.isBlank()) null else "Nothing happens. Not yet, anyway."
        }
        typed = ""
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
