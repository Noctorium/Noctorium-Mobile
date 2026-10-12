package app.noctorium.android

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.noctorium.playback.BoostProcessor
import app.noctorium.playback.VolumeBoost
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Something that sees the booster's sound go by -- what came in and what went out, interleaved floats -- for the
 * debug build's measurements. Nothing in a release build sets one.
 */
internal fun interface BoostTap {
    fun heard(input: FloatArray, output: FloatArray, samples: Int, channels: Int, sampleRate: Int)
}

/**
 * The volume booster on the phone: core's [BoostProcessor] as one of Media3's audio processors, so it works on
 * the sound itself on its way to the speaker, as mpv's filters do on the desktop.
 *
 * It replaced Android's LoudnessEnhancer, which is what made the phone's booster barely louder. That effect is a
 * fixed compressor -- seven to one above -8 to -10 dBFS, with an attack of a millisecond -- behind the gain it is
 * given, and run on music already mastered loud, as Android's own source has it, it made the music 2 LU *quieter*
 * at 200% and still quieter at 1000%: the gain went in and the compressor took more than that back out. It also
 * sits after the phone's volume, out of reach of anything that could measure it at volume 0. Here the boost is
 * the same everywhere: the listener's volume first, then a look-ahead limiter and a soft clipper (see
 * [VolumeBoost]), and what comes out can be measured in the app, whatever the phone's volume.
 *
 * Takes 16-bit and float PCM, which is all Media3 hands its processors, and passes anything else by. Always in the
 * path once configured, boosting or not, so that switching the booster on is a fade and never a reconfiguration;
 * at 100% it only delays the sound by its five-millisecond look-ahead.
 */
@OptIn(UnstableApi::class)
internal class BoostAudioProcessor : BaseAudioProcessor() {
    @Volatile private var percent = VolumeBoost.OFF_PERCENT
    @Volatile private var volume = 1f
    private var booster: BoostProcessor? = null
    private var samples = FloatArray(0)
    private var copied = FloatArray(0)

    /** The look-ahead's last few milliseconds, owed once the stream has ended and the output before them taken. */
    private var tailDue = false

    /** See [BoostTap]; null unless the debug build is measuring. */
    @Volatile var tap: BoostTap? = null

    /** The level to boost to, and the listener's volume (0 to 1) to put it on top of: see [BoostProcessor.setLevel]. */
    fun setLevel(percent: Int, volume: Float) {
        this.percent = percent
        this.volume = volume
        booster?.setLevel(percent, volume)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }

    override fun onFlush() {
        val format = inputAudioFormat
        tailDue = false
        if (format.encoding != C.ENCODING_PCM_16BIT && format.encoding != C.ENCODING_PCM_FLOAT) {
            booster = null
            return
        }
        val kept = booster?.takeIf { it.sampleRate == format.sampleRate && it.channels == format.channelCount }
        booster = (kept ?: BoostProcessor(format.sampleRate, format.channelCount)).apply {
            setLevel(percent, volume)
            reset()
        }
    }

    override fun onReset() {
        booster = null
        tailDue = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val booster = booster ?: run {
            // Not configured for this sound: it goes by as it came.
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val bytes = if (float) 4 else 2
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val count = input.remaining() / bytes / format.channelCount * format.channelCount
        if (count == 0) {
            input.position(input.limit())
            return
        }
        if (samples.size < count) samples = FloatArray(count)
        var at = input.position()
        for (index in 0 until count) {
            samples[index] = if (float) input.getFloat(at) else input.getShort(at) / 32_768f
            at += bytes
        }
        input.position(input.limit())
        val watching = tap
        if (watching != null) {
            if (copied.size < count) copied = FloatArray(count)
            System.arraycopy(samples, 0, copied, 0, count)
        }
        booster.process(samples, 0, count / format.channelCount)
        watching?.heard(copied, samples, count, format.channelCount, format.sampleRate)
        write(samples, count, float)
    }

    /**
     * The end of the stream: what the look-ahead still holds comes out, so the song is not five milliseconds short.
     * Once whatever is waiting to be taken has been: writing it now would put it in that output's place.
     */
    override fun onQueueEndOfStream() {
        if (hasPendingOutput()) tailDue = true else writeTail()
    }

    override fun getOutput(): ByteBuffer {
        if (tailDue && !hasPendingOutput()) {
            tailDue = false
            writeTail()
        }
        return super.getOutput()
    }

    override fun isEnded(): Boolean = super.isEnded() && !tailDue

    private fun writeTail() {
        val booster = booster ?: return
        val count = booster.latencyFrames * booster.channels
        if (samples.size < count) samples = FloatArray(count)
        samples.fill(0f, 0, count)
        booster.process(samples, 0, booster.latencyFrames)
        write(samples, count, inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
    }

    private fun write(source: FloatArray, count: Int, float: Boolean) {
        val output = replaceOutputBuffer(count * if (float) 4 else 2)
        for (index in 0 until count) {
            if (float) {
                output.putFloat(source[index])
            } else {
                output.putShort((source[index] * 32_768f).roundToInt().coerceIn(-32_768, 32_767).toShort())
            }
        }
        output.flip()
    }
}
