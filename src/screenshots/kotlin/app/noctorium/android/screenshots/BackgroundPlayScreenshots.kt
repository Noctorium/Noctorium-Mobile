package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import app.noctorium.android.Exit
import app.noctorium.android.ExitSummary
import app.noctorium.android.Interrupted
import app.noctorium.android.Interruption
import app.noctorium.android.PhoneMaker
import app.noctorium.android.ui.BackgroundPlayPicture
import app.noctorium.android.ui.KeepAliveCard
import app.noctorium.android.ui.LocalBackgroundPlayPicture
import app.noctorium.android.ui.LocalKeepAlivePicture
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A phone that restricts Noctorium in the background: the warning above the player bar on Home, the explanation on
 * coming back after Android stopped the music, and the keep-alive section on a made-up OnePlus with its checklist.
 * In the night theme, 98 and XP, at an ordinary size and the narrowest. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.*BackgroundPlayScreenshots"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class BackgroundPlayScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = backgroundPlayLooks("").map { arrayOf(it) }
    }
}

/** The same on the narrowest phone, where the warning's words are closest to its button. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class NarrowBackgroundPlayScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = backgroundPlayLooks("-narrow").map { arrayOf(it) }
    }
}

private val THEMES = listOf(
    "night" to NoctoriumPreferences(),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98, taskbarClock = false),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP, taskbarClock = false),
)

private val RESTRICTED = BackgroundPlayPicture(restricted = true)
private val STARTED_RESTRICTED = BackgroundPlayPicture(restricted = true, toldAsPlaybackStarts = true)
private val STOPPED_WHILE_AWAY = BackgroundPlayPicture(restricted = true, unseen = listOf(Interrupted(Interruption.RESTRICTED, KeepAliveStill.NOW - 60_000)))

/** A OnePlus that restricts Noctorium, and stopped its music twice this week. */
private val ONEPLUS = KeepAliveStill.stopped.copy(
    exits = ExitSummary(stopped = 2, lastStopped = Exit(android.app.ApplicationExitInfo.REASON_FREEZER, 125, KeepAliveStill.NOW - 3_600_000)),
    maker = PhoneMaker.ONEPLUS,
    manufacturer = "OnePlus",
    notificationsAllowed = true,
    restricted = true,
    musicStopped = 2,
)

private fun backgroundPlayLooks(size: String): List<Look> = THEMES.flatMap { (theme, preferences) ->
    listOf(
        Look("restricted-home-$theme$size", preferences) { _, state ->
            CompositionLocalProvider(LocalBackgroundPlayPicture provides RESTRICTED) { NoctoriumPhone(state) }
        },
        Look("restricted-started-$theme$size", preferences, whole = true) { _, state ->
            CompositionLocalProvider(LocalBackgroundPlayPicture provides STARTED_RESTRICTED) { NoctoriumPhone(state) }
        },
        Look("restricted-explained-$theme$size", preferences, whole = true) { _, state ->
            CompositionLocalProvider(LocalBackgroundPlayPicture provides STOPPED_WHILE_AWAY) { NoctoriumPhone(state) }
        },
        Look("restricted-keep-alive-$theme$size", preferences.copy(phone = PhonePreferences(keepAlive = true)), playing = { null }) { settings, state ->
            CompositionLocalProvider(LocalKeepAlivePicture provides ONEPLUS) { Card { KeepAliveCard(settings, state) } }
        },
    )
}
