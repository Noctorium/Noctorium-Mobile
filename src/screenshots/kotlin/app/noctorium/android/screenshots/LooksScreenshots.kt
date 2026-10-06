package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.PlayerBarCard
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemePreset
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
        fun looks(): List<Array<Any>> = seekBarLooks().map { arrayOf(it) }
    }
}

/** The same, for the cards in Customization, which want a taller screen to be seen whole. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = VERY_TALL_PHONE, application = Application::class)
class PickerScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = pickerLooks().map { arrayOf(it) }
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
    private val screen: @Composable (SettingsState, AppState) -> Unit,
) {
    fun shoot(compose: ComposeContentTestRule) {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, name, preferences, playing(), kept = kept(), content = screen)
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

/** The pickers in Customization. */
private fun pickerLooks(): List<Look> = THEMES.flatMap { (theme, preferences) ->
    listOf(
        Look("picker-player-bar-$theme", preferences.copy(progressBarStyle = ProgressBarStyle.NEON)) { settings, state ->
            Card { PlayerBarCard(settings, state) }
        },
    )
}
