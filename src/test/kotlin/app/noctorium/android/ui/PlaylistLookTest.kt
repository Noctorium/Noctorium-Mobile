package app.noctorium.android.ui

import app.noctorium.domain.Artist
import app.noctorium.domain.PlaybackContext
import app.noctorium.domain.PlaybackOrigin
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.QueueManager
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.QueueColumn
import app.noctorium.settings.SettingsState
import app.noctorium.settings.TimeDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sums behind the Playlist layout, the Toolbar bar, the booster's mark and Clear all but this song. */
class PlaylistLookTest {

    private fun song(id: String, seconds: Long) = Track(
        provider = ProviderType.SOUNDCLOUD,
        id = id,
        title = "Song $id",
        artists = listOf(Artist("a-$id", "Someone", ProviderType.SOUNDCLOUD)),
        durationMs = seconds * 1_000,
        artworkUrl = null,
        sourceUrl = "https://example.invalid/$id",
    )

    @Test
    fun `columns switched on join the end of the table, and the title cannot be switched off`() {
        val columns = QueueColumn.DEFAULT
        assertEquals(columns + QueueColumn.SERVICE, columnsWith(columns, QueueColumn.SERVICE, shown = true))
        assertEquals(columns - QueueColumn.ALBUM, columnsWith(columns, QueueColumn.ALBUM, shown = false))
        assertTrue(QueueColumn.TITLE in columnsWith(columns, QueueColumn.TITLE, shown = false))
    }

    @Test
    fun `a column moves among the others and no further than either end`() {
        val columns = listOf(QueueColumn.NUMBER, QueueColumn.TITLE, QueueColumn.ARTIST)
        assertEquals(listOf(QueueColumn.TITLE, QueueColumn.NUMBER, QueueColumn.ARTIST), columnsMoved(columns, QueueColumn.TITLE, -1))
        assertEquals(columns, columnsMoved(columns, QueueColumn.NUMBER, -1))
        assertEquals(columns, columnsMoved(columns, QueueColumn.ARTIST, 1))
        assertEquals(columns, columnsMoved(columns, QueueColumn.ALBUM, 1))
    }

    @Test
    fun `the table's columns are listed in their order, then the ones it has not`() {
        val chosen = listOf(QueueColumn.TITLE, QueueColumn.DURATION)
        assertEquals(
            listOf(QueueColumn.TITLE, QueueColumn.DURATION, QueueColumn.NUMBER, QueueColumn.ARTIST, QueueColumn.ALBUM, QueueColumn.SERVICE),
            columnsInOrder(chosen),
        )
    }

    @Test
    fun `the queue's tab says how many songs and how long`() {
        val queue = QueueManager().apply {
            playQueue(listOf(song("1", 180), song("2", 200)), 0, PlaybackContext(ProviderType.SOUNDCLOUD, PlaybackOrigin.HOME))
        }.state.value
        assertEquals("Queue · 2 songs · 6:20", queueTabName(queue, 2))
    }

    @Test
    fun `the toolbar's time is where the song is and its length, or what is left`() {
        assertEquals("0:58 / 2:31", toolbarTime(58_000, 151_000, TimeDisplay.TOTAL))
        assertEquals("0:58 / -1:33", toolbarTime(58_000, 151_000, TimeDisplay.REMAINING))
        assertEquals("0:00 / --:--", toolbarTime(0, 0, TimeDisplay.TOTAL))
    }

    @Test
    fun `the volume button shows the boost while it is on and can be heard`() {
        val boosted = SettingsState(NoctoriumPreferences(volumeBoostPercent = 400))
        assertEquals(400, shownBoost(boosted, PlaybackState()))
        assertNull(shownBoost(boosted, PlaybackState(volumeBoostAvailable = false)))
        assertNull(shownBoost(SettingsState(NoctoriumPreferences()), PlaybackState()))
    }

    @Test
    fun `Clear all but this song has something to do only with a song playing and something else queued`() {
        val manager = QueueManager()
        assertFalse(canClearAllButCurrent(manager.state.value))
        manager.playQueue(listOf(song("1", 180)), 0, PlaybackContext(ProviderType.SOUNDCLOUD, PlaybackOrigin.HOME))
        assertFalse(canClearAllButCurrent(manager.state.value))
        manager.playQueue(listOf(song("1", 180), song("2", 200)), 1, PlaybackContext(ProviderType.SOUNDCLOUD, PlaybackOrigin.HOME))
        assertTrue(canClearAllButCurrent(manager.state.value))
        manager.clearAllButCurrent()
        assertFalse(canClearAllButCurrent(manager.state.value))
    }
}
