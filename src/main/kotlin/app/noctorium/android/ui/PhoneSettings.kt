package app.noctorium.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.domain.ProviderType
import app.noctorium.settings.HYBRID_SEARCH_SERVICES
import app.noctorium.settings.HomePart
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.ThemeSkin

/**
 * A theme's whole name, for the Theme and colour tile: a one-word name with its family in front -- "Catppuccin
 * Mocha", "Windows 98" -- since "Mocha" alone means nothing; a family's only theme by the family's name; and a
 * name of two words or more as it is, being whole already: "Noctorium 98" rather than "Windows Noctorium 98",
 * "Solarized Dark" rather than "Solarized Solarized Dark".
 */
internal fun themeName(theme: ThemePreset): String = when {
    theme == ThemePreset.CUSTOM -> "Your own colours"
    theme.displayName == theme.family || ' ' in theme.displayName -> theme.displayName
    else -> "${theme.family} ${theme.displayName}"
}

/**
 * Settings, as much of them as mean anything on a phone.
 *
 * Deliberately shorter than the desktop's. Discord Rich Presence needs its desktop app over a named pipe
 * and there is none here; the window chrome settings describe a window this has not got. What is left is
 * what actually differs from device to device: the accounts, where saved music goes, and what is on the
 * machine.
 *
 * A search box sits at the top: anything typed there lists the settings it finds, by name, by the other words
 * people use for them, or by what they do, and choosing one opens its page with it brought into view and lit.
 *
 * [backEnabled] is off while something covers this screen -- the now playing sheet, a sign-in -- so the
 * back button closes that first instead of turning a page nobody can see.
 */
@Composable
internal fun SettingsScreen(state: AppState, backEnabled: Boolean = true, signIn: (ProviderType) -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = backEnabled && page != null) {
        page = null
        highlight = null
    }
    // Back from a search empties it before it leaves Settings.
    BackHandler(enabled = backEnabled && page == null && query.isNotEmpty()) { query = "" }

    // Into a page from the right, and back out to it, as the desktop's Settings do.
    MotionContent(page, Modifier.fillMaxSize(), kind = MotionKind.PAGE, forward = { _, to -> to != null }) { shown ->
        if (shown == null) {
            SettingsHome(state, query, { query = it }) { to, setting ->
                page = to
                highlight = setting
            }
        } else {
            SettingsPageScreen(shown, state, signIn, highlight = highlight) {
                page = null
                highlight = null
            }
        }
    }
}

@Composable
private fun SettingsHome(
    state: AppState,
    query: String,
    search: (String) -> Unit,
    open: (SettingsPage, String?) -> Unit,
) {
    val settings by state.settings.collectAsState()
    val likes by state.likes.collectAsState()
    val account by state.account.collectAsState()
    val connect by state.connect.collectAsState()
    val updates by state.updates.collectAsState()
    val preferences = settings.preferences
    val found = remember(query) { searchPhoneSettings(query) }
    val tabSetting = LocalTabSetting.current

    // The status-bar inset the other screens get from ScreenScaffold. Without it the title sits under
    // the clock, which on this phone is exactly where the clock is.
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = chromePadding(28.dp),
    ) {
        item { ScreenTitle("Settings", "Accounts, services and how Noctorium behaves", close = { state.navigate(Destination.HOME) }) }
        item(key = "search") { SettingsSearchField(query, search) }
        if (query.isNotBlank()) {
            if (found.isEmpty()) item(key = "nothing") { NoSettingsFound(query) }
            found.forEach { (setting, match) ->
                item(key = "found:${setting.id}") {
                    SettingsTile(setting.page.glyph.image, setting.title, resultDetail(setting, match)) {
                        // A setting on a tab is opened there, and lit as one on a page is.
                        val tab = setting.tab
                        if (tab == null) {
                            open(setting.page, setting.id)
                        } else {
                            tabSetting.id = setting.id
                            state.navigate(tab)
                        }
                    }
                }
            }
            return@LazyColumn
        }
        settingsMessage(settings, state)

        item { SectionLabel(SettingsSection.YOU.title) }
        item {
            SettingsTile(
                SettingsPage.ACCOUNT.glyph.image,
                account.user?.displayName ?: "Noctorium account",
                if (account.signedIn) {
                    "${account.stats.streams} streamed · ${account.stats.uniqueTracks} different"
                } else {
                    "Sign in to count what you listen to"
                },
                active = account.signedIn,
            ) { open(SettingsPage.ACCOUNT, null) }
        }
        item {
            SettingsTile(SettingsPage.PROFILE.glyph.image, preferences.profileName, "Your local profile and history") {
                open(SettingsPage.PROFILE, null)
            }
        }

        item { SectionLabel(SettingsSection.LOOK.title) }
        item {
            SettingsTile(SettingsPage.THEME.glyph.image, "Theme and colour", "${themeName(preferences.theme)} · ${preferences.accent.displayName} accent") {
                open(SettingsPage.THEME, null)
            }
        }
        item {
            SettingsTile(
                SettingsPage.TEXT_AND_LAYOUT.glyph.image,
                "Text and layout",
                "${preferences.textSize.displayName} text · ${preferences.font.displayName} typeface",
            ) { open(SettingsPage.TEXT_AND_LAYOUT, null) }
        }
        item {
            val hidden = preferences.hiddenHomeParts.size
            SettingsTile(
                SettingsPage.HOME_LOOK.glyph.image,
                "Home",
                if (hidden == 0) "Everything Home has" else "$hidden ${if (hidden == 1) "part" else "parts"} put away",
            ) { open(SettingsPage.HOME_LOOK, null) }
        }
        item {
            SettingsTile(
                SettingsPage.PLAYER_BAR.glyph.image,
                "Player bar",
                "${preferences.phone.playerBarStyle.displayName} bar · ${preferences.progressBarStyle.displayName} seek bar",
            ) { open(SettingsPage.PLAYER_BAR, null) }
        }
        item {
            SettingsTile(
                SettingsPage.NOW_PLAYING.glyph.image,
                "Now playing",
                "${preferences.phone.nowPlayingLayout.displayName} layout · ${preferences.visualiser.displayName} visualiser",
            ) { open(SettingsPage.NOW_PLAYING, null) }
        }
        item {
            SettingsTile(SettingsPage.ICONS.glyph.image, "Icons", "${shownIconSet(preferences.iconSet).displayName} icons") {
                open(SettingsPage.ICONS, null)
            }
        }
        item {
            val tabs = visibleTabs(preferences.phone.hiddenDestinations).size
            SettingsTile(
                SettingsPage.TABS.glyph.image,
                "Tabs",
                "$tabs tabs" + if (preferences.phone.navigationLabels) ", named" else ", icons only",
            ) { open(SettingsPage.TABS, null) }
        }

        item { SectionLabel(SettingsSection.LISTENING.title) }
        item {
            SettingsTile(
                SettingsPage.PLAYBACK.glyph.image,
                "Playback",
                playbackSummary(preferences.copy(autoplay = false)).replaceFirstChar { it.uppercase() },
            ) { open(SettingsPage.PLAYBACK, null) }
        }
        item {
            SettingsTile(
                SettingsPage.AUTOPLAY.glyph.image,
                "Autoplay and the queue",
                if (preferences.autoplay) "Autoplay on · ${preferences.autoplayFrom.displayName}" else "Autoplay off",
            ) { open(SettingsPage.AUTOPLAY, null) }
        }
        item {
            SettingsTile(
                SettingsPage.SOUND.glyph.image,
                "Sound",
                soundSummary(preferences.equalizer, LocalEqualizerAvailable.current, preferences.volumeBoostPercent),
                active = preferences.equalizer.enabled && LocalEqualizerAvailable.current,
            ) { open(SettingsPage.SOUND, null) }
        }
        item {
            SettingsTile(
                SettingsPage.LYRICS.glyph.image,
                "Lyrics",
                preferences.lyricsProvider?.let { "Opens on ${it.displayName}, when it has the song" }
                    ?: "The best answer from eight sources",
            ) { open(SettingsPage.LYRICS, null) }
        }
        item {
            SettingsTile(
                SettingsPage.SCROBBLING.glyph.image,
                "Scrobbling",
                scrobblingSummary(settings.scrobbling),
                active = scrobblingAnywhere(settings.scrobbling),
            ) { open(SettingsPage.SCROBBLING, null) }
        }

        item { SectionLabel(SettingsSection.SERVICES.title) }
        item {
            SettingsTile(
                SettingsPage.YOUTUBE.glyph.image,
                "YouTube Music",
                serviceSummary(ProviderType.YOUTUBE_MUSIC, settings, likes),
                active = likes.youTubeReady,
            ) { open(SettingsPage.YOUTUBE, null) }
        }
        item {
            SettingsTile(
                SettingsPage.SOUNDCLOUD.glyph.image,
                "SoundCloud",
                serviceSummary(ProviderType.SOUNDCLOUD, settings, likes),
                active = likes.soundCloudReady,
            ) { open(SettingsPage.SOUNDCLOUD, null) }
        }
        item {
            SettingsTile(
                SettingsPage.SPOTIFY.glyph.image,
                "Spotify",
                spotifySummary(settings.spotify),
                tint = ProviderType.SPOTIFY.badgeColour(),
                active = settings.spotify.connected,
            ) { open(SettingsPage.SPOTIFY, null) }
        }
        item {
            val name = preferences.bandcampUsername
            SettingsTile(
                SettingsPage.BANDCAMP.glyph.image,
                "Bandcamp",
                bandcampSummary(name, settings.bandcamp),
                tint = ProviderType.BANDCAMP.badgeColour(),
                active = name.isNotBlank(),
            ) { open(SettingsPage.BANDCAMP, null) }
        }
        item {
            SettingsTile(
                SettingsPage.VK.glyph.image,
                "VK Music",
                vkSummary(settings.vk),
                tint = ProviderType.VK.badgeColour(),
                active = settings.vk.connected,
            ) { open(SettingsPage.VK, null) }
        }
        item {
            SettingsTile(
                SettingsPage.APPLE_MUSIC.glyph.image,
                "Apple Music",
                appleMusicSummary(settings.appleMusic),
                tint = ProviderType.APPLE_MUSIC.tint(),
                active = settings.appleMusic.connected,
            ) { open(SettingsPage.APPLE_MUSIC, null) }
        }
        item {
            SettingsTile(
                SettingsPage.DEEZER.glyph.image,
                "Deezer",
                deezerSummary(settings.deezer),
                tint = ProviderType.DEEZER.tint(),
                active = settings.deezer.profileId.isNotBlank(),
            ) { open(SettingsPage.DEEZER, null) }
        }
        item {
            SettingsTile(
                SettingsPage.TIDAL.glyph.image,
                "Tidal",
                tidalSummary(settings.tidal),
                tint = ProviderType.TIDAL.tint(),
                active = settings.tidal.connected,
            ) { open(SettingsPage.TIDAL, null) }
        }
        item {
            SettingsTile(SettingsPage.SEARCH.glyph.image, "Search", hybridSummary(preferences.hybridSearch)) {
                open(SettingsPage.SEARCH, null)
            }
        }

        item { SectionLabel(SettingsSection.PHONE.title) }
        item {
            // The one thing here that can stop the music, so it is said on the tile when it is not settled.
            val unrestricted = rememberBackgroundUnrestricted()
            SettingsTile(
                if (unrestricted) Glyph.PHONE_ANDROID.image else Glyph.BATTERY_ALERT.image,
                "On this phone",
                if (unrestricted) "Screen, downloads, data and haptics" else "The music may stop when the phone locks. Tap to fix it.",
                tint = if (unrestricted) null else MaterialTheme.colorScheme.error,
            ) {
                open(SettingsPage.PHONE, null)
            }
        }
        item {
            SettingsTile(
                SettingsPage.CONNECT.glyph.image,
                "Noctorium Connect",
                when {
                    !preferences.connect.enabled -> "Off: your other devices cannot see this phone"
                    !connect.available -> "Sign in to your Noctorium account to use it"
                    connect.devices.isEmpty() -> "Appears as \"${connect.thisDevice}\""
                    connect.devices.size == 1 -> "Appears as \"${connect.thisDevice}\" · 1 other device"
                    else -> "Appears as \"${connect.thisDevice}\" · ${connect.devices.size} other devices"
                },
                active = connect.available && preferences.connect.enabled,
            ) { open(SettingsPage.CONNECT, null) }
        }
        item {
            SettingsTile(
                SettingsPage.SAVING.glyph.image,
                "Saving music",
                preferences.exportFolder.ifBlank { state.exportFolder()?.toString() ?: "Your Music folder" },
            ) { open(SettingsPage.SAVING, null) }
        }

        item { SectionLabel(SettingsSection.ABOUT.title) }
        item {
            val available = updates.available
            SettingsTile(
                SettingsPage.UPDATES.glyph.image,
                "Updates",
                when {
                    available != null -> "Noctorium ${available.version} is available"
                    updates.currentVersion.isNotBlank() -> "You have ${updates.currentVersion}"
                    else -> "Check for a newer Noctorium"
                },
                active = available != null,
            ) { open(SettingsPage.UPDATES, null) }
        }
        item {
            SettingsTile(SettingsPage.DIAGNOSTICS.glyph.image, "Diagnostics", "Check what this phone can reach and play") {
                open(SettingsPage.DIAGNOSTICS, null)
            }
        }
    }
}

/** What the Sound tile says: the booster when it is on, then the equaliser, in the few words a tile has room for. */
internal fun soundSummary(equalizer: app.noctorium.settings.EqualizerSettings, available: Boolean, boostPercent: Int): String =
    listOfNotNull(
        "Boost ${app.noctorium.playback.VolumeBoost.percentLabel(boostPercent)}".takeIf { app.noctorium.playback.VolumeBoost.isOn(boostPercent) },
        equalizerSummary(equalizer, available),
    ).joinToString(" · ")

/**
 * One page of Settings, with the way back at the top: its cards, one after another. [highlight] is a setting a
 * search led here to: the page opens at its card, and the setting is brought into view and lit (see
 * [SettingAnchor]).
 */
@Composable
internal fun SettingsPageScreen(
    page: SettingsPage,
    state: AppState,
    signIn: (ProviderType) -> Unit,
    highlight: String? = null,
    back: () -> Unit,
) {
    val settings by state.settings.collectAsState()
    val likes by state.likes.collectAsState()
    val cards = pageCards(page, settings, likes, state, signIn)
    // The profile page has the history under its cards, as many lines as there are songs in it, so they are items
    // of this list rather than one card that would lay out every one of them at once.
    var historyFilter by rememberSaveable { mutableStateOf("") }
    val history = if (page == SettingsPage.PROFILE) rememberHistoryDays(state, historyFilter) else null
    val list = rememberLazyListState()
    val target = PHONE_SETTINGS.firstOrNull { it.id == highlight && it.page == page }
    LaunchedEffect(target) {
        val card = target?.card ?: return@LaunchedEffect
        val index = cards.indexOfFirst { it.first == card }.takeIf { it >= 0 } ?: return@LaunchedEffect
        // The heading, and the note from the last thing done when there is one, come before the cards.
        list.scrollToItem(index + 1 + if (settings.message != null) 1 else 0)
    }

    CompositionLocalProvider(LocalSettingHighlight provides target?.id) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            state = list,
            contentPadding = chromePadding(28.dp),
        ) {
            item {
                if (LocalSkin.current != ThemeSkin.STANDARD) {
                    // A page of Settings is a window of its own under the Windows skins, closed by its cross.
                    SkinTitleBar(page.title, Modifier.padding(bottom = 6.dp)) {
                        CaptionButton(Caption.CLOSE, "Back to Settings", back)
                    }
                    return@item
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(back) { Icon(Glyph.ARROW_BACK.image, "Back to Settings") }
                    Spacer(Modifier.width(2.dp))
                    Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            settingsMessage(settings, state)
            items(cards, key = { it.first }) { (_, card) -> card() }
            history?.let { historyLines(it, historyFilter, { typed -> historyFilter = typed }, state) }
        }
    }
}

/** The cards on [page], each by the name its settings know it by (see [PhoneSetting.card]). */
private fun pageCards(
    page: SettingsPage,
    settings: app.noctorium.settings.SettingsState,
    likes: app.noctorium.core.LikeState,
    state: AppState,
    signIn: (ProviderType) -> Unit,
): List<Pair<String, @Composable () -> Unit>> = when (page) {
    SettingsPage.ACCOUNT -> listOf("account" to { NoctoriumAccountCard(state) })
    // The history's songs follow these cards, a line each: see historyLines.
    SettingsPage.PROFILE -> listOf(
        "name" to { ProfileNameCard(settings, state) },
        "history" to { HistoryCard(settings, state) },
    )
    SettingsPage.THEME -> listOf(
        "theme" to { ThemeCard(settings, state) },
        "colour" to { ColourCard(settings, state) },
    )
    SettingsPage.TEXT_AND_LAYOUT -> listOf("text" to { TextAndLayoutCard(settings, state) })
    SettingsPage.HOME_LOOK -> listOf("home" to { HomeLookCard(settings, state) })
    SettingsPage.PLAYER_BAR -> listOf(
        "player-bar" to { PlayerBarCard(settings, state) },
        "player-buttons" to { PlayerButtonsCard(settings, state) },
    )
    SettingsPage.NOW_PLAYING -> listOf(
        "now-playing" to { NowPlayingLookCard(settings, state) },
        "playlist-layout" to { PlaylistLayoutCard(settings, state) },
    )
    SettingsPage.ICONS -> listOf("icons" to { IconSetCard(settings, state) })
    SettingsPage.TABS -> listOf("tabs" to { TabsCard(settings, state) })
    SettingsPage.PLAYBACK -> listOf("playback" to { PlaybackOptionsCard(settings, state) })
    SettingsPage.AUTOPLAY -> listOf("autoplay" to { AutoplayCard(settings, state) })
    SettingsPage.SOUND -> listOf(
        "booster" to { VolumeBoosterCard(settings, state) },
        "equaliser" to { SoundCard(settings, state) },
    )
    SettingsPage.LYRICS -> listOf(
        "lyrics-source" to { LyricsCard(settings, state) },
        "lyrics-look" to { LyricsLookCard(settings, state) },
    )
    SettingsPage.SCROBBLING -> listOf(
        "lastfm" to { LastFmCard(settings, state) },
        "librefm" to { LibreFmCard(settings, state) },
        "listenbrainz" to { ListenBrainzCard(settings, state) },
    )
    SettingsPage.YOUTUBE -> listOf("youtube" to { ServiceCard(ProviderType.YOUTUBE_MUSIC, settings, likes, state, signIn) })
    SettingsPage.SOUNDCLOUD -> listOf("soundcloud" to { ServiceCard(ProviderType.SOUNDCLOUD, settings, likes, state, signIn) })
    SettingsPage.SPOTIFY -> listOf("spotify" to { SpotifyCards(settings, state) })
    SettingsPage.BANDCAMP -> listOf(
        "bandcamp" to { BandcampCard(settings, state) },
        "bandcamp-genres" to { BandcampGenresCard(settings, state) },
    )
    SettingsPage.VK -> listOf("vk" to { VkCard(settings, state, signIn) })
    SettingsPage.APPLE_MUSIC -> listOf(
        "apple-music" to { AppleMusicCard(settings, state, signIn) },
        "apple-music-token" to { AppleMusicTokenCard(settings, state) },
    )
    SettingsPage.DEEZER -> listOf("deezer" to { DeezerCard(settings, state) })
    SettingsPage.TIDAL -> listOf(
        "tidal" to { TidalCard(settings, state) },
        "tidal-app" to { TidalAppCard(settings, state) },
    )
    SettingsPage.SEARCH -> listOf("hybrid" to { HybridSearchCard(settings, state) })
    SettingsPage.PHONE -> listOf(
        "keep-playing" to { KeepPlayingCard() },
        "phone" to { PhoneOptionsCard(settings, state) },
    )
    SettingsPage.CONNECT -> listOf("connect" to { ConnectCard(settings, state) })
    SettingsPage.SAVING -> listOf("saving" to { SavingCard(settings, state) })
    SettingsPage.UPDATES -> listOf("updates" to { UpdateCard(settings, state) })
    SettingsPage.DIAGNOSTICS -> listOf("diagnostics" to { DiagnosticsCard(settings, state) })
}

/** A note from the last thing done, shown wherever Settings is open until it is dismissed. */
private fun LazyListScope.settingsMessage(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val message = settings.message ?: return
    item {
        SettingsCardShell {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), fontSize = 12.sp)
                SkinnedTextButton(state::clearSettingsMessage) { Text("OK") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        SkinSectionLabel(text)
        return
    }
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
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        SkinTile(icon, title, subtitle, tint, active, open)
        return
    }
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
                Icon(Glyph.CHECK_CIRCLE.image, "Connected", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Glyph.CHEVRON_RIGHT.image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    SettingsCardShell(anchor = SettingIds.ACCOUNT) {
        CardHeading(Glyph.INSIGHTS.image, "Noctorium account")
        Spacer(Modifier.height(6.dp))
        if (account.signedIn) {
            Text(account.user?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "${account.stats.streams} streamed · ${account.stats.uniqueTracks} different",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            SkinnedOutlinedButton(state::signOutOfNoctorium) { Text("Sign out") }
        } else {
            NoctoriumAccountForm(state, busy = account.busy)
        }
        account.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/**
 * The card every settings section sits in. Shared with the customization cards next door. Under the Windows
 * skins it is a group box, with the card's heading set into its edge.
 */
@Composable
internal fun SettingsCardShell(
    /** The setting this whole card is, for a card that is one: the search lights all of it. See [SettingAnchor]. */
    anchor: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inside: @Composable ColumnScope.() -> Unit = if (anchor == null) content else ({ SettingAnchor(anchor, content = content) })
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        GroupBox(inside)
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = inside)
    }
}

internal typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
internal fun CardHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color? = null) {
    if (LocalInGroupBox.current) {
        GroupBoxHeading(icon, title, tint)
        return
    }
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
        SkinnedButton(
            { state.logInToNoctorium(email, password) },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        ) { Text("Sign in") }
        SkinnedOutlinedButton(
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
 *
 * YouTube Music's and SoundCloud's only: everything here that is not SoundCloud's is read as YouTube's.
 * Bandcamp, which needs a name rather than a session, has a card of its own.
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

    SettingsCardShell(anchor = if (isSoundCloud) SettingIds.SOUNDCLOUD_SIGN_IN else SettingIds.YOUTUBE_SIGN_IN) {
        CardHeading(if (isSoundCloud) Glyph.CLOUD.image else Glyph.PLAY_CIRCLE.image, provider.displayName)
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
            SkinnedButton({ signIn(provider) }) {
                Text(if (source.isConfigured) "Sign in again" else "Sign in")
            }
            if (source.isConfigured) {
                SkinnedOutlinedButton({ state.disconnectAccount(provider) }) { Text("Disconnect") }
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
private fun SoundCloudProfileField(saved: String, state: AppState) = SettingAnchor(SettingIds.SOUNDCLOUD_PROFILE) {
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
                SkinnedTextButton({ state.setSoundCloudUsername(name) }) { Text("Save") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    // Nobody knows their own profile name -- it is not the display name and not the email -- so the box
    // above is an unanswerable question until something answers it. The session can: SoundCloud's own
    // API names the account it belongs to. The desktop has had this button all along.
    if (saved.isBlank()) {
        SkinnedTextButton({ state.detectSoundCloudProfile() }) { Text("Find it from my account") }
    }
}

/**
 * Spotify, through Noctorium's own Spotify app: two sign-ins, one for any account and one for Premium.
 *
 * Any account brings its playlists, liked songs and search here, with hearts that save to Liked Songs and
 * two rows on Home, and its songs play matched to the same recordings on YouTube Music. The Premium sign-in
 * also lets Noctorium tell the account's own Spotify app what to play -- which is the only way Spotify's
 * audio is ever heard here, since nothing but Spotify decodes it.
 *
 * A Spotify app of the listener's own is still offered, folded away, for somebody who would rather sign in
 * through that. The desktop and the phone use the same redirect address, so one registration serves both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotifyCards(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val spotify = settings.spotify
    val green = ProviderType.SPOTIFY.badgeColour()
    var clientId by remember(settings.preferences.spotifyClientId) {
        mutableStateOf(settings.preferences.spotifyClientId)
    }
    var ownAppOpen by remember { mutableStateOf(settings.preferences.spotifyClientId.isNotBlank()) }
    // Not while a Client ID is typed and not yet in use: that sign-in would go through the app being left.
    val canConnect = !spotify.connecting && clientId == settings.preferences.spotifyClientId
    val who = spotify.accountName.takeIf(String::isNotBlank)?.let { " as $it" }.orEmpty()

    SettingsCardShell(anchor = SettingIds.SPOTIFY_SIGN_IN) {
        CardHeading(Glyph.LIBRARY_MUSIC.image, "Spotify", tint = green)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                spotify.connected && spotify.canPlay -> "Signed in with Premium$who."
                // Not "your playlists are here" while the line below says Spotify will not hand them over.
                spotify.connected && spotify.message != null -> "Signed in$who."
                spotify.connected -> "Signed in$who. Your playlists, liked songs and search are here."
                else -> "Any Spotify account brings its playlists, liked songs and search here, with hearts " +
                    "that save to Liked Songs and two rows on Home. Its songs play matched to the same " +
                    "recordings on YouTube Music."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (spotify.canPlay) {
                "Spotify songs can play in your own Spotify app, which has to be open somewhere: this phone, a " +
                    "computer or a speaker. Noctorium never decodes Spotify's audio; it only tells Spotify what to play."
            } else {
                "With Premium, Spotify songs can play in your own Spotify app instead: on this phone, a computer " +
                    "or a speaker, wherever the Spotify app is open. Noctorium never decodes Spotify's audio; it " +
                    "only tells Spotify what to play."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        // Wrapped rather than squeezed: three buttons do not fit a row at a large font size.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !spotify.connected -> {
                    SkinnedButton({ state.connectSpotify() }, enabled = canConnect) { Text("Connect Spotify") }
                    SkinnedOutlinedButton(state::connectSpotifyPremium, enabled = canConnect) { Text("Connect Spotify Premium") }
                }
                spotify.canPlay -> {
                    SkinnedOutlinedButton(state::connectSpotifyPremium, enabled = canConnect) { Text("Reconnect") }
                    SkinnedOutlinedButton(state::disconnectSpotify) { Text("Disconnect") }
                }
                else -> {
                    SkinnedButton(state::connectSpotifyPremium, enabled = canConnect) { Text("Connect Spotify Premium") }
                    SkinnedOutlinedButton({ state.connectSpotify() }, enabled = canConnect) { Text("Reconnect") }
                    SkinnedOutlinedButton(state::disconnectSpotify) { Text("Disconnect") }
                }
            }
        }
        spotify.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }

    SpotifyPlaybackCard(spotify, state)

    SettingsCardShell(anchor = SettingIds.SPOTIFY_OWN_APP) {
        Row(
            Modifier.fillMaxWidth().clickable { ownAppOpen = !ownAppOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Use your own Spotify app", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                // Said folded as well as open: it is the reason a Spotify app answers nothing at all.
                Text(
                    (if (spotify.ownApp) "In use." else "Optional.") +
                        " In development mode, the Spotify app's owner needs Premium.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Icon(if (ownAppOpen) Glyph.EXPAND_LESS.image else Glyph.EXPAND_MORE.image, if (ownAppOpen) "Fold away" else "Show")
        }
        if (ownAppOpen) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Create an app in Spotify's dashboard, add this address to its Redirect URIs, then paste its " +
                    "Client ID below and connect again. A new app starts in development mode, so its owner -- you -- " +
                    "needs Premium, or Spotify answers nothing.",
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
                    Icon(Glyph.CONTENT_COPY.image, "Copy the redirect address", Modifier.size(15.dp))
                }
            }
            SkinnedOutlinedButton(state::openSpotifyDashboard) {
                Icon(Glyph.OPEN_IN_NEW.image, null, Modifier.size(16.dp))
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
                SkinnedButton(
                    { state.setSpotifyClientId(clientId) },
                    enabled = clientId.isNotBlank() && clientId != settings.preferences.spotifyClientId,
                ) { Icon(Glyph.SAVE.image, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Use this app") }
                if (spotify.ownApp) {
                    SkinnedOutlinedButton({ clientId = ""; state.setSpotifyClientId("") }) { Text("Use Noctorium's") }
                }
            }
        }
    }
}

/**
 * Where Spotify songs play: in the account's own Spotify app, or matched on YouTube Music.
 *
 * The switch needs the Premium sign-in and says so when it is not there, rather than vanishing. With it,
 * the devices are where Spotify is open right now, as Spotify said when last asked; Spotify sends the song
 * to the one chosen, or to whichever it has active when that one is not open.
 */
@Composable
private fun SpotifyPlaybackCard(spotify: app.noctorium.settings.SpotifyConnectionState, state: AppState) {
    SettingsCardShell(anchor = SettingIds.SPOTIFY_PLAYBACK) {
        CardHeading(Glyph.SPEAKER.image, "Playing on Spotify", tint = ProviderType.SPOTIFY.badgeColour())
        Spacer(Modifier.height(6.dp))
        Toggle(
            "Play Spotify songs on Spotify",
            if (spotify.canPlay) {
                "In your Spotify app, which has to be open on this phone, a computer or a speaker. Off, they " +
                    "play matched on YouTube Music."
            } else {
                "Needs the Premium sign-in above. Until then, Spotify songs play matched on YouTube Music."
            },
            spotify.playsOnSpotify,
            enabled = spotify.canPlay && !spotify.connecting,
        ) { state.setSpotifyPlayback(it) }
        if (spotify.canPlay) SpotifyDevices(spotify, state)
    }
}

/** Where Spotify is open, to choose which one plays, as Spotify said when last asked. */
@Composable
private fun SpotifyDevices(spotify: app.noctorium.settings.SpotifyConnectionState, state: AppState) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Play on", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SkinnedTextButton(state::refreshSpotifyDevices) {
            Icon(Glyph.REFRESH.image, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh")
        }
    }
    SpotifyDeviceRow(
        Glyph.DEVICES.image,
        "Any active device",
        "Wherever Spotify is playing, or played last",
        selected = spotify.device.isBlank(),
        enabled = true,
    ) { state.chooseSpotifyDevice("") }
    spotify.devices.forEach { device ->
        SpotifyDeviceRow(
            spotifyDeviceIcon(device.type).image,
            device.name,
            spotifyDeviceDetail(device),
            selected = spotify.device == device.id,
            enabled = !device.isRestricted,
        ) { state.chooseSpotifyDevice(device.id) }
    }
    val note = when {
        spotify.devices.isEmpty() -> "Open Spotify on a phone, a computer or a speaker, then Refresh."
        spotify.device.isNotBlank() && spotify.devices.none { it.id == spotify.device } ->
            "The device chosen before is not open now, so Spotify plays wherever it is active."
        else -> null
    }
    note?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** One place Spotify can play, as a choice among the rest: what it is, what it is called, and whether it is picked. */
@Composable
private fun SpotifyDeviceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    choose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = choose)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // The row is what is tapped; the button only shows which one is chosen.
        SkinnedRadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

/** A picture for Spotify's word for a device: Computer, Smartphone, Speaker, TV and the rest. */
private fun spotifyDeviceIcon(type: String): Glyph = when (type.lowercase()) {
    "computer" -> Glyph.COMPUTER
    "smartphone" -> Glyph.PHONE_ANDROID
    "tablet" -> Glyph.TABLET
    "tv", "castvideo", "stb" -> Glyph.TV
    "automobile" -> Glyph.DIRECTIONS_CAR
    "speaker", "avr", "castaudio", "audiodongle" -> Glyph.SPEAKER
    else -> Glyph.DEVICES
}

/**
 * VK Music, through the session of a vk.ru sign-in.
 *
 * VK has no way in for other apps, so this goes in the way VK's own web player does, which VK's terms forbid;
 * the card says so, with what follows from it, before anybody signs in. Signing in is VK's own page inside
 * Noctorium. Pasting the two cookies from a browser is the way round a page that will not finish.
 */
@Composable
internal fun VkCard(settings: app.noctorium.settings.SettingsState, state: AppState, signIn: (ProviderType) -> Unit) {
    val vk = settings.vk
    val focus = LocalFocusManager.current
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    fun usePasted() {
        if (pasted.isBlank() || vk.checking) return
        focus.clearFocus()
        state.completeVkSignIn(pasted)
        // Gone from the screen and from here as soon as it is handed over: it is a session, not a note.
        pasted = ""
    }

    SettingsCardShell(anchor = SettingIds.VK_SIGN_IN) {
        CardHeading(Glyph.AUDIOTRACK.image, "VK Music", tint = ProviderType.VK.badgeColour())
        Spacer(Modifier.height(6.dp))
        if (vk.connected) {
            Text("${vkSummary(vk)}.", fontSize = 12.sp)
            Text(
                "Your music and playlists are in the library, and a heart adds a song to My music. VK songs play " +
                    "here and are not downloaded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            SkinnedOutlinedButton(state::disconnectVk, enabled = !vk.checking) { Text("Sign out") }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "VK offers no music to other apps, so Noctorium uses your vk.ru session the way VK's own web " +
                        "player does. That is against VK's terms: VK may ask you to confirm it is you, or freeze " +
                        "accounts it thinks are automated. Many songs do not play outside Russia, and VK songs " +
                        "cannot be downloaded.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            SkinnedButton({ signIn(ProviderType.VK) }, enabled = !vk.checking) { Text("Sign in on vk.ru") }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clickable { pasteOpen = !pasteOpen }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paste the cookies instead", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(if (pasteOpen) Glyph.EXPAND_LESS.image else Glyph.EXPAND_MORE.image, if (pasteOpen) "Fold away" else "Show")
            }
            if (pasteOpen) {
                Text(
                    "From a browser signed in on vk.ru: the p cookie, which is on login.vk.ru, and remixsid, which " +
                        "is on vk.ru. As p=…; remixsid=…, or however the browser copies them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    pasted,
                    { pasted = it.take(4_000) },
                    label = { Text("p and remixsid") },
                    // Hidden as a password is: whoever holds these is signed in to VK as you.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { usePasted() }),
                    // Two lines from two hosts are as good as one; core reads either.
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SkinnedButton({ usePasted() }, enabled = pasted.isNotBlank() && !vk.checking) { Text("Use these") }
            }
        }
        if (vk.checking) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking with VK…", fontSize = 12.sp)
            }
        }
        vk.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/**
 * Which services the Hybrid chip on Search asks, all at once.
 *
 * Each still answers only where it can, and the card says where that is. At least one always stays, so the
 * last one's chip cannot be switched off -- core would refuse it anyway, and a chip that does nothing when
 * tapped reads as a broken one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HybridSearchCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val included = settings.preferences.hybridSearch
    SettingsCardShell(anchor = SettingIds.HYBRID_SEARCH) {
        CardHeading(Glyph.SEARCH.image, "Hybrid search")
        Text(
            "The services the Hybrid chip on Search asks at once. Spotify answers only while its songs play on " +
                "Spotify, VK only while you are signed in to it, and Tidal once it is set up. Apple Music, Deezer " +
                "and Tidal are left out to begin with: their songs play matched on YouTube Music, which already answers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HYBRID_SEARCH_SERVICES.forEach { provider ->
                SkinnedFilterChip(
                    selected = provider in included,
                    enabled = canSwitchHybrid(provider, included),
                    onClick = { state.setHybridSearchService(provider, included = provider !in included) },
                    label = { Text(provider.displayName, fontSize = 11.sp) },
                )
            }
        }
        Text(
            "At least one always stays.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Bandcamp, which needs no sign-in: a fan's collection is shown to anybody, so the name at the end of their
 * address is all it takes to bring it into the library, with the wishlist beside it.
 *
 * The name is checked with Bandcamp when it is saved, which is the moment of checking after Save -- a
 * misspelt one would otherwise be an empty library with no reason given. Searching Bandcamp, its rows on
 * Home and playing from it want no name at all, and the card says so, so nobody thinks they must give one.
 */
@Composable
private fun BandcampCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val saved = settings.preferences.bandcampUsername
    val bandcamp = settings.bandcamp
    val focus = LocalFocusManager.current
    var name by remember(saved) { mutableStateOf(saved) }
    // Only a name other than the one kept, read the way core will read it, is worth asking Bandcamp about.
    val asked = bandcampName(name)
    val canSave = !bandcamp.checking && asked.isNotBlank() && !asked.equals(saved, ignoreCase = true)

    fun save() {
        if (!canSave) return
        // The keyboard goes, so Bandcamp's answer under the buttons is not left behind it.
        focus.clearFocus()
        state.setBandcampUsername(name)
    }

    SettingsCardShell(anchor = SettingIds.BANDCAMP_NAME) {
        CardHeading(Glyph.ALBUM.image, "Bandcamp", tint = ProviderType.BANDCAMP.badgeColour())
        Spacer(Modifier.height(6.dp))
        Text(
            "No sign-in is needed. Give your Bandcamp name, the end of bandcamp.com/<name>, and your " +
                "collection and wishlist appear in the library. Searching Bandcamp and its rows on Home " +
                "work without one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            name,
            { name = it.take(120) },
            label = { Text("Your Bandcamp name") },
            placeholder = { Text("your-name") },
            singleLine = true,
            supportingText = { Text("Or paste the whole address.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinnedButton({ save() }, enabled = canSave) { Text("Save") }
            if (saved.isNotBlank()) {
                SkinnedOutlinedButton(
                    { focus.clearFocus(); state.setBandcampUsername("") },
                    enabled = !bandcamp.checking,
                ) { Text("Remove") }
            }
        }
        bandcampStatus(saved, bandcamp)?.let { status ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bandcamp.checking) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(status, fontSize = 12.sp)
            }
        }
        bandcamp.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/**
 * The genres Home has a Bandcamp row for: Bandcamp's best-sellers in each one picked, in the order picked.
 *
 * Whether Bandcamp's rows are on Home at all is decided under Customization, so this says when they are put
 * away -- otherwise picking a genre here would seem to do nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandcampGenresCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    val chosen = settings.preferences.bandcampGenres
    SettingsCardShell(anchor = SettingIds.BANDCAMP_GENRES) {
        CardHeading(Glyph.HOME.image, "Genres on Home")
        Spacer(Modifier.height(6.dp))
        Text(
            "Each genre picked is a row on Home of what sells best on Bandcamp in it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        if (HomePart.BANDCAMP in settings.preferences.hiddenHomeParts) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Bandcamp's rows are put away just now. Bring them back under Customization, Home.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BandcampGenre.entries.forEach { genre ->
                SkinnedFilterChip(
                    selected = genre in chosen,
                    onClick = { state.setBandcampGenres(toggledGenre(chosen, genre)) },
                    label = { Text(genre.displayName, fontSize = 11.sp) },
                )
            }
        }
    }
}

@Composable
private fun SavingCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    var folder by remember(settings.preferences.exportFolder) { mutableStateOf(settings.preferences.exportFolder) }
    SettingsCardShell(anchor = SettingIds.EXPORT_FOLDER) {
        CardHeading(Glyph.SAVE.image, "Saving music")
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
                    SkinnedTextButton({ state.setExportFolder(folder) }) { Text("Save") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DiagnosticsCard(settings: app.noctorium.settings.SettingsState, state: AppState) {
    SettingsCardShell(anchor = SettingIds.DIAGNOSTICS) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHeading(Glyph.HISTORY.image, "Diagnostics")
            Spacer(Modifier.weight(1f))
            SkinnedTextButton(state::runDiagnostics, enabled = !settings.diagnosticsRunning) {
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
