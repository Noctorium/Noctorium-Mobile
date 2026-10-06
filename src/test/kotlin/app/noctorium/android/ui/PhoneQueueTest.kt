package app.noctorium.android.ui

import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.QueueState
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.AutoplaySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What the queue says about its end, and the kept queue's song in the player. Every name here is made up. */
class PhoneQueueTest {

    private fun track(id: String, provider: ProviderType = ProviderType.SOUNDCLOUD, durationMs: Long? = 200_000) = Track(
        provider = provider,
        id = id,
        title = "Song $id",
        artists = listOf(Artist("a-$id", "Artist $id", provider)),
        sourceUrl = "https://example.invalid/$id",
        durationMs = durationMs,
    )

    private val queue = QueueState(tracks = listOf(track("1"), track("2"), track("3")), currentIndex = 1)

    // --- Up next ---

    @Test
    fun `off is said first, whatever else is lined up`() {
        val lined = queue.copy(suggestions = listOf(track("s1")), suggestionsFrom = "Related on SoundCloud")
        assertEquals(UpNext.OFF, upNext(lined, autoplay = false))
        assertEquals("Autoplay is off", autoplayHeading(lined, UpNext.OFF))
    }

    @Test
    fun `a queue that repeats never ends for autoplay to follow`() {
        assertEquals(UpNext.REPEATING, upNext(queue.copy(repeatMode = RepeatMode.ALL), autoplay = true))
        assertEquals(UpNext.REPEATING, upNext(queue.copy(repeatMode = RepeatMode.ONE), autoplay = true))
    }

    @Test
    fun `lined-up songs are named for where they come from`() {
        val lined = queue.copy(suggestions = listOf(track("s1")), suggestionsFrom = "Related on SoundCloud")
        assertEquals(UpNext.LINED_UP, upNext(lined, autoplay = true))
        assertEquals("Autoplay · Related on SoundCloud", autoplayHeading(lined, UpNext.LINED_UP))
        // Where nobody said, the heading is just autoplay rather than a dot leading nowhere.
        assertEquals("Autoplay", autoplayHeading(lined.copy(suggestionsFrom = null), UpNext.LINED_UP))
        assertEquals("Autoplay", autoplayHeading(lined.copy(suggestionsFrom = " "), UpNext.LINED_UP))
    }

    @Test
    fun `Spotify carrying on by itself says so, and that next still works`() {
        val spotify = queue.copy(continuesElsewhere = true, suggestionsFrom = "Spotify chooses what comes next")
        assertEquals(UpNext.ELSEWHERE, upNext(spotify, autoplay = true))
        assertEquals("Autoplay · Spotify", autoplayHeading(spotify, UpNext.ELSEWHERE))
        assertTrue("Spotify chooses what comes next" in autoplayNote(UpNext.ELSEWHERE))
        assertTrue("Next still works" in autoplayNote(UpNext.ELSEWHERE))
    }

    @Test
    fun `on with nothing lined up waits for the end of the queue`() {
        assertEquals(UpNext.WAITING, upNext(queue, autoplay = true))
        assertEquals("Autoplay", autoplayHeading(queue, UpNext.WAITING))
        assertTrue("nears its end" in autoplayNote(UpNext.WAITING))
    }

    @Test
    fun `every state has a line of its own`() {
        val notes = UpNext.entries.map(::autoplayNote)
        assertEquals(notes.size, notes.distinct().size)
        assertTrue(notes.none(String::isBlank))
    }

    // --- Settings ---

    @Test
    fun `the same service's autoplay names every service's own way of carrying on`() {
        val detail = autoplayDetail(AutoplaySource.SAME_SERVICE)
        listOf("YouTube Music's radio", "SoundCloud's related", "Spotify's own autoplay", "Bandcamp", "VK").forEach {
            assertTrue(it in detail, "\"$it\" missing from: $detail")
        }
        assertTrue("YouTube Music's radio" in autoplayDetail(AutoplaySource.YOUTUBE_MUSIC))
    }

    // --- The kept queue ---

    @Test
    fun `a kept queue's song is shown, paused, while nothing plays`() {
        val shown = shownPlayback(PlaybackState(), queue)
        assertEquals(queue.current, shown.track)
        assertEquals(PlaybackStatus.IDLE, shown.status)
        assertEquals(0, shown.positionMs)
        assertEquals(200_000, shown.durationMs)
        assertEquals(0, shownPlayback(PlaybackState(), queue.copy(tracks = listOf(track("1", durationMs = null)), currentIndex = 0)).durationMs)
    }

    @Test
    fun `what is playing is shown as it is`() {
        val playing = PlaybackState(status = PlaybackStatus.PLAYING, track = track("9"), positionMs = 5_000)
        assertSame(playing, shownPlayback(playing, queue))
        // Loading, paused or failed is a state of its own, not the kept queue's song.
        val loading = PlaybackState(status = PlaybackStatus.RESOLVING)
        assertSame(loading, shownPlayback(loading, queue))
        val ended = PlaybackState(status = PlaybackStatus.IDLE, track = track("3"), positionMs = 200_000)
        assertSame(ended, shownPlayback(ended, queue))
    }

    @Test
    fun `nothing kept, nothing shown`() {
        assertNull(shownPlayback(PlaybackState(), QueueState()).track)
        assertNull(shownPlayback(PlaybackState(), queue.copy(currentIndex = -1)).track)
    }
}
