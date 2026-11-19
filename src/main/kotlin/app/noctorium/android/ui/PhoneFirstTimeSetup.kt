package app.noctorium.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.SearchMode
import app.noctorium.domain.ProviderType
import app.noctorium.settings.SetupStep

/**
 * The first-time setup on the phone, in place of everything else until it is finished or skipped. The steps and what
 * they say are the core's ([SetupStep]), as on every Noctorium; "Sign in" on the accounts step finishes it and opens
 * the phone's own sign-in for that service. Back goes back a step, and from the first step skips nothing: it closes
 * the app as Back always does there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneFirstTimeSetup(state: AppState, signIn: (ProviderType) -> Unit) {
    val preferences = state.settings.collectAsState().value.preferences
    val ui by state.ui.collectAsState()
    var step by rememberSaveable { mutableStateOf(SetupStep.WELCOME) }
    val scheme = MaterialTheme.colorScheme
    BackHandler(enabled = step.previous != null) { step.previous?.let { step = it } }

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SetupStep.entries.forEach { s ->
                        Box(
                            Modifier.size(if (s == step) 20.dp else 7.dp, 7.dp).clip(CircleShape)
                                .background(if (s.ordinal <= step.ordinal) scheme.primary else scheme.onSurface.copy(alpha = .18f)),
                        )
                    }
                }
                if (step != SetupStep.DONE) TextButton(state::finishSetup) { Text("Skip setup") }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(SetupStep.progress(step).uppercase(), color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                Text(step.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text(step.about, color = scheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                when (step) {
                    SetupStep.WELCOME, SetupStep.DONE -> Unit
                    SetupStep.LOOK -> FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SetupStep.themes.forEach { theme ->
                            val colours = theme.colours ?: return@forEach
                            val chosen = preferences.theme == theme
                            Column(
                                Modifier.width(140.dp).clip(RoundedCornerShape(14.dp))
                                    .border(BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) scheme.primary else scheme.onSurface.copy(alpha = .14f)), RoundedCornerShape(14.dp))
                                    .clickable { state.setTheme(theme) }
                                    .padding(10.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(9.dp))) {
                                    Box(Modifier.weight(1f).fillMaxSize().background(Color(colours.background)))
                                    Box(Modifier.weight(1f).fillMaxSize().background(Color(colours.card)))
                                    Box(Modifier.weight(1f).fillMaxSize().background(Color(colours.accent)))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(theme.displayName, fontSize = 13.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    SetupStep.SEARCH -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SetupStep.searchModes.forEach { mode ->
                            SkinnedFilterChip(
                                selected = ui.searchMode == mode,
                                onClick = { state.setSearchMode(mode) },
                                label = { Text(if (mode == SearchMode.HYBRID) "Every service at once" else mode.displayName, fontSize = 14.sp) },
                            )
                        }
                    }
                    SetupStep.LISTENING -> Surface(shape = RoundedCornerShape(14.dp), color = scheme.surfaceVariant.copy(alpha = .5f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Autoplay", fontWeight = FontWeight.SemiBold)
                                Text("Songs like the last one, when the queue runs out", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(preferences.autoplay, state::setAutoplay)
                        }
                    }
                    SetupStep.ACCOUNTS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(ProviderType.YOUTUBE_MUSIC, ProviderType.SOUNDCLOUD).forEach { service ->
                            OutlinedButton({ signIn(service) }, Modifier.fillMaxWidth()) { Text("Sign in to ${service.displayName}") }
                        }
                        Text("Spotify, VK, Deezer and the rest are in Settings.", color = scheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                step.previous?.let { back -> TextButton({ step = back }) { Text("Back") } }
                Spacer(Modifier.weight(1f))
                val next = step.next
                Button({ if (next == null) state.finishSetup() else step = next }) {
                    Text(
                        when (step) {
                            SetupStep.WELCOME -> "Get started"
                            SetupStep.DONE -> "Start listening"
                            else -> "Next"
                        },
                    )
                }
            }
        }
    }
}
