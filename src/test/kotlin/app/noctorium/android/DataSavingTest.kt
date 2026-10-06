package app.noctorium.android

import app.noctorium.settings.DataSaver
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the data saver chooses out of a page's audio, with streams shaped the way NewPipe hands them back
 * for YouTube and for SoundCloud.
 */
class DataSavingTest {

    private fun stream(
        id: String,
        format: MediaFormat,
        kbps: Int,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        track: String? = null,
        content: String = "https://example.invalid/$id",
    ): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent(content, true)
        .setMediaFormat(format)
        .setDeliveryMethod(delivery)
        .setAverageBitrate(kbps)
        .apply { if (track != null) setAudioTrackId(track) }
        .build()

    /** YouTube's audio-only formats: Opus at three sizes, and AAC at two. */
    private val youTube = listOf(
        stream("249", MediaFormat.WEBMA_OPUS, 50),
        stream("250", MediaFormat.WEBMA_OPUS, 70),
        stream("251", MediaFormat.WEBMA_OPUS, 160),
        stream("139", MediaFormat.M4A, 48),
        stream("140", MediaFormat.M4A, 128),
    )

    /** SoundCloud's transcodings as NewPipe reads them: MP3 both ways, Opus and AAC over HLS. */
    private val soundCloud = listOf(
        stream("mp3_progressive", MediaFormat.MP3, 128),
        stream("mp3_hls", MediaFormat.MP3, 128, DeliveryMethod.HLS),
        stream("opus_hls", MediaFormat.OPUS, 64, DeliveryMethod.HLS),
        stream("aac_160k", MediaFormat.M4A, 160, DeliveryMethod.HLS),
    )

    @Test
    fun `without the saver the best is played, as it always was`() {
        assertEquals("251", chooseAudioStream(youTube, smallest = false)?.id)
        assertEquals("aac_160k", chooseAudioStream(soundCloud, smallest = false)?.id)
    }

    @Test
    fun `with the saver YouTube's smallest is played`() {
        assertEquals("139", chooseAudioStream(youTube, smallest = true)?.id)
        assertEquals("249", chooseAudioStream(youTube.filterNot { it.id == "139" }, smallest = true)?.id)
    }

    @Test
    fun `SoundCloud's Opus over HLS is never chosen, small as it is`() {
        val chosen = chooseAudioStream(soundCloud, smallest = true)
        assertEquals("mp3_hls", chosen?.id, "the smallest fetched the way the best one is")
    }

    @Test
    fun `a stream that does not say its bitrate is not taken for a small one`() {
        val streams = youTube + stream("unknown", MediaFormat.M4A, AudioStream.UNKNOWN_BITRATE)
        assertEquals("139", chooseAudioStream(streams, smallest = true)?.id)
    }

    @Test
    fun `a video's other audio tracks are left alone`() {
        val streams = listOf(
            stream("251-original", MediaFormat.WEBMA_OPUS, 160, track = "en.4"),
            stream("249-original", MediaFormat.WEBMA_OPUS, 50, track = "en.4"),
            stream("249-dubbed", MediaFormat.WEBMA_OPUS, 32, track = "de.3"),
        )
        assertEquals("249-original", chooseAudioStream(streams, smallest = true)?.id)
    }

    @Test
    fun `nothing to play is nothing, and a stream with no address is not one`() {
        assertNull(chooseAudioStream(emptyList(), smallest = true))
        assertNull(chooseAudioStream(listOf(stream("blank", MediaFormat.M4A, 48, content = "")), smallest = false))
        assertEquals("140", chooseAudioStream(listOf(stream("blank", MediaFormat.M4A, 48, content = ""), youTube[4]), smallest = true)?.id)
    }

    @Test
    fun `the saver is off, always on, or on while the connection is paid for`() {
        assertFalse(savesData(DataSaver.OFF) { error("not asked when off") })
        assertTrue(savesData(DataSaver.ALWAYS) { error("not asked when always") })
        assertTrue(savesData(DataSaver.ON_MOBILE_DATA) { true })
        assertFalse(savesData(DataSaver.ON_MOBILE_DATA) { false })
    }
}
