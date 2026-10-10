package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.QueueScreen
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.TextSize
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every look there was before the Toolbar, the Slider and the Playlist layout, in every kind of theme: each
 * player bar on Home, each seek bar and each layout on Now playing, and the queue, in a dark theme, a light one,
 * 98, Noctorium 98 and XP. Rendered before a change and after it and compared pixel for pixel, they are the
 * proof that what somebody already chose looks exactly as it did.
 *
 * The lists are written out rather than read from the enums, so this set stays the same as choices are added:
 * a new bar has pictures of its own elsewhere, and here would only make the two sets disagree in length.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class UnchangedScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = unchangedLooks().map { arrayOf(it) }
    }
}

/** Every theme the looks are drawn in: a dark one, a light one, and the three Windows skins. */
private val EVERY_KIND_OF_THEME = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "noctorium-98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98_NOCTORIUM),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
)

private val OLD_PLAYER_BARS = listOf(
    PhonePlayerBarStyle.CLASSIC,
    PhonePlayerBarStyle.SLIM,
    PhonePlayerBarStyle.SLIM_LEFT,
    PhonePlayerBarStyle.CONTROLS,
    PhonePlayerBarStyle.SPOTLIGHT,
    PhonePlayerBarStyle.FLOATING,
    PhonePlayerBarStyle.LINE,
    PhonePlayerBarStyle.RECORD,
    PhonePlayerBarStyle.TASKBAR,
)

private val OLD_SEEK_BARS = listOf(
    ProgressBarStyle.MATERIAL,
    ProgressBarStyle.MINIMAL,
    ProgressBarStyle.WAVE,
    ProgressBarStyle.SEGMENTS,
    ProgressBarStyle.CAPSULE,
    ProgressBarStyle.BARS,
    ProgressBarStyle.BEADS,
    ProgressBarStyle.NEON,
    ProgressBarStyle.RULER,
    ProgressBarStyle.CLASSIC,
    ProgressBarStyle.LUNA,
)

private val OLD_LAYOUTS = listOf(
    PhoneNowPlayingLayout.CLASSIC,
    PhoneNowPlayingLayout.FULL_COVER,
    PhoneNowPlayingLayout.RECORD,
    PhoneNowPlayingLayout.COVER_FLOW,
    PhoneNowPlayingLayout.SING_ALONG,
    PhoneNowPlayingLayout.BIG_TYPE,
)

internal fun unchangedLooks(): List<Look> = EVERY_KIND_OF_THEME.flatMap { (theme, preferences) ->
    // The Windows themes' clock says whatever the time is when the picture is taken, which no two runs agree on.
    val still = preferences.copy(taskbarClock = false)
    OLD_PLAYER_BARS.map { bar ->
        Look("unchanged-bar-${slug(bar.name)}-$theme", still.copy(phone = PhonePreferences(playerBarStyle = bar))) { _, state ->
            NoctoriumPhone(state)
        }
    } + OLD_SEEK_BARS.map { style ->
        Look("unchanged-seek-${slug(style.name)}-$theme", still.copy(progressBarStyle = style, ambientBackdrop = false)) { _, state ->
            NowPlayingScreen(state) {}
        }
    } + OLD_LAYOUTS.map { layout ->
        Look(
            "unchanged-layout-${slug(layout.name)}-$theme",
            still.copy(phone = PhonePreferences(nowPlayingLayout = layout)),
            playing = { Covers.on(Still.queue[2]) },
            kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
        ) { _, state -> NowPlayingScreen(state) {} }
    } + listOf(
        Look(
            "unchanged-queue-$theme",
            still,
            playing = { Covers.on(Still.queue[1]) },
            kept = { SavedQueue(Covers.on(Still.queue), 1, 83_000) },
        ) { _, state -> QueueScreen(state) },
        // The largest text, where a look that only just fitted would be the first to move.
        Look("unchanged-home-largest-$theme", still.copy(textSize = TextSize.LARGEST)) { _, state -> NoctoriumPhone(state) },
        Look("unchanged-now-playing-largest-$theme", still.copy(textSize = TextSize.LARGEST, ambientBackdrop = false)) { _, state ->
            NowPlayingScreen(state) {}
        },
    )
}

private fun slug(name: String) = name.lowercase().replace('_', '-')
