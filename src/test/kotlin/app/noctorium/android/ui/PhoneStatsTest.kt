package app.noctorium.android.ui

import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.history.HistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the history adds up to on the Stats page. Every name here is made up. */
class PhoneStatsTest {

    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun track(id: String, vararg artists: String, provider: ProviderType = ProviderType.SOUNDCLOUD) = Track(
        provider = provider,
        id = id,
        title = "Song $id",
        artists = artists.map { Artist("a-$it", it, provider) },
        sourceUrl = "https://example.invalid/$id",
        durationMs = 180_000,
    )

    private val history = listOf(
        HistoryEntry(track("1", "Ada"), now - 1 * day),
        HistoryEntry(track("1", "Ada"), now - 2 * day),
        HistoryEntry(track("2", "Ada", "Bo", provider = ProviderType.YOUTUBE_MUSIC), now - 3 * day),
        HistoryEntry(track("3", "Cy"), now - 40 * day),
    )

    @Test
    fun `a span counts only its own listens`() {
        val week = listeningStats(history, StatsSpan.WEEK, now)
        assertEquals(3, week.listens)
        assertEquals(2, week.songs)
        assertEquals(3 * 180_000L, week.listenedMs)
        assertEquals(4, listeningStats(history, StatsSpan.ALL, now).listens)
    }

    @Test
    fun `the tops are ordered by plays, and every artist of a song counts once for it`() {
        val week = listeningStats(history, StatsSpan.WEEK, now)
        assertEquals("1" to 2, week.topSongs.first().let { it.first.id to it.second })
        assertEquals(listOf("Ada" to 3, "Bo" to 1), week.topArtists)
        assertEquals(listOf(ProviderType.SOUNDCLOUD to 2, ProviderType.YOUTUBE_MUSIC to 1), week.services)
    }

    @Test
    fun `listened time reads as minutes, then hours`() {
        assertEquals("3 min", listenedTime(180_000))
        assertEquals("2 h 5 min", listenedTime(125 * 60_000L))
    }
}
