package app.noctorium.android.ui

import app.noctorium.settings.EqualizerPreset
import app.noctorium.settings.EqualizerSettings
import app.noctorium.settings.parseHexColour
import app.noctorium.settings.toHexColour
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The accent picker's sums: hue, saturation and brightness to the colour the settings keep, and back, and
 * the hex the box beside it shows. A picker whose numbers drift a step every time they go round would walk
 * the accent somewhere else each time it was opened.
 */
class ColoursTest {

    @Test
    fun `the primaries come out as themselves`() {
        assertEquals(0xFFFF0000L, Hsv(0f, 1f, 1f).toArgb())
        assertEquals(0xFF00FF00L, Hsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FFL, Hsv(240f, 1f, 1f).toArgb())
        assertEquals(0xFFFFFFFFL, Hsv(0f, 0f, 1f).toArgb())
        assertEquals(0xFF000000L, Hsv(200f, 1f, 0f).toArgb())
    }

    @Test
    fun `a hue of 360 is red again, and every colour made is opaque`() {
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(360f, 1f, 1f).toArgb())
        listOf(Hsv(30f, .5f, .5f), Hsv(300f, .2f, .9f), Hsv(-60f, 1f, 1f)).forEach {
            assertEquals(0xFF000000L, it.toArgb() and 0xFF000000L)
        }
    }

    @Test
    fun `a stored colour reads back as the numbers that make it`() {
        val violet = argbToHsv(0xFFB47CFF)
        assertTrue(abs(violet.hue - 265.6f) < .5f, "hue was ${violet.hue}")
        assertTrue(abs(violet.saturation - .514f) < .01f, "saturation was ${violet.saturation}")
        assertEquals(1f, violet.value)
    }

    /** Every colour on a coarse grid, through the picker's numbers and back, lands on itself exactly. */
    @Test
    fun `any colour goes round the picker and comes back unchanged`() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            assertEquals(argb.toHexColour(), argbToHsv(argb).toArgb().toHexColour())
        }
    }

    @Test
    fun `a grey keeps the hue the picker was showing`() {
        assertEquals(212f, argbToHsv(0xFF808080, keepHue = 212f).hue)
        assertEquals(0f, argbToHsv(0xFF808080, keepHue = 212f).saturation)
        assertEquals(97f, argbToHsv(0xFF000000, keepHue = 97f).hue)
    }

    @Test
    fun `the alpha of a stored colour does not change what the picker shows`() {
        assertEquals(argbToHsv(0xFF5AB2FF), argbToHsv(0x105AB2FF))
    }

    @Test
    fun `hex typed in goes through the picker and back as the same hex`() {
        val typed = parseHexColour("#b47cff")!!
        assertEquals("#B47CFF", argbToHsv(typed).toArgb().toHexColour())
    }

    @Test
    fun `decibels read the way an equaliser prints them`() {
        assertEquals("0", decibelsLabel(0f))
        assertEquals("+3", decibelsLabel(3f))
        assertEquals("-2.5", decibelsLabel(-2.5f))
        assertEquals("+12", decibelsLabel(12f))
        // Finer than half a decibel is rounded to the nearest half.
        assertEquals("+1.5", decibelsLabel(1.4f))
        assertEquals("0", decibelsLabel(-.2f))
    }

    @Test
    fun `the Sound tile says what the equaliser is doing in a few words`() {
        assertEquals("Equaliser off", equalizerSummary(EqualizerSettings(), available = true))
        assertEquals(
            "Equaliser: Rock",
            equalizerSummary(EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK), available = true),
        )
        assertEquals(
            "Equaliser: Flat · preamp -3 dB",
            equalizerSummary(EqualizerSettings(enabled = true, preampDb = -3f), available = true),
        )
        assertEquals("This phone has no equaliser for apps", equalizerSummary(EqualizerSettings(enabled = true), available = false))
    }
}
