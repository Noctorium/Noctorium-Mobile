package app.noctorium.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.LikeState
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.settings.AccountConnectionStatus
import app.noctorium.settings.SettingsState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * What the YouTube Music card adds beyond signing in: sending the sign-in to a computer, the account and
 * channel to act as, and whether plays go into the account's history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneYouTubeExtras(settings: SettingsState, likes: LikeState, state: AppState) {
    val source = settings.preferences.youtubeCookies
    var pasteOpen by remember { mutableStateOf(false) }
    if (pasteOpen) PhonePasteCookiesDialog(state) { pasteOpen = false }
    // The camera screen is the scanner library's own; what it read comes back here, and goes straight out.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(state::sendYouTubeSignIn)
    }
    LaunchedEffect(likes.youTubeReady) {
        if (likes.youTubeReady && likes.youTubeChannels.isEmpty()) state.loadYouTubeChannels()
    }

    // A session found to have lapsed says so, in place of the reassurance above it.
    val account = settings.youtubeAccount
    if (account.status == AccountConnectionStatus.ERROR) {
        Spacer(Modifier.height(8.dp))
        Text(account.detail.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        account.hint?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    }

    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (likes.youTubeReady) {
            // The way SimpMusic signs its desktop in: from here, where the account already is.
            SkinnedOutlinedButton({
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Point at the code in Noctorium on your computer")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false),
                )
            }) {
                Icon(Icons.Default.Computer, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send to computer")
            }
        }
        if (source.isConfigured) SkinnedOutlinedButton(state::checkYouTubeSignIn) { Text("Check now") }
        SkinnedTextButton({ pasteOpen = true }) { Text("Paste cookies") }
    }

    likes.message?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }

    if (!likes.youTubeReady) return

    Spacer(Modifier.height(14.dp))
    Text("Account and channel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(
        "Whichever is chosen is the one Noctorium acts as: its likes, its playlists, its history.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(8.dp))
    val preferences = settings.preferences
    val accounts = likes.youTubeChannels.map { it.email }.distinct().size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        likes.youTubeChannels.groupBy { it.email }.forEach { (email, owned) ->
            if (email != null && accounts > 1) Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            owned.forEach { channel ->
                val chosen = channel.pageId == preferences.youtubePageId && channel.authUser == preferences.youtubeAuthUser
                Surface(
                    onClick = { state.setYouTubeChannel(channel) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .5f) else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkinnedRadioButton(chosen, { state.setYouTubeChannel(channel) })
                        Artwork(channel.photoUrl, 36.dp, corner = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(channel.name, fontSize = 14.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                listOfNotNull(channel.handle, if (channel.isDefault) "own channel" else "brand channel").joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Add what I play to my YouTube Music history", fontSize = 13.sp)
            Text(
                "So YouTube Music's own recommendations learn from it too.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        SkinnedSwitch(preferences.youtubeHistory, state::setYouTubeHistory)
    }
}

/** Follow the playing song's artist on YouTube Music. Shown once the artist is known, for YouTube songs only. */
@Composable
internal fun PhoneFollowArtistChip(track: Track, state: AppState, modifier: Modifier = Modifier) {
    val artist by state.artistFollow.collectAsState()
    if (track.provider != ProviderType.YOUTUBE_MUSIC && track.provider != ProviderType.YOUTUBE_VIDEO) return
    val known = artist ?: return
    val following = known.following == true
    SkinnedFilterChip(
        selected = following,
        onClick = state::toggleFollowArtist,
        label = { Text(if (following) "Following ${known.name}" else "Follow ${known.name}") },
        leadingIcon = { Icon(if (following) Icons.Default.Check else Icons.Default.PersonAdd, null, Modifier.size(18.dp)) },
        modifier = modifier,
    )
}

@Composable
private fun PhonePasteCookiesDialog(state: AppState, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    SkinnedAlertDialog(
        onDismissRequest = close,
        title = "Paste cookies",
        text = {
            Column {
                Text(
                    "A cookies.txt, a cookie-editor export, or a Cookie header from music.youtube.com while signed in. " +
                        "They are as good as your password, and stay on this phone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            }
        },
        confirmButton = { SkinnedButton({ state.importYouTubeCookies(text); close() }, enabled = text.isNotBlank()) { Text("Sign in") } },
        dismissButton = { SkinnedTextButton(close) { Text("Cancel") } },
    )
}
