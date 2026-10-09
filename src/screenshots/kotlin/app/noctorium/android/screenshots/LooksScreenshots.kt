package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ui.CLOCK_MENU
import app.noctorium.android.ui.LibraryScreen
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlaying
import app.noctorium.android.ui.NowPlayingLookCard
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.PlayerBarCard
import app.noctorium.android.ui.QueueScreen
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.android.ui.SkinnedAlertDialog
import app.noctorium.android.ui.SkinnedTextButton
import app.noctorium.android.ui.SingAlongNowPlaying
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SettingsState
import app.noctorium.settings.SurfaceStyle
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.themeSkin
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The looks: every seek bar, player bar and now playing layout, and the Windows skins, each in a dark theme
 * and a light one. Too many to write out one test apiece, so each is a [Look] in a list and the runner makes
 * a test of every one. See `screenshots` in build.gradle.kts for how to run them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class LooksScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (seekBarLooks() + playerBarLooks() + layoutLooks() + skinLooks() + clockLooks()).map { arrayOf(it) }
    }
}

/** The same, for the cards in Customization, which want a taller screen to be seen whole. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TALLEST_PHONE, application = Application::class)
class PickerScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (pickerLooks() + skinPageLooks()).map { arrayOf(it) }
    }
}

/**
 * The Windows taskbars on a phone 360 points wide, with the clock and without it: the room the clock gives up
 * is what lets the start button keep its name there.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h891dp-xxhdpi", application = Application::class)
class NarrowTaskbarScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = SKINS.flatMap { (skin, preferences) ->
            listOf(
                Look("skin-$skin-home-360", preferences) { _, state -> NoctoriumPhone(state) },
                Look("skin-$skin-home-360-no-clock", preferences.copy(taskbarClock = false)) { _, state -> NoctoriumPhone(state) },
            )
        }.map { arrayOf(it) }
    }
}

/**
 * One picture: what it is called, the settings it is drawn with, what is playing and what was queued, and
 * the screen. The song and the queue are made when the picture is, since their covers are painted then.
 */
class Look(
    private val name: String,
    private val preferences: NoctoriumPreferences,
    private val playing: () -> Track? = { Covers.on(Still.nowPlaying) },
    private val kept: () -> SavedQueue? = { null },
    /** Done to the state before the screen is drawn, for what is done rather than set, such as opening an album. */
    private val prepare: (AppState) -> Unit = {},
    /** Whether to take the whole screen rather than the content: a dialog is a window of its own. */
    private val whole: Boolean = false,
    /** Done to the screen once it has settled, such as holding something down; the whole screen is taken after. */
    private val act: (ComposeContentTestRule.() -> Unit)? = null,
    private val screen: @Composable (SettingsState, AppState) -> Unit,
) {
    fun shoot(compose: ComposeContentTestRule) {
        Covers.install(ApplicationProvider.getApplicationContext())
        val then: (() -> Unit)? = when {
            act != null -> { { compose.act() } }
            whole -> { {} }
            else -> null
        }
        compose.shootPhone(screenshotFolder, name, preferences, playing(), kept = kept(), prepare = prepare, then = then, content = screen)
    }

    override fun toString() = name
}

/** A dark theme and a light one, by the name each picture carries. */
private val THEMES = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
)

private val NEW_SEEK_BARS = listOf(
    ProgressBarStyle.BARS,
    ProgressBarStyle.BEADS,
    ProgressBarStyle.NEON,
    ProgressBarStyle.RULER,
    ProgressBarStyle.LUNA,
)

/** Each new seek bar on the now playing screen, and as the thin line along the player bar on Home. */
private fun seekBarLooks(): List<Look> = NEW_SEEK_BARS.flatMap { style ->
    THEMES.flatMap { (theme, preferences) ->
        val name = style.name.lowercase()
        listOf(
            Look("seek-$name-$theme", preferences.copy(progressBarStyle = style)) { _, state -> NowPlayingScreen(state) {} },
            Look("line-$name-$theme", preferences.copy(progressBarStyle = style)) { _, state -> NoctoriumPhone(state) },
        )
    }
}

private val NEW_PLAYER_BARS = listOf(
    PhonePlayerBarStyle.FLOATING,
    PhonePlayerBarStyle.LINE,
    PhonePlayerBarStyle.RECORD,
    PhonePlayerBarStyle.TASKBAR,
)

/** Each new player bar on Home, solid and under glass, and with the Ruler for the line the others carry. */
private fun playerBarLooks(): List<Look> = NEW_PLAYER_BARS.flatMap { layout ->
    THEMES.flatMap { (theme, preferences) ->
        val name = layout.name.lowercase()
        SurfaceStyle.entries.map { surface ->
            Look(
                "bar-$name-$theme" + if (surface.isGlass) "-glass" else "",
                preferences.copy(surfaceStyle = surface, phone = PhonePreferences(playerBarStyle = layout)),
            ) { _, state -> NoctoriumPhone(state) }
        }
    }
} + Look(
    "bar-record-paused-night",
    NoctoriumPreferences(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.RECORD)),
    playing = { null },
    kept = { SavedQueue(Covers.on(Still.queue), 1, 67_000) },
) { _, state -> NoctoriumPhone(state) }

/** Each now playing layout, with Classic beside them now that it has a cover to show. */
private fun layoutLooks(): List<Look> = THEMES.flatMap { (theme, preferences) ->
    fun laid(layout: PhoneNowPlayingLayout) = preferences.copy(phone = PhonePreferences(nowPlayingLayout = layout))
    listOf(
        PhoneNowPlayingLayout.CLASSIC,
        PhoneNowPlayingLayout.FULL_COVER,
        PhoneNowPlayingLayout.RECORD,
        PhoneNowPlayingLayout.BIG_TYPE,
    ).map { layout ->
        Look("layout-${layout.name.lowercase().replace('_', '-')}-$theme", laid(layout)) { _, state -> NowPlayingScreen(state) {} }
    } + listOf(
        // In the middle of the queue, so there are covers either side.
        Look(
            "layout-cover-flow-$theme",
            laid(PhoneNowPlayingLayout.COVER_FLOW),
            playing = { Covers.on(Still.queue[2]) },
            kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
        ) { _, state -> NowPlayingScreen(state) {} },
        Look(
            "layout-big-type-long-$theme",
            laid(PhoneNowPlayingLayout.BIG_TYPE),
            playing = { Covers.on(Still.longTitle) },
        ) { _, state -> NowPlayingScreen(state) {} },
        // Drawn with lyrics of its own making, since the screen itself would ask the lyric sources for some.
        Look("layout-sing-along-$theme", laid(PhoneNowPlayingLayout.SING_ALONG)) { settings, state -> SingAlongWithLyrics(settings, state) },
    )
}

/** Sing along, as the now playing screen lays it out, with [Still.lyrics] in place of what would be fetched. */
@Composable
private fun SingAlongWithLyrics(settings: SettingsState, state: AppState) {
    val playback by state.playback.collectAsState()
    val queue by state.queue.state.collectAsState()
    val track = playback.track ?: return
    SingAlongNowPlaying(
        NowPlaying(
            state = state,
            track = track,
            playback = playback,
            queue = queue,
            settings = settings,
            lyrics = Still.lyrics(track),
            hidden = emptySet(),
            haptics = {},
            onSpotify = false,
            showLyrics = true,
            toggleLyrics = {},
            openQueue = {},
            close = {},
        ),
    )
}

/** The pickers in Customization. */
private fun pickerLooks(): List<Look> = THEMES.flatMap { (theme, preferences) ->
    listOf(
        Look("picker-player-bar-$theme", preferences.copy(progressBarStyle = ProgressBarStyle.NEON)) { settings, state ->
            Card { PlayerBarCard(settings, state) }
        },
        // The Taskbar bar chosen, so the card offers its clock.
        Look("picker-player-bar-taskbar-$theme", preferences.copy(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.TASKBAR))) { settings, state ->
            Card { PlayerBarCard(settings, state) }
        },
        Look("picker-now-playing-$theme", preferences.copy(phone = PhonePreferences(nowPlayingLayout = PhoneNowPlayingLayout.COVER_FLOW))) { settings, state ->
            Card { NowPlayingLookCard(settings, state) }
        },
    )
}

/** The Windows themes, by the name each picture carries: 98, 98 in Noctorium's night, and XP. */
private val SKINS = listOf(
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "noctorium-98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98_NOCTORIUM),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
)

/** The seek bar each Windows theme was made for: 98's progress bar, in either of its palettes, or Luna's. */
private fun windowsSeekBar(preferences: NoctoriumPreferences): ProgressBarStyle =
    if (preferences.themeSkin == ThemeSkin.WINDOWS_XP) ProgressBarStyle.LUNA else ProgressBarStyle.CLASSIC

/**
 * The Windows skins on the screens they dress: Home with its taskbar, a list, Settings, Now playing on the
 * desktop and on the cover's wash, and a dialog.
 */
private fun skinLooks(): List<Look> = SKINS.flatMap { (skin, preferences) ->
    val plain = preferences.copy(ambientBackdrop = false)
    listOf(
        Look("skin-$skin-home", preferences) { _, state -> NoctoriumPhone(state) },
        Look(
            "skin-$skin-home-taskbar-bar",
            preferences.copy(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.TASKBAR)),
        ) { _, state -> NoctoriumPhone(state) },
        Look(
            "skin-$skin-home-floating-bar",
            preferences.copy(progressBarStyle = windowsSeekBar(preferences), phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.FLOATING)),
        ) { _, state -> NoctoriumPhone(state) },
        Look(
            "skin-$skin-queue",
            preferences,
            playing = { Covers.on(Still.queue[1]) },
            kept = { SavedQueue(Covers.on(Still.queue), 1, 83_000) },
        ) { _, state -> QueueScreen(state) },
        Look("skin-$skin-settings", preferences) { _, state -> SettingsScreen(state) {} },
        Look("skin-$skin-now-playing", plain) { _, state -> NowPlayingScreen(state) {} },
        Look("skin-$skin-now-playing-wash", preferences.copy(progressBarStyle = ProgressBarStyle.MATERIAL)) { _, state -> NowPlayingScreen(state) {} },
        Look(
            "skin-$skin-now-playing-seek",
            plain.copy(progressBarStyle = windowsSeekBar(preferences)),
        ) { _, state -> NowPlayingScreen(state) {} },
        Look(
            "skin-$skin-cover-flow",
            plain.copy(phone = PhonePreferences(nowPlayingLayout = PhoneNowPlayingLayout.COVER_FLOW)),
            playing = { Covers.on(Still.queue[2]) },
            kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
        ) { _, state -> NowPlayingScreen(state) {} },
        Look("skin-$skin-dialog", preferences, whole = true) { _, _ -> DeleteDialogShown() },
        Look("skin-$skin-library", preferences) { _, state -> LibraryScreen(state) },
        // An album's list, with a song's menu open over it.
        Look(
            "skin-$skin-album-menu",
            preferences,
            prepare = { it.openAndWait(Still.bandcampAlbum) },
            act = {
                onAllNodesWithContentDescription("Track actions")[0].performClick()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
            },
        ) { _, state -> LibraryScreen(state) },
    )
}

/** The pages of Settings under the skins, which want the very tall screen to be seen whole. */
private fun skinPageLooks(): List<Look> = SKINS.flatMap { (skin, preferences) ->
    listOf(
        Look("skin-$skin-playback", preferences.copy(playbackSpeed = 1.25f)) { _, state ->
            SettingsPageScreen(SettingsPage.PLAYBACK, state, signIn = {}, back = {})
        },
        Look("skin-$skin-player-bar-card", preferences.copy(progressBarStyle = windowsSeekBar(preferences))) { settings, state ->
            Card { PlayerBarCard(settings, state) }
        },
        // Customization, with the themes to choose from at the top of it.
        Look("skin-$skin-customization", preferences) { _, state ->
            SettingsPageScreen(SettingsPage.CUSTOMIZATION, state, signIn = {}, back = {})
        },
    )
} + Look(
    // Noctorium 98 chosen on Customization while 98 is the theme: the page turns to night there and then.
    "skin-98-choosing-noctorium-98",
    NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    act = {
        onNodeWithText("Noctorium 98").performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    },
) { _, state -> SettingsPageScreen(SettingsPage.CUSTOMIZATION, state, signIn = {}, back = {}) }

/**
 * The taskbars' clock: put away under each Windows theme, from the tabs' taskbar, the Taskbar bar's and the one
 * under the Now playing window; the Taskbar bar without it in an ordinary theme, solid and under glass; the
 * menu holding it down opens, in each look; and that menu putting it away. With the clock, they are the
 * pictures of Home and Now playing above.
 */
private fun clockLooks(): List<Look> = SKINS.flatMap { (skin, preferences) ->
    val off = preferences.copy(taskbarClock = false)
    listOf(
        Look("skin-$skin-home-no-clock", off) { _, state -> NoctoriumPhone(state) },
        Look(
            "skin-$skin-home-taskbar-bar-no-clock",
            off.copy(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.TASKBAR)),
        ) { _, state -> NoctoriumPhone(state) },
        Look("skin-$skin-now-playing-no-clock", off.copy(ambientBackdrop = false)) { _, state -> NowPlayingScreen(state) {} },
        Look("skin-$skin-clock-menu", preferences, act = holdTheClock) { _, state -> NoctoriumPhone(state) },
        Look("skin-$skin-clock-put-away", preferences, act = putTheClockAway) { _, state -> NoctoriumPhone(state) },
    )
} + THEMES.flatMap { (theme, preferences) ->
    val taskbar = preferences.copy(phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.TASKBAR))
    listOf(
        Look("bar-taskbar-$theme-no-clock", taskbar.copy(taskbarClock = false)) { _, state -> NoctoriumPhone(state) },
        Look("bar-taskbar-$theme-clock-menu", taskbar, act = holdTheClock) { _, state -> NoctoriumPhone(state) },
    )
} + Look(
    "bar-taskbar-night-glass-no-clock",
    NoctoriumPreferences(surfaceStyle = SurfaceStyle.GLASS, taskbarClock = false, phone = PhonePreferences(playerBarStyle = PhonePlayerBarStyle.TASKBAR)),
) { _, state -> NoctoriumPhone(state) }

/** The taskbar's clock, known by what holding it down does, since what it says is whenever the pictures are taken. */
private val theClock = SemanticsMatcher("is the taskbar's clock") {
    it.config.getOrNull(SemanticsActions.OnLongClick)?.label == CLOCK_MENU
}

private val holdTheClock: ComposeContentTestRule.() -> Unit = {
    onNode(theClock).performTouchInput { longClick() }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText("Show the clock").assertExists()
}

/** The menu's one item taken off, which leaves no clock on the screen at all. */
private val putTheClockAway: ComposeContentTestRule.() -> Unit = {
    holdTheClock()
    onNodeWithText("Show the clock").performClick()
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    onAllNodes(theClock).assertCountEquals(0)
    onNodeWithText("Show the clock").assertDoesNotExist()
}

/** A question with two answers, as the playlists ask before deleting one, over Home. */
@Composable
private fun DeleteDialogShown() {
    SkinnedAlertDialog(
        onDismissRequest = {},
        title = "Delete \"Night Drives\"?",
        text = { Text("This deletes the playlist from YouTube Music too. The songs in it are not touched.") },
        confirmButton = { SkinnedTextButton({}) { Text("Delete") } },
        dismissButton = { SkinnedTextButton({}) { Text("Keep") } },
    )
}
