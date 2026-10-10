package app.noctorium.android

import app.noctorium.playback.AudioAnalysisKind
import app.noctorium.playback.Visualiser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone's visualiser feed: the sound as it passes, measured the way every player measures it, so the bars stand
 * at the same heights for the same song wherever it plays -- and nothing kept while nobody is watching.
 */
class AudioMeterTest {

    private val rate = 48_000

    /** [seconds] of a sine at [hz], [amplitude] of full scale, as 16-bit PCM in [channels] channels. */
    private fun sine(hz: Double, amplitude: Double = 1.0, seconds: Double = .5, channels: Int = 2, right: Double = amplitude): ByteBuffer {
        val frames = (rate * seconds).toInt()
        val buffer = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            val value = sin(2 * PI * hz * frame / rate)
            buffer.putShort((value * amplitude * 32_767).roundToInt().toShort())
            if (channels == 2) buffer.putShort((value * right * 32_767).roundToInt().toShort())
        }
        buffer.flip()
        return buffer
    }

    private fun meterWith(buffer: ByteBuffer, channels: Int = 2): AudioMeter = AudioMeter().apply {
        listening = true
        begin(rate, channels)
        write(buffer, PcmEncoding.PCM_16)
    }

    @Test
    fun `a full-scale sine stands at -3 dB in its own band, as mpv's crossover puts it`() {
        // 1 kHz is the 1k band's centre: the sixth of ten.
        val meter = meterWith(sine(1_000.0))
        val frame = meter.analyse(meter.written, spectrum = true)!!
        assertEquals(AudioAnalysisKind.SPECTRUM, frame.kind)
        val band = Visualiser.bandOf(1_000f)
        assertEquals(Visualiser.level(-3f), frame.bands[band], .02f)
        // The rest of the spectrum is a long way down: only the window's leakage reaches the next band at all.
        frame.bands.forEachIndexed { index, level -> if (abs(index - band) > 1) assertTrue(level < .3f, "band $index at $level") }
    }

    @Test
    fun `half the amplitude is six decibels lower`() {
        val loud = meterWith(sine(250.0)).let { it.analyse(it.written, true)!! }
        val quiet = meterWith(sine(250.0, amplitude = .5)).let { it.analyse(it.written, true)!! }
        val band = Visualiser.bandOf(250f)
        // Six decibels is a tenth of the sixty the visualiser's scale spans.
        assertEquals(.1f, loud.bands[band] - quiet.bands[band], .01f)
    }

    @Test
    fun `each channel has its own level, left first`() {
        val meter = meterWith(sine(440.0, amplitude = 1.0, right = .25))
        val frame = meter.analyse(meter.written, spectrum = false)!!
        assertEquals(AudioAnalysisKind.LEVELS, frame.kind)
        assertTrue(frame.bands.isEmpty(), "a level meter is not given bands it did not ask for")
        assertEquals(1f, frame.peaks[0], .01f)
        assertEquals(Visualiser.level(-12f), frame.peaks[1], .01f)
        // A sine's RMS is 3 dB under its peak.
        assertEquals(Visualiser.level(-3f), frame.rms[0], .01f)
        assertEquals(Visualiser.level(-15f), frame.rms[1], .01f)
    }

    @Test
    fun `mono sound has one level`() {
        val meter = meterWith(sine(440.0, channels = 1), channels = 1)
        val frame = meter.analyse(meter.written, spectrum = false)!!
        assertEquals(1, frame.peaks.size)
        assertEquals(1, frame.rms.size)
    }

    @Test
    fun `silence is all at the floor`() {
        val meter = meterWith(sine(440.0, amplitude = 0.0))
        val frame = meter.analyse(meter.written, spectrum = true)!!
        assertTrue(frame.bands.all { it == 0f })
        assertTrue(frame.peaks.all { it == 0f } && frame.rms.all { it == 0f })
    }

    @Test
    fun `nothing is kept while nobody is listening`() {
        val meter = AudioMeter()
        meter.begin(rate, 2)
        meter.write(sine(440.0), PcmEncoding.PCM_16)
        assertEquals(0, meter.written)
        assertNull(meter.analyse(0, spectrum = true))
    }

    @Test
    fun `the buffer handed over is left as it was`() {
        val buffer = sine(440.0)
        val before = buffer.position() to buffer.limit()
        meterWith(buffer)
        assertEquals(before, buffer.position() to buffer.limit())
    }

    @Test
    fun `float and 24-bit samples are read as well as 16-bit`() {
        val frames = rate / 4
        val floats = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        val packed = ByteBuffer.allocate(frames * 3).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            val value = .5 * sin(2 * PI * 440.0 * frame / rate)
            floats.putFloat(value.toFloat())
            val int = (value * 8_388_607).roundToInt()
            packed.put((int and 0xFF).toByte()).put((int shr 8 and 0xFF).toByte()).put((int shr 16).toByte())
        }
        floats.flip()
        packed.flip()
        listOf(floats to PcmEncoding.FLOAT, packed to PcmEncoding.PCM_24).forEach { (buffer, encoding) ->
            val meter = AudioMeter().apply { listening = true; begin(rate, 1); write(buffer, encoding) }
            assertEquals(Visualiser.level(-6.02f), meter.analyse(meter.written, false)!!.peaks[0], .01f, "$encoding")
        }
    }

    @Test
    fun `the frame heard follows the song's position from where its stretch began`() {
        val clock = MeterClock()
        // A song starts at 10 s into it; 0.3 s of sound has gone ahead of the speaker.
        val start = 1_000L
        val heardAtStart = clock.heard(10_000, stretches = 1, stretchStart = start, written = start + 14_400, sampleRate = rate, ringSize = AudioMeter.RING_FRAMES)
        assertEquals(start, heardAtStart)
        val later = clock.heard(10_500, stretches = 1, stretchStart = start, written = start + 38_400, sampleRate = rate, ringSize = AudioMeter.RING_FRAMES)
        assertEquals(start + 24_000, later)
    }

    @Test
    fun `a song starting over by itself is followed by how far ahead the sound runs`() {
        val clock = MeterClock()
        clock.heard(0, 1, 0, 14_400, rate, AudioMeter.RING_FRAMES)
        clock.heard(1_000, 1, 0, 62_400, rate, AudioMeter.RING_FRAMES)
        // Repeat-one: the position goes back to the top while the frames carry on.
        val heard = clock.heard(0, 1, 0, 400_000, rate, AudioMeter.RING_FRAMES)
        val lead = 400_000 - heard
        assertTrue(lead in 10_000..20_000, "lead $lead frames")
    }

    @Test
    fun `the FFT finds a sine in its own bin`() {
        val fft = Fft(64)
        for (i in 0 until 64) {
            fft.real[i] = sin(2 * PI * 5 * i / 64).toFloat()
            fft.imaginary[i] = 0f
        }
        fft.transform()
        val magnitudes = (0 until 32).map { kotlin.math.hypot(fft.real[it].toDouble(), fft.imaginary[it].toDouble()) }
        assertEquals(5, magnitudes.indices.maxBy { magnitudes[it] })
        assertEquals(32.0, magnitudes[5], .001)
    }
}
