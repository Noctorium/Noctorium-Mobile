package app.noctorium.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.Playlist
import app.noctorium.domain.PlaylistEdit
import app.noctorium.domain.PlaylistVisibility
import app.noctorium.domain.ProviderType
import app.noctorium.settings.ThemeSkin
import app.noctorium.domain.editableOnService
import app.noctorium.domain.shownVisibility
import app.noctorium.domain.pluralTracks
import app.noctorium.playlists.LocalPlaylist

/**
 * Who can see a playlist on the account it lives on, and renaming and deleting it there -- the desktop's
 * playlist actions, at phone size.
 *
 * Privacy is two chips side by side rather than a button that says the opposite of the current state: the
 * lit one is how it is now, and the other is one tap away. While the service is being asked, the chosen one
 * shows a spinner, so a tap never looks as though it did nothing.
 *
 * Nothing at all for a playlist the account cannot change. YouTube has a privacy SoundCloud does not --
 * unlisted -- and a description to edit, so its playlists have controls of their own: see
 * [YouTubePlaylistControls]. Named service by service, so a Bandcamp album that got this far is never renamed
 * on YouTube.
 */
@Composable
internal fun ServicePlaylistControls(playlist: Playlist, notice: String?, state: AppState) {
    if (!playlist.editableOnService()) return
    when (playlist.provider) {
        ProviderType.SOUNDCLOUD -> SoundCloudPlaylistControls(playlist, notice, state)
        ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO -> YouTubePlaylistControls(playlist, notice, state)
        ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.APPLE_MUSIC, ProviderType.DEEZER,
        ProviderType.TIDAL, ProviderType.LOCAL -> Unit
    }
}

/** SoundCloud's two privacies as two chips, then Rename and Delete. */
@Composable
private fun SoundCloudPlaylistControls(playlist: Playlist, notice: String?, state: AppState) {
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
        state.setSoundCloudPlaylistVisibility(playlist.id, isPublic)
    }

    if (renaming) {
        PlaylistNameDialog("Rename on ${playlist.provider.displayName}", playlist.title, "Rename") { title ->
            renaming = false
            title?.let { state.renameSoundCloudPlaylist(playlist.id, it) }
        }
    }
    if (deleting) {
        DeleteFromServiceDialog(playlist, dismiss = { deleting = false }) {
            deleting = false
            state.deleteSoundCloudPlaylist(playlist.id)
        }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false, true).forEach { isPublic ->
            SkinnedFilterChip(
                selected = playlist.isPublic == isPublic,
                onClick = { setPublic(isPublic) },
                label = { Text(if (isPublic) "Public" else "Private") },
                leadingIcon = {
                    if (asked == isPublic) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (isPublic) Glyph.PUBLIC.image else Glyph.LOCK.image, null, Modifier.size(16.dp))
                    }
                },
            )
        }
        AssistChip(
            onClick = { renaming = true },
            label = { Text("Rename") },
            leadingIcon = { Icon(Glyph.EDIT.image, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = { deleting = true },
            label = { Text("Delete") },
            leadingIcon = { Icon(Glyph.DELETE.image, null, Modifier.size(16.dp)) },
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
 * One of the listener's own YouTube playlists: who can see it, as three chips -- YouTube's public, unlisted and
 * private -- with what the lit one means under them, then Edit for the name, the description and the privacy
 * together, and Delete. The desktop's badge and Edit dialog, at phone size; the words are YouTube's own, so the
 * choice reads here as it does there.
 *
 * The chips stay unlit until YouTube has said which it is, so none is ever lit on a guess; one tap still sets it.
 */
@Composable
private fun YouTubePlaylistControls(playlist: Playlist, notice: String?, state: AppState) {
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val visibility = playlist.shownVisibility
    // The privacy asked for and not yet confirmed, as with SoundCloud's: cleared when the playlist says it, or
    // when YouTube answers at all -- a refusal included, which arrives as the notice.
    var asked by remember(playlist.id) { mutableStateOf<PlaylistVisibility?>(null) }
    LaunchedEffect(visibility) { if (visibility == asked) asked = null }
    LaunchedEffect(notice) { asked = null }

    fun choose(option: PlaylistVisibility) {
        if (visibility == option || asked != null) return
        asked = option
        state.setYouTubePlaylistVisibility(playlist.id, option)
    }

    if (editing) {
        YouTubePlaylistEditDialog(playlist) { edit ->
            editing = false
            edit?.let { state.editYouTubePlaylist(playlist.id, it) }
        }
    }
    if (deleting) {
        DeleteFromServiceDialog(playlist, dismiss = { deleting = false }) {
            deleting = false
            state.deleteYouTubePlaylist(playlist.id)
        }
    }

    // Two rows rather than one: five chips in a row ran Delete off the side of a phone, where nobody would find it.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistVisibility.entries.forEach { option ->
            SkinnedFilterChip(
                selected = visibility == option,
                onClick = { choose(option) },
                label = { Text(option.displayName) },
                leadingIcon = {
                    if (asked == option) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(privacyGlyph(option).image, null, Modifier.size(16.dp))
                    }
                },
            )
        }
    }
    Text(
        visibility?.let { "${privacyNote(it)}." } ?: "YouTube has not said who can see it. Choose one to set it.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = { editing = true },
            label = { Text("Edit") },
            leadingIcon = { Icon(Glyph.EDIT.image, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = { deleting = true },
            label = { Text("Delete") },
            leadingIcon = { Icon(Glyph.DELETE.image, null, Modifier.size(16.dp)) },
        )
    }
}

/** What choosing [visibility] means, in YouTube's own words for it -- the desktop's, the same. */
internal fun privacyNote(visibility: PlaylistVisibility): String = when (visibility) {
    PlaylistVisibility.PUBLIC -> "Anyone can search for and view"
    PlaylistVisibility.UNLISTED -> "Anyone with the link can view"
    PlaylistVisibility.PRIVATE -> "Only you can view"
}

/** The world for public, a link for unlisted, a padlock for private. */
internal fun privacyGlyph(visibility: PlaylistVisibility): Glyph = when (visibility) {
    PlaylistVisibility.PUBLIC -> Glyph.PUBLIC
    PlaylistVisibility.UNLISTED -> Glyph.LINK
    PlaylistVisibility.PRIVATE -> Glyph.LOCK
}

/**
 * Name, description and privacy for one of the listener's own YouTube playlists, saved together as YouTube
 * Music's own Edit saves them.
 *
 * [finish] is handed only what changed -- see [PlaylistEdit.between] -- or null for Cancel. Save waits until
 * something has changed and the name is not blank, so it never sends a request that does nothing.
 */
@Composable
internal fun YouTubePlaylistEditDialog(playlist: Playlist, finish: (PlaylistEdit?) -> Unit) {
    var name by remember(playlist.playlistKey) { mutableStateOf(playlist.title) }
    var description by remember(playlist.playlistKey) { mutableStateOf(playlist.description.orEmpty()) }
    var visibility by remember(playlist.playlistKey) { mutableStateOf(playlist.shownVisibility) }
    val edit = PlaylistEdit.between(playlist, name, description, visibility)

    SkinnedAlertDialog(
        onDismissRequest = { finish(null) },
        title = "Edit on ${playlist.provider.displayName}",
        text = {
            // A description of a few lines and three choices can outgrow a small phone held sideways.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    name,
                    { name = it.take(150) },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    description,
                    { description = it.take(5_000) },
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Privacy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                PlaylistVisibility.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(visibility == option, role = Role.RadioButton) { visibility = option }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkinnedRadioButton(visibility == option, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            privacyGlyph(option).image,
                            null,
                            Modifier.size(18.dp),
                            tint = if (visibility == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(option.displayName, fontSize = 14.sp)
                            Text(privacyNote(option), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (playlist.shownVisibility == null) {
                    Text(
                        "YouTube did not say who can see this playlist now. Choose one to set it, or leave it as it is.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            SkinnedTextButton(enabled = name.isNotBlank() && !edit.isEmpty, onClick = { finish(edit) }) { Text("Save") }
        },
        dismissButton = { SkinnedTextButton({ finish(null) }) { Text("Cancel") } },
    )
}

/** Deleting one of the account's own playlists from the account itself, asked first. */
@Composable
private fun DeleteFromServiceDialog(playlist: Playlist, dismiss: () -> Unit, delete: () -> Unit) {
    DeletePlaylistDialog(
        title = playlist.title,
        detail = "This removes it from your ${playlist.provider.displayName} account, not just from Noctorium. " +
            "The tracks themselves are untouched.",
        dismiss = dismiss,
        delete = delete,
    )
}

/**
 * The way from a Bandcamp album, artist or wishlist, or a VK playlist, to its own page: where a Bandcamp
 * record is bought, and where VK keeps its music.
 *
 * In the place the service controls take for an account's own playlists, which neither has here. Their
 * songs are played and not kept, so there is no download for them; the page is the other half.
 */
@Composable
internal fun ServicePageAction(label: String, page: String, state: AppState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        AssistChip(
            onClick = { state.openExternalUrl(page) },
            label = { Text(label) },
            leadingIcon = { Icon(Glyph.OPEN_IN_NEW.image, null, Modifier.size(16.dp)) },
        )
    }
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
    val subtitle = pluralTracks(playlist.trackCount) + " · " + localPlaylistOrigin(playlist, short = false)

    if (renaming) {
        PlaylistNameDialog("Rename", playlist.title, "Rename") { title ->
            renaming = false
            title?.let { state.renamePlaylist(playlist.id, it) }
        }
    }
    if (deleting) {
        DeletePlaylistDialog(
            title = playlist.title,
            detail = if (playlist.fromSpotifyImport) {
                "It came from Spotify's data download, so it goes from Noctorium, and your playlist on Spotify is " +
                    "untouched. Importing the file again brings it back."
            } else {
                "It was made here, so it is gone from Noctorium for good. The tracks themselves are untouched."
            },
            dismiss = { deleting = false },
        ) {
            deleting = false
            state.deletePlaylist(playlist.id)
        }
    }

    ScreenScaffold {
        val actions: @Composable () -> Unit = {
            if (playlist.tracks.isNotEmpty()) {
                IconButton({ state.playLocalPlaylist(playlist) }) { Icon(Glyph.PLAY_ARROW.image, "Play this playlist") }
            }
            Box {
                IconButton({ menu = true }) { Icon(Glyph.MORE_VERT.image, "Playlist actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Glyph.EDIT.image, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    // Copied up only to the accounts that are signed in, and only the tracks that live there --
                    // picked out here, since a Bandcamp song sent along to YouTube would arrive as a video id.
                    services.forEach { service ->
                        DropdownMenuItem(
                            text = { Text("Copy to ${service.displayName}") },
                            leadingIcon = { Icon(Glyph.CLOUD_UPLOAD.image, null) },
                            onClick = {
                                menu = false
                                val copy = playlist.copy(tracks = tracksPlaylistCanHold(service, playlist.tracks))
                                when (service) {
                                    ProviderType.SOUNDCLOUD -> state.publishPlaylistToSoundCloud(copy)
                                    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO -> state.publishPlaylistToYouTube(copy)
                                    else -> Unit
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Glyph.DELETE.image, null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }
        // A window's title bar under the Windows skins, closed by its cross; the row it always was otherwise.
        if (LocalSkin.current != ThemeSkin.STANDARD) {
            SkinScreenTitle(playlist.title, subtitle, state::closeLocalPlaylist) {
                Row { actions() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(state::closeLocalPlaylist) { Icon(Glyph.ARROW_BACK.image, "Back to the library") }
                Column(Modifier.weight(1f)) {
                    Text(playlist.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                actions()
            }
        }

        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        if (playlist.tracks.isEmpty()) {
            EmptyNote("Nothing in here yet", "Add songs from the menu on any track, under Add to playlist.")
        } else {
            LazyColumn(Modifier.skinList(), contentPadding = chromePadding(24.dp)) {
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
                                        Icon(Glyph.KEYBOARD_ARROW_UP.image, "Move up")
                                    }
                                    IconButton(
                                        { state.moveInLocalPlaylist(playlist.id, index, index + 1) },
                                        enabled = index < playlist.tracks.lastIndex,
                                        modifier = Modifier.size(34.dp),
                                    ) { Icon(Glyph.KEYBOARD_ARROW_DOWN.image, "Move down") }
                                    IconButton({ state.removeTrackFromPlaylist(playlist.id, track.queueKey) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Glyph.REMOVE_CIRCLE_OUTLINE.image, "Take out of this playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun PlaylistNameDialog(title: String, initial: String, confirm: String, finish: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    SkinnedAlertDialog(
        onDismissRequest = { finish(null) },
        title = title,
        text = {
            OutlinedTextField(name, { name = it.take(100) }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            SkinnedTextButton(enabled = name.isNotBlank() && name.trim() != initial, onClick = { finish(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { SkinnedTextButton({ finish(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun DeletePlaylistDialog(title: String, detail: String, dismiss: () -> Unit, delete: () -> Unit) {
    SkinnedAlertDialog(
        onDismissRequest = dismiss,
        title = "Delete \"$title\"?",
        text = { Text(detail) },
        confirmButton = { SkinnedTextButton(delete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { SkinnedTextButton(dismiss) { Text("Keep") } },
    )
}

