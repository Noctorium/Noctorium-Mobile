package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.domain.Track
import app.noctorium.playback.QueueState
import app.noctorium.settings.QueueColumn
import app.noctorium.settings.ThemeSkin

/**
 * The Playlist layout: a desktop music player's window, for a phone held upright.
 *
 * An info bar across the top -- the cover, the title set large, the artist and the album, and the visualiser --
 * then the queue as a table, under its tab, in the columns the listener chose and with the song playing marked
 * and lit, and the song's lyrics under the tab beside it; the seek bar and the controls every layout has; and the
 * status bar along the foot, saying what is playing as a player reports it ("Opus, stereo, 48 kHz, 128 kbit/s")
 * and how far into the whole queue.
 *
 * Under the Windows skins the table is their list view -- white, sunk into the window, its headings raised
 * buttons, the song playing in the selection's blue -- under their tabs, and the status bar is theirs, in sunken
 * panels.
 */
@Composable
internal fun PlaylistNowPlaying(screen: NowPlaying) {
    val preferences = screen.settings.preferences
    NowPlayingColumn {
        NowPlayingTopBar(screen)
        InfoBar(screen)
        TrackHeading(screen, title = false)
        Spacer(Modifier.height(10.dp))
        TabbedTable(
            screen,
            tableColumns(preferences.queueColumns, screen.queue.tracks.ifEmpty { listOf(screen.track) }),
            Modifier.weight(1f).fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
        if (preferences.statusBar) StatusBar(screen)
    }
}

/** The cover, the title large with the artist and the album under it, and the visualiser at the end. */
@Composable
private fun InfoBar(screen: NowPlaying) {
    val track = screen.track
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.seekOnDoubleTap(screen)) { Artwork(track.artworkUrl, 68.dp, corner = 8.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                track.artistLine.ifBlank { "Unknown artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.album?.title?.takeIf { it.isNotBlank() }?.let { album ->
                Text(
                    album,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Visualiser(screen.state, screen.settings.preferences.visualiser, Modifier.size(width = 78.dp, height = 46.dp))
    }
}

/**
 * The tabs over the table, in the order they stand: the queue's first, as it always was, and the lyrics' straight
 * after it. The desktop's table has tabs after these that come and go -- a playlist's, autoplay's -- and any the
 * phone's gains go after these two as well, so the lyrics' tab never moves from under the thumb.
 */
internal enum class PlaylistTab { QUEUE, LYRICS }

/** What a tab says: "Queue · 12 songs · 48:20" for the queue's, and "Lyrics". */
internal fun playlistTabName(tab: PlaylistTab, queue: QueueState, count: Int): String = when (tab) {
    PlaylistTab.QUEUE -> queueTabName(queue, count)
    PlaylistTab.LYRICS -> "Lyrics"
}

/**
 * The table under its tabs: the queue, or the lyrics of the song shown.
 *
 * The lyrics' tab is the lyrics button's own doing by another way in -- choosing it is pressing the button, and the
 * button, pressed, chooses it -- so the bar along the top says "Lyrics" while it is chosen, and the lyrics are asked
 * for only then, as they are in every layout.
 */
@Composable
private fun TabbedTable(screen: NowPlaying, columns: List<QueueColumn>, modifier: Modifier) {
    val queue = screen.queue
    val rows = queue.tracks.ifEmpty { listOf(screen.track) }
    val chosen = if (screen.showLyrics) PlaylistTab.LYRICS else PlaylistTab.QUEUE
    val skin = LocalSkin.current
    Column(modifier) {
        TableTabs(PlaylistTab.entries.map { playlistTabName(it, queue, rows.size) }, chosen.ordinal) { index ->
            if (index != chosen.ordinal) {
                screen.haptics.tick()
                screen.toggleLyrics()
            }
        }
        // The page under the tabs is joined to the chosen one, so its corner under the first tab is square only
        // while the first is chosen.
        val corner = if (chosen == PlaylistTab.QUEUE) 0.dp else 10.dp
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (skin == ThemeSkin.STANDARD) {
                        Modifier
                            .clip(RoundedCornerShape(topStart = corner, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))
                    } else {
                        Modifier.skinList()
                    },
                ),
        ) {
            when (chosen) {
                PlaylistTab.QUEUE -> QueueRows(screen, columns, rows)
                PlaylistTab.LYRICS -> LyricsPage(screen)
            }
        }
    }
}

/**
 * The queue as a table: the headings of the chosen columns, and a row for each song -- the one playing marked with
 * play and lit, the rest in a quiet alternating shade. Tapping a row plays it. It opens at the song playing, not at
 * the top, since in a long queue that is where the next songs are.
 */
@Composable
private fun QueueRows(screen: NowPlaying, columns: List<QueueColumn>, rows: List<Track>) {
    val queue = screen.queue
    val playing = if (queue.tracks.isEmpty()) 0 else queue.currentIndex
    TableHeadings(columns)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (playing - 1).coerceAtLeast(0))
    LaunchedEffect(playing) { if (playing >= 0) list.animateScrollToItem((playing - 1).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = list) {
        itemsIndexed(rows, key = { index, track -> "$index:${track.queueKey}" }) { index, track ->
            TableRow(index, track, columns, current = index == playing, odd = index % 2 == 1) {
                screen.haptics.tick()
                if (queue.tracks.isNotEmpty()) screen.state.jumpToQueueItem(index)
            }
        }
    }
}

/**
 * The lyrics' tab: the lyrics as every layout shows them -- the sources to choose between above, the line being sung
 * kept in the middle of the page -- for the song shown. While this phone is another device's remote control that is
 * the other device's song, at the place it has reached there, so the lines keep time with what is heard in that room.
 */
@Composable
private fun LyricsPage(screen: NowPlaying) {
    Box(Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp, top = 8.dp)) {
        LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
    }
}

/**
 * The columns the table is drawn with: the ones chosen, less any that would be empty for every song -- the album,
 * for a queue of uploads that have none -- which on a phone is room the titles want.
 */
internal fun tableColumns(chosen: List<QueueColumn>, rows: List<Track>): List<QueueColumn> =
    QueueColumn.normalised(chosen).filter { column ->
        column != QueueColumn.ALBUM || rows.any { !it.album?.title.isNullOrBlank() }
    }

/** "Queue · 12 songs · 48:20": what the tab over the table says. */
internal fun queueTabName(queue: QueueState, count: Int): String = listOfNotNull(
    "Queue",
    if (count == 1) "1 song" else "$count songs",
    queue.durationMs.takeIf { it > 0 }?.let(::formatDuration),
).joinToString(" · ")

/**
 * The table's tabs, [names] in order with the [chosen] one joined to the table under it; [choose] is told the place
 * of the one tapped. The first gives way, ending in an ellipsis, before the others are cut: the queue's says the most,
 * and the most of it can go.
 */
@Composable
private fun TableTabs(names: List<String>, chosen: Int, choose: (Int) -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier.fillMaxWidth().padding(start = if (skin == ThemeSkin.STANDARD) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (skin == ThemeSkin.STANDARD) 4.dp else 0.dp),
        // A tab not chosen stands a little lower than the one that is, as Windows' did.
        verticalAlignment = Alignment.Bottom,
    ) {
        names.forEachIndexed { index, name ->
            TableTab(
                name,
                chosen = index == chosen,
                modifier = if (index == 0) Modifier.weight(1f, fill = false) else Modifier,
            ) { choose(index) }
        }
    }
}

/**
 * One tab over the table. The chosen one is joined to the table -- in its colour in the ordinary themes, taller and
 * standing on it under 98, white with Luna's orange along its top under XP -- and the others sit back from it.
 */
@Composable
private fun TableTab(name: String, chosen: Boolean, modifier: Modifier, choose: () -> Unit) {
    val skin = LocalSkin.current
    val win98 = Win98
    val tab = modifier.selectable(chosen, role = Role.Tab, onClick = choose)
    val text: @Composable () -> Unit = {
        Text(
            name,
            color = if (chosen || skin != ThemeSkin.STANDARD) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // The tabs not chosen are this much shorter, so they stand lower than the chosen one.
    val lower = if (chosen) 0.dp else 2.dp
    when (skin) {
        ThemeSkin.STANDARD -> Box(
            tab
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (chosen) .35f else .18f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) { text() }
        ThemeSkin.WINDOWS_98 -> Box(
            tab
                .background(win98.Face)
                .drawWithContent {
                    drawContent()
                    // The chosen tab is a raised edge without its foot, standing on the panel it opens; the others
                    // keep theirs, behind it.
                    drawEdge98(Edge98.RAISED, win98, size = if (chosen) size.copy(height = size.height + 2f) else size)
                }
                .padding(start = 12.dp, end = 12.dp, top = 5.dp - lower, bottom = 5.dp),
        ) { text() }
        ThemeSkin.WINDOWS_XP -> Box(
            tab
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(Brush.verticalGradient(listOf(Color.White, if (chosen) Luna.TabChosen else Luna.Face)))
                .border(1.dp, Luna.TabEdge, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .drawWithContent {
                    drawContent()
                    if (chosen) drawRect(Luna.Hot, size = size.copy(height = 3.dp.toPx()))
                }
                .padding(start = 12.dp, end = 12.dp, top = 5.dp - lower, bottom = 5.dp),
        ) { text() }
    }
}

/** The columns' headings: quiet small capitals, or under the Windows skins a row of raised header buttons. */
@Composable
private fun TableHeadings(columns: List<QueueColumn>) {
    val skin = LocalSkin.current
    val win98 = Win98
    Row(Modifier.fillMaxWidth().height(if (skin == ThemeSkin.STANDARD) 30.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
        columns.forEach { column ->
            Cell(column, Modifier.fillMaxHeight().then(
                when (skin) {
                    ThemeSkin.STANDARD -> Modifier
                    ThemeSkin.WINDOWS_98 -> Modifier.background(win98.Face).drawWithContent {
                        drawContent()
                        drawEdge98(Edge98.RAISED, win98)
                    }
                    ThemeSkin.WINDOWS_XP -> Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEBEADB)))).drawWithContent {
                        drawContent()
                        drawRect(Color(0xFFC7C5B2), topLeft = androidx.compose.ui.geometry.Offset(size.width - 1f, 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(1f, size.height - 8.dp.toPx()))
                        drawRect(Color(0xFFD6D2C2), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2f), size = androidx.compose.ui.geometry.Size(size.width, 2f))
                    }
                },
            )) {
                Text(
                    column.heading,
                    color = if (skin == ThemeSkin.STANDARD) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = if (skin == ThemeSkin.STANDARD) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = column.alignment(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (skin == ThemeSkin.STANDARD) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
}

/** One song in the table. */
@Composable
private fun TableRow(index: Int, track: Track, columns: List<QueueColumn>, current: Boolean, odd: Boolean, play: () -> Unit) {
    val skin = LocalSkin.current
    val line: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .then(
                    when {
                        current && skin == ThemeSkin.STANDARD -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .16f))
                        odd && skin == ThemeSkin.STANDARD -> Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .035f))
                        else -> Modifier
                    },
                )
                .clickable(onClickLabel = "Play", onClick = play)
                .semantics(mergeDescendants = true) { contentDescription = "${index + 1}. ${track.title}" + if (current) ", playing" else "" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column -> Cell(column, Modifier) { CellText(index, track, column, current) } }
        }
    }
    if (current && skin != ThemeSkin.STANDARD) SkinSelected(line) else line()
}

/** What one cell of a song's row says. */
@Composable
private fun CellText(index: Int, track: Track, column: QueueColumn, current: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    if (column == QueueColumn.NUMBER && current) {
        Icon(Glyph.PLAY_ARROW.image, "Playing", Modifier.size(15.dp), tint = accent)
        return
    }
    val (text, colour, weight) = when (column) {
        QueueColumn.NUMBER -> Triple("${index + 1}", quiet, FontWeight.Normal)
        QueueColumn.TITLE -> Triple(track.title, if (current) accent else MaterialTheme.colorScheme.onSurface, if (current) FontWeight.SemiBold else FontWeight.Normal)
        QueueColumn.ARTIST -> Triple(track.artistLine.ifBlank { "Unknown artist" }, quiet, FontWeight.Normal)
        QueueColumn.ALBUM -> Triple(track.album?.title.orEmpty(), quiet, FontWeight.Normal)
        QueueColumn.DURATION -> Triple(track.durationMs?.let(::formatDuration) ?: "", quiet, FontWeight.Normal)
        QueueColumn.SERVICE -> Triple(track.provider.displayName, quiet, FontWeight.Normal)
    }
    Text(
        text,
        color = colour,
        fontSize = 12.sp,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = column.alignment(),
        style = TextStyle(fontFeatureSettings = if (column == QueueColumn.NUMBER || column == QueueColumn.DURATION) "tnum" else null),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A cell, as wide as its column: the number and the length narrow and fixed, the words sharing what is left. */
@Composable
private fun RowScope.Cell(column: QueueColumn, modifier: Modifier, content: @Composable () -> Unit) {
    // The fixed columns grow with the text, so a heading still fits at the largest size.
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val width = when (column) {
        QueueColumn.NUMBER -> Modifier.width(30.dp * scale)
        QueueColumn.DURATION -> Modifier.width(66.dp * scale)
        QueueColumn.SERVICE -> Modifier.weight(1.1f)
        QueueColumn.TITLE -> Modifier.weight(2.2f)
        QueueColumn.ARTIST -> Modifier.weight(1.4f)
        QueueColumn.ALBUM -> Modifier.weight(1.2f)
    }
    Box(width.then(modifier).padding(horizontal = 5.dp), contentAlignment = column.boxAlignment()) { content() }
}

private fun QueueColumn.alignment(): TextAlign = when (this) {
    QueueColumn.NUMBER -> TextAlign.Center
    QueueColumn.DURATION -> TextAlign.End
    else -> TextAlign.Start
}

private fun QueueColumn.boxAlignment(): Alignment = when (this) {
    QueueColumn.NUMBER -> Alignment.Center
    QueueColumn.DURATION -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}

/**
 * The status bar: what is playing, as the player reports it -- "Opus, stereo, 48 kHz, 128 kbit/s" -- on the left,
 * and how far into the whole queue on the right, "2:31 / 1:03:41". Until the player knows the format, a dash.
 */
@Composable
private fun StatusBar(screen: NowPlaying) {
    val skin = LocalSkin.current
    val win98 = Win98
    val format = screen.playback.format?.describe() ?: "—"
    val queue = screen.queue
    val elapsed = if (queue.tracks.isEmpty()) screen.playback.positionMs else queue.elapsedMs(screen.playback.positionMs)
    val total = queue.durationMs.takeIf { it > 0 } ?: screen.playback.durationMs
    val where = "${formatDuration(elapsed)} / ${formatDuration(total)}"
    val style = TextStyle(fontSize = 11.sp, fontFeatureSettings = "tnum")
    val ink = if (skin == ThemeSkin.STANDARD) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (skin == ThemeSkin.STANDARD) 6.dp else 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Status: $format. $where" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val panel: Modifier = when (skin) {
            ThemeSkin.STANDARD -> Modifier
            ThemeSkin.WINDOWS_98 -> Modifier.drawWithContent {
                drawContent()
                drawEdge98(Edge98.SHALLOW, win98)
            }.padding(horizontal = 5.dp, vertical = 3.dp)
            ThemeSkin.WINDOWS_XP -> Modifier.border(1.dp, Luna.TabEdge.copy(alpha = .7f)).padding(horizontal = 5.dp, vertical = 3.dp)
        }
        Box(Modifier.weight(1f).then(panel)) {
            Text(format, color = ink, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(panel) { Text(where, color = ink, style = style, maxLines = 1, softWrap = false) }
    }
}
