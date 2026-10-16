package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.core.AppState
import app.noctorium.scrobble.ScrobbleManager
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ScrobbleConnectionStatus
import app.noctorium.settings.ScrobbleServiceState
import app.noctorium.settings.ScrobbleState
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Scrobbling page with Libre.fm on it beside Last.fm: nothing connected; Last.fm waiting to be allowed, its page
 * kept with Copy link and Open again for a browser that did not open, and that link copied; Libre.fm connected; and a
 * sign-in Last.fm stopped taking, with Libre.fm's starting -- in a dark theme, a light one, 98 and XP, and on the
 * narrowest phone; with Settings' tile. Every account is made up, and nothing asks Last.fm or Libre.fm anything: the
 * scrobblers are handed how they stand. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.Scrobbling*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TALL_PHONE, application = Application::class)
class ScrobblingScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (pageLooks("") + onceLooks()).only().map { arrayOf(it) }
    }
}

/** The page on the narrowest phone still sold, where the waiting sign-in's four buttons have the least room. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class ScrobblingNarrowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = pageLooks("-narrow").only().map { arrayOf(it) }
    }
}

/**
 * Settings' list with the Scrobbling tile saying who is connected, on a screen tall enough to reach it: the list is
 * not scrolled, since a picture's clock moves only when told to and a scroll waits on it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1700dp-xxhdpi", application = Application::class)
class ScrobblingTileScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(
            Look("scrobbling-tile-night", FOUR.first().second, playing = { null }, prepare = scrobblingAs(libreFmConnected)) { _, state ->
                SettingsScreen(state) {}
            },
        ).only().map { arrayOf(it) }
    }
}

/** The four kinds of theme these are drawn in, with the Windows taskbars' clock put away so two runs agree. */
private val FOUR = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
).map { (kind, preferences) -> kind to preferences.copy(taskbarClock = false) }

/** Made up, as every name in these pictures is: Last.fm's page as it would be, with a key and a token of nobody's. */
private const val LAST_FM_PAGE =
    "https://www.last.fm/api/auth/?api_key=0f1e2d3c4b5a69788796a5b4c3d2e1f0&token=sampletoken0123456789abcdefghij"

private val nothingConnected = ScrobbleState(lastFmConfigured = true)

private val lastFmWaiting = ScrobbleState(
    lastFmConfigured = true,
    lastFm = ScrobbleServiceState(
        ScrobbleConnectionStatus.AWAITING_APPROVAL,
        message = "Allow Noctorium on Last.fm's page, and it connects by itself.",
        approvalUrl = LAST_FM_PAGE,
    ),
)

/** What Base says once a sign-in has begun and no browser answered: the case the link is kept for. */
private const val NO_BROWSER = "Your browser did not open. Copy the link into it, and allow Noctorium on Last.fm there."

private val libreFmConnected = ScrobbleState(
    lastFmConfigured = true,
    libreFm = ScrobbleServiceState(ScrobbleConnectionStatus.CONNECTED, "sample-listener", "Ready to scrobble"),
    scrobblesThisSession = 3,
)

private val refusedAndStarting = ScrobbleState(
    lastFmConfigured = true,
    lastFm = ScrobbleServiceState(
        ScrobbleConnectionStatus.ERROR,
        message = "Last.fm needs connecting again: it no longer accepts the old sign-in. Anything played meanwhile is kept and sent once you do.",
    ),
    libreFm = ScrobbleServiceState(ScrobbleConnectionStatus.CONNECTING, message = "Starting Libre.fm sign-in…"),
)

private val scrobblingPage: @androidx.compose.runtime.Composable (SettingsState, AppState) -> Unit = { _, state ->
    SettingsPageScreen(SettingsPage.SCROBBLING, state, signIn = {}, back = {})
}

/** The page nothing connected, Last.fm waiting, and Libre.fm connected, in each of the four. */
private fun pageLooks(size: String): List<Look> = FOUR.flatMap { (kind, preferences) ->
    listOf(
        Look("scrobbling-disconnected-$kind$size", preferences, playing = { null }, prepare = scrobblingAs(nothingConnected), screen = scrobblingPage),
        Look(
            "scrobbling-lastfm-waiting-$kind$size",
            preferences,
            playing = { null },
            prepare = scrobblingAs(lastFmWaiting, NO_BROWSER),
            screen = scrobblingPage,
        ),
        Look("scrobbling-librefm-connected-$kind$size", preferences, playing = { null }, prepare = scrobblingAs(libreFmConnected), screen = scrobblingPage),
    )
}

/** Once each: Copy link pressed, and a refused sign-in beside one starting. */
private fun onceLooks(): List<Look> {
    val night = FOUR.first().second
    return listOf(
        Look(
            "scrobbling-lastfm-copied-night",
            night,
            playing = { null },
            prepare = scrobblingAs(lastFmWaiting, NO_BROWSER),
            act = copyTheLink,
            screen = scrobblingPage,
        ),
        Look("scrobbling-refused-night", night, playing = { null }, prepare = scrobblingAs(refusedAndStarting), screen = scrobblingPage),
    )
}

/** Copy link pressed, and Base's word that it was: the page the card shows is the one the scrobbler keeps. */
private val copyTheLink: ComposeContentTestRule.() -> Unit = {
    onNodeWithText("Copy link").performClick()
    mainClock.advanceTimeBy(500)
    waitForIdle()
    onNodeWithText("Link copied. Open it in your browser and allow Noctorium on Last.fm.").assertExists()
}

/**
 * Hands [state]'s scrobblers [scrobbling], as if they had come to stand that way, and Settings the note [message].
 *
 * Through the scrobble manager's own state, which Base copies into the settings, so that Copy link and the rest find
 * the page the card shows. Base reads the sign-ins it has kept once, as it starts, and that would undo these were it
 * to come after them; the settings only show them once it has, since they are copied from the scrobblers only after.
 */
private fun scrobblingAs(scrobbling: ScrobbleState, message: String? = null): (AppState) -> Unit = { state ->
    val manager = AppState::class.java.getDeclaredField("scrobbleManager").apply { isAccessible = true }.get(state)
    @Suppress("UNCHECKED_CAST")
    val held = ScrobbleManager::class.java.getDeclaredField("mutableState").apply { isAccessible = true }.get(manager)
        as MutableStateFlow<ScrobbleState>
    state.waitUntil {
        if (held.value != scrobbling) held.value = scrobbling
        settings.value.scrobbling == scrobbling
    }
    if (message != null) {
        @Suppress("UNCHECKED_CAST")
        val shown = AppState::class.java.getDeclaredField("mutableSettings").apply { isAccessible = true }.get(state)
            as MutableStateFlow<SettingsState>
        shown.update { it.copy(message = message) }
    }
}
