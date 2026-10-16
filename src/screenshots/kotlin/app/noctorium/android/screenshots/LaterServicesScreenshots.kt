package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.SearchScreen
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.core.AppState
import app.noctorium.core.SearchMode
import app.noctorium.domain.ProviderType
import app.noctorium.settings.BadgePolicy
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ServiceBadgeStyle
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.TidalConnectionState
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
 * Apple Music, Deezer and Tidal on the phone: each one's page in Settings, set up and not, and their badges on Home
 * and on a Hybrid search's albums and artists -- in a dark theme, a light one, 98 and XP, on an ordinary phone and the
 * narrowest. Every account and every song is made up, and nothing asks Apple, Deezer or Tidal anything: their
 * providers answer from memory, and what a sign-in would leave is handed to the settings. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.LaterServices*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class LaterServicesScreenshots(private val look: Look) {
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

/** The same on the narrowest phone still sold, where the Tidal app's address and the chips have the least room. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class LaterServicesNarrowScreenshots(private val look: Look) {
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
 * Settings' list with the three new tiles under VK's, on a screen tall enough to reach them: Apple Music signed in,
 * Deezer's profile given, and Tidal still without an app.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi", application = Application::class)
class LaterServicesTileScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = FOUR.map { (kind, preferences) ->
            Look("later-tiles-$kind", preferences.withLaterSetUp(), playing = { null }, prepare = { it.waitUntil { settings.value.appleMusic.connected } }) { _, state ->
                SettingsScreen(state) {}
            }
        }.only().map { arrayOf(it) }
    }
}

/** The four kinds of theme these are drawn in, with the Windows taskbars' clock put away so two runs agree. */
private val FOUR = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
).map { (kind, preferences) -> kind to preferences.copy(taskbarClock = false) }

/** Signed in to Apple Music in the United Kingdom, and a Deezer profile given: made up, both. */
private fun NoctoriumPreferences.withLaterSetUp() = copy(
    appleMusicSignedIn = true,
    appleMusicStorefront = "gb",
    deezerProfileId = "1234567",
    deezerProfileName = "Night Owl",
)

private fun page(page: SettingsPage): @androidx.compose.runtime.Composable (SettingsState, AppState) -> Unit = { _, state ->
    SettingsPageScreen(page, state, signIn = {}, back = {})
}

/** A Tidal app of nobody's, its client id made up and no secret: signing in is possible, searching signed out is not. */
private val tidalAppSet: (AppState) -> Unit = { state ->
    state.configureTidalApplication("madeupclientid0123456789", "")
    state.waitUntil { settings.value.tidal.configured && settings.value.tidal.message != null }
}

/** Signed in to Tidal, as Base would say it once the browser's reply had come back. */
private val tidalConnected: (AppState) -> Unit = { state ->
    tidalAppSet(state)
    state.settingsAs {
        copy(
            tidal = TidalConnectionState(
                configured = true,
                ownApp = true,
                readsCatalogue = true,
                connected = true,
                accountName = "Night Owl",
                message = "Tidal connected as Night Owl. Your collection is in the library.",
            ),
        )
    }
}

/** Each page, set up and not, and the badges on Home and in a search, in each of the four. */
private fun pageLooks(size: String): List<Look> = FOUR.flatMap { (kind, preferences) ->
    val none = { null }
    listOf(
        Look("apple-music-signed-out-$kind$size", preferences, playing = none, screen = page(SettingsPage.APPLE_MUSIC)),
        Look(
            "apple-music-signed-in-$kind$size",
            preferences.withLaterSetUp(),
            playing = none,
            prepare = { it.waitUntil { settings.value.appleMusic.connected } },
            screen = page(SettingsPage.APPLE_MUSIC),
        ),
        Look("deezer-empty-$kind$size", preferences, playing = none, screen = page(SettingsPage.DEEZER)),
        Look(
            "deezer-set-$kind$size",
            preferences.withLaterSetUp(),
            playing = none,
            prepare = { it.waitUntil { settings.value.deezer.found } },
            screen = page(SettingsPage.DEEZER),
        ),
        Look("tidal-no-app-$kind$size", preferences, playing = none, screen = page(SettingsPage.TIDAL)),
        Look("tidal-app-set-$kind$size", preferences, playing = none, prepare = tidalAppSet, screen = page(SettingsPage.TIDAL)),
        Look(
            "tidal-connected-$kind$size",
            preferences.copy(tidalAccountName = "Night Owl"),
            playing = none,
            prepare = tidalConnected,
            screen = page(SettingsPage.TIDAL),
        ),
        Look(
            "home-later-$kind$size",
            preferences,
            playing = none,
            shelves = Still.shelves + Still.laterShelves,
            pinned = Still.pinnedEverywhere,
        ) { _, state -> NoctoriumPhone(state) },
        Look(
            "search-later-$kind$size",
            preferences.copy(
                hybridSearch = setOf(ProviderType.BANDCAMP, ProviderType.APPLE_MUSIC, ProviderType.DEEZER, ProviderType.TIDAL),
            ),
            playing = none,
            prepare = { it.searchAndWait("harbour") },
        ) { _, state -> SearchScreen(state) },
    )
}

/** Once each: the folded parts opened, and the badges drawn as letters. */
private fun onceLooks(): List<Look> {
    val night = FOUR.first().second
    val none = { null }
    return listOf(
        Look(
            "apple-music-paste-night",
            night,
            playing = none,
            act = open("Paste the media-user-token cookie instead", "Use this"),
            screen = page(SettingsPage.APPLE_MUSIC),
        ),
        Look(
            "apple-music-token-night",
            night.withLaterSetUp(),
            playing = none,
            prepare = { it.waitUntil { settings.value.appleMusic.connected } },
            act = open("Your own developer token", "Use this token"),
            screen = page(SettingsPage.APPLE_MUSIC),
        ),
        Look(
            "tidal-app-open-night",
            night,
            playing = none,
            prepare = tidalAppSet,
            act = open("Your Tidal app", "Use this app"),
            screen = page(SettingsPage.TIDAL),
        ),
        // Deezer alone, with the badges always on: a round artist's badge sits on its circle, not out in the corner.
        Look(
            "search-deezer-badges-night",
            night.copy(badgePolicy = BadgePolicy.ALWAYS),
            playing = none,
            prepare = {
                it.setSearchMode(SearchMode.DEEZER)
                it.searchAndWait("harbour")
            },
        ) { _, state -> SearchScreen(state) },
        Look(
            "home-later-letters-night",
            night.copy(serviceBadgeStyle = ServiceBadgeStyle.LETTERS),
            playing = none,
            shelves = Still.shelves + Still.laterShelves,
            pinned = Still.pinnedEverywhere,
        ) { _, state -> NoctoriumPhone(state) },
    )
}

/** Taps [what] open, and waits for [then] to be there. */
private fun open(what: String, then: String): ComposeContentTestRule.() -> Unit = {
    onNodeWithText(what).performClick()
    mainClock.advanceTimeBy(500)
    waitForIdle()
    onNodeWithText(then).assertExists()
}

/**
 * Hands Settings [change], as Base would publish it, once Base has published what it reads from the credential store
 * as it starts -- which would otherwise come after this and undo it.
 */
private fun AppState.settingsAs(change: SettingsState.() -> SettingsState) {
    @Suppress("UNCHECKED_CAST")
    val shown = AppState::class.java.getDeclaredField("mutableSettings").apply { isAccessible = true }.get(this)
        as MutableStateFlow<SettingsState>
    val wanted = shown.value.change()
    val deadline = System.currentTimeMillis() + 2_000
    // Set, and set again for as long as anything starting up still writes over it.
    while (System.currentTimeMillis() < deadline) {
        if (shown.value.tidal != wanted.tidal || shown.value.appleMusic != wanted.appleMusic || shown.value.deezer != wanted.deezer) {
            shown.update { it.change() }
        }
        Thread.sleep(50)
    }
}
