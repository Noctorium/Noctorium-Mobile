package app.noctorium.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the status bar says a song is, from Media3's numbers for it. */
class PlayerFormatTest {

    @Test
    fun `a stream described as a desktop player's status bar describes it`() {
        assertEquals(
            "Opus, stereo, 48 kHz, 128 kbit/s",
            streamFormatOf("audio/opus", null, 48_000, 2, 128_000)?.describe(),
        )
        assertEquals("AAC, stereo, 44.1 kHz", streamFormatOf("audio/mp4a-latm", "mp4a.40.2", 44_100, 2, -1)?.describe())
    }

    @Test
    fun `what Media3 does not know is left out, and nothing known is nothing said`() {
        assertEquals("MP3", streamFormatOf("audio/mpeg", null, -1, -1, -1)?.describe())
        assertNull(streamFormatOf(null, null, -1, -1, -1))
    }

    @Test
    fun `a file that declares no bitrate has one worked out from its size`() {
        assertEquals("Opus, stereo, 48 kHz, 131 kbit/s", streamFormatOf("audio/opus", null, 48_000, 2, -1, estimatedBitrate = 131_000)?.describe())
        // A declared bitrate wins over the estimate.
        assertEquals("Opus, 160 kbit/s", streamFormatOf("audio/opus", null, -1, -1, 160_000, estimatedBitrate = 131_000)?.describe())
    }

    @Test
    fun `the whole file's size is read from a range or from a length that started at the top`() {
        assertEquals(3_456_789, totalBytesFrom(mapOf("Content-Range" to listOf("bytes 0-1048575/3456789")), 0))
        assertEquals(3_456_789, totalBytesFrom(mapOf("content-range" to listOf("bytes 1048576-2097151/3456789")), 1_048_576))
        assertEquals(5_000_000, totalBytesFrom(mapOf("Content-Length" to listOf("5000000")), 0))
        // A length for a part that did not start at the top is the part's, not the file's.
        assertNull(totalBytesFrom(mapOf("Content-Length" to listOf("5000000")), 1_000))
        assertNull(totalBytesFrom(mapOf("Content-Range" to listOf("bytes 0-1023/*")), 0))
        assertNull(totalBytesFrom(emptyMap(), 0))
    }

    @Test
    fun `a bitrate is the size over the length, and only a believable one is given`() {
        // Four minutes at 128 kbit/s is 3.84 MB.
        assertEquals(128_000, estimatedBitrate(3_840_000, 240_000))
        assertNull(estimatedBitrate(null, 240_000))
        assertNull(estimatedBitrate(3_840_000, 0))
        assertNull(estimatedBitrate(100, 240_000))
    }
}
