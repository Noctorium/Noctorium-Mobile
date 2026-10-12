package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.QueueScreen
import app.noctorium.android.ui.SEARCH_TAG
import app.noctorium.android.ui.SettingIds
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.BadgePolicy
import app.noctorium.settings.IconSet
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.QueueColumn
import app.noctorium.settings.ServiceBadgeStyle
import app.noctorium.settings.SpotifyPlayback
import app.noctorium.settings.SurfaceStyle
import app.noctorium.settings.TextSize
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.VisualiserStyle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The desktop-music-player look and what came with it: the Toolbar bar, the Slider, the Playlist layout with its
 * visualiser and status bar, the volume booster, Stop, the icon sets, the services' badges with their marks, Clear
 * all but this song, and Settings with
 * its sections and search -- each in a dark theme, a light one, 98, Noctorium 98 and XP, at the ordinary text
 * size and the largest.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class NewLooksScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (
            toolbarLooks() + sliderLooks() + playlistLooks() + boosterLooks() + stopLooks() + iconLooks() +
                badgeLooks() + queueLooks() + settingsLooks()
            ).only().map { arrayOf(it) }
    }
}

/** The pages of Settings, which want a tall screen to be seen whole. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xxhdpi", application = Application::class)
class NewSettingsPagesScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = settingsPageLooks().only().map { arrayOf(it) }
    }
}

/** The narrowest phone still sold, for the bar with the most buttons on it. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h760dp-xxhdpi", application = Application::class)
class NewNarrowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(
            Look("new-toolbar-narrow-night", toolbar(NoctoriumPreferences())) { _, state -> NoctoriumPhone(state) },
            Look("new-toolbar-narrow-98", toolbar(NoctoriumPreferences(theme = ThemePreset.WINDOWS_98))) { _, state -> NoctoriumPhone(state) },
            Look("new-playlist-narrow-night", playlist(NoctoriumPreferences()), playing = { Covers.on(Still.queue[2]) }, kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) }) { _, state ->
                NowPlayingScreen(state) {}
            },
        ).only().map { arrayOf(it) }
    }
}

/** The five kinds of theme, by the name each picture carries. */
private val KINDS = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "noctorium-98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98_NOCTORIUM),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
)

/** Each kind at the ordinary text size and at the largest. */
private val KINDS_AND_SIZES = KINDS.flatMap { (kind, preferences) ->
    listOf(kind to preferences, "$kind-largest" to preferences.copy(textSize = TextSize.LARGEST))
}

/** The clock in the Windows taskbars says whenever the picture is taken; it is put away so two runs agree. */
private fun still(preferences: NoctoriumPreferences) = preferences.copy(taskbarClock = false)

private fun toolbar(preferences: NoctoriumPreferences) =
    still(preferences).copy(phone = preferences.phone.copy(playerBarStyle = PhonePlayerBarStyle.TOOLBAR))

private fun playlist(preferences: NoctoriumPreferences) =
    still(preferences).copy(ambientBackdrop = false, phone = preferences.phone.copy(nowPlayingLayout = PhoneNowPlayingLayout.PLAYLIST))

private fun toolbarLooks(): List<Look> = KINDS_AND_SIZES.map { (kind, preferences) ->
    Look("new-toolbar-$kind", toolbar(preferences)) { _, state -> NoctoriumPhone(state) }
} + listOf(
    Look("new-toolbar-night-glass", toolbar(NoctoriumPreferences(surfaceStyle = SurfaceStyle.GLASS))) { _, state -> NoctoriumPhone(state) },
    Look(
        "new-toolbar-night-top",
        toolbar(NoctoriumPreferences(playerBarPosition = app.noctorium.settings.PlayerBarPosition.TOP)),
    ) { _, state -> NoctoriumPhone(state) },
    // Stop put away, and shuffle with it: the buttons left share the row.
    Look(
        "new-toolbar-night-fewer-buttons",
        toolbar(NoctoriumPreferences()).let { it.copy(phone = it.phone.copy(hiddenPlayerButtons = setOf(PlayerButton.STOP, PlayerButton.SHUFFLE))) },
    ) { _, state -> NoctoriumPhone(state) },
)

private fun sliderLooks(): List<Look> = KINDS_AND_SIZES.map { (kind, preferences) ->
    Look("new-slider-$kind", still(preferences).copy(progressBarStyle = ProgressBarStyle.SLIDER, ambientBackdrop = false)) { _, state ->
        NowPlayingScreen(state) {}
    }
} + KINDS.map { (kind, preferences) ->
    // The thin line along an ordinary bar, for the Slider chosen.
    Look("new-slider-line-$kind", still(preferences).copy(progressBarStyle = ProgressBarStyle.SLIDER)) { _, state -> NoctoriumPhone(state) }
}

private fun playlistLooks(): List<Look> = KINDS_AND_SIZES.map { (kind, preferences) ->
    Look(
        "new-playlist-$kind",
        playlist(preferences),
        playing = { Covers.on(Still.queue[2]) },
        kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
    ) { _, state -> NowPlayingScreen(state) {} }
} + listOf(
    Look(
        "new-playlist-night-meter-columns",
        playlist(NoctoriumPreferences(visualiser = VisualiserStyle.LEVEL_METER, queueColumns = listOf(QueueColumn.TITLE, QueueColumn.SERVICE, QueueColumn.DURATION))),
        playing = { Covers.on(Still.queue[2]) },
        kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
    ) { _, state -> NowPlayingScreen(state) {} },
    Look(
        "new-playlist-98-meter",
        playlist(NoctoriumPreferences(theme = ThemePreset.WINDOWS_98, visualiser = VisualiserStyle.LEVEL_METER)),
        playing = { Covers.on(Still.queue[2]) },
        kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
    ) { _, state -> NowPlayingScreen(state) {} },
    Look(
        "new-playlist-day-no-status-no-visualiser",
        playlist(NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY, statusBar = false, visualiser = VisualiserStyle.OFF)),
        playing = { Covers.on(Still.queue[2]) },
        kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
    ) { _, state -> NowPlayingScreen(state) {} },
)

/** The volume button opened, with the booster on at 400% -- past 200%, so with its warning. */
private val openVolume: ComposeContentTestRule.() -> Unit = {
    onAllNodesWithContentDescription("Volume", substring = true)[0].performClick()
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
}

private fun boosterLooks(): List<Look> = KINDS.map { (kind, preferences) ->
    Look("new-booster-$kind", still(preferences).copy(volumeBoostPercent = 400, ambientBackdrop = false), act = openVolume) { _, state ->
        NowPlayingScreen(state) {}
    }
} + listOf(
    // Closed: the volume button says by how much the sound is boosted.
    Look("new-booster-mark-night", NoctoriumPreferences(volumeBoostPercent = 250)) { _, state -> NowPlayingScreen(state) {} },
    Look("new-booster-mark-largest", NoctoriumPreferences(volumeBoostPercent = 1000, textSize = TextSize.LARGEST)) { _, state -> NowPlayingScreen(state) {} },
    Look("new-booster-off-night", NoctoriumPreferences(), act = openVolume) { _, state -> NowPlayingScreen(state) {} },
    // A song playing in Spotify's own app: the booster says it cannot reach it, rather than offering a slider.
    Look(
        "new-booster-spotify-night",
        NoctoriumPreferences(volumeBoostPercent = 400, spotifyCanPlay = true, spotifyPlayback = SpotifyPlayback.ON_SPOTIFY),
        playing = { Still.spotifyPlaying },
        prepare = { it.waitUntil { settings.value.spotify.playsOnSpotify } },
        act = openVolume,
    ) { _, state -> NowPlayingScreen(state) {} },
)

private fun stopLooks(): List<Look> = listOf("night", "98", "xp").map { kind ->
    val preferences = KINDS.first { it.first == kind }.second
    Look(
        "new-stop-$kind",
        still(preferences).copy(ambientBackdrop = false, phone = PhonePreferences(shownPlayerButtons = setOf(PlayerButton.STOP))),
    ) { _, state -> NowPlayingScreen(state) {} }
} + Look(
    "new-stop-after-night",
    NoctoriumPreferences(phone = PhonePreferences(shownPlayerButtons = setOf(PlayerButton.STOP))),
    prepare = { it.setStopAfterCurrent(true) },
) { _, state -> NowPlayingScreen(state) {} }

private fun iconLooks(): List<Look> = listOf(IconSet.ROUNDED, IconSet.OUTLINED, IconSet.SHARP, IconSet.TWO_TONE, IconSet.LINE).flatMap { set ->
    val name = set.name.lowercase().replace('_', '-')
    listOf(
        Look("new-icons-$name-now-playing", NoctoriumPreferences(iconSet = set)) { _, state -> NowPlayingScreen(state) {} },
        Look("new-icons-$name-home", NoctoriumPreferences(iconSet = set, theme = ThemePreset.NOCTORIUM_DAY)) { _, state -> NoctoriumPhone(state) },
    )
} + listOf(
    Look("new-icons-line-toolbar-98", toolbar(NoctoriumPreferences(theme = ThemePreset.WINDOWS_98, iconSet = IconSet.LINE))) { _, state -> NoctoriumPhone(state) },
    Look("new-icons-line-settings-xp", NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP, iconSet = IconSet.LINE)) { _, state -> SettingsScreen(state) {} },
)

/**
 * The services' badges on Home's covers, every one badged: with their marks in every kind of theme and at the
 * largest text, and with their letters to compare.
 */
private fun badgeLooks(): List<Look> = KINDS_AND_SIZES.map { (kind, preferences) ->
    Look("new-badges-marks-home-$kind", still(preferences).copy(badgePolicy = BadgePolicy.ALWAYS)) { _, state -> NoctoriumPhone(state) }
} + KINDS.map { (kind, preferences) ->
    Look(
        "new-badges-letters-home-$kind",
        still(preferences).copy(badgePolicy = BadgePolicy.ALWAYS, serviceBadgeStyle = ServiceBadgeStyle.LETTERS),
    ) { _, state -> NoctoriumPhone(state) }
}

/** The queue's menu open, with Clear all but this song beside Clear the queue. */
private val openQueueMenu: ComposeContentTestRule.() -> Unit = {
    onNodeWithContentDescription("Queue actions").performClick()
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
}

private fun queueLooks(): List<Look> = KINDS.map { (kind, preferences) ->
    Look(
        "new-queue-menu-$kind",
        still(preferences),
        playing = { Covers.on(Still.queue[1]) },
        kept = { SavedQueue(Covers.on(Still.queue), 1, 83_000) },
        act = openQueueMenu,
    ) { _, state -> QueueScreen(state) }
} + Look(
    // After it: the song playing alone, and autoplay waiting to find songs after it.
    "new-queue-cleared-all-but-this-night",
    NoctoriumPreferences(),
    playing = { Covers.on(Still.queue[1]) },
    kept = { SavedQueue(Covers.on(Still.queue), 1, 83_000) },
    prepare = { it.clearAllButCurrent() },
) { _, state -> QueueScreen(state) }

/** Types [query] into Settings' search box. */
private fun searchFor(query: String): ComposeContentTestRule.() -> Unit = {
    onNodeWithTag(SEARCH_TAG).performTextInput(query)
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
}

private fun settingsLooks(): List<Look> = KINDS_AND_SIZES.map { (kind, preferences) ->
    Look("new-settings-$kind", still(preferences)) { _, state -> SettingsScreen(state) {} }
} + KINDS.map { (kind, preferences) ->
    Look("new-settings-search-dark-mode-$kind", still(preferences), act = searchFor("dark mode")) { _, state -> SettingsScreen(state) {} }
} + listOf(
    Look("new-settings-search-louder-night", NoctoriumPreferences(), act = searchFor("louder")) { _, state -> SettingsScreen(state) {} },
    Look("new-settings-search-typo-night", NoctoriumPreferences(), act = searchFor("visualizr")) { _, state -> SettingsScreen(state) {} },
    Look("new-settings-search-nothing-night", NoctoriumPreferences(), act = searchFor("zebra crossing")) { _, state -> SettingsScreen(state) {} },
)

/** The pages that are new or changed, and a search result's setting lit on its page. */
private fun settingsPageLooks(): List<Look> = KINDS.flatMap { (kind, preferences) ->
    val base = still(preferences)
    listOf(
        Look("new-page-theme-$kind", base) { _, state -> SettingsPageScreen(SettingsPage.THEME, state, signIn = {}, back = {}) },
        Look("new-page-text-$kind", base) { _, state -> SettingsPageScreen(SettingsPage.TEXT_AND_LAYOUT, state, signIn = {}, back = {}) },
        Look("new-page-player-bar-$kind", toolbar(base).copy(progressBarStyle = ProgressBarStyle.SLIDER)) { _, state ->
            SettingsPageScreen(SettingsPage.PLAYER_BAR, state, signIn = {}, back = {})
        },
        Look("new-page-now-playing-$kind", playlist(base)) { _, state -> SettingsPageScreen(SettingsPage.NOW_PLAYING, state, signIn = {}, back = {}) },
        Look("new-page-icons-$kind", base.copy(iconSet = IconSet.LINE)) { _, state -> SettingsPageScreen(SettingsPage.ICONS, state, signIn = {}, back = {}) },
        Look("new-page-tabs-$kind", base) { _, state -> SettingsPageScreen(SettingsPage.TABS, state, signIn = {}, back = {}) },
        Look("new-page-playback-$kind", base) { _, state -> SettingsPageScreen(SettingsPage.PLAYBACK, state, signIn = {}, back = {}) },
        Look("new-page-autoplay-$kind", base) { _, state -> SettingsPageScreen(SettingsPage.AUTOPLAY, state, signIn = {}, back = {}) },
        Look("new-page-sound-$kind", base.copy(volumeBoostPercent = 600)) { _, state -> SettingsPageScreen(SettingsPage.SOUND, state, signIn = {}, back = {}) },
        Look("new-page-highlight-$kind", base) { _, state ->
            SettingsPageScreen(SettingsPage.PLAYER_BAR, state, signIn = {}, highlight = SettingIds.TIME_DISPLAY, back = {})
        },
    )
} + listOf(
    Look("new-page-playback-largest", NoctoriumPreferences(textSize = TextSize.LARGEST)) { _, state -> SettingsPageScreen(SettingsPage.PLAYBACK, state, signIn = {}, back = {}) },
    Look("new-page-now-playing-largest", playlist(NoctoriumPreferences(textSize = TextSize.LARGEST))) { _, state ->
        SettingsPageScreen(SettingsPage.NOW_PLAYING, state, signIn = {}, back = {})
    },
    Look("new-page-autoplay-off", NoctoriumPreferences(autoplay = false)) { _, state -> SettingsPageScreen(SettingsPage.AUTOPLAY, state, signIn = {}, back = {}) },
)
