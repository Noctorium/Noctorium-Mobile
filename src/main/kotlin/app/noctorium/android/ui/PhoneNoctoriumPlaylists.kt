package app.noctorium.android.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.playlists.PlaylistCloudKind
import app.noctorium.playlists.PlaylistSyncState
import app.noctorium.settings.SettingsState

/*
 * Settings › You › Noctorium playlists on the phone: where the playlists every Noctorium shares are kept. A folder
 * the phone's own file picker chooses, or the listener's Google Drive, signed in to. The desktop's page, at a
 * phone's size, and with the phone's own way into a folder.
 */

/** What a Noctorium playlist would be, in the dialog that makes one. */
internal fun noctoriumPlaylistNote(sync: PlaylistSyncState): String = when {
    sync.kind == PlaylistCloudKind.NONE -> "On every device you use Noctorium on. Choose where they are kept in Settings › Noctorium playlists."
    sync.location != null -> "On every device you use Noctorium on, kept in ${sync.location}."
    else -> "On every device you use Noctorium on."
}

/** The settings tile's line: where they are kept, and how the last sync went. */
internal fun noctoriumPlaylistsSubtitle(sync: PlaylistSyncState): String {
    val problem = sync.problem
    return when {
        sync.kind == PlaylistCloudKind.NONE -> "Your playlists on every device"
        problem != null -> problem
        else -> "In ${sync.location ?: sync.kind.displayName}"
    }
}

@Composable
internal fun NoctoriumPlaylistsCard(settings: SettingsState, state: AppState) {
    val sync by state.playlistSyncState.collectAsState()
    val cloud = settings.preferences.playlistCloud
    val context = LocalContext.current
    var clientId by remember(cloud.googleClientId) { mutableStateOf(cloud.googleClientId) }
    var clientSecret by remember { mutableStateOf("") }
    // The system's folder picker. The permission it gives is kept, so the folder is still ours after a restart.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            state.setPlaylistCloud(PlaylistCloudKind.PHONE_FOLDER, uri.toString())
        }
    }

    SettingsCardShell(anchor = SettingIds.NOCTORIUM_PLAYLISTS) {
        CardHeading(Glyph.CLOUD.image, "Noctorium playlists")
        Spacer(Modifier.height(6.dp))
        Text(
            "A Noctorium playlist is on every device you use Noctorium on -- this phone, your computer, Noctorium Rust -- " +
                "kept in storage of your own. A local playlist stays on this phone. You choose which when you make one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        StatusLine(sync, state)
        Spacer(Modifier.height(12.dp))
        Text("Keep them in", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Option(
            "Google Drive",
            "Signed in to your Google Drive. Noctorium sees only the files it makes there. The way to reach Drive from a phone: its app does not lend whole folders to other apps.",
            cloud.kind == PlaylistCloudKind.GOOGLE_DRIVE,
        ) { state.setPlaylistCloud(PlaylistCloudKind.GOOGLE_DRIVE) }
        if (cloud.kind == PlaylistCloudKind.GOOGLE_DRIVE) {
            Column(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sync.googleSignedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Signed in to Google Drive", Modifier.weight(1f), fontSize = 13.sp)
                        SkinnedTextButton(state::signOutOfGoogleDrive) { Text("Sign out") }
                    }
                } else {
                    Text(
                        "Needs an OAuth client of your own, the same one your computer uses: Google Cloud Console › Credentials › " +
                            "OAuth client, \"Desktop app\" type, with the Google Drive API on. Paste its id and secret.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    OutlinedTextField(clientId, { clientId = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Client id") }, singleLine = true)
                    OutlinedTextField(
                        clientSecret,
                        { clientSecret = it.trim() },
                        Modifier.fillMaxWidth(),
                        label = { Text(if (sync.googleAppReady) "Client secret (saved)" else "Client secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (clientId != cloud.googleClientId || clientSecret.isNotBlank()) {
                            SkinnedTextButton({ state.setGoogleDriveApp(clientId, clientSecret); clientSecret = "" }) { Text("Save") }
                        }
                        SkinnedTextButton(state::signInToGoogleDrive, enabled = sync.googleAppReady && !sync.googleSigningIn) {
                            if (sync.googleSigningIn) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Sign in with Google")
                        }
                    }
                }
            }
        }
        Option(
            "A folder on this phone",
            "Any folder the phone's file picker reaches -- its storage, an SD card, a folder Syncthing keeps in step with your computer.",
            cloud.kind == PlaylistCloudKind.PHONE_FOLDER,
        ) { pickFolder.launch(null) }
        if (cloud.kind == PlaylistCloudKind.PHONE_FOLDER) {
            SkinnedTextButton({ pickFolder.launch(null) }, modifier = Modifier.padding(start = 4.dp)) { Text("Choose another folder") }
        }
        if (cloud.kind != PlaylistCloudKind.NONE) {
            Spacer(Modifier.height(6.dp))
            SkinnedTextButton({ state.setPlaylistCloud(PlaylistCloudKind.NONE) }) { Text("Stop syncing") }
        }
    }
}

@Composable
private fun StatusLine(sync: PlaylistSyncState, state: AppState) {
    val problem = sync.problem
    val text = when {
        sync.kind == PlaylistCloudKind.NONE -> "Not set up: Noctorium playlists stay on this phone until you choose where to keep them."
        problem != null -> problem
        sync.syncing -> "Syncing…"
        else -> "Kept in ${sync.location ?: sync.kind.displayName}."
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyph.CLOUD.image, null, Modifier.size(18.dp), tint = if (problem != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(9.dp))
        Text(text, Modifier.weight(1f), fontSize = 12.sp)
        if (sync.kind != PlaylistCloudKind.NONE) SkinnedTextButton(state::syncPlaylistsNow, enabled = !sync.syncing) { Text("Sync now") }
    }
}

@Composable
private fun Option(title: String, note: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected, choose)
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
