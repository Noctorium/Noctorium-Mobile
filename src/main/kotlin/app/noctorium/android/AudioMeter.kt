package app.noctorium.android

import app.noctorium.playback.AudioAnalysis
import app.noctorium.playback.Visualiser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** How the samples copied to the meter are written: Media3's PCM encodings, as far as the meter cares. */
internal enum class PcmEncoding(val bytes: Int) { PCM_16(2), PCM_24(3), PCM_32(4), FLOAT(4) }

/**
 * What the phone's visualiser is drawn from: the sound itself, copied as it passes on its way to the speaker.
 *
 * Media3 hands every buffer of decoded sound to a tee before it goes on (see [Media3PlaybackEngine]); the tee
 * gives a read-only view of it here, untouched, and the sound carries on exactly as it was. This keeps the last
 * second and a half of it, channel by channel, and [analyse] measures whatever stretch of that is being heard
 * right now: an FFT for the bars, the peak and the RMS of each channel for a level meter, both in the numbers
 * every player uses ([Visualiser]), so the phone's bars stand where the desktop's do for the same song.
 *
 * Nothing is kept while nobody is [listening]: the tee then costs one look at a flag per buffer. Written on the
 * player's own thread and read on another without a lock -- a frame read while a buffer is half written is a
 * frame of a visualiser a few samples out, which nobody can see -- so the counters are volatile and nothing more.
 *
 * Android's own `Visualizer` is not used: it needs the microphone permission to see what the phone itself is
 * playing, and asking somebody for their microphone to draw some bars would be the wrong thing to ask.
 */
internal class AudioMeter(private val ringSize: Int = RING_FRAMES) {
    private val left = FloatArray(ringSize)
    private val right = FloatArray(ringSize)

    /** Whether anything wants frames. While false, [write] keeps nothing. */
    @Volatile var listening: Boolean = false

    /**
     * What the samples are multiplied by as they are kept. The meter sees the sound after the booster, which carries
     * the listener's volume while it is on; this takes the volume back out, so the bars read the same either way.
     */
    @Volatile var gain: Float = 1f

    /** Frames ever kept, across every stretch: the position the next frame is written at. */
    @Volatile var written: Long = 0
        private set

    /** The sound's rate and channels, as the last stretch began. */
    @Volatile var sampleRate: Int = 0
        private set

    @Volatile var channels: Int = 0
        private set

    /** Where the current stretch began, in [written]'s count: a new song, a seek, a change of format. */
    @Volatile var stretchStart: Long = 0
        private set

    /** How many stretches there have been, so a reader can tell a new one has begun. */
    @Volatile var stretches: Int = 0
        private set

    private val fft = Fft(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }
    private val windowPower = window.sumOf { (it * it).toDouble() }

    /** A new stretch of sound: what follows is from somewhere else in the song, or another song. */
    fun begin(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
        stretchStart = written
        stretches++
    }

    /** Keeps the frames in [buffer], from its position to its limit, leaving the buffer itself as it was. */
    fun write(buffer: ByteBuffer, encoding: PcmEncoding) {
        if (!listening) return
        val count = channels
        if (count <= 0) return
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = encoding.bytes * count
        var at = data.position()
        val end = data.limit()
        var next = written
        val gain = this.gain
        while (at + frameBytes <= end) {
            val l = sample(data, at, encoding)
            val r = if (count >= 2) sample(data, at + encoding.bytes, encoding) else l
            val slot = (next % ringSize).toInt()
            left[slot] = l * gain
            right[slot] = r * gain
            next++
            at += frameBytes
        }
        written = next
    }

    /**
     * The sound as it was in the frames up to [heard] (in [written]'s count): each channel's peak and RMS over
     * the last [LEVEL_WINDOW_MS], and -- when [spectrum] is asked for -- the ten bands from an FFT of the last
     * [FFT_SIZE] frames. Null with nothing kept to measure.
     */
    fun analyse(heard: Long, spectrum: Boolean): AudioAnalysis? {
        val rate = sampleRate
        val total = written
        if (rate <= 0 || total <= 0) return null
        val oldest = (total - ringSize + 1).coerceAtLeast(0)
        val end = heard.coerceIn(oldest, total)
        val stereo = channels >= 2

        val levelFrames = (rate * LEVEL_WINDOW_MS / 1_000).coerceIn(1, ringSize)
        val levelStart = (end - levelFrames).coerceAtLeast(oldest)
        var peakL = 0f
        var peakR = 0f
        var sumL = 0.0
        var sumR = 0.0
        var frames = 0
        for (frame in levelStart until end) {
            val slot = (frame % ringSize).toInt()
            val l = left[slot]
            val r = right[slot]
            peakL = maxOf(peakL, abs(l))
            peakR = maxOf(peakR, abs(r))
            sumL += l * l
            sumR += r * r
            frames++
        }
        if (frames == 0) return null
        val peaks = if (stereo) listOf(level(peakL), level(peakR)) else listOf(level(peakL))
        val rms = if (stereo) {
            listOf(level(sqrt(sumL / frames).toFloat()), level(sqrt(sumR / frames).toFloat()))
        } else {
            listOf(level(sqrt(sumL / frames).toFloat()))
        }
        if (!spectrum) return AudioAnalysis(peaks = peaks, rms = rms)
        return AudioAnalysis(bands = bands(end, oldest, rate), peaks = peaks, rms = rms)
    }

    /** The ten bands of the [FFT_SIZE] frames before [end], the two channels folded to one first. */
    private fun bands(end: Long, oldest: Long, rate: Int): List<Float> {
        val real = fft.real
        val imaginary = fft.imaginary
        val start = end - FFT_SIZE
        for (i in 0 until FFT_SIZE) {
            val frame = start + i
            val value = if (frame < oldest) {
                0f
            } else {
                val slot = (frame % ringSize).toInt()
                (left[slot] + right[slot]) * .5f
            }
            real[i] = value * window[i]
            imaginary[i] = 0f
        }
        fft.transform()
        // Each bin's share of the mean square, as Visualiser.bandLevels wants them: 2|X|² / (N·Σw²).
        val scale = (2.0 / (FFT_SIZE * windowPower)).toFloat()
        val bins = FloatArray(FFT_SIZE / 2) { k -> (real[k] * real[k] + imaginary[k] * imaginary[k]) * scale }
        return Visualiser.bandLevels(bins, rate.toFloat() / FFT_SIZE)
    }

    companion object {
        /** About a second and a half of sound at 44.1 or 48 kHz: more than the sound ever runs ahead of the speaker. */
        const val RING_FRAMES = 65_536

        /** The FFT's length: a tenth of a second, enough for the lowest band to have bins of its own. */
        const val FFT_SIZE = 4_096

        /** How much of the sound a level is measured over: a little more than a frame of the visualiser. */
        const val LEVEL_WINDOW_MS = 50

        /** [amplitude] (0..1 of full scale) as a height, through the decibels every player uses. */
        fun level(amplitude: Float): Float =
            if (amplitude <= 0f) 0f else Visualiser.level(20f * log10(amplitude))

        private fun sample(data: ByteBuffer, at: Int, encoding: PcmEncoding): Float = when (encoding) {
            PcmEncoding.PCM_16 -> data.getShort(at) / 32_768f
            PcmEncoding.PCM_24 -> {
                val value = (data.get(at).toInt() and 0xFF) or
                    ((data.get(at + 1).toInt() and 0xFF) shl 8) or
                    (data.get(at + 2).toInt() shl 16)
                value / 8_388_608f
            }
            PcmEncoding.PCM_32 -> data.getInt(at) / 2_147_483_648f
            PcmEncoding.FLOAT -> data.getFloat(at)
        }
    }
}

/**
 * Where the speaker is in the sound the meter has kept: the frame being heard now, from the player's position.
 *
 * The tee sees the sound before the speaker does -- by however much Android keeps in its buffers, a quarter of a
 * second or so -- and drawing the newest frames would put the bars ahead of the beat. So frames are counted
 * against the song: when a stretch begins (a song starts, a seek lands) the player's position then is where its
 * first frame sits in the song, and every frame after it is a sample further on. The frame heard is then the
 * player's position now, in frames from there.
 *
 * Where that cannot be right -- a song repeating by itself goes back to its start without a new stretch, and the
 * position jumps back while the frames go on -- it falls back on how far ahead the sound has been running, which
 * it keeps track of while the counting works.
 */
internal class MeterClock {
    private var stretch = -1
    private var startFrame = 0L
    private var startMs = 0L

    /** How far the newest frame has run ahead of the one heard, in frames, smoothed. */
    private var leadFrames = -1.0

    /**
     * Whether the meter has only just started keeping frames, part of the way into a stretch: its first frame is
     * then the newest the tee has seen, already ahead of the speaker, not the stretch's first.
     */
    private var joinedLate = true

    /** The frame heard at [positionMs] into the song, given the meter's counts. */
    fun heard(
        positionMs: Long,
        stretches: Int,
        stretchStart: Long,
        written: Long,
        sampleRate: Int,
        ringSize: Int,
    ): Long {
        if (sampleRate <= 0) return written
        if (stretches != stretch) {
            stretch = stretches
            startMs = positionMs
            startFrame = if (joinedLate) {
                // Only frames from now on are kept, and they run ahead of the speaker by Android's buffer.
                (written - aheadFrames(sampleRate)).coerceAtLeast(stretchStart)
            } else {
                stretchStart
            }
            joinedLate = false
        }
        val heard = startFrame + (positionMs - startMs) * sampleRate / 1_000
        val lead = written - heard
        val sane = lead >= 0 && lead < ringSize - AudioMeter.FFT_SIZE
        if (sane) {
            leadFrames = if (leadFrames < 0) lead.toDouble() else leadFrames * .9 + lead * .1
            return heard
        }
        // The counting has come loose from the song: start it again from how far ahead the sound runs.
        startFrame = (written - aheadFrames(sampleRate)).coerceAtLeast(stretchStart)
        startMs = positionMs
        return startFrame
    }

    /** How far ahead of the speaker the newest frame is taken to be: as measured, or Android's usual buffer. */
    private fun aheadFrames(sampleRate: Int): Long =
        (if (leadFrames >= 0) leadFrames else sampleRate * DEFAULT_LEAD_MS / 1_000.0).toLong()

    /** Starts the counting again, for a meter that has just begun keeping frames part of the way into a song. */
    fun restart() {
        stretch = -1
        joinedLate = true
    }

    private companion object {
        /** How far ahead the sound is taken to run before it has been measured: Android's usual buffer. */
        const val DEFAULT_LEAD_MS = 300
    }
}

/** A radix-2 FFT of a fixed length, in place on [real] and [imaginary]. */
internal class Fft(private val size: Int) {
    val real = FloatArray(size)
    val imaginary = FloatArray(size)
    private val cosines = FloatArray(size / 2) { cos(-2.0 * PI * it / size).toFloat() }
    private val sines = FloatArray(size / 2) { sin(-2.0 * PI * it / size).toFloat() }
    private val reversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "An FFT's length is a power of two" }
    }

    fun transform() {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val m = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = m
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val c = cosines[k * step]
                    val s = sines[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = real[b] * c - imaginary[b] * s
                    val ti = real[b] * s + imaginary[b] * c
                    real[b] = real[a] - tr
                    imaginary[b] = imaginary[a] - ti
                    real[a] += tr
                    imaginary[a] += ti
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
