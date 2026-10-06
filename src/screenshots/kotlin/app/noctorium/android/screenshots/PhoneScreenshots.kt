package app.noctorium.android.screenshots

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.noctorium.android.ui.ColourCard
import app.noctorium.android.ui.HomeLookCard
import app.noctorium.android.ui.LibraryScreen
import app.noctorium.android.ui.LinkScreen
import app.noctorium.android.ui.LyricsLookCard
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.PhoneOptionsCard
import app.noctorium.android.ui.PlaybackOptionsCard
import app.noctorium.android.ui.PlayerButtonsCard
import app.noctorium.android.ui.QueueScreen
import app.noctorium.android.ui.SearchScreen
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.android.ui.SoundCard
import app.noctorium.android.ui.SpotifyCards
import app.noctorium.android.ui.TabsCard
import app.noctorium.android.ui.TextAndLayoutCard
import app.noctorium.android.ui.TrackRow
import app.noctorium.android.ui.VkCard
import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.core.AppState
import app.noctorium.core.SearchMode
import app.noctorium.domain.ProviderType
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.AutoplaySource
import app.noctorium.settings.DataSaver
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.settings.SpotifyPlayback
import app.noctorium.settings.VkConnectionState
import app.noctorium.spotify.SpotifyDevice
import app.noctorium.core.Destination
import app.noctorium.domain.Track
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.EqualizerPreset
import app.noctorium.settings.EqualizerSettings
import app.noctorium.settings.FontChoice
import app.noctorium.settings.HomePart
import app.noctorium.settings.LyricsAlignment
import app.noctorium.settings.LyricsLook
import app.noctorium.settings.LyricsSize
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemePreset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A phone of ordinary size: 411 by 891 points, drawn three pixels to the point. */
internal const val PHONE = "w411dp-h891dp-xxhdpi"

/** The narrowest phone still sold, or an ordinary one with its display size turned up. */
internal const val NARROW_PHONE = "w320dp-h1100dp-xxhdpi"

/** Tall enough for the longest card to be seen whole. */
internal const val TALL_PHONE = "w411dp-h1100dp-xxhdpi"

/** For the Playback card, which autoplay's choices have made the longest of all. */
internal const val VERY_TALL_PHONE = "w411dp-h1400dp-xxhdpi"

/** For the Player bar card with the Taskbar bar chosen, which offers the clock beside everything else. */
internal const val TALLEST_PHONE = "w411dp-h1560dp-xxhdpi"

/** A curve somebody made by hand: a lot of bass, a dip in the middle, some air at the top. */
private val HAND_MADE = listOf(7f, 5f, 2f, -1f, -4f, -3f, 0f, 3f, 5.5f, 2f)

/**
 * The new choices in Settings and the screens they change, drawn by Robolectric in the app's own theme and
 * saved as PNGs to be looked at. Opt-in; see `screenshots` in build.gradle.kts for how to run them.
 *
 * The application is a plain one, not Noctorium's: Noctorium's builds a player and starts reading the
 * services the moment it is created, and every screen here is handed a still life instead (see StillLife).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class PhoneScreenshots {

    private val compose = createComposeRule()

    /** The Compose rule's empty activity, made known to Robolectric before the rule tries to start it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    private val folder = screenshotFolder

    // --- Sound ---

    @Test
    fun soundRock() = shoot(
        "sound-rock",
        NoctoriumPreferences(equalizer = EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK)),
    ) { _, state -> SettingsPageScreen(SettingsPage.SOUND, state, signIn = {}, back = {}) }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun soundCustom() = shoot("sound-custom", handMadeCurve()) { settings, state -> Card { SoundCard(settings, state) } }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun soundCustomNarrow() = shoot("sound-custom-narrow", handMadeCurve()) { settings, state -> Card { SoundCard(settings, state) } }

    @Test
    fun soundUnavailable() = shoot("sound-unavailable", equalizerAvailable = false) { settings, state ->
        Card { SoundCard(settings, state) }
    }

    @Test
    fun settingsHome() = shoot(
        "settings-home",
        NoctoriumPreferences(equalizer = EqualizerSettings(enabled = true, preset = EqualizerPreset.ELECTRONIC)),
    ) { _, state -> SettingsScreen(state) {} }

    // --- Your own accent ---

    @Test
    fun accentPickerDark() = shoot(
        "accent-picker-dark",
        NoctoriumPreferences(accent = AccentPreset.CUSTOM, customAccent = 0xFF3FB8AF),
    ) { settings, state -> Card { ColourCard(settings, state) } }

    @Test
    fun accentPickerLight() = shoot(
        "accent-picker-light",
        NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY, accent = AccentPreset.CUSTOM, customAccent = 0xFFE0592A),
    ) { settings, state -> Card { ColourCard(settings, state) } }

    /** A pure colour sits in the square's top right corner, where its ring is most easily cut off. */
    @Test
    @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun accentPickerPureColour() = shoot(
        "accent-picker-pure-colour",
        NoctoriumPreferences(accent = AccentPreset.CUSTOM, customAccent = 0xFFFF00FF),
    ) { settings, state -> Card { ColourCard(settings, state) } }

    // --- Typeface ---

    @Test
    fun typefaceDefault() = shoot("typeface-default") { settings, state -> Card { TextAndLayoutCard(settings, state) } }

    @Test
    fun typefaceSerif() = shoot("typeface-serif", NoctoriumPreferences(font = FontChoice.SERIF)) { settings, state ->
        Card { TextAndLayoutCard(settings, state) }
    }

    @Test
    fun typefaceMono() = shoot("typeface-mono", NoctoriumPreferences(font = FontChoice.MONO)) { settings, state ->
        Card { TextAndLayoutCard(settings, state) }
    }

    // --- How lyrics look ---

    @Test
    fun lyricsLookDefault() = shoot("lyrics-look-default") { settings, state -> Card { LyricsLookCard(settings, state) } }

    @Test
    fun lyricsLookLeftLargeUndimmed() = shoot(
        "lyrics-look-left-large-undimmed",
        NoctoriumPreferences(lyrics = LyricsLook(size = LyricsSize.LARGE, alignment = LyricsAlignment.START, dimOtherLines = false)),
    ) { settings, state -> Card { LyricsLookCard(settings, state) } }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun lyricsLookHugeNarrow() = shoot(
        "lyrics-look-huge-narrow",
        NoctoriumPreferences(lyrics = LyricsLook(size = LyricsSize.HUGE)),
    ) { settings, state -> Card { LyricsLookCard(settings, state) } }

    // --- Player buttons, Home and the tabs ---

    @Test
    fun playerButtons() = shoot(
        "player-buttons",
        NoctoriumPreferences(phone = PhonePreferences(hiddenPlayerButtons = setOf(PlayerButton.SHUFFLE, PlayerButton.LYRICS, PlayerButton.VOLUME))),
    ) { settings, state -> Card { PlayerButtonsCard(settings, state) } }

    @Test
    fun homeCard() = shoot(
        "home-card",
        NoctoriumPreferences(hiddenHomeParts = setOf(HomePart.GREETING, HomePart.PINNED, HomePart.SOUNDCLOUD)),
    ) { settings, state -> Card { HomeLookCard(settings, state) } }

    @Test
    fun tabsCard() = shoot(
        "tabs-card",
        NoctoriumPreferences(phone = PhonePreferences(hiddenDestinations = setOf(Destination.SEARCH, Destination.DOWNLOADS))),
    ) { settings, state -> Card { TabsCard(settings, state) } }

    // --- The screens they change ---

    @Test
    fun nowPlayingDefault() = shoot("now-playing-default", playing = Still.nowPlaying) { _, state ->
        NowPlayingScreen(state) {}
    }

    /** Shuffle, lyrics, volume and the sleep timer put away -- and the timer back, because one is running. */
    @Test
    fun nowPlayingHiddenButtons() = shoot(
        "now-playing-hidden-buttons",
        NoctoriumPreferences(
            phone = PhonePreferences(
                hiddenPlayerButtons = setOf(PlayerButton.SHUFFLE, PlayerButton.LYRICS, PlayerButton.VOLUME, PlayerButton.SLEEP_TIMER),
            ),
        ),
        playing = Still.nowPlaying,
        prepare = { it.startSleepTimer(30) },
    ) { _, state -> NowPlayingScreen(state) {} }

    @Test
    fun homeDefault() = shoot("home-default", playing = Still.nowPlaying) { _, state -> NoctoriumPhone(state) }

    /** The greeting, the pins and SoundCloud's rows put away, two tabs hidden, and a Slim bar without shuffle. */
    @Test
    fun homeHiddenParts() = shoot(
        "home-hidden-parts",
        NoctoriumPreferences(
            hiddenHomeParts = setOf(HomePart.GREETING, HomePart.PINNED, HomePart.SOUNDCLOUD),
            phone = PhonePreferences(
                hiddenDestinations = setOf(Destination.SEARCH, Destination.DOWNLOADS),
                hiddenPlayerButtons = setOf(PlayerButton.SHUFFLE),
                playerBarStyle = PhonePlayerBarStyle.SLIM,
            ),
        ),
        playing = Still.nowPlaying,
    ) { _, state -> NoctoriumPhone(state) }

    @Test
    fun homeAllHidden() = shoot(
        "home-all-hidden",
        NoctoriumPreferences(hiddenHomeParts = HomePart.entries.toSet()),
    ) { _, state -> NoctoriumPhone(state) }

    // --- Bandcamp ---

    /** No name given yet: what Bandcamp wants and does not, and the genres Home starts with. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun bandcampSettingsEmpty() = shoot("bandcamp-settings-empty") { _, state ->
        SettingsPageScreen(SettingsPage.BANDCAMP, state, signIn = {}, back = {})
    }

    /** A name kept, three genres picked in an order of their own, and Bandcamp's rows put away on Home. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun bandcampSettings() = shoot(
        "bandcamp-settings",
        NoctoriumPreferences(
            bandcampUsername = "nightowl",
            bandcampGenres = listOf(BandcampGenre.AMBIENT, BandcampGenre.JAZZ, BandcampGenre.ELECTRONIC),
            hiddenHomeParts = setOf(HomePart.BANDCAMP),
        ),
    ) { _, state -> SettingsPageScreen(SettingsPage.BANDCAMP, state, signIn = {}, back = {}) }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun settingsHomeBandcamp() = shoot("settings-home-bandcamp", NoctoriumPreferences(bandcampUsername = "nightowl")) { _, state ->
        SettingsScreen(state) {}
    }

    /** Albums and an artist from a Bandcamp search, above its songs. */
    @Test
    fun searchBandcamp() = shoot("search-bandcamp", prepare = { it.searchAndWait("harbour") }) { _, state ->
        SearchScreen(state)
    }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun searchBandcampNarrow() = shoot("search-bandcamp-narrow", prepare = { it.searchAndWait("harbour") }) { _, state ->
        SearchScreen(state)
    }

    /** A Bandcamp album opened: no download at the top, and the way to its page instead. */
    @Test
    fun bandcampAlbum() = shoot("bandcamp-album", prepare = { it.openAndWait(Still.bandcampAlbum) }) { _, state ->
        LibraryScreen(state)
    }

    /** A Bandcamp song's menu: nothing to download or save, and its page in their place. */
    @Test
    fun bandcampTrackMenu() = shoot(
        "bandcamp-track-menu",
        prepare = { it.openAndWait(Still.bandcampAlbum) },
        then = {
            openFirstTrackMenu()
            // The album's own, on the page, and the song's, in the menu.
            compose.onAllNodesWithText("Open on Bandcamp").assertCountEquals(2)
            compose.onNodeWithText("Copy link").assertExists()
            compose.onNodeWithText("Download for offline").assertDoesNotExist()
            compose.onNodeWithText("Save a copy…").assertDoesNotExist()
            compose.onNodeWithText("Add to playlist…").assertDoesNotExist()
        },
    ) { _, state -> LibraryScreen(state) }

    /** The same menu on a YouTube Music song, which keeps its download, its copy and its playlists. */
    @Test
    fun youTubeTrackMenu() = shoot(
        "youtube-track-menu",
        then = {
            openFirstTrackMenu()
            compose.onNodeWithText("Download for offline").assertExists()
            compose.onNodeWithText("Save a copy…").assertExists()
            compose.onNodeWithText("Add to playlist…").assertExists()
            compose.onNodeWithText("Open on Bandcamp").assertDoesNotExist()
        },
    ) { _, state -> Column { TrackRow(Still.nowPlaying, state) {} } }

    // --- Spotify, VK, and the new ways of listening ---

    /** Every service's tile: Bandcamp with a name, VK signed in, Spotify not yet, and the Search tile. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun settingsHomeServices() = shoot(
        "settings-home-services",
        NoctoriumPreferences(bandcampUsername = "nightowl", vkAccountName = "Night Owl"),
        prepare = { it.waitUntil { settings.value.vk.connected } },
    ) { _, state -> SettingsScreen(state) {} }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun spotifySignedOut() = shoot("spotify-signed-out") { _, state ->
        SettingsPageScreen(SettingsPage.SPOTIFY, state, signIn = {}, back = {})
    }

    /** Signed in with Premium, songs on Spotify, and the places Spotify is open. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun spotifyPremium() = shoot("spotify-premium") { settings, state ->
        Card {
            SpotifyCards(
                settings.copy(
                    spotify = SpotifyConnectionState(
                        connected = true,
                        accountName = "Night Owl",
                        canPlay = true,
                        playsOnSpotify = true,
                        devices = listOf(
                            SpotifyDevice("d-1", "Pocket phone", "Smartphone", isActive = false),
                            SpotifyDevice("d-2", "Kitchen speaker", "Speaker", isActive = true, volumePercent = 40),
                            SpotifyDevice("d-3", "Study computer", "Computer", isActive = false),
                            SpotifyDevice("d-4", "Car stereo", "Automobile", isActive = false, isRestricted = true),
                        ),
                        device = "d-2",
                    ),
                ),
                state,
            )
        }
    }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun vkSignedOut() = shoot("vk-signed-out") { _, state ->
        SettingsPageScreen(SettingsPage.VK, state, signIn = {}, back = {})
    }

    /** The way round a sign-in page that will not finish, opened. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun vkPasteCookies() = shoot(
        "vk-paste-cookies",
        then = {
            compose.onNodeWithText("Paste the cookies instead").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Use these").assertExists()
        },
    ) { _, state -> SettingsPageScreen(SettingsPage.VK, state, signIn = {}, back = {}) }

    @Test
    fun vkSignedIn() = shoot("vk-signed-in") { settings, state ->
        Card {
            VkCard(
                settings.copy(
                    vk = VkConnectionState(
                        connected = true,
                        accountName = "Night Owl",
                        message = "Signed in to VK as Night Owl. VK may hold some songs back outside Russia.",
                    ),
                ),
                state,
                signIn = {},
            )
        }
    }

    /** Albums and an artist from a Spotify search: the artist round, and saying so once. */
    @Test
    fun searchSpotify() = shoot(
        "search-spotify",
        prepare = {
            it.setSearchMode(SearchMode.SPOTIFY)
            it.searchAndWait("lantern")
        },
    ) { _, state -> SearchScreen(state) }

    @Test
    fun nowPlayingOnSpotify() = shoot("now-playing-on-spotify", onSpotify(), playing = Still.spotifyPlaying, prepare = ::untilOnSpotify) { _, state ->
        NowPlayingScreen(state) {}
    }

    @Test
    fun homeOnSpotify() = shoot("home-on-spotify", onSpotify(), playing = Still.spotifyPlaying, prepare = ::untilOnSpotify) { _, state ->
        NoctoriumPhone(state)
    }

    @Test
    fun slimBarOnSpotify() = shoot(
        "slim-bar-on-spotify",
        onSpotify().copy(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.SLIM)),
        playing = Still.spotifyPlaying,
        prepare = ::untilOnSpotify,
    ) { _, state -> NoctoriumPhone(state) }

    /** The equaliser, switched off and saying why, while Spotify plays the song. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun soundOnSpotify() = shoot(
        "sound-on-spotify",
        onSpotify().copy(equalizer = EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK)),
        playing = Still.spotifyPlaying,
        prepare = ::untilOnSpotify,
    ) { settings, state -> Card { SoundCard(settings, state) } }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun playbackSettings() = shoot(
        "playback-settings",
        NoctoriumPreferences(playbackSpeed = 1.25f, sleepFadeSeconds = 30),
    ) { _, state -> SettingsPageScreen(SettingsPage.PLAYBACK, state, signIn = {}, back = {}) }

    @Test
    fun searchSettings() = shoot(
        "search-settings",
        NoctoriumPreferences(hybridSearch = setOf(ProviderType.YOUTUBE_MUSIC, ProviderType.SOUNDCLOUD, ProviderType.BANDCAMP)),
    ) { _, state -> SettingsPageScreen(SettingsPage.SEARCH, state, signIn = {}, back = {}) }

    /** Hybrid down to its last service, whose chip cannot be switched off. */
    @Test
    fun searchSettingsLastOne() = shoot(
        "search-settings-last-one",
        NoctoriumPreferences(hybridSearch = setOf(ProviderType.BANDCAMP)),
    ) { _, state -> SettingsPageScreen(SettingsPage.SEARCH, state, signIn = {}, back = {}) }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun dataSaver() = shoot(
        "data-saver",
        NoctoriumPreferences(phone = PhonePreferences(dataSaver = DataSaver.ON_MOBILE_DATA)),
    ) { settings, state -> Card { PhoneOptionsCard(settings, state) } }

    /** A speed other than normal, shown under the start of the seek bar. */
    @Test
    fun nowPlayingSpeed() = shoot("now-playing-speed", NoctoriumPreferences(playbackSpeed = 1.25f), playing = Still.nowPlaying) { _, state ->
        NowPlayingScreen(state) {}
    }

    @Test
    fun nowPlayingSpeedMenu() = shoot(
        "now-playing-speed-menu",
        NoctoriumPreferences(playbackSpeed = 1.25f),
        playing = Still.nowPlaying,
        then = {
            compose.onNodeWithText("1.25×").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            compose.onNodeWithText("Normal").assertExists()
            compose.onNodeWithText("0.5×").assertExists()
        },
    ) { _, state -> NowPlayingScreen(state) {} }

    /** A VK song's menu: its page in place of a download, and VK's own name for where it opens. */
    @Test
    fun vkTrackMenu() = shoot(
        "vk-track-menu",
        prepare = { it.openAndWait(Still.vkMyMusic) },
        then = {
            openFirstTrackMenu()
            // The playlist's own, on the page, and the song's, in the menu.
            compose.onAllNodesWithText("Open on VK").assertCountEquals(2)
            compose.onNodeWithText("Download for offline").assertDoesNotExist()
            compose.onNodeWithText("Save a copy…").assertDoesNotExist()
            compose.onNodeWithText("Add to playlist…").assertDoesNotExist()
        },
    ) { _, state -> LibraryScreen(state) }

    // --- The queue, and what autoplay lines up after it ---
    //
    // Each queue is on a song well before its end. Autoplay only looks for songs as the end nears, and a
    // queue there would have it ask SoundCloud or YouTube for real; these are lined up by hand instead.

    /** Five queued, and four of SoundCloud's related songs after them, quieter, to play, keep or leave out. */
    @Test
    @Config(qualifiers = TALL_PHONE)
    fun queueUpNext() = shoot(
        "queue-up-next",
        playing = Still.queue[1],
        kept = kept(Still.queue),
        prepare = { it.lineUp(Still.related, "Related on SoundCloud") },
    ) { _, state -> QueueScreen(state) }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun queueUpNextNarrow() = shoot(
        "queue-up-next-narrow",
        playing = Still.queue[1],
        kept = kept(Still.queue),
        prepare = { it.lineUp(Still.related, "Related on SoundCloud") },
    ) { _, state -> QueueScreen(state) }

    @Test
    fun queueAutoplayOff() = shoot(
        "queue-autoplay-off",
        NoctoriumPreferences(autoplay = false),
        playing = Still.queue[1],
        kept = kept(Still.queue),
    ) { _, state -> QueueScreen(state) }

    /** On, with nothing lined up yet, because the queue's end is still three songs away. */
    @Test
    fun queueWaiting() = shoot("queue-waiting", playing = Still.queue[1], kept = kept(Still.queue)) { _, state -> QueueScreen(state) }

    /** Spotify songs played on Spotify, whose own autoplay takes over at the end. Nothing plays, so nothing is matched. */
    @Test
    fun queueOnSpotify() = shoot(
        "queue-spotify",
        kept = kept(Still.spotifyQueue),
        prepare = { it.lineUp(emptyList(), "Spotify chooses what comes next", continuesElsewhere = true) },
    ) { _, state -> QueueScreen(state) }

    @Test
    fun queueMenu() = shoot(
        "queue-menu",
        playing = Still.queue[1],
        kept = kept(Still.queue),
        then = {
            openQueueMenu()
            compose.onNodeWithText("Shuffle what's next").assertExists()
            compose.onNodeWithText("Clear what's next").assertExists()
            compose.onNodeWithText("Save queue as playlist…").assertExists()
            compose.onNodeWithText("Clear the queue").assertExists()
        },
    ) { _, state -> QueueScreen(state) }

    // "Save queue as playlist…" opens the same name dialog as a new playlist, and is not drawn here: a text
    // field with a label inside a dialog never lets Robolectric settle, focused or not, in touch mode or out.

    /** Launched with last time's queue kept and nothing playing: its song in the bar, paused, ready to go on. */
    @Test
    fun keptQueueHome() = shoot("kept-queue-home", kept = kept(Still.queue, positionMs = 67_000)) { _, state ->
        NoctoriumPhone(state)
    }

    @Test
    fun keptQueueNowPlaying() = shoot("kept-queue-now-playing", kept = kept(Still.queue, positionMs = 67_000)) { _, state ->
        NowPlayingScreen(state) {}
    }

    /** The last song with autoplay off: next has nowhere to go, so it is greyed, in the bar and the full screen. */
    @Test
    fun lastSongHome() = shoot(
        "last-song-home",
        NoctoriumPreferences(autoplay = false),
        playing = Still.queue.last(),
        kept = kept(Still.queue, index = Still.queue.lastIndex),
    ) { _, state -> NoctoriumPhone(state) }

    @Test
    fun lastSongControlsBar() = shoot(
        "last-song-controls-bar",
        NoctoriumPreferences(autoplay = false, phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.CONTROLS)),
        playing = Still.queue.last(),
        kept = kept(Still.queue, index = Still.queue.lastIndex),
    ) { _, state -> NoctoriumPhone(state) }

    @Test
    fun lastSongNowPlaying() = shoot(
        "last-song-now-playing",
        NoctoriumPreferences(autoplay = false),
        playing = Still.queue.last(),
        kept = kept(Still.queue, index = Still.queue.lastIndex),
    ) { _, state -> NowPlayingScreen(state) {} }

    /** Autoplay's choices on the Playback page: where its songs come from, what it leaves out, and the kept queue. */
    @Test
    @Config(qualifiers = VERY_TALL_PHONE)
    fun playbackAutoplay() = shoot("playback-autoplay") { settings, state -> Card { PlaybackOptionsCard(settings, state) } }

    @Test
    @Config(qualifiers = VERY_TALL_PHONE)
    fun playbackAutoplayOff() = shoot(
        "playback-autoplay-off",
        NoctoriumPreferences(autoplay = false, autoplayFrom = AutoplaySource.YOUTUBE_MUSIC, keepQueue = false),
    ) { settings, state -> Card { PlaybackOptionsCard(settings, state) } }

    @Test
    @Config(qualifiers = "w320dp-h2100dp-xxhdpi")
    fun playbackAutoplayNarrow() = shoot("playback-autoplay-narrow") { settings, state -> Card { PlaybackOptionsCard(settings, state) } }

    @Test
    fun linkScreen() = shoot("link-screen") { _, state -> LinkScreen(state) }

    @Test
    @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun linkScreen360() = shoot("link-screen-360") { _, state -> LinkScreen(state) }

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun linkScreenNarrow() = shoot("link-screen-narrow") { _, state -> LinkScreen(state) }

    /** [tracks] as last time's queue, on its [index]th song. */
    private fun kept(tracks: List<Track>, index: Int = 1, positionMs: Long = 0) = SavedQueue(tracks, index, positionMs)

    /** Lines up autoplay's songs after the queue as core does, for its last song, so nothing is asked for. */
    private fun AppState.lineUp(songs: List<Track>, from: String, continuesElsewhere: Boolean = false) {
        val last = queue.state.value.tracks.last()
        queue.setSuggestions("${last.queueKey}|${AutoplaySource.SAME_SERVICE}", songs, from, continuesElsewhere)
    }

    private fun openQueueMenu() {
        compose.onNodeWithContentDescription("Queue actions").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Spotify songs set to play on Spotify, which a Premium sign-in allows. */
    private fun onSpotify() = NoctoriumPreferences(spotifyCanPlay = true, spotifyPlayback = SpotifyPlayback.ON_SPOTIFY)

    /** Core says where Spotify songs play a moment after it starts; the picture waits for it. */
    private fun untilOnSpotify(state: AppState) = state.waitUntil { settings.value.spotify.playsOnSpotify }

    /** Taps the first row's ⋮ and lets the menu finish opening. */
    private fun openFirstTrackMenu() {
        compose.onAllNodesWithContentDescription("Track actions")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun handMadeCurve() = NoctoriumPreferences(
        equalizer = EqualizerSettings(enabled = true, preset = EqualizerPreset.CUSTOM, customGains = HAND_MADE, preampDb = -3f),
    )

    /**
     * Draws [content] in the app's theme over its own background, from a still-life state made of
     * [preferences], and saves it as [name].png. [prepare] runs on the state first, for anything that is
     * done rather than set -- a sleep timer started, say. [then] is done to the screen once it has settled,
     * such as opening a menu; the picture is then of the whole screen, so the menu's own window is in it.
     */
    private fun shoot(
        name: String,
        preferences: NoctoriumPreferences = NoctoriumPreferences(),
        playing: Track? = null,
        equalizerAvailable: Boolean = true,
        kept: SavedQueue? = null,
        prepare: (AppState) -> Unit = {},
        then: (() -> Unit)? = null,
        content: @Composable (SettingsState, AppState) -> Unit,
    ) = compose.shootPhone(folder, name, preferences, playing, equalizerAvailable, kept, prepare, then, content)
}

/** Where the pictures go: the folder -PscreenshotDir named, or the build's own. */
internal val screenshotFolder = File(System.getProperty("noctorium.screenshots.dir") ?: "build/outputs/screenshots")

/**
 * Draws [content] in the app's theme over its own background, from a still-life state made of [preferences],
 * and saves it in [folder] as [name].png. [prepare] runs on the state first, for anything that is done rather
 * than set -- a sleep timer started, say. [then] is done to the screen once it has settled, such as opening a
 * menu; the picture is then of the whole screen, so the menu's own window is in it.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.shootPhone(
    folder: File,
    name: String,
    preferences: NoctoriumPreferences = NoctoriumPreferences(),
    playing: Track? = null,
    equalizerAvailable: Boolean = true,
    kept: SavedQueue? = null,
    prepare: (AppState) -> Unit = {},
    then: (() -> Unit)? = null,
    content: @Composable (SettingsState, AppState) -> Unit,
) {
    val state = stillState(preferences, playing, kept = kept)
    try {
        prepare(state)
        // Time moves only when told to. The full-screen seek bar's wave runs for as long as it is on
        // screen, so a screen that waited to be still before its picture was taken would wait forever;
        // two seconds in is long enough for everything that eases into place to have arrived.
        mainClock.autoAdvance = false
        setContent {
            val settings by state.settings.collectAsState()
            NoctoriumTheme(settings.preferences, equalizerAvailable) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    content(settings, state)
                }
            }
        }
        mainClock.advanceTimeBy(2_000)
        if (then == null) {
            onRoot().captureRoboImage(File(folder, "$name.png").path)
        } else {
            then()
            mainClock.advanceTimeBy(1_000)
            captureScreenRoboImage(File(folder, "$name.png").path)
        }
    } finally {
        state.close()
    }
}

/**
 * Declares the activity the Compose test rule draws into, for this run only.
 *
 * The rule starts an empty ComponentActivity, which an app normally declares through a test library merged
 * into the debug manifest. That would put it in the APK for the sake of a screenshot, so it is told to
 * Robolectric's package manager here instead, and the APK stays as it is.
 */
internal class EmptyActivityDeclared : ExternalResource() {
    override fun before() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val activity = ComponentName(application, ComponentActivity::class.java)
        val packages = Shadows.shadowOf(application.packageManager)
        packages.addActivityIfNotPresent(activity)
        packages.addIntentFilterForActivity(
            activity,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }
}

/** A settings card on its own, where it would sit on its page. */
@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp), content = content)
}
