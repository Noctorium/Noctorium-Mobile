package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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

    /** A swipe up on the cover, nowhere near the handle, opens the queue. */
    @Test
    fun swipeUpForQueue() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(
            screenshotFolder,
            "new-player-swipe-up",
            laid(PhoneNowPlayingLayout.CLASSIC),
            playing = Still.nowPlaying,
            then = {
                compose.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - 400f) }
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNodeWithText("Queue").assertExists()
            },
        ) { _, state -> NowPlayingScreen(state) {} }
    }

    /** At the first song, a pull down on the sheet's list puts it away again. */
    @Test
    fun pullDownClosesQueueSheet() {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(
            screenshotFolder,
            "new-player-pull-down",
            laid(PhoneNowPlayingLayout.CLASSIC),
            playing = Still.nowPlaying,
            then = {
                compose.onRoot().performTouchInput { swipeUp(startY = centerY, endY = centerY - 400f) }
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNodeWithText("Queue").assertExists()
                compose.onRoot().performTouchInput { swipeDown(startY = height * .45f, endY = height * .85f) }
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNodeWithText("Queue").assertDoesNotExist()
            },
        ) { _, state -> NowPlayingScreen(state) {} }
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

    /** Signed in: the account's own statistics, streaks and charts, as the service sends them. */
    @Test
    fun accountStats() {
        val full = app.noctorium.account.AccountStats.parse(
            // From a local stats service seeded with invented listening (scripts/seed-local.mjs).
            ACCOUNT_STATS,
        )!!
        compose.shootPhone(screenshotFolder, "new-account-stats", NoctoriumPreferences()) { _, state ->
            StatsScreen(state, load = { full }) {}
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

private const val ACCOUNT_STATS = """{"range":"30d","from":"2026-10-29T23:00:00.000Z","streams":140,"uniqueTracks":33,"artists":12,"msPlayed":25572368,"hours":7.1,"firstPlay":"2026-10-30T08:21:00.286Z","lastPlay":"2026-11-28T09:00:44.021Z","byProvider":[{"provider":"YOUTUBE_MUSIC","streams":60,"hours":3.3},{"provider":"SOUNDCLOUD","streams":32,"hours":1.5},{"provider":"SPOTIFY","streams":20,"hours":0.9},{"provider":"VK","streams":15,"hours":0.7},{"provider":"BANDCAMP","streams":13,"hours":0.7}],"topTracks":[{"title":"Harbour Lights","artist":"Night Tram","provider":"YOUTUBE_MUSIC","streams":15,"minutes":53},{"title":"Copper Fields","artist":"Halvard Moss","provider":"SOUNDCLOUD","streams":7,"minutes":24},{"title":"Orbit Song","artist":"Paper Satellites","provider":"SOUNDCLOUD","streams":7,"minutes":23},{"title":"Platform Nine","artist":"Night Tram","provider":"YOUTUBE_MUSIC","streams":7,"minutes":21},{"title":"A Thousand Kites","artist":"Little Kestrel","provider":"YOUTUBE_MUSIC","streams":6,"minutes":19}],"topArtists":[{"artist":"Night Tram","streams":36,"minutes":116,"tracks":5},{"artist":"Paper Satellites","streams":16,"minutes":48,"tracks":3},{"artist":"Halvard Moss","streams":12,"minutes":34,"tracks":2},{"artist":"Little Kestrel","streams":12,"minutes":45,"tracks":3},{"artist":"The Lamplighters","streams":12,"minutes":34,"tracks":3}],"timeline":{"bucket":"day","points":[{"start":"2026-10-30","streams":4,"minutes":16},{"start":"2026-10-31","streams":4,"minutes":12},{"start":"2026-11-01","streams":6,"minutes":23},{"start":"2026-11-02","streams":2,"minutes":7},{"start":"2026-11-03","streams":6,"minutes":21},{"start":"2026-11-04","streams":2,"minutes":8},{"start":"2026-11-05","streams":7,"minutes":21},{"start":"2026-11-06","streams":5,"minutes":17},{"start":"2026-11-07","streams":0,"minutes":0},{"start":"2026-11-08","streams":7,"minutes":22},{"start":"2026-11-09","streams":3,"minutes":9},{"start":"2026-11-10","streams":0,"minutes":0},{"start":"2026-11-11","streams":6,"minutes":15},{"start":"2026-11-12","streams":2,"minutes":9},{"start":"2026-11-13","streams":6,"minutes":13},{"start":"2026-11-14","streams":14,"minutes":38},{"start":"2026-11-15","streams":1,"minutes":5},{"start":"2026-11-16","streams":0,"minutes":0},{"start":"2026-11-17","streams":1,"minutes":2},{"start":"2026-11-18","streams":4,"minutes":14},{"start":"2026-11-19","streams":4,"minutes":9},{"start":"2026-11-20","streams":8,"minutes":28},{"start":"2026-11-21","streams":10,"minutes":25},{"start":"2026-11-22","streams":10,"minutes":27},{"start":"2026-11-23","streams":5,"minutes":15},{"start":"2026-11-24","streams":8,"minutes":25},{"start":"2026-11-25","streams":3,"minutes":5},{"start":"2026-11-26","streams":1,"minutes":4},{"start":"2026-11-27","streams":6,"minutes":19},{"start":"2026-11-28","streams":5,"minutes":16}]},"hourly":[{"hour":0,"streams":13,"minutes":48},{"hour":1,"streams":0,"minutes":0},{"hour":2,"streams":1,"minutes":2},{"hour":3,"streams":1,"minutes":2},{"hour":4,"streams":1,"minutes":1},{"hour":5,"streams":0,"minutes":0},{"hour":6,"streams":3,"minutes":9},{"hour":7,"streams":0,"minutes":0},{"hour":8,"streams":9,"minutes":25},{"hour":9,"streams":14,"minutes":49},{"hour":10,"streams":13,"minutes":36},{"hour":11,"streams":6,"minutes":14},{"hour":12,"streams":2,"minutes":7},{"hour":13,"streams":0,"minutes":0},{"hour":14,"streams":0,"minutes":0},{"hour":15,"streams":1,"minutes":2},{"hour":16,"streams":1,"minutes":3},{"hour":17,"streams":0,"minutes":0},{"hour":18,"streams":0,"minutes":0},{"hour":19,"streams":17,"minutes":50},{"hour":20,"streams":16,"minutes":48},{"hour":21,"streams":14,"minutes":43},{"hour":22,"streams":13,"minutes":42},{"hour":23,"streams":15,"minutes":44}],"weekdays":[{"day":1,"streams":10,"minutes":32},{"day":2,"streams":15,"minutes":47},{"day":3,"streams":15,"minutes":42},{"day":4,"streams":14,"minutes":43},{"day":5,"streams":29,"minutes":93},{"day":6,"streams":33,"minutes":91},{"day":7,"streams":24,"minutes":78}],"recent":[{"title":"Foghorn Waltz","artist":"Morning Ferry","provider":"BANDCAMP","playedAt":"2026-11-28T09:00:44.021Z","msPlayed":244128},{"title":"Sodium Sky","artist":"Night Tram","provider":"YOUTUBE_MUSIC","playedAt":"2026-11-28T08:39:57.368Z","msPlayed":235687},{"title":"Paper Satellites","artist":"Marrow & Fern","provider":"VK","playedAt":"2026-11-28T08:20:09.831Z","msPlayed":215373},{"title":"Low Tide Static","artist":"Odile Brandt","provider":"VK","playedAt":"2026-11-28T07:13:10.959Z","msPlayed":71292},{"title":"Sodium Sky","artist":"Night Tram","provider":"YOUTUBE_MUSIC","playedAt":"2026-11-28T05:25:07.943Z","msPlayed":167070}],"streak":{"current":12,"longest":22}}"""
