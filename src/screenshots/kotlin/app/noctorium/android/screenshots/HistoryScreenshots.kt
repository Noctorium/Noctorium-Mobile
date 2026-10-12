package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.history.HistoryEntry
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The profile page with a history in it: several days of made-up listening from every service, long titles among
 * them, at an ordinary size and a narrow one, under the day theme and Windows 98, with a song's menu and the
 * question before clearing -- and with nothing in it; and the profile's tile in Settings. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.HistoryScreenshots"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class HistoryScreenshots {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    private fun shoot(
        name: String,
        preferences: NoctoriumPreferences = NoctoriumPreferences(),
        history: List<HistoryEntry> = madeUpHistory(),
        then: (() -> Unit)? = null,
    ) {
        Covers.install(ApplicationProvider.getApplicationContext())
        compose.shootPhone(screenshotFolder, name, preferences, then = then, history = history) { _, state ->
            SettingsPageScreen(SettingsPage.PROFILE, state, signIn = {}, back = {})
        }
    }

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun history() = shoot("history")

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun historyNarrow() = shoot("history-narrow")

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun historyDay() = shoot("history-day", NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun history98() = shoot("history-98", NoctoriumPreferences(theme = ThemePreset.WINDOWS_98))

    @Test
    fun historyEmpty() = shoot("history-empty", history = emptyList())

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun historyMenu() = shoot("history-menu", then = {
        compose.onAllNodesWithContentDescription("Track actions")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    })

    @Test
    fun historyClear() = shoot("history-clear", then = {
        compose.onNodeWithText("Clear history…").performClick()
    })

    @Test
    fun settingsTile() {
        compose.shootPhone(screenshotFolder, "history-settings-tile", NoctoriumPreferences(), history = madeUpHistory()) { _, state ->
            SettingsScreen(state, signIn = {})
        }
    }

    /** Six days of listening, newest first: the last half hour, last night, earlier this week, last month, last year. */
    private fun madeUpHistory(): List<HistoryEntry> {
        fun song(n: Int, title: String, artist: String, provider: ProviderType, second: String? = null) = Covers.on(
            Track(
                provider = provider,
                id = "history-$n",
                title = title,
                artists = listOfNotNull(artist.takeIf(String::isNotEmpty), second).map { Artist("artist-$it", it, provider) },
                durationMs = 180_000L + n * 7_000L,
                sourceUrl = "https://example.invalid/history/$n",
            ),
        )
        val songs = listOf(
            song(1, "Lanterns Over the Harbour", "The Quiet Hours", ProviderType.YOUTUBE_MUSIC),
            song(2, "Tape Hiss Lullaby", "Saltmarsh", ProviderType.SOUNDCLOUD),
            song(3, "Halfway to Morning (Extended Mix for the Last Train Home, Remastered in 2026)", "Juniper Kaye", ProviderType.SPOTIFY, "Saltmarsh"),
            song(4, "Glasshouse", "Northern Lights Committee", ProviderType.BANDCAMP),
            song(5, "Paper Satellites", "Marrow & Fern", ProviderType.VK),
            song(6, "Signal Fires — live at the Corn Exchange, second night, with the string section", "The Quiet Hours", ProviderType.YOUTUBE_VIDEO),
            song(7, "Low Tide Static", "Odile Brandt", ProviderType.SOUNDCLOUD),
            song(8, "Kitchen Radio Demo 3", "", ProviderType.LOCAL),
        )
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(daysAgo: Long, hour: Int, minute: Int) = today.minusDays(daysAgo).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val plays = buildList {
            (0 until 4).forEach { i -> add(HistoryEntry(songs[i], now - (4L + i * 4L) * 60_000L)) }
            listOf(23 to 41, 22 to 58, 22 to 54, 21 to 12).forEachIndexed { i, (h, m) -> add(HistoryEntry(songs[(i + 4) % songs.size], at(1, h, m))) }
            listOf(18 to 30, 7 to 55).forEachIndexed { i, (h, m) -> add(HistoryEntry(songs[(i + 1) % songs.size], at(3, h, m))) }
            listOf(16 to 0, 15 to 56).forEachIndexed { i, (h, m) -> add(HistoryEntry(songs[(i + 5) % songs.size], at(24, h, m))) }
            add(HistoryEntry(songs[2], at(400, 19, 20)))
        }
        return plays.sortedByDescending { it.playedAt }
    }
}
