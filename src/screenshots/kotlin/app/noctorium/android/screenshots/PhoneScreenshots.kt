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
import app.noctorium.android.ui.LyricsLookCard
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.PlayerButtonsCard
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.android.ui.SoundCard
import app.noctorium.android.ui.TabsCard
import app.noctorium.android.ui.TextAndLayoutCard
import app.noctorium.core.AppState
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A phone of ordinary size: 411 by 891 points, drawn three pixels to the point. */
private const val PHONE = "w411dp-h891dp-xxhdpi"

/** The narrowest phone still sold, or an ordinary one with its display size turned up. */
private const val NARROW_PHONE = "w320dp-h1100dp-xxhdpi"

/** Tall enough for the longest card to be seen whole. */
private const val TALL_PHONE = "w411dp-h1100dp-xxhdpi"

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

    private val folder = File(System.getProperty("noctorium.screenshots.dir") ?: "build/outputs/screenshots")

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

    private fun handMadeCurve() = NoctoriumPreferences(
        equalizer = EqualizerSettings(enabled = true, preset = EqualizerPreset.CUSTOM, customGains = HAND_MADE, preampDb = -3f),
    )

    /**
     * Draws [content] in the app's theme over its own background, from a still-life state made of
     * [preferences], and saves it as [name].png. [prepare] runs on the state first, for anything that is
     * done rather than set -- a sleep timer started, say.
     */
    private fun shoot(
        name: String,
        preferences: NoctoriumPreferences = NoctoriumPreferences(),
        playing: Track? = null,
        equalizerAvailable: Boolean = true,
        prepare: (AppState) -> Unit = {},
        content: @Composable (SettingsState, AppState) -> Unit,
    ) {
        val state = stillState(preferences, playing)
        try {
            prepare(state)
            // Time moves only when told to. The full-screen seek bar's wave runs for as long as it is on
            // screen, so a screen that waited to be still before its picture was taken would wait forever;
            // two seconds in is long enough for everything that eases into place to have arrived.
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val settings by state.settings.collectAsState()
                NoctoriumTheme(settings.preferences, equalizerAvailable) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        content(settings, state)
                    }
                }
            }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onRoot().captureRoboImage(File(folder, "$name.png").path)
        } finally {
            state.close()
        }
    }
}

/**
 * Declares the activity the Compose test rule draws into, for this run only.
 *
 * The rule starts an empty ComponentActivity, which an app normally declares through a test library merged
 * into the debug manifest. That would put it in the APK for the sake of a screenshot, so it is told to
 * Robolectric's package manager here instead, and the APK stays as it is.
 */
private class EmptyActivityDeclared : ExternalResource() {
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
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp), content = content)
}
