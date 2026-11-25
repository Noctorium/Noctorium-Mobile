package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.StatsScreen
import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.history.HistoryEntry
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.SurfaceStyle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.42's phone: the new default player with the queue a swipe up, the old default beside it, the song menu as a
 * sheet, glass buttons over a solid page, the glass search box, and Stats from the picture. Opt-in, as the others:
 *
 *     ./gradlew testPhoneDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.NewPlayerScreenshots"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TALL_PHONE, application = Application::class)
class NewPlayerScreenshots {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    private fun laid(layout: PhoneNowPlayingLayout) = NoctoriumPreferences(phone = PhonePreferences(nowPlayingLayout = layout))

    private val glassButtons = NoctoriumPreferences(surfaceStyle = SurfaceStyle.GLASS, backgroundSurface = SurfaceStyle.SOLID)

    @Test
    fun player() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, "new-player", laid(PhoneNowPlayingLayout.CLASSIC), playing = Still.nowPlaying) { _, state ->
            NowPlayingScreen(state) {}
        }
    }

    @Test
    fun oldPlayer() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, "old-player", laid(PhoneNowPlayingLayout.OLD_CLASSIC), playing = Still.nowPlaying) { _, state ->
            NowPlayingScreen(state) {}
        }
    }

    @Test
    fun songSheet() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(
            screenshotFolder,
            "new-song-sheet",
            laid(PhoneNowPlayingLayout.CLASSIC),
            playing = Still.nowPlaying,
            then = {
                compose.onAllNodesWithContentDescription("Track actions")[0].performClick()
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
            },
        ) { _, state -> NowPlayingScreen(state) {} }
    }

    @Test
    fun glassButtonsHome() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, "new-glass-buttons-home", glassButtons, playing = Still.nowPlaying) { _, state ->
            NoctoriumPhone(state)
        }
    }

    @Test
    fun glassSearch() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(
            screenshotFolder,
            "new-glass-search",
            NoctoriumPreferences(surfaceStyle = SurfaceStyle.GLASS),
            playing = Still.nowPlaying,
            prepare = { it.navigate(app.noctorium.core.Destination.SEARCH) },
        ) { _, state -> NoctoriumPhone(state) }
    }

    @Test
    fun pictureMenu() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, "new-picture-menu", NoctoriumPreferences(), history = history(), then = {
            compose.onNodeWithContentDescription("Account").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            compose.onNodeWithText("Stats").assertExists()
            compose.onNodeWithText("Stats").performClick()
            compose.waitForIdle()
        }) { _, state -> NoctoriumPhone(state) }
    }

    @Test
    fun stats() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, "new-stats", NoctoriumPreferences(), history = history()) { _, state ->
            StatsScreen(state) {}
        }
    }

    private fun history(): List<HistoryEntry> {
        fun song(n: Int, title: String, artist: String, provider: ProviderType) = Covers.on(
            Track(provider, "stats-$n", title, listOf(Artist("a-$artist", artist, provider)), durationMs = 200_000L, sourceUrl = "https://example.invalid/$n"),
        )
        val songs = listOf(
            song(1, "Lanterns Over the Harbour", "The Quiet Hours", ProviderType.YOUTUBE_MUSIC),
            song(2, "Tape Hiss Lullaby", "Saltmarsh", ProviderType.SOUNDCLOUD),
            song(3, "Glasshouse", "Northern Lights Committee", ProviderType.BANDCAMP),
            song(4, "Low Tide Static", "Odile Brandt", ProviderType.SOUNDCLOUD),
            song(5, "Paper Satellites", "Marrow & Fern", ProviderType.SPOTIFY),
        )
        val now = System.currentTimeMillis()
        // Song 1 most, then 2, and so on: a ladder the tops can be read off.
        return songs.flatMapIndexed { i, track -> (0 until (songs.size - i) * 3).map { k -> HistoryEntry(track, now - (i * 7 + k) * 3_600_000L) } }
            .sortedByDescending { it.playedAt }
    }
}
