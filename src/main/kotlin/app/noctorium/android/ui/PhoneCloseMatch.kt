package app.noctorium.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.lyrics.LyricsResult
import app.noctorium.settings.SettingsState

/** The songs whose close-match note was closed: closed for that song until Noctorium is next opened. */
internal object CloseMatchNotes {
    val closed = mutableStateListOf<String>()
}

/**
 * A line above lyrics found by a close match -- a song uploaded under somebody else's name, whose lyrics were
 * found under the name the source knows it by -- saying whose they are, and that they may not be this song's.
 * Small, and closed with its ✕ for the song; switched off for good in Settings › Lyrics, which leaves the close
 * match itself as it is. The desktop's CloseMatchNote, at a phone's size.
 */
@Composable
internal fun PhoneCloseMatchNote(result: LyricsResult?, trackKey: String, state: AppState) {
    val match = result?.closeMatch ?: return
    val settings by state.settings.collectAsState()
    if (!settings.preferences.lyricsCloseMatchWarning || trackKey in CloseMatchNotes.closed) return
    val tint = MaterialTheme.colorScheme.tertiary
    Surface(
        color = tint.copy(alpha = .12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(7.dp))
            Text(
                "Close match: “${match.title}” by ${match.artist}. May not be this song's lyrics.",
                Modifier.weight(1f).padding(vertical = 6.dp),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton({ CloseMatchNotes.closed += trackKey }, Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Hide this note for this song", Modifier.size(14.dp))
            }
        }
    }
}

/** Settings › Lyrics: whether lyrics found by a close match say so. */
@Composable
internal fun CloseMatchCard(settings: SettingsState, state: AppState) {
    SettingsCardShell(anchor = SettingIds.LYRICS_CLOSE_MATCH) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                CardHeading(Icons.Default.Info, "Warn when lyrics are a close match")
                Spacer(Modifier.size(6.dp))
                Text(
                    "When a song has no lyrics under its own name -- uploaded by somebody else, or \"slowed + reverb\" -- " +
                        "the closest song that has some is shown, with a small note that they may not be right. Off hides the note.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            SkinnedSwitch(settings.preferences.lyricsCloseMatchWarning, state::setLyricsCloseMatchWarning)
        }
    }
}
