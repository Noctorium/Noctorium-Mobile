package app.noctorium.android

import app.noctorium.settings.Equalizer
import app.noctorium.settings.EqualizerSettings
import kotlin.math.roundToInt

/**
 * The listener's ten-band curve, as levels for whatever bands this phone's own equaliser has.
 *
 * Android's equaliser is not ten bands an octave apart. Most phones offer five, at centres the
 * manufacturer chose -- 60 Hz, 230 Hz, 910 Hz, 3.6 kHz and 14 kHz is common -- and some offer more. So each
 * of the phone's bands is set to what the curve is at its own centre, read off the curve by
 * [Equalizer.gainAt], with the preamp on top, since the phone's equaliser has no preamp of its own.
 *
 * A curve that lifts anything is also given half its highest lift back as headroom, taken off every band,
 * which is what the desktop does in front of mpv's bands. Most of what a curve lifts is quieter than the
 * loudest part of the record, so half is enough to keep a boosted preset from clipping, and it costs a
 * curve that only cuts nothing at all. The desktop puts a limiter after its bands for whatever still
 * reaches the ceiling; Android's equaliser has none to put there, which makes the headroom matter more
 * here, not less. The highest lift is read from the ten bands the listener set, not from the phone's own,
 * so the same curve is turned down by the same amount on both.
 *
 * Android counts centres in thousandths of a hertz and levels in hundredths of a decibel, and every phone
 * has a range of its own past which it refuses a level outright. A level outside it is clamped to the edge
 * rather than refused: a preamp that lifts beyond what the phone can do comes out as much as it can do,
 * which is the nearest thing to what was asked for.
 *
 * Kept apart from the effect itself, and free of anything Android, so it can be checked on the JVM.
 *
 * @param centresMilliHz the centre of each of the phone's bands, as `Equalizer.getCenterFreq` gives them.
 * @param minMillibels the bottom of the phone's band level range.
 * @param maxMillibels the top of it.
 */
internal fun deviceBandLevels(
    settings: EqualizerSettings,
    centresMilliHz: List<Int>,
    minMillibels: Int,
    maxMillibels: Int,
): List<Short> {
    val gains = settings.gains
    // The curve is normalised by the core; the preamp is not, and a hand-edited settings file is no reason
    // for the audio to fail -- roundToInt throws on a value that is not a number.
    val preamp = settings.preampDb.takeIf { it.isFinite() } ?: 0f
    val headroom = (gains.maxOrNull() ?: 0f).coerceAtLeast(0f) / 2f
    val low = minOf(minMillibels, maxMillibels)
    val high = maxOf(minMillibels, maxMillibels)
    return centresMilliHz.map { centre ->
        val decibels = Equalizer.gainAt(gains, centre / 1_000.0) + preamp - headroom
        (decibels * 100).roundToInt().coerceIn(low, high).toShort()
    }
}
