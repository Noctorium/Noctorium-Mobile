package app.noctorium.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.ProviderType
import app.noctorium.settings.ScrobbleConnectionStatus
import app.noctorium.settings.ThemePreset

/**
 * The pages under Settings, in the order the list shows them.
 *
 * Laid out the way the desktop's are: a short list of tiles, each saying where it stands, and one page
 * behind each. Settings used to be every card one after another, which put the lyrics below the updates
 * and the diagnostics a long scroll from anywhere.
 */
private enum class SettingsPage(val title: String) {
    ACCOUNT("Noctorium account"),
    YOUTUBE("YouTube Music"),
    SOUNDCLOUD("SoundCloud"),
    SPOTIFY("Spotify library"),
    CUSTOMIZATION("Customization"),
    PLAYBACK("Playback"),
    PHONE("On this phone"),
    LYRICS("Lyrics"),
    SCROBBLING("Scrobbling"),
    CONNECT("Noctorium Connect"),
    SAVING("Saving music"),
    UPDATES("Updates"),
    DIAGNOSTICS("Diagnostics"),
}

/**
 * Settings, as much of them as mean anything on a phone.
 *
 * Deliberately shorter than the desktop's. Discord Rich Presence needs its desktop app over a named pipe
 * and there is none here; the window chrome settings describe a window this has not got. What is left is
 * what actually differs from device to device: the accounts, where saved music goes, and what is on the
 * machine.
 *
 * [backEnabled] is off while something covers this screen -- the now playing sheet, a sign-in -- so the
 * back button closes that first instead of turning a page nobody can see.
 */
@Composable
internal fun SettingsScreen(state: AppState, backEnabled: Boolean = true, signIn: (ProviderType) -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = backEnabled && page != null) { page = null }

    val open = page
    if (open == null) {
        SettingsHome(state) { page = it }
    } else {
        SettingsPageScreen(open, state, signIn) { page = null }
    }
}

@Composable
private fun SettingsHome(state: AppState, open: (SettingsPage) -> Unit) {
    val settings by state.settings.collectAsState()
    val likes by state.likes.collectAsState()
    val account by state.account.collectAsState()
    val connect by state.connect.collectAsState()
    val updates by state.updates.collectAsState()
    val preferences = settings.preferences

    // The status-bar inset the other screens get from ScreenScaffold. Without it the title sits under
    // the clock, which on this phone is exactly where the clock is.
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = chromePadding(28.dp),
    ) {
        item { ScreenTitle("Settings", "Accounts, services and how Noctorium behaves") }
        settingsMessage(settings, state)

        item { SectionLabel("Your account") }
        item {
            SettingsTile(
                Icons.Default.Insights,
                account.user?.displayName ?: "Noctorium account",
                if (account.signedIn) {
                    "${account.stats.streams} streamed · ${account.stats.uniqueTracks} different"
                } else {
                    "Sign in to count what you listen to"
                },
                active = account.signedIn,
            ) { open(SettingsPage.ACCOUNT) }
        }

        item { SectionLabel("Services") }
        item {
            SettingsTile(
                Icons.Default.PlayCircle,
                "YouTube Music",
                serviceSummary(ProviderType.YOUTUBE_MUSIC, settings, likes),
                active = likes.youTubeReady,
            ) { open(SettingsPage.YOUTUBE) }
        }
        item {
            SettingsTile(
                Icons.Default.Cloud,
                "SoundCloud",
                serviceSummary(ProviderType.SOUNDCLOUD, settings, likes),
                active = likes.soundCloudReady,
            ) { open(SettingsPage.SOUNDCLOUD) }
        }
        item {
            val spotify = settings.spotify
            SettingsTile(
                Icons.Default.LibraryMusic,
                "Spotify library",
                when {
                    spotify.connecting -> "Waiting for Spotify…"
                    spotify.connected && spotify.accountName.isNotBlank() -> "Reading ${spotify.accountName}'s library"
                    spotify.connected -> "Your Spotify library is connected"
                    else -> "Read your playlists and liked songs"
                },
                tint = Color(0xFF1DB954),
                active = spotify.connected,
            ) { open(SettingsPage.SPOTIFY) }
        }

        item { SectionLabel("Look and feel") }
        item {
            val theme = preferences.theme
            SettingsTile(
                Icons.Default.Palette,
                "Customization",
                when {
                    theme == ThemePreset.CUSTOM -> "Your own colours · ${preferences.phone.playerBarStyle.displayName} player bar"
                    theme.family == theme.displayName -> "${theme.displayName} · ${preferences.phone.playerBarStyle.displayName} player bar"
                    else -> "${theme.family} ${theme.displayName} · ${preferences.phone.playerBarStyle.displayName} player bar"
                },
            ) { open(SettingsPage.CUSTOMIZATION) }
        }
        item {
            SettingsTile(
                Icons.Default.GraphicEq,
                "Playback",
                listOfNotNull(
                    if (preferences.phone.playbackSpeed == 1f) "Normal speed" else "${preferences.phone.playbackSpeed}× speed",
                    "skips silence".takeIf { preferences.phone.skipSilence },
                    "skips non-music".takeIf { preferences.skipNonMusic },
                ).joinToString(" · ").replaceFirstChar { it.uppercase() },
            ) { open(SettingsPage.PLAYBACK) }
        }
        item {
            SettingsTile(Icons.Default.PhoneAndroid, "On this phone", "Screen, downloads, tabs and haptics") {
                open(SettingsPage.PHONE)
            }
        }

        item { SectionLabel("Listening") }
        item {
            SettingsTile(
                Icons.Default.Lyrics,
                "Lyrics",
                preferences.lyricsProvider?.let { "Opens on ${it.displayName}, when it has the song" }
                    ?: "The best answer from eight sources",
            ) { open(SettingsPage.LYRICS) }
        }
        item {
            val scrobbling = settings.scrobbling
            val connected = listOf(scrobbling.lastFm, scrobbling.listenBrainz)
                .filter { it.status == ScrobbleConnectionStatus.CONNECTED }
                .mapNotNull { it.username }
            SettingsTile(
                Icons.Default.History,
                "Scrobbling",
                when {
                    scrobbling.lastFm.status == ScrobbleConnectionStatus.ERROR -> "Last.fm needs connecting again"
                    connected.isNotEmpty() -> "Connected: ${connected.joinToString()}"
                    else -> "Connect Last.fm or ListenBrainz"
                },
                active = connected.isNotEmpty(),
            ) { open(SettingsPage.SCROBBLING) }
        }
        item {
            SettingsTile(
                Icons.Default.Devices,
                "Noctorium Connect",
                when {
                    !preferences.connect.enabled -> "Off: your other devices cannot see this phone"
                    !connect.available -> "Sign in to your Noctorium account to use it"
                    connect.devices.isEmpty() -> "Appears as \"${connect.thisDevice}\""
                    connect.devices.size == 1 -> "Appears as \"${connect.thisDevice}\" · 1 other device"
                    else -> "Appears as \"${connect.thisDevice}\" · ${connect.devices.size} other devices"
                },
                active = connect.available && preferences.connect.enabled,
            ) { open(SettingsPage.CONNECT) }
        }

        item { SectionLabel("Noctorium") }
        item {
            SettingsTile(
                Icons.Default.Save,
                "Saving music",
                preferences.exportFolder.ifBlank { state.exportFolder()?.toString() ?: "Your Music folder" },
            ) { open(SettingsPage.SAVING) }
        }
        item {
            val available = updates.available
            SettingsTile(
                Icons.Default.SystemUpdateAlt,
                "Updates",
                when {
                    available != null -> "Noctorium ${available.version} is available"
                    updates.currentVersion.isNotBlank() -> "You have ${updates.currentVersion}"
                    else -> "Check for a newer Noctorium"
                },
                active = available != null,
            ) { open(SettingsPage.UPDATES) }
        }
        item {
            SettingsTile(Icons.Default.MonitorHeart, "Diagnostics", "Check what this phone can reach and play") {
                open(SettingsPage.DIAGNOSTICS)
            }
        }
    }
}

/** One page of Settings, with the way back at the top. */
@Composable
private fun SettingsPageScreen(page: SettingsPage, state: AppState, signIn: (ProviderType) -> Unit, back: () -> Unit) {
    val settings by state.settings.collectAsState()
    val likes by state.likes.collectAsState()

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = chromePadding(28.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Settings") }
                Spacer(Modifier.width(2.dp))
                Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        settingsMessage(settings, state)

        when (page) {
            SettingsPage.ACCOUNT -> item { NoctoriumAccountCard(state) }
            SettingsPage.YOUTUBE -> item { ServiceCard(ProviderType.YOUTUBE_MUSIC, settings, likes, state, signIn) }
            SettingsPage.SOUNDCLOUD -> item { ServiceCard(ProviderType.SOUNDCLOUD, settings, likes, state, signIn) }
            SettingsPage.SPOTIFY -> item { SpotifyCard(settings, state) }
            SettingsPage.CUSTOMIZATION -> customizationCards(settings, state)
            SettingsPage.PLAYBACK -> item { PlaybackOptionsCard(settings, state) }
            SettingsPage.PHONE -> item { PhoneOptionsCard(settings, state) }
            SettingsPage.LYRICS -> item { LyricsCard(settings, state) }
            SettingsPage.SCROBBLING -> {
                item { LastFmCard(settings, state) }
                item { ListenBrainzCard(settings, state) }
            }
            SettingsPage.CONNECT -> item { ConnectCard(settings, state) }
            SettingsPage.SAVING -> item { SavingCard(settings, state) }
            SettingsPage.UPDATES -> item { UpdateCard(settings, state) }
            SettingsPage.DIAGNOSTICS -> item { DiagnosticsCard(settings, state) }
        }
    }
}

/** A note from the last thing done, shown wherever Settings is open until it is dismissed. */
private fun LazyListScope.settingsMessage(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val message = settings.message ?: return
    item {
        SettingsCardShell {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), fontSize = 12.sp)
                TextButton(state::clearSettingsMessage) { Text("OK") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
    )
}

/** A line of Settings: what it is, where it stands, and a way in. The desktop's tiles, at phone size. */
@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color? = null,
    active: Boolean = false,
    open: () -> Unit,
) {
    Surface(
        onClick = open,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, "Connected", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Where a service account stands, in the few words a tile has room for. */
private fun serviceSummary(
    provider: ProviderType,
    settings: app.noctorium.settings.SettingsState,
    likes: app.noctorium.core.LikeState,
): String {
    val isSoundCloud = provider == ProviderType.SOUNDCLOUD
    val source = if (isSoundCloud) settings.preferences.soundCloudCookies else settings.preferences.youtubeCookies
    val connected = if (isSoundCloud) likes.soundCloudReady else likes.youTubeReady
    val channel = settings.preferences.youtubeChannelName.takeIf { !isSoundCloud && it.isNotBlank() }
    return when {
        connected && channel != null -> "Signed in as $channel"
        connected -> "Signed in"
        !isSoundCloud && settings.youtubeAccount.status == app.noctorium.settings.AccountConnectionStatus.ERROR -> "Signed out"
        source.isConfigured -> "Session saved, not checked yet"
        else -> "Not connected on this phone"
    }
}

@Composable
private fun NoctoriumAccountCard(state: AppState) {
    val account by state.account.collectAsState()
    SettingsCardShell {
        CardHeading(Icons.Default.Insights, "Noctorium account")
        Spacer(Modifier.height(6.dp))
        if (account.signedIn) {
            Text(account.user?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "${account.stats.streams} streamed · ${account.stats.uniqueTracks} different",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(state::signOutOfNoctorium) { Text("Sign out") }
        } else {
            NoctoriumAccountForm(state, busy = account.busy)
        }
        account.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/** The card every settings section sits in. Shared with the customization cards next door. */
@Composable
internal fun SettingsCardShell(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

internal typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
internal fun CardHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(9.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun NoctoriumAccountForm(state: AppState, busy: Boolean) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        "Signing in counts what you listen to, on this phone and on the desktop together.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        email,
        { email = it.trim() },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        password,
        { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            { state.logInToNoctorium(email, password) },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        ) { Text("Sign in") }
        OutlinedButton(
            { state.signUpToNoctorium(email, password, email.substringBefore('@')) },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        ) { Text("Create account") }
    }
}

/**
 * A service account, and the one thing a phone can honestly say about it.
 *
 * Signing in needs a WebView flow that does not exist yet, so this reports what is stored and points at
 * the desktop rather than offering a button that cannot work. Saying "not yet" is better than a control
 * that does nothing.
 */
@Composable
private fun ServiceCard(
    provider: ProviderType,
    settings: app.noctorium.settings.SettingsState,
    likes: app.noctorium.core.LikeState,
    state: AppState,
    signIn: (ProviderType) -> Unit,
) {
    val isSoundCloud = provider == ProviderType.SOUNDCLOUD
    val source = if (isSoundCloud) settings.preferences.soundCloudCookies else settings.preferences.youtubeCookies
    val connected = if (isSoundCloud) likes.soundCloudReady else likes.youTubeReady

    SettingsCardShell {
        CardHeading(if (isSoundCloud) Icons.Default.Cloud else Icons.Default.PlayCircle, provider.displayName)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                connected -> "Signed in. Your playlists and likes are in the library."
                // Said once, below, in red, with what to do; this line must not contradict it.
                !isSoundCloud && settings.youtubeAccount.status == app.noctorium.settings.AccountConnectionStatus.ERROR -> "Signed out."
                source.isConfigured -> "A session is saved. It has not been checked on this device."
                else -> "Not connected on this phone."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        if (isSoundCloud) {
            Spacer(Modifier.height(10.dp))
            SoundCloudProfileField(settings.preferences.soundCloudUsername, state)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ signIn(provider) }) {
                Text(if (source.isConfigured) "Sign in again" else "Sign in")
            }
            if (source.isConfigured) {
                OutlinedButton({ state.disconnectAccount(provider) }) { Text("Disconnect") }
            }
        }
        if (!isSoundCloud) PhoneYouTubeExtras(settings, likes, state)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your password goes to the service's own page inside Noctorium, never to a form of ours, " +
                "and only the session is kept.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SoundCloudProfileField(saved: String, state: AppState) {
    var name by remember(saved) { mutableStateOf(saved) }
    OutlinedTextField(
        name,
        { name = it.take(80) },
        label = { Text("Your SoundCloud profile name") },
        placeholder = { Text("your-name") },
        singleLine = true,
        supportingText = { Text("SoundCloud finds your own playlists by profile name.") },
        trailingIcon = {
            if (name != saved) {
                TextButton({ state.setSoundCloudUsername(name) }) { Text("Save") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    // Nobody knows their own profile name -- it is not the display name and not the email -- so the box
    // above is an unanswerable question until something answers it. The session can: SoundCloud's own
    // API names the account it belongs to. The desktop has had this button all along.
    if (saved.isBlank()) {
        TextButton({ state.detectSoundCloudProfile() }) { Text("Find it from my account") }
    }
}

/**
 * Spotify, read as a library through Noctorium's own Spotify app: one button, and a browser.
 *
 * A Spotify app of the listener's own is still offered, folded away, for somebody who would rather sign in
 * through that. The desktop and the phone use the same redirect address, so one registration serves both.
 */
@Composable
private fun SpotifyCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val spotify = settings.spotify
    var clientId by remember(settings.preferences.spotifyClientId) {
        mutableStateOf(settings.preferences.spotifyClientId)
    }
    var ownAppOpen by remember { mutableStateOf(settings.preferences.spotifyClientId.isNotBlank()) }

    SettingsCardShell {
        CardHeading(Icons.Default.LibraryMusic, "Spotify library", tint = Color(0xFF1DB954))
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                spotify.connected && spotify.accountName.isNotBlank() -> "Reading ${spotify.accountName}'s library."
                spotify.connected -> "Connected. Your playlists are in the library."
                else -> "Your playlists and liked songs, read into Noctorium. Playback never comes from Spotify, " +
                    "and nothing is ever written to it."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                state::connectSpotify,
                enabled = !spotify.connecting && clientId == settings.preferences.spotifyClientId,
            ) { Text(if (spotify.connected) "Reconnect" else "Connect Spotify") }
            if (spotify.connected) {
                OutlinedButton(state::disconnectSpotify) { Text("Disconnect") }
            }
        }
        spotify.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }

    SettingsCardShell {
        Row(
            Modifier.fillMaxWidth().clickable { ownAppOpen = !ownAppOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Use your own Spotify app", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (spotify.ownApp) "In use" else "Optional",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Icon(if (ownAppOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (ownAppOpen) "Fold away" else "Show")
        }
        if (ownAppOpen) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Create an app in Spotify's dashboard, add this address to its Redirect URIs, then paste its " +
                    "Client ID below and connect again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.spotifyRedirectUri(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(state::copySpotifyRedirectUri, Modifier.size(30.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy the redirect address", Modifier.size(15.dp))
                }
            }
            OutlinedButton(state::openSpotifyDashboard) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Spotify dashboard")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                clientId,
                { clientId = it.trim().take(64) },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    { state.setSpotifyClientId(clientId) },
                    enabled = clientId.isNotBlank() && clientId != settings.preferences.spotifyClientId,
                ) { Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Use this app") }
                if (spotify.ownApp) {
                    OutlinedButton({ clientId = ""; state.setSpotifyClientId("") }) { Text("Use Noctorium's") }
                }
            }
        }
    }
}

@Composable
private fun SavingCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    var folder by remember(settings.preferences.exportFolder) { mutableStateOf(settings.preferences.exportFolder) }
    SettingsCardShell {
        CardHeading(Icons.Default.Save, "Saving music")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            folder,
            { folder = it },
            label = { Text("Folder") },
            placeholder = { Text(state.exportFolder()?.toString() ?: "Your Music folder") },
            singleLine = true,
            supportingText = {
                Text(
                    if (state.canSaveAsMp3()) {
                        "Saved as MP3."
                    } else {
                        "Saved as it comes — m4a or opus, which this phone plays anyway."
                    },
                )
            },
            trailingIcon = {
                if (folder != settings.preferences.exportFolder) {
                    TextButton({ state.setExportFolder(folder) }) { Text("Save") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DiagnosticsCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    SettingsCardShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHeading(Icons.Default.History, "Diagnostics")
            Spacer(Modifier.weight(1f))
            TextButton(state::runDiagnostics, enabled = !settings.diagnosticsRunning) {
                Text(if (settings.diagnosticsRunning) "Checking…" else "Check")
            }
        }
        settings.diagnostics.forEach { result ->
            Spacer(Modifier.height(6.dp))
            Text(result.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(result.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
