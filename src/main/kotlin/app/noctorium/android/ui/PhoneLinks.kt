package app.noctorium.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.LinkAction
import app.noctorium.core.LinkState
import app.noctorium.core.LinkStatus
import app.noctorium.domain.LinkKind
import app.noctorium.domain.PlaybackOrigin
import app.noctorium.domain.Track
import app.noctorium.domain.arrivedAtOnce
import app.noctorium.domain.findMusicLink
import app.noctorium.domain.pluralTracks
import app.noctorium.downloads.DownloadStage
import app.noctorium.playback.PlaybackState
import kotlin.math.roundToInt

/**
 * Paste a link, hear the song.
 *
 * The clipboard is read when Paste is pressed and at no other time. Android tells the listener every time
 * an app reads it, and a screen that looked every second or two to offer what was there would announce
 * itself in a stream of notices. Pasting into the box by hand works the same way, and so does sharing a
 * link to Noctorium from another app, which lands here.
 */
@Composable
internal fun LinkScreen(state: AppState) {
    val link by state.linkState.collectAsState()
    val playback by state.playback.collectAsState()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(link.link?.url.orEmpty()) }
    // A link shared from another app arrives without going through the box, and the box then shows it.
    LaunchedEffect(link.link) {
        link.link?.let { arrived -> if (findMusicLink(text) != arrived) text = arrived.url }
    }

    fun submit(value: String, action: LinkAction = LinkAction.PLAY) {
        text = findMusicLink(value)?.url ?: value
        state.openLink(value, action)
    }

    ScreenScaffold {
        ScreenTitle("Link", "Play a song or a playlist from its link")
        LazyColumn(contentPadding = chromePadding(24.dp)) {
            item {
                PhoneLinkField(
                    text = text,
                    onText = { value ->
                        val before = findMusicLink(text)

                        val pasted = arrivedAtOnce(text, value)
                        text = value
                        // Played on arrival when pasted, once per link. Typed by hand, a link is valid several
                        // letters before it is the right one, so a typed link plays on Go instead.
                        val now = findMusicLink(value)
                        if (pasted && now != null && now != before) submit(value)
                    },
                    onSubmit = { submit(text) },
                    onClear = { text = ""; state.clearLink() },
                    placeholder = "Paste a YouTube or SoundCloud link",
                )
            }
            item {
                Button(
                    { clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let(::submit) },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(52.dp),
                ) {
                    Icon(Icons.Default.ContentPaste, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste and play", fontSize = 16.sp)
                }
            }
            phoneLinkOutcome(link, playback, state)
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Or, in YouTube, YouTube Music or SoundCloud, tap Share and pick Noctorium.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneLinkField(
    text: String,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        text,
        onText,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Link, null) },
        trailingIcon = {
            if (text.isNotEmpty()) IconButton(onClear) { Icon(Icons.Default.Close, "Clear") }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun LazyListScope.phoneLinkOutcome(link: LinkState, playback: PlaybackState, state: AppState) {
    when (link.status) {
        LinkStatus.IDLE -> Unit
        LinkStatus.OPENING -> item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        link.link?.kind == LinkKind.SHORT -> "Following the share link…"
                        link.link?.kind == LinkKind.PLAYLIST -> "Reading the playlist…"
                        else -> "Opening the song…"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinkStatus.FAILED -> item {
            Text(
                link.message ?: "Could not open that link.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        LinkStatus.DONE -> {
            link.message?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
            if (link.link?.kind == LinkKind.PLAYLIST) {
                item { PhonePlaylistActions(link.tracks, state) }
                items(link.tracks, key = { it.queueKey }) { track ->
                    TrackRow(track, state, isCurrent = playback.track?.queueKey == track.queueKey) {
                        state.play(track, PlaybackOrigin.PLAYLIST, link.tracks)
                    }
                }
            } else {
                link.tracks.firstOrNull()?.let { found ->
                    item {
                        val shown = playback.track?.takeIf { it.queueKey == found.queueKey } ?: found
                        PhoneLinkTrackCard(shown, playing = playback.track?.queueKey == found.queueKey, state)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneLinkTrackCard(track: Track, playing: Boolean, state: AppState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, 84.dp, corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (playing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Playing", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                    Text(track.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(track.artistLine.ifBlank { "Reading the artist…" }, track.provider.displayName).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!playing) {
                    Button({ state.play(track) }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Play")
                    }
                }
                FilledTonalButton({ state.addToQueue(track) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Queue")
                }
                // Not for a Bandcamp or VK song, which is there to be heard rather than kept.
                if (state.canKeep(track)) PhoneDownloadButton(track, state)
            }
        }
    }
}

/** Wrapped rather than squeezed: at a large font size three buttons do not fit a row, and a squeezed one breaks its label letter by letter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhonePlaylistActions(tracks: List<Track>, state: AppState) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button({ tracks.firstOrNull()?.let { state.play(it, PlaybackOrigin.PLAYLIST, tracks) } }) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Play all", maxLines = 1)
        }
        FilledTonalButton({ tracks.forEach(state::addToQueue) }) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Queue all", maxLines = 1)
        }
        // A Bandcamp album has nothing in it that may be kept, nor a VK playlist, so neither is offered it.
        if (tracks.any(state::canKeep)) {
            FilledTonalButton({ state.downloadAll(tracks) }) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download all", maxLines = 1)
            }
        }
    }
    Text(
        pluralTracks(tracks.size),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/** Download as a labelled button that says how far along it is. */
@Composable
private fun PhoneDownloadButton(track: Track, state: AppState) {
    val downloads by state.downloadState.collectAsState()
    val onDisk = state.downloadableTrack(track)
    val job = downloads.jobFor(onDisk)
    val kept = downloads.isDownloaded(onDisk)
    OutlinedButton(
        onClick = {
            when {
                job?.stage == DownloadStage.FAILED -> { state.cancelDownload(onDisk.queueKey); state.downloadTrack(track) }
                job != null -> state.cancelDownload(onDisk.queueKey)
                else -> state.downloadTrack(track)
            }
        },
        enabled = !kept,
    ) {
        Icon(if (kept) Icons.Default.DownloadDone else Icons.Default.Download, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                kept -> "Downloaded"
                job?.stage == DownloadStage.FAILED -> "Try again"
                job?.stage == DownloadStage.DOWNLOADING -> "${(job.progress * 100).roundToInt()}%"
                job != null -> "Waiting"
                else -> "Download"
            },
        )
    }
}

/**
 * Everything kept on the phone, what is still arriving, and a box to download more by link.
 *
 * The same list the library's card summarises, given the room to show a playlist's worth arriving one
 * after another.
 */
@Composable
internal fun DownloadsScreen(state: AppState) {
    val downloads by state.downloadState.collectAsState()
    val link by state.linkState.collectAsState()
    val playback by state.playback.collectAsState()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    // A Bandcamp link is turned down here, with the reason, rather than taken and nothing coming of it.
    val pasted = findMusicLink(text)
    val downloadable = downloadsFrom(pasted)

    val summary = buildString {
        append(if (downloads.entries.isEmpty()) "Nothing kept yet" else pluralTracks(downloads.entries.size))
        if (downloads.entries.isNotEmpty()) append(" · they play with no connection")
    }

    ScreenScaffold {
        ScreenTitle("Downloads", summary) {
            if (downloads.entries.isNotEmpty()) {
                FilledTonalIconButton({ state.playDownloads() }) { Icon(Icons.Default.PlayArrow, "Play all downloads") }
            }
        }
        LazyColumn(contentPadding = chromePadding(24.dp)) {
            item {
                PhoneLinkField(
                    text = text,
                    onText = { text = it },
                    onSubmit = { if (downloadable) state.openLink(text, LinkAction.DOWNLOAD) },
                    onClear = { text = "" },
                    placeholder = "Paste a link to download it",
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        { clipboard.getText()?.text?.takeIf(String::isNotBlank)?.let { text = it } },
                        Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Paste")
                    }
                    Button(
                        { state.openLink(text, LinkAction.DOWNLOAD) },
                        Modifier.weight(1f),
                        enabled = downloadable,
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download")
                    }
                }
                pasted?.let { keepRefusal(it.provider) }?.let { PhoneNote(it) }
                if (link.action == LinkAction.DOWNLOAD) {
                    when (link.status) {
                        LinkStatus.OPENING -> PhoneNote("Looking it up…")
                        LinkStatus.FAILED -> PhoneNote(link.message ?: "", error = true)
                        else -> Unit
                    }
                }
                downloads.message?.let { PhoneNote(it) }
            }
            if (downloads.active.isNotEmpty()) {
                item { PhoneSection("On the way") }
                items(downloads.active, key = { "active:" + it.track.queueKey }) { job ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(job.track.artworkUrl, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.track.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            if (job.stage == DownloadStage.FAILED) {
                                Text(job.detail ?: "Could not download this one.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 2)
                            } else {
                                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
                            }
                        }
                        IconButton({ state.cancelDownload(job.track.queueKey) }) { Icon(Icons.Default.Close, "Stop this download") }
                    }
                }
            }
            item { PhoneSection("On this phone") }
            if (downloads.entries.isEmpty()) {
                item { PhoneNote("Nothing yet. Download a song from its ⋮ menu anywhere in Noctorium, or paste its link above.") }
            } else {
                items(downloads.entries, key = { "kept:" + it.queueKey }) { entry ->
                    val track = entry.toTrack()
                    TrackRow(
                        track,
                        state,
                        isCurrent = playback.track?.queueKey == track.queueKey,
                        trailing = {
                            IconButton({ state.deleteDownload(entry.queueKey) }) {
                                Icon(Icons.Default.Delete, "Remove this download", Modifier.size(20.dp))
                            }
                        },
                    ) { state.playDownloads(track) }
                }
                item {
                    TextButton(state::deleteAllDownloads, Modifier.padding(horizontal = 8.dp)) {
                        Text("Remove all downloads", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSection(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun PhoneNote(text: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
