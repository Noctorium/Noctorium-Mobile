package app.noctorium.android

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import app.noctorium.playback.Loudness
import app.noctorium.playback.VolumeBoost
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The booster as Media3 runs it on the phone: core's arithmetic behind Media3's processor interface, fed the
 * way the sink feeds it -- buffers in, output taken, the end of the stream drained.
 */
class BoostAudioProcessorTest {
    private val rate = 48_000

    /** Everything that comes out for [input], fed in blocks of 4,096 bytes, to the end of the stream. */
    private fun run(processor: AudioProcessor, input: ByteBuffer): ByteBuffer {
        val out = ByteBuffer.allocate(input.remaining() * 2).order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) {
            val block = input.slice().order(ByteOrder.nativeOrder())
            block.limit(minOf(4_096, input.remaining()))
            processor.queueInput(block)
            input.position(input.position() + (block.position()))
            out.put(processor.output)
        }
        processor.queueEndOfStream()
        while (!processor.isEnded) out.put(processor.output)
        out.flip()
        return out
    }

    private fun configured(encoding: Int, percent: Int, volume: Float = 1f) = BoostAudioProcessor().apply {
        configure(AudioFormat(rate, 2, encoding))
        setLevel(percent, volume)
        flush()
    }

    private fun shorts(samples: FloatArray): ByteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
        samples.forEach { putShort((it * 32_767).toInt().toShort()) }
        flip()
    }

    private fun floatsOf(buffer: ByteBuffer, encoding: Int): FloatArray = if (encoding == C.ENCODING_PCM_FLOAT) {
        FloatArray(buffer.remaining() / 4) { buffer.getFloat(it * 4) }
    } else {
        FloatArray(buffer.remaining() / 2) { buffer.getShort(it * 2) / 32_768f }
    }

    /** Loud, dense stereo: a full-scale chord with noise on it, as a mastered record is at its loudest. */
    private fun loud(seconds: Int = 4): FloatArray {
        val random = Random(5)
        return FloatArray(rate * seconds * 2) { index ->
            val t = (index / 2).toDouble() / rate
            ((sin(2 * PI * 55 * t) + sin(2 * PI * 220 * t) + .6 * sin(2 * PI * 330 * t)) / 2.6 * .9 + (random.nextFloat() - .5f) * .1).toFloat()
        }
    }

    @Test
    fun `off, sixteen-bit sound comes out exactly as it went in, all of it`() {
        val input = shorts(loud(1))
        val samples = input.remaining() / 2
        val out = run(configured(C.ENCODING_PCM_16BIT, 100), input.duplicate())
        // Five milliseconds late: that much silence first, and the look-ahead's last five drained at the end.
        val latency = 239 * 2
        assertEquals(samples + latency, out.remaining() / 2, "nothing held back at the end")
        val original = ShortArray(samples) { input.getShort(it * 2) }
        val heard = ShortArray(samples + latency) { out.getShort(it * 2) }
        assertTrue(heard.copyOfRange(0, latency).all { it == 0.toShort() })
        assertContentEquals(original, heard.copyOfRange(latency, samples + latency))
    }

    @Test
    fun `on loud music every level is louder, and nothing passes the ceiling`() {
        val music = loud()
        val readings = listOf(100, 200, 400, 1_000).associateWith { percent ->
            val out = floatsOf(run(configured(C.ENCODING_PCM_16BIT, percent), shorts(music)), C.ENCODING_PCM_16BIT)
            if (percent > 100) assertTrue(out.maxOf { abs(it) } <= VolumeBoost.CEILING + 1f / 32_768, "$percent%")
            Loudness.integrated(out, 2, rate)
        }
        println("phone processor, LUFS by level: $readings")
        // How much each step adds depends on the music (core measures that on music); every step adds, and in all a lot.
        assertTrue(readings.getValue(200) > readings.getValue(100) + 2, "$readings")
        assertTrue(readings.getValue(400) > readings.getValue(200) + .5, "$readings")
        assertTrue(readings.getValue(1_000) > readings.getValue(400) + .5, "$readings")
        assertTrue(readings.getValue(1_000) > readings.getValue(100) + 5, "$readings")
    }

    @Test
    fun `float sound is boosted too, and the volume is used first`() {
        val quiet = FloatArray(rate * 2 * 2) { (sin(2 * PI * 440 * (it / 2) / rate) * .2).toFloat() }
        val input = ByteBuffer.allocate(quiet.size * 4).order(ByteOrder.nativeOrder()).apply { quiet.forEach(::putFloat); flip() }
        // At half volume, 200% is unity: the sound comes out as it went in, with the player at full volume.
        val out = floatsOf(run(configured(C.ENCODING_PCM_FLOAT, 200, volume = .5f), input), C.ENCODING_PCM_FLOAT)
        assertEquals(Loudness.integrated(quiet, 2, rate), Loudness.integrated(out, 2, rate), absoluteTolerance = .05)
    }

    @Test
    fun `a format it cannot work on is passed by`() {
        val processor = BoostAudioProcessor()
        processor.configure(AudioFormat(rate, 2, C.ENCODING_PCM_24BIT))
        processor.flush()
        assertFalse(processor.isActive)
        val active = BoostAudioProcessor()
        active.configure(AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        active.flush()
        assertTrue(active.isActive, "in the path whenever it can be, boosting or not, so switching it on is a fade")
    }

    @Test
    fun `the debug build's tap sees what went in and what came out`() {
        val processor = configured(C.ENCODING_PCM_16BIT, 400)
        var seen = 0
        processor.tap = BoostTap { input, output, samples, channels, sampleRate ->
            assertEquals(2, channels)
            assertEquals(rate, sampleRate)
            assertTrue((0 until samples).any { input[it] != output[it] })
            seen += samples
        }
        run(processor, shorts(loud(1)))
        assertEquals(rate * 2, seen)
    }
}
