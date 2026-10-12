package app.noctorium.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.history.HistoryDay
import app.noctorium.history.HistoryEntry
import app.noctorium.history.historyByDay
import app.noctorium.history.matches
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemeSkin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The history on the profile page: every song played on this phone, by day, kept on this phone and nowhere else.
 * The card says what it is and holds its switch and Clear; the songs come after it as lines of the page's own list.
 */

/** What the history card says, with the switch that stops it and the way to clear it, asked first. */
@Composable
internal fun HistoryCard(settings: SettingsState, state: AppState) {
    val history by state.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    SettingsCardShell {
        SettingAnchor(SettingIds.HISTORY) {
            CardHeading(Glyph.HISTORY.image, "History")
            Text(
                "Every song you listen to on this phone, newest first. It stays on this phone: it is not sent to your " +
                    "Noctorium account, your other devices or anywhere else.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
        }
        SettingAnchor(SettingIds.KEEP_HISTORY) {
            Toggle(
                "Keep a history of what I play",
                "A song goes in once you have heard half a minute of it. Off adds nothing more and keeps what is here.",
                settings.preferences.keepHistory,
            ) { state.setKeepHistory(it) }
        }
        // The anchor around the whole line: it takes the width it is given, which beside the count would be all of it.
        SettingAnchor(SettingIds.CLEAR_HISTORY, Modifier.padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        history.isNotEmpty() -> songsPlayed(history.size)
                        settings.preferences.keepHistory -> "Nothing played yet"
                        else -> "Nothing here, and nothing goes in while it is off"
                    },
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                SkinnedTextButton({ confirmClear = true }, enabled = history.isNotEmpty()) { Text("Clear history…") }
            }
        }
    }
    if (confirmClear) {
        SkinnedAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = "Clear your history?",
            text = { Text("All ${songsPlayed(history.size)} are forgotten, on this phone, for good. Nothing else is touched.") },
            confirmButton = {
                SkinnedTextButton({ confirmClear = false; state.clearHistory() }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { SkinnedTextButton({ confirmClear = false }) { Text("Keep") } },
        )
    }
}

/** "1 song played", "1,204 songs played": a song played twice is two of them. */
private fun songsPlayed(count: Int): String = if (count == 1) "1 song played" else "${"%,d".format(count)} songs played"

/** The history as the page lists it: how many there are, and the days of those [filter] lets through. */
internal class HistoryView(val total: Int, val shown: Int, val days: List<HistoryDay>)

/** The history, filtered and gathered by day once for each change rather than at every turn of the list. */
@Composable
internal fun rememberHistoryDays(state: AppState, filter: String): HistoryView {
    val history by state.history.collectAsState()
    return remember(history, filter) {
        val shown = if (filter.isBlank()) history else history.filter { it.matches(filter) }
        HistoryView(history.size, shown.size, historyByDay(shown))
    }
}

/**
 * The history's songs, after the profile page's cards: a filter while there is anything to filter, then each day's
 * name and its songs, newest first.
 */
internal fun LazyListScope.historyLines(view: HistoryView, filter: String, setFilter: (String) -> Unit, state: AppState) {
    if (view.total == 0) return
    item(key = "history-filter") { HistoryFilter(filter, setFilter) }
    if (view.shown == 0) {
        item(key = "history-nothing") {
            EmptyNote("Nothing matches “${filter.trim()}”", "The filter looks in the titles, the artists and the albums.")
        }
        return
    }
    view.days.forEach { day ->
        item(key = "history-day:${day.date}") { HistoryDayLabel(day) }
        items(day.entries, key = { it.key }) { entry -> HistoryLine(entry, state) }
    }
}

@Composable
private fun HistoryFilter(filter: String, setFilter: (String) -> Unit) {
    OutlinedTextField(
        filter,
        setFilter,
        placeholder = { Text("Filter by title, artist or album") },
        leadingIcon = { Icon(Glyph.SEARCH.image, null, Modifier.size(20.dp)) },
        trailingIcon = if (filter.isEmpty()) null else {
            { IconButton({ setFilter("") }) { Icon(Glyph.CLOSE.image, "Clear the filter", Modifier.size(18.dp)) } }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** A day's name over its songs, in the accent as Settings names its sections, or the Windows skins' own label. */
@Composable
private fun HistoryDayLabel(day: HistoryDay) {
    val count = if (day.entries.size == 1) "1 song" else "${day.entries.size} songs"
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        SkinSectionLabel("${day.label} · $count")
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(day.label, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

/**
 * One listen, as a track is a line everywhere else on the phone: the cover, the song and who it is by, and on the
 * right when it was played over where it is from. A tap plays it, as a song on Home does; its menu is the track's
 * menu, with Play first and Remove from history last.
 */
@Composable
private fun HistoryLine(entry: HistoryEntry, state: AppState) {
    val track = entry.track
    val context = LocalContext.current
    val time = remember(entry.playedAt) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(entry.playedAt))
    }
    Row(
        Modifier.fillMaxWidth().clickable { state.playFromHome(track) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // An empty artist line means the service did not say, and is said as such.
                track.artistLine.ifBlank { "Unknown artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            ProviderBadge(track)
        }
        TrackMenuButton(
            track,
            state,
            first = { close ->
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Glyph.PLAY_ARROW.image, null) },
                    onClick = { state.playFromHome(track); close() },
                )
                HorizontalDivider()
            },
            last = { close ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove from history") },
                    leadingIcon = { Icon(Glyph.REMOVE_CIRCLE_OUTLINE.image, null) },
                    onClick = { state.removeFromHistory(entry); close() },
                )
            },
        )
    }
}
