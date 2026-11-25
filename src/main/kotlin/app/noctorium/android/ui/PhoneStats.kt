package app.noctorium.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.history.HistoryEntry

/** The spans the stats count over, as the Stats app's chips have them. */
internal enum class StatsSpan(val label: String, val days: Int?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    YEAR("Year", 365),
    ALL("All time", null),
}

/** What the history adds up to over one span. */
internal data class ListeningStats(
    val listens: Int,
    /** Listened time, from each song's length: a song skipped halfway counts whole, as the history cannot tell. */
    val listenedMs: Long,
    val songs: Int,
    val artists: Int,
    val topSongs: List<Pair<Track, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val services: List<Pair<ProviderType, Int>>,
)

/** The history's listens since [span] began, before [now], counted up. Pure, so it is tested without a screen. */
internal fun listeningStats(entries: List<HistoryEntry>, span: StatsSpan, now: Long = System.currentTimeMillis()): ListeningStats {
    val since = span.days?.let { now - it * 86_400_000L } ?: Long.MIN_VALUE
    val counted = entries.filter { it.playedAt in since..now }
    val bySong = counted.groupBy { it.track.queueKey }
    // Each artist of a song made by several counts once for it, under the name as written.
    val byArtist = counted.flatMap { entry -> entry.track.artists.map { it.name.trim() }.filter(String::isNotEmpty).distinct() }
        .groupingBy { it }.eachCount()
    return ListeningStats(
        listens = counted.size,
        listenedMs = counted.sumOf { it.track.durationMs ?: 0L },
        songs = bySong.size,
        artists = byArtist.size,
        // The newest listen's track, so a title edited since is shown as edited.
        topSongs = bySong.values.map { it.first().track to it.size }.sortedByDescending { it.second }.take(10),
        topArtists = byArtist.entries.map { it.key to it.value }.sortedByDescending { it.second }.take(10),
        services = counted.groupingBy { it.track.provider }.eachCount().entries.map { it.key to it.value }.sortedByDescending { it.second },
    )
}

/** Hours and minutes, or minutes alone under an hour. */
internal fun listenedTime(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/**
 * Noctorium Stats inside Noctorium: what this phone's history adds up to, over a week, a month, a year or all of it.
 *
 * From the history kept on this phone, so it needs no account and sends nothing anywhere; it counts what was played
 * here, and nothing played elsewhere.
 */
@Composable
internal fun StatsScreen(state: AppState, close: () -> Unit) {
    BackHandler(onBack = close)
    val history by state.history.collectAsState()
    val settings by state.settings.collectAsState()
    var span by rememberSaveable { mutableStateOf(StatsSpan.MONTH) }
    val stats = remember(history, span) { listeningStats(history, span) }

    ScreenScaffold {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Glyph.ARROW_BACK.image, "Back") }
            Text("Stats", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = chromePadding(24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatsSpan.entries.forEach { option ->
                        SkinnedFilterChip(selected = span == option, onClick = { span = option }, label = { Text(option.label) })
                    }
                }
            }
            if (!settings.preferences.keepHistory) {
                item { StatsNote("The history is switched off, so nothing new is being counted. It is in Settings, under History.") }
            }
            if (stats.listens == 0) {
                item { StatsNote("Nothing played on this phone in this span yet. Play something and it adds up here.") }
                return@LazyColumn
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Listens", stats.listens.toString(), Modifier.weight(1f))
                    StatTile("Listened", listenedTime(stats.listenedMs), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Songs", stats.songs.toString(), Modifier.weight(1f))
                    StatTile("Artists", stats.artists.toString(), Modifier.weight(1f))
                }
            }
            item { StatsHeading("Top songs") }
            stats.topSongs.forEachIndexed { i, (track, count) ->
                item(key = "song:${track.queueKey}") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Artwork(track.artworkUrl, 40.dp, corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artistLine, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(plays(count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            item { StatsHeading("Top artists") }
            val most = stats.topArtists.firstOrNull()?.second ?: 1
            stats.topArtists.forEachIndexed { i, (artist, count) ->
                item(key = "artist:$artist") { StatBar("${i + 1}. $artist", plays(count), count.toFloat() / most) }
            }
            item { StatsHeading("By service") }
            stats.services.forEach { (provider, count) ->
                item(key = "service:$provider") {
                    StatBar(provider.displayName, "${count * 100 / stats.listens}%", count.toFloat() / stats.listens, provider.badgeColour())
                }
            }
        }
    }
}

private fun plays(count: Int) = if (count == 1) "1 play" else "$count plays"

@Composable
private fun StatsNote(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

@Composable
private fun StatsHeading(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

/** A line with a bar under it as long as its share of the most. */
@Composable
private fun StatBar(label: String, value: String, share: Float, colour: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(colour))
        }
    }
}
