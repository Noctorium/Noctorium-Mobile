package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.noctorium.android.ui.ThemeCard
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlaying
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.PageGlowLayer
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SingAlongNowPlaying
import app.noctorium.android.ui.pageGlow
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.domain.Track
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.HomePart
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.SettingsState
import app.noctorium.settings.SurfaceStyle
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Glow themes on the phone: Home, Now playing in Classic and in Sing along -- the arrangement the look they come
 * from has -- and the theme picker, in each of the four; Afterglow under a red cover, a blue one and none, since its
 * light is the cover's colour; a page of switches in Velvet, whose accent is white; Search, the Library and glass;
 * and "Match the artwork" on Night, which takes the cover's colour now too. See `screenshots` in build.gradle.kts
 * for how to run them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class GlowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = glowLooks("").map { arrayOf(it) }
    }
}

/** The same on the narrowest phone, where the controls and the chips are closest together. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class NarrowGlowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = glowLooks("-narrow").map { arrayOf(it) }
    }
}

/** A song under a cover of one colour, or of none, by the name its pictures carry. */
private class Cover(val name: String, val song: () -> Track?)

private val RED = Cover("red") { Covers.on(Still.nowPlaying, Covers.Sleeve.RED) }
private val BLUE = Cover("blue") { Covers.on(Still.nowPlaying, Covers.Sleeve.BLUE) }
private val NO_COVER = Cover("no-cover") { Still.nowPlaying }
private val PAINTED = Cover("painted") { Covers.on(Still.nowPlaying) }

/**
 * Each Glow theme with the covers it is shown under: Afterglow's light follows the cover, so it has three; Cinder's
 * is red whatever plays, which a blue cover shows; Velvet's and Smoke's are across the top, under any sleeve.
 */
private val GLOW_THEMES = listOf(
    Triple("afterglow", ThemePreset.AFTERGLOW, listOf(RED, BLUE, NO_COVER)),
    Triple("cinder", ThemePreset.CINDER, listOf(BLUE)),
    Triple("velvet", ThemePreset.VELVET, listOf(PAINTED)),
    Triple("smoke", ThemePreset.SMOKE, listOf(PAINTED)),
)

private fun glowLooks(size: String): List<Look> = GLOW_THEMES.flatMap { (name, theme, covers) ->
    val preferences = NoctoriumPreferences(theme = theme)
    fun laid(layout: PhoneNowPlayingLayout) = preferences.copy(phone = PhonePreferences(nowPlayingLayout = layout))
    covers.flatMap { cover ->
        val tag = "glow-$name-${cover.name}"
        listOf(
            Look("$tag-home$size", preferences, playing = cover.song) { _, state -> NoctoriumPhone(state) },
            Look("$tag-now-playing-classic$size", laid(PhoneNowPlayingLayout.CLASSIC), playing = cover.song) { _, state ->
                NowPlayingScreen(state) {}
            },
            // With shuffle and repeat on, so a white accent's way of marking them is seen.
            Look("$tag-now-playing-sing-along$size", laid(PhoneNowPlayingLayout.SING_ALONG), playing = cover.song) { settings, state ->
                GlowSingAlong(settings, state)
            },
        )
    } + Look("glow-$name-picker$size", preferences) { settings, state ->
        // The theme card on the page's light, scrolled to its foot so the Glow row and the ones round it are seen: the
        // page's own list scrolls by animating, which a picture whose clock stands still would wait on for ever.
        Box(Modifier.fillMaxSize().pageGlow()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(Int.MAX_VALUE)).padding(top = 12.dp)) {
                ThemeCard(settings, state)
            }
        }
    }
} + listOf(
    // A page of switches in Velvet, some on and some off, on its white accent.
    Look(
        "glow-velvet-switches$size",
        NoctoriumPreferences(
            theme = ThemePreset.VELVET,
            hiddenHomeParts = setOf(HomePart.PINNED, HomePart.SOUNDCLOUD, HomePart.VK),
        ),
    ) { _, state ->
        Box(Modifier.fillMaxSize().pageGlow()) { SettingsPageScreen(SettingsPage.HOME_LOOK, state, signIn = {}, back = {}) }
    },
    // Search and the Library in the phone as it is, tabs and all: the light under their lists, and in Velvet the song
    // playing among the results marked as playing, its title being the colour of every title on a white accent.
    Look(
        "glow-velvet-search$size",
        NoctoriumPreferences(theme = ThemePreset.VELVET),
        playing = { Covers.on(Still.bandcampAlbumTracks[0], Covers.Sleeve.BLUE) },
        prepare = {
            it.navigate(Destination.SEARCH)
            it.searchAndWait("harbour")
        },
    ) { _, state -> NoctoriumPhone(state) },
    Look(
        "glow-afterglow-red-library$size",
        NoctoriumPreferences(theme = ThemePreset.AFTERGLOW),
        playing = RED.song,
        prepare = { it.navigate(Destination.LIBRARY) },
    ) { _, state -> NoctoriumPhone(state) },
    // Afterglow under glass: the panes bend the page's light rather than a blurred cover.
    Look(
        "glow-afterglow-red-home-glass$size",
        NoctoriumPreferences(theme = ThemePreset.AFTERGLOW, surfaceStyle = SurfaceStyle.entries.first { it.isGlass }),
        playing = RED.song,
    ) { _, state -> NoctoriumPhone(state) },
    // "Match the artwork" on an older theme, which the phone now takes from the cover rather than falling back.
    Look(
        "artwork-accent-night-red-now-playing$size",
        NoctoriumPreferences(accent = AccentPreset.ARTWORK, phone = PhonePreferences(shownPlayerButtons = setOf(PlayerButton.STOP))),
        playing = RED.song,
    ) { _, state -> NowPlayingScreen(state) {} },
)

/**
 * Sing along as the now playing screen lays it out on a Glow theme -- the page and its light, then the layout -- with
 * [Still.lyrics] in place of what would be fetched, and shuffle and repeat switched on.
 */
@Composable
private fun GlowSingAlong(settings: SettingsState, state: AppState) {
    val playback by state.playback.collectAsState()
    val queue by state.queue.state.collectAsState()
    val track = playback.track ?: return
    Box(Modifier.fillMaxSize()) {
        PageGlowLayer()
        SingAlongNowPlaying(
            NowPlaying(
                state = state,
                track = track,
                playback = playback,
                queue = queue.copy(shuffleEnabled = true, repeatMode = RepeatMode.ALL),
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
}
