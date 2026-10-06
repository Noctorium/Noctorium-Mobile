package app.noctorium.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.QueueState
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.AutoplaySource

/**
 * The queue: what is playing, what follows it, and what autoplay has lined up after that.
 *
 * Autoplay's songs are shown under the queue rather than in it, and quieter, because nobody chose them.
 * Each can be played now, kept in the queue, or left out, and the queue the listener built stays as they
 * built it. Whatever autoplay is doing -- off, waiting for the end, or handing over to Spotify -- is said in
 * the same place, so the end of the queue is never a surprise.
 */
@Composable
internal fun QueueScreen(state: AppState) {
    val queue by state.queue.state.collectAsState()
    val settings by state.settings.collectAsState()
    var naming by remember { mutableStateOf(false) }
    // What saving the queue came to, in the library's own words; gone again once the queue changes.
    var saved by remember(queue.tracks) { mutableStateOf<String?>(null) }

    if (naming) {
        PlaylistNameDialog("Save queue as playlist", "", "Save") { title ->
            naming = false
            if (title != null) {
                state.saveQueueAsPlaylist(title)
                saved = state.library.value.notice
            }
        }
    }

    ScreenScaffold {
        ScreenTitle("Queue", "What is playing, and what follows", close = { state.navigate(Destination.HOME) }) {
            if (queue.tracks.isNotEmpty()) QueueMenu(queue, state) { naming = true }
        }
        saved?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (queue.tracks.isEmpty()) {
            EmptyNote("Nothing queued", "Play something and it will show up here.")
            return@ScreenScaffold
        }
        val upNext = upNext(queue, settings.preferences.autoplay)
        LazyColumn(Modifier.skinList(), contentPadding = chromePadding(24.dp)) {
            items(queue.tracks.size) { index ->
                val track = queue.tracks[index]
                TrackRow(
                    track,
                    state,
                    isCurrent = index == queue.currentIndex,
                    trailing = {
                        IconButton({ state.removeQueueItem(index) }) {
                            Icon(Icons.Default.Delete, "Remove from the queue", Modifier.size(18.dp))
                        }
                    },
                ) { state.jumpToQueueItem(index) }
            }
            item(key = "autoplay") { AutoplayHeading(autoplayHeading(queue, upNext), refreshable = upNext == UpNext.LINED_UP, state) }
            item(key = "autoplay-note") { AutoplayNote(upNext, state) }
            if (upNext == UpNext.LINED_UP) {
                itemsIndexed(queue.suggestions) { index, track -> SuggestionRow(track, index, state) }
            }
        }
    }
}

/** What can be done to the queue as a whole: shuffle or clear what is still to come, save it, or empty it. */
@Composable
private fun QueueMenu(queue: QueueState, state: AppState, saveAs: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Default.MoreVert, "Queue actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Shuffle what's next") },
                leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                // Two songs at least: one has nowhere to be shuffled to.
                enabled = queue.upNext.size >= 2,
                onClick = { state.shuffleUpcoming(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Clear what's next") },
                leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                enabled = queue.upNext.isNotEmpty(),
                onClick = { state.clearUpcoming(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Save queue as playlist…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { open = false; saveAs() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Clear the queue") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { state.clearQueue(); open = false },
            )
        }
    }
}

/** The line that starts autoplay's part of the queue, with a way to ask for other songs once there are some. */
@Composable
private fun AutoplayHeading(heading: String, refreshable: Boolean, state: AppState) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AllInclusive, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            heading,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            // Two lines: "More from <a long band name> on Bandcamp" does not fit one on a narrow phone.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (refreshable) {
            IconButton(state::refreshSuggestions) {
                Icon(Icons.Default.Refresh, "Other songs", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // The heading keeps its height either way, so the list under it does not move as songs arrive.
            Spacer(Modifier.size(48.dp))
        }
    }
}

/** What autoplay is doing, in a line, and the way to switch it on when it is off. */
@Composable
private fun AutoplayNote(upNext: UpNext, state: AppState) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
        Text(autoplayNote(upNext), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (upNext == UpNext.OFF) {
            // Moved back by its own padding, so its word lines up with the line above it.
            SkinnedTextButton({ state.setAutoplay(true) }, Modifier.offset(x = (-12).dp)) { Text("Turn on") }
        }
    }
}

/**
 * One of autoplay's songs: quieter than a song in the queue, since nobody chose it. Tapping it plays it now,
 * with the ones above it joining the queue on the way; the buttons keep it in the queue or leave it out.
 */
@Composable
private fun SuggestionRow(track: Track, index: Int, state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Play now") { state.playSuggestion(index) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(SUGGESTION_ALPHA)) { Artwork(track.artworkUrl, 44.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(SUGGESTION_ALPHA)) {
            Text(track.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    track.artistLine.ifBlank { "Unknown artist" },
                    track.provider.displayName,
                    track.durationMs?.let(::formatDuration),
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton({ state.keepSuggestion(index) }, Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ state.removeSuggestion(index) }, Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, "Leave out", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How much quieter autoplay's songs are drawn than the queue's own. */
private const val SUGGESTION_ALPHA = .72f

/** Where the queue stands with autoplay, which decides what is said and shown under it. */
internal enum class UpNext {
    /** Autoplay is switched off: the music stops when the queue does. */
    OFF,

    /** The queue goes round, so it never ends for autoplay to follow. */
    REPEATING,

    /** Songs are lined up after the queue. */
    LINED_UP,

    /** The queue goes on in Spotify's own autoplay, which cannot be lined up here. */
    ELSEWHERE,

    /** On, and nothing lined up yet: autoplay looks as the queue nears its end. */
    WAITING,
}

internal fun upNext(queue: QueueState, autoplay: Boolean): UpNext = when {
    !autoplay -> UpNext.OFF
    queue.repeatMode != RepeatMode.OFF -> UpNext.REPEATING
    queue.suggestions.isNotEmpty() -> UpNext.LINED_UP
    queue.continuesElsewhere -> UpNext.ELSEWHERE
    else -> UpNext.WAITING
}

/** "Autoplay · Related on SoundCloud": autoplay's part of the queue, named for where its songs come from. */
internal fun autoplayHeading(queue: QueueState, upNext: UpNext): String = when (upNext) {
    UpNext.OFF -> "Autoplay is off"
    UpNext.LINED_UP -> listOfNotNull("Autoplay", queue.suggestionsFrom?.takeIf(String::isNotBlank)).joinToString(" · ")
    UpNext.ELSEWHERE -> "Autoplay · Spotify"
    UpNext.REPEATING, UpNext.WAITING -> "Autoplay"
}

/** The line under that heading. */
internal fun autoplayNote(upNext: UpNext): String = when (upNext) {
    UpNext.OFF -> "When the queue ends, the music stops."
    UpNext.REPEATING -> "Repeat is on, so the queue never ends for autoplay to follow."
    UpNext.LINED_UP -> "These play when the queue ends. Tap one to play it now."
    UpNext.ELSEWHERE ->
        "When the queue ends, Spotify chooses what comes next, and each song joins the queue as it starts. " +
            "Next still works: it asks Spotify to move on."
    UpNext.WAITING -> "As the queue nears its end, autoplay lines up songs like the last one here."
}

/**
 * What autoplay's switch says it does, for the source chosen: the same service's own idea of what comes next,
 * which is what makes autoplay sound like the service the listener picked, or YouTube Music's radio for all.
 */
internal fun autoplayDetail(source: AutoplaySource): String = when (source) {
    AutoplaySource.SAME_SERVICE ->
        "When the queue ends, songs like the last one follow it from the same service: YouTube Music's radio, " +
            "SoundCloud's related tracks, more from the artist on Bandcamp or Spotify (or Spotify's own autoplay, " +
            "while Spotify plays its songs itself), and VK's suggestions."
    AutoplaySource.YOUTUBE_MUSIC -> "When the queue ends, YouTube Music's radio carries on from the last song, whatever service it was on."
}

/**
 * What the player shows: what is playing, or -- when nothing is, after a launch that put a kept queue back
 * -- the song that queue was left on, paused, so play picks it up rather than the player being nowhere.
 *
 * Only while the player is idle with nothing in it; a song loading, playing, paused or failed is shown as
 * it is.
 */
internal fun shownPlayback(playback: PlaybackState, queue: QueueState): PlaybackState {
    if (playback.track != null || playback.status != PlaybackStatus.IDLE) return playback
    val kept = queue.current ?: return playback
    return playback.copy(track = kept, positionMs = 0, durationMs = kept.durationMs ?: 0)
}
