package app.noctorium.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.pluralTracks
import app.noctorium.playlists.LocalPlaylist

/**
 * Who can see a playlist on the account it lives on, and renaming and deleting it there -- the desktop's
 * playlist actions, at phone size.
 *
 * Privacy is two chips side by side rather than a button that says the opposite of the current state: the
 * lit one is how it is now, and the other is one tap away. While the service is being asked, the chosen one
 * shows a spinner, so a tap never looks as though it did nothing.
 */
@Composable
internal fun ServicePlaylistControls(playlist: Playlist, notice: String?, state: AppState) {
    val onSoundCloud = playlist.provider == ProviderType.SOUNDCLOUD
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // The privacy asked for and not yet confirmed. Cleared when the playlist says it, or when the service
    // answers with anything at all -- a refusal included, which arrives as the notice.
    var asked by remember(playlist.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(playlist.isPublic) { if (playlist.isPublic == asked) asked = null }
    LaunchedEffect(notice) { asked = null }

    fun setPublic(isPublic: Boolean) {
        if (playlist.isPublic == isPublic || asked != null) return
        asked = isPublic
        if (onSoundCloud) state.setSoundCloudPlaylistVisibility(playlist.id, isPublic)
        else state.setYouTubePlaylistVisibility(playlist.id, isPublic)
    }

    if (renaming) {
        PlaylistNameDialog("Rename on ${playlist.provider.displayName}", playlist.title, "Rename") { title ->
            renaming = false
            title?.let {
                if (onSoundCloud) state.renameSoundCloudPlaylist(playlist.id, it) else state.renameYouTubePlaylist(playlist.id, it)
            }
        }
    }
    if (deleting) {
        DeletePlaylistDialog(
            title = playlist.title,
            detail = "This removes it from your ${playlist.provider.displayName} account, not just from Noctorium. " +
                "The tracks themselves are untouched.",
            dismiss = { deleting = false },
        ) {
            deleting = false
            if (onSoundCloud) state.deleteSoundCloudPlaylist(playlist.id) else state.deleteYouTubePlaylist(playlist.id)
        }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false, true).forEach { isPublic ->
            FilterChip(
                selected = playlist.isPublic == isPublic,
                onClick = { setPublic(isPublic) },
                label = { Text(if (isPublic) "Public" else "Private") },
                leadingIcon = {
                    if (asked == isPublic) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (isPublic) Icons.Default.Public else Icons.Default.Lock, null, Modifier.size(16.dp))
                    }
                },
            )
        }
        AssistChip(
            onClick = { renaming = true },
            label = { Text("Rename") },
            leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = { deleting = true },
            label = { Text("Delete") },
            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) },
        )
    }
    Text(
        when (playlist.isPublic) {
            true -> "Anyone can find it on your ${playlist.provider.displayName} profile."
            false -> "Only you can see it, and anyone you send its link to."
            null -> "${playlist.provider.displayName} has not said who can see it. Choose one to set it."
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/**
 * A playlist made in Noctorium, opened: play it, put it in order, take tracks out, rename it, delete it,
 * or copy it up to an account.
 *
 * The phone could open one of these and then showed nothing: the list said it was open and no screen
 * answered. This is that screen.
 */
@Composable
internal fun LocalPlaylistScreen(playlist: LocalPlaylist, notice: String?, services: List<ProviderType>, state: AppState) {
    val playback by state.playback.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (renaming) {
        PlaylistNameDialog("Rename", playlist.title, "Rename") { title ->
            renaming = false
            title?.let { state.renamePlaylist(playlist.id, it) }
        }
    }
    if (deleting) {
        DeletePlaylistDialog(
            title = playlist.title,
            detail = "It was made here, so it is gone from Noctorium for good. The tracks themselves are untouched.",
            dismiss = { deleting = false },
        ) {
            deleting = false
            state.deletePlaylist(playlist.id)
        }
    }

    ScreenScaffold {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(state::closeLocalPlaylist) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the library") }
            Column(Modifier.weight(1f)) {
                Text(playlist.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralTracks(playlist.trackCount) + " · made in Noctorium",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            if (playlist.tracks.isNotEmpty()) {
                IconButton({ state.playLocalPlaylist(playlist) }) { Icon(Icons.Default.PlayArrow, "Play this playlist") }
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Playlist actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    // Copied up only to the accounts that are signed in, and only the tracks that live there.
                    services.forEach { service ->
                        DropdownMenuItem(
                            text = { Text("Copy to ${service.displayName}") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                            onClick = {
                                menu = false
                                if (service == ProviderType.SOUNDCLOUD) state.publishPlaylistToSoundCloud(playlist)
                                else state.publishPlaylistToYouTube(playlist)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }

        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        if (playlist.tracks.isEmpty()) {
            EmptyNote("Nothing in here yet", "Add songs from the menu on any track, under Add to playlist.")
        } else {
            LazyColumn(contentPadding = chromePadding(24.dp)) {
                itemsIndexed(playlist.tracks, key = { _, track -> track.queueKey }) { index, track ->
                    // Rows glide to their new places when one moves or is taken out, instead of jumping.
                    Box(if (LocalMotion.current) Modifier.animateItem() else Modifier) {
                        TrackRow(
                            track,
                            state,
                            isCurrent = playback.track?.queueKey == track.queueKey,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton({ state.moveInLocalPlaylist(playlist.id, index, index - 1) }, enabled = index > 0, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.KeyboardArrowUp, "Move up")
                                    }
                                    IconButton(
                                        { state.moveInLocalPlaylist(playlist.id, index, index + 1) },
                                        enabled = index < playlist.tracks.lastIndex,
                                        modifier = Modifier.size(34.dp),
                                    ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                    IconButton({ state.removeTrackFromPlaylist(playlist.id, track.queueKey) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.RemoveCircleOutline, "Take out of this playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                        ) { state.playLocalPlaylist(playlist, startAt = track) }
                    }
                }
            }
        }
    }
}

/** A name for a playlist, new or changed. Answers null when the listener thought better of it. */
@Composable
private fun PlaylistNameDialog(title: String, initial: String, confirm: String, finish: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { finish(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it.take(100) }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && name.trim() != initial, onClick = { finish(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton({ finish(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun DeletePlaylistDialog(title: String, detail: String, dismiss: () -> Unit, delete: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Delete \"$title\"?") },
        text = { Text(detail) },
        confirmButton = { TextButton(delete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(dismiss) { Text("Keep") } },
    )
}

