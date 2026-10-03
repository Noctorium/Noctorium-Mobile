package app.noctorium.android

import app.noctorium.settings.Equalizer
import app.noctorium.settings.EqualizerPreset
import app.noctorium.settings.EqualizerSettings
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ten-band curve, laid onto the bands a phone's own equaliser actually has.
 *
 * Android reports its centres in thousandths of a hertz and takes levels in hundredths of a decibel, inside
 * a range of its own; getting either unit wrong by a thousand is silent, and sounds like either nothing or
 * a blown speaker. And a curve that lifts anything is turned down by half its highest lift, as the
 * desktop's is, so a boosted preset does not clip.
 */
class EqualizerBandsTest {

    /** The five bands a great many phones offer, in milliHertz, and the commonest range, in millibels. */
    private val fiveBands = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
    private val low = -1_500
    private val high = 1_500

    private fun custom(gains: List<Float>, preamp: Float = 0f) =
        EqualizerSettings(enabled = true, preset = EqualizerPreset.CUSTOM, customGains = gains, preampDb = preamp)

    @Test
    fun `a flat curve with no preamp leaves every band of the phone at nothing`() {
        val levels = deviceBandLevels(EqualizerSettings(enabled = true), fiveBands, low, high)
        assertEquals(List<Short>(5) { 0 }, levels)
    }

    @Test
    fun `a phone band at one of ours takes that band's gain, less the headroom, in millibels`() {
        val rock = EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK)
        // Rock is -1 dB at 1 kHz and +1 dB at 250 Hz, and lifts +5 at most, so 2.5 dB comes off every band.
        assertEquals(listOf<Short>(-350, -150), deviceBandLevels(rock, listOf(1_000_000, 250_000), low, high))
    }

    /** Between two of ours the curve is read on a doubling scale, the way hearing and every equaliser lays it out. */
    @Test
    fun `a phone band between two of ours is read off the curve on a logarithmic scale`() {
        val bass = EqualizerSettings(enabled = true, preset = EqualizerPreset.BASS_BOOST)
        // 60 Hz lies between 31 Hz (+6) and 62 Hz (+5), nearly at the upper one: a little over +5. Bass
        // boost's highest lift is +6, so 3 dB of headroom comes off it.
        val t = ln(60.0 / 31) / ln(62.0 / 31)
        val expected = ((6 + (5 - 6) * t - 3) * 100).roundToInt().toShort()
        assertEquals(expected, deviceBandLevels(bass, fiveBands, low, high).first())
        assertEquals(205.toShort(), expected)
    }

    /** Half the highest lift, the same sum the desktop does in front of mpv's bands. */
    @Test
    fun `a curve that lifts is given half its highest lift back as headroom on every band`() {
        val lifted = custom(listOf(0f, 0f, 0f, 0f, 0f, 8f, 0f, 0f, 0f, 0f))
        // +8 at 1 kHz: 4 dB off everything, so 1 kHz plays at +4 and the flat parts at -4.
        assertEquals(listOf<Short>(400, -400), deviceBandLevels(lifted, listOf(1_000_000, 125_000), low, high))
    }

    @Test
    fun `a curve that only cuts is given no headroom, since it cannot clip`() {
        val less = EqualizerSettings(enabled = true, preset = EqualizerPreset.BASS_REDUCER)
        // Less bass is 0 at 1 kHz and below zero everywhere else: nothing comes off.
        assertEquals(listOf<Short>(0, -400), deviceBandLevels(less, listOf(1_000_000, 125_000), low, high))
    }

    /** The same curve is turned down by the same amount on any phone, whatever bands it happens to have. */
    @Test
    fun `the headroom comes from the listener's ten bands, not from the phone's own`() {
        // The lift is at 16 kHz, which this phone has no band near; its 1 kHz band is still turned down.
        val treble = custom(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 8f))
        assertEquals(listOf<Short>(-400), deviceBandLevels(treble, listOf(1_000_000), low, high))
    }

    @Test
    fun `the preamp is added to every band, since the phone's equaliser has no preamp of its own`() {
        val quieter = custom(Equalizer.FLAT, preamp = -3f)
        assertEquals(List<Short>(5) { -300 }, deviceBandLevels(quieter, fiveBands, low, high))

        // +2 at 1 kHz and 1.5 of preamp, less 1 of headroom.
        val lifted = custom(listOf(0f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f), preamp = 1.5f)
        assertEquals(250.toShort(), deviceBandLevels(lifted, listOf(1_000_000), low, high).single())
    }

    @Test
    fun `a level past what the phone allows is clamped to its edge rather than refused`() {
        // +12 everywhere, less 6 of headroom, plus 12 of preamp: +18, past any phone.
        val loud = custom(List(10) { 12f }, preamp = 12f)
        assertEquals(List<Short>(5) { 1_500 }, deviceBandLevels(loud, fiveBands, low, high))
        // A phone with a narrower range gets as much as it can do, and no more.
        assertEquals(List<Short>(5) { 1_000 }, deviceBandLevels(loud, fiveBands, -1_000, 1_000))

        val quiet = custom(List(10) { -12f }, preamp = -6f)
        assertEquals(List<Short>(5) { -1_500 }, deviceBandLevels(quiet, fiveBands, low, high))
    }

    @Test
    fun `a range reported upside down still clamps`() {
        val loud = custom(List(10) { 12f }, preamp = 12f)
        assertEquals(listOf<Short>(1_000), deviceBandLevels(loud, listOf(1_000_000), 1_000, -1_000))
    }

    @Test
    fun `beyond our lowest and highest bands the curve is held flat`() {
        // -4 at the bottom and +5 at the top, with 2.5 of headroom off both.
        val tilted = custom(listOf(-4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 5f))
        assertEquals(listOf<Short>(-650, 250), deviceBandLevels(tilted, listOf(20_000, 20_000_000), low, high))
    }

    @Test
    fun `a phone with more bands than five gets a level for each, in its own order`() {
        val centres = listOf(31_000, 62_000, 125_000, 250_000, 500_000, 1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000)
        val gains = listOf(1f, 2f, 3f, 4f, 5f, -1f, -2f, -3f, -4f, -5f)
        // The highest lift is +5, so every band sits 2.5 dB below what was set.
        assertEquals(gains.map { ((it - 2.5f) * 100).roundToInt().toShort() }, deviceBandLevels(custom(gains), centres, low, high))
    }

    @Test
    fun `a preamp that is not a number is taken as nothing rather than failing`() {
        val broken = custom(Equalizer.FLAT, preamp = Float.NaN)
        assertEquals(List<Short>(5) { 0 }, deviceBandLevels(broken, fiveBands, low, high))
    }
}
