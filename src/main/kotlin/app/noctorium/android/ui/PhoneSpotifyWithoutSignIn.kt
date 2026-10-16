package app.noctorium.android.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.domain.ProviderType
import app.noctorium.settings.SettingsState
import app.noctorium.spotify.SPOTIFY_DATA_EXPORT_HOW
import app.noctorium.spotify.SPOTIFY_DATA_EXPORT_LIMITS
import app.noctorium.spotify.SPOTIFY_PUBLIC_PLAYLIST_LIMITS
import app.noctorium.spotify.SpotifyPublicPlaylistState
import java.io.FileNotFoundException

/*
 * Spotify without a sign-in, on Spotify's page in Settings, under the sign-in: the two ways in for a listener whose
 * sign-in Spotify refuses, as it refuses every account but a handful while the owner of the Spotify app it goes
 * through has no Premium. Following public playlists by their links, kept in step with Spotify; and importing the
 * file Spotify sends anybody who asks for their data, for the private playlists and Liked Songs. Neither needs an
 * account, and the songs of both play matched on YouTube Music, as every Spotify song without Premium does.
 *
 * Each says first, as a warning, what it cannot do, in Base's words, which every player shows as they are.
 */

/**
 * Public Spotify playlists, followed by their links: the warning about what that can and cannot do, a box for the
 * link, and every playlist followed, as it was last read, with what went wrong the last time and a way to stop
 * following it. A playlist is opened in the library from here by its name.
 *
 * The box empties once what was in it is followed, and not before: a link Spotify turned down stays, to be put
 * right rather than pasted again.
 */
@Composable
internal fun SpotifyPublicPlaylistsCard(settings: SettingsState, state: AppState) {
    val spotify = settings.spotify
    val library by state.library.collectAsState()
    val focus = LocalFocusManager.current
    var link by remember { mutableStateOf("") }
    // How many were followed when Add was pressed, until the answer comes: more than that, and the box is done with.
    var before by remember { mutableStateOf<Int?>(null) }
    val canAdd = !spotify.addingPublicPlaylist && link.isNotBlank()
    LaunchedEffect(spotify.addingPublicPlaylist, spotify.publicPlaylists.size) {
        val asked = before ?: return@LaunchedEffect
        if (spotify.addingPublicPlaylist) return@LaunchedEffect
        if (spotify.publicPlaylists.size > asked) link = ""
        before = null
    }

    fun add() {
        if (!canAdd) return
        // The keyboard goes, so what Spotify answers under the box is not left behind it.
        focus.clearFocus()
        before = spotify.publicPlaylists.size
        state.addSpotifyPublicPlaylist(link)
    }

    SettingsCardShell(anchor = SettingIds.SPOTIFY_PUBLIC_PLAYLISTS) {
        CardHeading(Glyph.PUBLIC.image, "Public playlists by link", tint = ProviderType.SPOTIFY.badgeColour())
        Spacer(Modifier.height(6.dp))
        Text(
            "No account is needed, and no Premium. Each playlist followed is read again whenever the library is, " +
                "so it stays as it is on Spotify.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        LimitsNotice(SPOTIFY_PUBLIC_PLAYLIST_LIMITS)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            link,
            { link = it.take(500) },
            label = { Text("Link to a public playlist") },
            placeholder = { Text("open.spotify.com/playlist/…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            supportingText = { Text("In Spotify: Share, then Copy link to playlist.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SkinnedButton({ add() }, enabled = canAdd) { Text("Add") }
        if (spotify.addingPublicPlaylist) Waiting("Reading the playlist from Spotify's public page…")
        spotify.publicMessage?.let { Note(it) }
        if (spotify.publicPlaylists.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Followed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            spotify.publicPlaylists.forEach { playlist ->
                PublicPlaylistRow(
                    playlist,
                    open = {
                        state.openPlaylist(publicPlaylistToOpen(playlist, library.playlists))
                        state.navigate(Destination.LIBRARY)
                    },
                ) { state.removeSpotifyPublicPlaylist(playlist.id) }
            }
        }
    }
}

/**
 * One followed public playlist: its name -- or its id, until it has been read -- whose it is and how many songs, and
 * what went wrong the last time it was read, in red, with the copy before it still the one in the library.
 */
@Composable
private fun PublicPlaylistRow(playlist: SpotifyPublicPlaylistState, open: () -> Unit, remove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Open in the library", onClick = open).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyph.QUEUE_MUSIC.image, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title.ifBlank { playlist.id }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                spotifyPublicLine(playlist).takeIf(String::isNotBlank)?.let { line ->
                    Text(
                        line,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                playlist.lastError?.let { problem ->
                    Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        SkinnedTextButton(remove) { Text("Remove") }
    }
}

/**
 * Spotify's data download, imported: how to ask Spotify for it, what the import cannot do, and a button that opens
 * the phone's own file picker for it -- then how far the import has got, and what came of it.
 *
 * The picker is the system's: it hands back the one file chosen, which this app may read and nothing more, so no
 * storage permission is asked for or wanted. What the file is called comes from the picker too, for the import to
 * say, and the file itself is opened only when the import is ready to read it, off the main thread, by core.
 */
@Composable
internal fun SpotifyImportCard(settings: SettingsState, state: AppState) {
    val import = settings.spotify.dataImport
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen: Uri? ->
        val uri = chosen ?: return@rememberLauncherForActivityResult
        state.importSpotifyData(importFileName(resolver.displayName(uri), uri.lastPathSegment)) {
            resolver.openInputStream(uri) ?: throw FileNotFoundException("Android could not open it")
        }
    }

    SettingsCardShell(anchor = SettingIds.SPOTIFY_IMPORT) {
        CardHeading(Glyph.SAVE_ALT.image, "Import your Spotify data", tint = ProviderType.SPOTIFY.badgeColour())
        Spacer(Modifier.height(6.dp))
        Text(
            "Every playlist you made on Spotify, and your Liked Songs, as playlists of Noctorium's own, with no sign-in. " +
                SPOTIFY_DATA_EXPORT_HOW,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        LimitsNotice(SPOTIFY_DATA_EXPORT_LIMITS)
        Spacer(Modifier.height(10.dp))
        SkinnedButton({ picker.launch(SPOTIFY_EXPORT_TYPES) }, enabled = !import.importing) { Text("Choose file") }
        if (import.importing) Waiting(import.progress ?: "Importing…")
        import.message?.takeIf { !import.importing }?.let { said ->
            Spacer(Modifier.height(10.dp))
            Row {
                Icon(
                    if (import.failed) Glyph.ERROR_OUTLINE.image else Glyph.CHECK_CIRCLE.image,
                    null,
                    Modifier.size(16.dp),
                    tint = if (import.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    said,
                    color = if (import.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/** What the file picker calls the file at [uri], when it says; null when it does not, or cannot be asked. */
private fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { row ->
        if (row.moveToFirst()) row.getString(0) else null
    }
}.getOrNull()

/** A spinner with what is being waited for, under a card's buttons. */
@Composable
private fun Waiting(what: String) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(what, fontSize = 12.sp)
    }
}

/** What core said about the last thing done, under everything else above it on the card. */
@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}
