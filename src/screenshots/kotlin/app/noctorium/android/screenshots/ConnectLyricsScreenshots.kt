package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.noctorium.android.ui.ConnectPicture
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.lyrics.LyricLine
import app.noctorium.lyrics.LyricsProviderId
import app.noctorium.lyrics.LyricsProviderOutcome
import app.noctorium.lyrics.LyricsProviderStatus
import app.noctorium.lyrics.LyricsResult
import app.noctorium.lyrics.LyricsUiState
import app.noctorium.playback.SavedQueue
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The switch at the top of the Connect tab, on, off, and on while this phone drives the desk or the desk drives it --
 * with the same switch heading its card in Settings -- and the Playlist layout's Lyrics tab, with made-up synced
 * lyrics keeping time: in a dark theme, a light one, 98 and XP, on an ordinary phone and the narrowest. Opt-in, as the
 * others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.ConnectLyrics*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class ConnectLyricsScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (switchLooks("") + lyricsLooks("") + remoteLyricsLooks()).only().map { arrayOf(it) }
    }
}

/** The same on the narrowest phone still sold, where the switch's words and the two tabs have the least room. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class ConnectLyricsNarrowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (switchLooks("-narrow") + lyricsLooks("-narrow")).only().map { arrayOf(it) }
    }
}

/** The four kinds of theme these are drawn in, with the Windows taskbars' clock put away so two runs agree. */
private val FOUR = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
).map { (kind, preferences) -> kind to preferences.copy(taskbarClock = false) }

/** The Connect tab with its switch on, off, and on while a remote control is under way either way round; and Settings' card. */
private fun switchLooks(size: String): List<Look> = FOUR.flatMap { (kind, preferences) ->
    fun tab(name: String, picture: ConnectPicture) = Look(
        "connect-switch-$name-$kind$size",
        preferences,
        playing = { if (picture.here.track == null) null else Covers.on(Still.nowPlaying) },
        prepare = goToConnect,
        screen = seeing(picture) { NoctoriumPhone(it) },
    )
    listOf(
        tab("on", ConnectStill.three),
        tab("off", ConnectStill.off),
        tab("driving", ConnectStill.driving),
        tab("driven", ConnectStill.driven),
        Look("connect-switch-settings-on-$kind$size", preferences, screen = seeing(ConnectStill.three) { state ->
            SettingsPageScreen(SettingsPage.CONNECT, state, signIn = {}, back = {})
        }),
        Look("connect-switch-settings-off-$kind$size", preferences, screen = seeing(ConnectStill.off) { state ->
            SettingsPageScreen(SettingsPage.CONNECT, state, signIn = {}, back = {})
        }),
    )
}

private fun playlistLayout(preferences: NoctoriumPreferences) =
    preferences.copy(ambientBackdrop = false, phone = preferences.phone.copy(nowPlayingLayout = PhoneNowPlayingLayout.PLAYLIST))

/** The Playlist layout with its Lyrics tab chosen, the song a minute and a half in, in the middle of the queue. */
private fun lyricsLooks(size: String): List<Look> = FOUR.map { (kind, preferences) ->
    Look(
        "playlist-lyrics-$kind$size",
        playlistLayout(preferences),
        playing = { Covers.on(Still.queue[2]) },
        kept = { SavedQueue(Covers.on(Still.queue), 2, 83_000) },
        prepare = { lyricsReady(it, it.shownPlayback.value.track ?: it.playback.value.track) },
        act = chooseTheLyricsTab,
    ) { _, state -> NowPlayingScreen(state) {} }
}

/** The same while this phone drives the desk: the desk's song, its lines in time with the desk. */
private fun remoteLyricsLooks(): List<Look> = listOf(
    Look(
        "playlist-lyrics-driving-night",
        playlistLayout(FOUR.first().second),
        playing = { null },
        prepare = { lyricsReady(it, ConnectStill.driving.shown.track) },
        act = chooseTheLyricsTab,
        screen = seeing(ConnectStill.driving) { state -> NowPlayingScreen(state) {} },
    ),
)

/** The Lyrics tab tapped, and the lines found there already rather than asked for. */
private val chooseTheLyricsTab: ComposeContentTestRule.() -> Unit = {
    onNodeWithText("Lyrics").performClick()
    mainClock.advanceTimeBy(1_500)
    waitForIdle()
    // Had the lyrics been asked for after all, the page would be waiting for the sources instead of following these.
    onNodeWithText("Synced lyrics, following the song").assertExists()
}

/**
 * Lines for a song that does not exist, written for these pictures and for nothing else: a night bus, the wipers,
 * somebody's radio. Nine seconds apart, so a minute and a half in is well into them.
 */
private val NIGHT_BUS = listOf(
    "Window seat and the wipers keep time",
    "Streetlights counting the stops down the line",
    "Somebody's radio two rows ahead",
    "Humming the chorus of something unsaid",
    "",
    "Ride on, ride on, the depot can wait",
    "Night bus home and I'm already late",
    "Fogged-up glass with a name in the frost",
    "Drawn with a finger and already lost",
    "Ride on, ride on, the driver won't mind",
    "Leaving the last of the city behind",
    "Window seat and the wipers keep time",
)

/**
 * Hands [state] the made-up lines for [track] as if a lyric source had just answered for it, so that opening the
 * lyrics finds them there and asks no source for any.
 *
 * Base keeps the lyrics it has under a key of the song's own making, and asks the sources again for a song whose key
 * is not the one kept; the key is made here the way Base makes it. Were the two ever to differ, the page would go
 * looking, and [chooseTheLyricsTab] fails rather than taking a picture of it.
 */
private fun lyricsReady(state: AppState, track: Track?) {
    track ?: return
    val lines = NIGHT_BUS.mapIndexed { index, text -> LyricLine(text, startTimeMs = index * 9_000L) }
    val found = LyricsResult(LyricsProviderId.LRCLIB, lines, synced = true)
    val lyrics = LyricsUiState(
        trackKey = "${track.queueKey}|${track.title}|${track.artistLine}|${track.durationMs ?: 0}",
        outcomes = listOf(
            LyricsProviderOutcome(LyricsProviderId.LRCLIB, LyricsProviderStatus.FOUND, found),
            LyricsProviderOutcome(LyricsProviderId.LYRICS_OVH, LyricsProviderStatus.NOT_FOUND),
            LyricsProviderOutcome(LyricsProviderId.GENIUS, LyricsProviderStatus.NEEDS_KEY),
        ),
        selectedProvider = LyricsProviderId.LRCLIB,
    )
    val held = AppState::class.java.getDeclaredField("mutableLyrics").apply { isAccessible = true }.get(state)
    @Suppress("UNCHECKED_CAST")
    (held as MutableStateFlow<LyricsUiState>).value = lyrics
}
