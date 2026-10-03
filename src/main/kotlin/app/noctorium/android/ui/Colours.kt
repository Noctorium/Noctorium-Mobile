package app.noctorium.android.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A colour as a picker holds it: which hue, how much of it, and how bright.
 *
 * The accent picker works in these rather than in red, green and blue, because they are the three things a
 * person choosing a colour is actually deciding -- "that purple, a bit less washed out, a bit darker" -- and
 * each is one direction on the picker. Hue is in degrees, 0 to 360; the other two run from 0 to 1.
 */
internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** An opaque ARGB colour, as the settings keep it, from a picker's three numbers. */
internal fun Hsv.toArgb(): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val sector = h / 60f
    val x = chroma * (1f - abs(sector % 2f - 1f))
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = v - chroma
    fun channel(c: Float): Long = ((c + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

/**
 * The picker's three numbers for a stored colour, ignoring its alpha.
 *
 * A grey has no hue of its own, so it reads as hue 0; [keepHue] lets the picker keep the hue it was showing
 * instead, or dragging to the white or black corner of the square would snap the hue strip back to red.
 */
internal fun argbToHsv(argb: Long, keepHue: Float = 0f): Hsv {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> keepHue
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return Hsv(hue, saturation, max)
}
