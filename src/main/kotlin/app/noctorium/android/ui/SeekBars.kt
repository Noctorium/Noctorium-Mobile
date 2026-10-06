package app.noctorium.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SeekBar
import app.noctorium.settings.WindowsXpColours
import kotlin.math.min
import kotlin.math.sin

/**
 * Every drawn seek bar, in one place. The desktop's sums, so a style chosen on either looks the same.
 *
 * Kept apart from the composable that owns the gestures because these are the only part that differs
 * between the styles. Seeking, the two times and what counts as an unknown length are written once
 * above and cannot quietly work in one style and not another.
 *
 * [phase] runs 0..1 and is one full wavelength of travel. [amplitude] is 0..1 and is faded rather than
 * stopped, so a paused wave settles into the flat line the other styles draw instead of freezing
 * mid-crest, which reads as a rendering fault rather than as a paused song.
 */
internal fun DrawScope.drawSeekBar(
    style: ProgressBarStyle,
    fraction: Float,
    /** Whether to draw the handle. False while a track is unseekable, where a handle would be a lie. */
    showHead: Boolean,
    track: Color,
    filled: Color,
    phase: Float,
    amplitude: Float,
    /** The song's row of bars and the ruler's ticks, for the two styles drawn from them. */
    shapes: SeekBarShapes = SeekBarShapes.NONE,
    /** How far the neon spark has breathed in, 0..1. Held where it is while the music is paused. */
    pulse: Float = .5f,
    /** A pale page, which the neon brightens less against and Luna's well is white on. */
    pale: Boolean = false,
) {
    val centreY = size.height / 2f
    val head = (size.width * fraction.coerceIn(0f, 1f))
    when (style) {
        ProgressBarStyle.MATERIAL -> Unit // Drawn by Material's own slider, never here.

        ProgressBarStyle.MINIMAL -> {
            val thickness = SeekBar.LINE_DP.dp.toPx()
            drawLine(track, Offset(0f, centreY), Offset(size.width, centreY), thickness, StrokeCap.Round)
            if (head > 0f) {
                drawLine(filled, Offset(0f, centreY), Offset(head, centreY), thickness, StrokeCap.Round)
            }
            if (showHead) drawCircle(filled, SeekBar.DOT_RADIUS_DP.dp.toPx(), Offset(head, centreY))
        }

        ProgressBarStyle.WAVE -> {
            val thickness = SeekBar.LINE_DP.dp.toPx()
            // The part still to come stays flat: a wave on both sides would say nothing about progress.
            drawLine(track, Offset(head, centreY), Offset(size.width, centreY), thickness, StrokeCap.Round)
            if (head > 0f) {
                val peak = SeekBar.WAVE_AMPLITUDE_DP.dp.toPx() * amplitude
                val wavelength = SeekBar.WAVE_LENGTH_DP.dp.toPx().coerceAtLeast(1f)
                val path = Path().apply {
                    moveTo(0f, centreY)
                    var x = 0f
                    while (x <= head) {
                        val turns = (x / wavelength) - phase
                        lineTo(x, centreY + sin(turns * 2f * Math.PI.toFloat()) * peak)
                        x += 2f
                    }
                    lineTo(head, centreY + sin(((head / wavelength) - phase) * 2f * Math.PI.toFloat()) * peak)
                }
                drawPath(path, filled, style = Stroke(width = thickness, cap = StrokeCap.Round))
            }
            if (showHead) drawCircle(filled, SeekBar.DOT_RADIUS_DP.dp.toPx(), Offset(head, centreY))
        }

        ProgressBarStyle.SEGMENTS -> {
            // The count follows the width, so a block is the same size on a phone and in a window.
            val count = (size.width / SeekBar.SEGMENT_PITCH_DP.dp.toPx()).toInt().coerceAtLeast(4)
            val pitch = size.width / count
            val width = (pitch * (1f - SeekBar.SEGMENT_GAP_RATIO)).coerceAtLeast(1f)
            val height = SeekBar.CAPSULE_DP.dp.toPx()
            // A gentle rounding rather than half the width. At this size half the width is a pill, and a
            // row of pills is a row of dots -- which is a nice enough bar but not the one called Segments.
            val corner = height * .25f
            val radius = CornerRadius(corner, corner)
            repeat(count) { index ->
                val left = index * pitch
                // Lit once the head has reached the block rather than once it has passed it: waiting for
                // a block to finish before lighting it reads as running behind the music.
                drawRoundRect(
                    color = if (left < head) filled else track,
                    topLeft = Offset(left, centreY - height / 2f),
                    size = Size(width, height),
                    cornerRadius = radius,
                )
            }
        }

        ProgressBarStyle.CAPSULE -> {
            val height = SeekBar.CAPSULE_DP.dp.toPx()
            val radius = CornerRadius(height / 2f, height / 2f)
            drawRoundRect(track, Offset(0f, centreY - height / 2f), Size(size.width, height), radius)
            if (head > 0f) {
                // Never narrower than it is tall, or the filled end stops being a capsule and becomes a
                // lens: a few seconds into a long track the two corner radii meet and pinch.
                drawRoundRect(
                    color = filled,
                    topLeft = Offset(0f, centreY - height / 2f),
                    size = Size(head.coerceAtLeast(height), height),
                    cornerRadius = radius,
                )
            }
        }

        ProgressBarStyle.BARS -> {
            /*
             * The song's row of bars, standing on a line a little below the middle with a short, faint
             * reflection under it, the way SoundCloud draws its waveforms. The tallest is the core's height
             * whatever the canvas, so the row is the same size on every player; the leftover width is split
             * between the two ends rather than all left at the right.
             */
            val pitch = SeekBar.BARS_PITCH_DP.dp.toPx()
            val width = SeekBar.BARS_WIDTH_DP.dp.toPx()
            val count = (size.width / pitch).toInt()
            if (count <= 0) return
            val start = (size.width - (count * pitch - (pitch - width))) / 2f
            val tallest = SeekBar.BARS_HEIGHT_DP.dp.toPx()
            val gap = 1.dp.toPx()
            val baseline = (size.height - (tallest * (1f + BARS_REFLECTION) + gap)) / 2f + tallest
            fun bar(index: Int, colour: Color) {
                val left = start + index * pitch
                val height = tallest * shapes.barAt(index, count)
                drawRect(colour, Offset(left, baseline - height), Size(width, height))
                drawRect(
                    colour.copy(alpha = colour.alpha * BARS_REFLECTION_ALPHA),
                    Offset(left, baseline + gap),
                    Size(width, height * BARS_REFLECTION),
                )
            }
            repeat(count) { index ->
                val left = start + index * pitch
                when {
                    left + width <= head -> bar(index, filled)
                    left >= head -> bar(index, track)
                    // The bar the song is in the middle of, lit as far as the song has reached.
                    else -> {
                        bar(index, track)
                        clipRect(right = head) { bar(index, filled) }
                    }
                }
            }
        }

        ProgressBarStyle.BEADS -> {
            // The count follows the width, as the segments' does, so a bead is a bead on every screen.
            val count = (size.width / SeekBar.BEAD_PITCH_DP.dp.toPx()).toInt().coerceAtLeast(4)
            val pitch = size.width / count
            val radius = SeekBar.BEAD_RADIUS_DP.dp.toPx()
            // The thread they are strung on, faint, so the dots read as a string rather than as a pattern.
            drawLine(
                track.copy(alpha = track.alpha * .55f),
                Offset(pitch / 2f, centreY),
                Offset(size.width - pitch / 2f, centreY),
                1.dp.toPx(),
            )
            repeat(count) { index ->
                val x = (index + .5f) * pitch
                drawCircle(if (x <= head) filled else track, radius, Offset(x, centreY))
            }
            if (showHead) {
                // The bead the song has reached, larger, in a soft halo of its own colour. Where the song
                // is rather than snapped to the nearest bead, so it slides along the string as it plays.
                val headRadius = SeekBar.BEAD_HEAD_RADIUS_DP.dp.toPx()
                val at = Offset(head, centreY)
                val halo = headRadius * 2.3f
                drawCircle(
                    Brush.radialGradient(
                        0f to filled.copy(alpha = .38f),
                        1f to filled.copy(alpha = 0f),
                        center = at,
                        radius = halo,
                    ),
                    halo,
                    at,
                )
                drawCircle(filled, headRadius, at)
            }
        }

        ProgressBarStyle.NEON -> {
            /*
             * A tube of light: the line in a brighter accent with its glow built up from the same line drawn
             * wider and fainter, out to the core's reach. A glow drawn with a blur would be one call, but a
             * blurred stroke is not drawn at all by the hardware canvas before Android 9, and this phone app
             * still runs on 8. What is still to come is faint dashes, which stand still as the head passes
             * over them rather than crawling along with it.
             */
            val line = SeekBar.NEON_LINE_DP.dp.toPx()
            val glow = SeekBar.NEON_GLOW_DP.dp.toPx()
            val bright = neonColour(filled, pale)
            if (head < size.width) {
                val dash = SeekBar.NEON_DASH_DP.dp.toPx()
                val period = dash + SeekBar.NEON_DASH_GAP_DP.dp.toPx()
                drawLine(
                    track,
                    Offset(head + glow / 2f, centreY),
                    Offset(size.width, centreY),
                    line,
                    StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, period - dash), (head + glow / 2f) % period),
                )
            }
            if (head > 0f) {
                repeat(NEON_GLOW_LAYERS) { layer ->
                    val spread = glow * (NEON_GLOW_LAYERS - layer) / NEON_GLOW_LAYERS
                    drawLine(bright.copy(alpha = NEON_GLOW_ALPHA), Offset(0f, centreY), Offset(head, centreY), line + spread * 2f, StrokeCap.Round)
                }
                drawLine(bright, Offset(0f, centreY), Offset(head, centreY), line, StrokeCap.Round)
                // A whiter core along the middle of the tube, as a lit tube has. Not on a pale page, where
                // white is the page and the core would read as a gap.
                if (!pale) drawLine(lerp(bright, Color.White, .6f), Offset(0f, centreY), Offset(head, centreY), line * .45f, StrokeCap.Round)
            }
            if (showHead) {
                // The spark, breathing while the music plays.
                val at = Offset(head, centreY)
                val halo = glow * (1.05f + .4f * pulse)
                drawCircle(
                    Brush.radialGradient(0f to bright.copy(alpha = .6f), 1f to bright.copy(alpha = 0f), center = at, radius = halo),
                    halo,
                    at,
                )
                drawCircle(if (pale) bright else lerp(bright, Color.White, .75f), (2.5f + 1.2f * pulse).dp.toPx(), at)
            }
        }

        ProgressBarStyle.RULER -> {
            /*
             * A ruler laid along the song: a hairline to stand on, a tick every few seconds and a longer one
             * on each minute, and a pointer riding above the place the song has reached, with a faint line
             * down from its tip to the edge it is measuring. Lit up to the pointer, faint after it.
             */
            val pointer = SeekBar.RULER_POINTER_DP.dp.toPx()
            val major = SeekBar.RULER_MAJOR_TICK_DP.dp.toPx()
            val minor = SeekBar.RULER_TICK_DP.dp.toPx()
            val hair = 1.dp.toPx().coerceAtLeast(1f)
            val top = (size.height - pointer - major) / 2f
            val baseline = top + pointer + major
            drawLine(track, Offset(0f, baseline), Offset(size.width, baseline), hair)
            if (head > 0f) drawLine(filled, Offset(0f, baseline), Offset(head, baseline), hair * 1.6f)
            shapes.ticks.forEach { tick ->
                val x = tick.fraction * size.width
                drawLine(
                    if (x <= head) filled else track,
                    Offset(x, baseline),
                    Offset(x, baseline - if (tick.major) major else minor),
                    if (tick.major) hair * 1.6f else hair,
                )
            }
            if (showHead) {
                drawLine(filled.copy(alpha = .5f), Offset(head, top + pointer), Offset(head, baseline), hair)
                val half = pointer * .65f
                val triangle = Path().apply {
                    moveTo(head - half, top)
                    lineTo(head + half, top)
                    lineTo(head, top + pointer)
                    close()
                }
                drawPath(triangle, filled)
                drawPath(triangle, filled, style = Stroke(hair, join = StrokeJoin.Round))
            }
        }

        ProgressBarStyle.CLASSIC -> {
            /*
             * Windows 98's progress bar with its trackbar's thumb on top. The well is sunk into the page --
             * dark edge above and to the left, light below and to the right, the way that desktop lit every
             * control from the top left -- and fills from the left with square blocks. Black and white for
             * the bevel rather than the theme's own colours, because that is what the bevels were, and they
             * read on a dark page as well as on the grey one they were made for.
             */
            val height = SeekBar.CLASSIC_WELL_DP.dp.toPx()
            val top = centreY - height / 2f
            val edge = 1.dp.toPx().coerceAtLeast(1f)
            val shadow = Color.Black.copy(alpha = .55f)
            val light = Color.White.copy(alpha = .85f)
            drawRect(track, Offset(0f, top), Size(size.width, height))
            drawRect(shadow, Offset(0f, top), Size(size.width, edge))
            drawRect(shadow, Offset(0f, top), Size(edge, height))
            drawRect(light, Offset(0f, top + height - edge), Size(size.width, edge))
            drawRect(light, Offset(size.width - edge, top), Size(edge, height))

            val inset = edge * 2f
            val block = SeekBar.CLASSIC_BLOCK_DP.dp.toPx()
            val step = block + SeekBar.CLASSIC_BLOCK_GAP_DP.dp.toPx()
            val end = size.width - inset
            var left = inset
            // Lit once the head has reached the block, as the other blocky bar does.
            while (left < head && left < end) {
                drawRect(filled, Offset(left, top + inset), Size(minOf(block, end - left), height - inset * 2f))
                left += step
            }

            if (showHead) {
                val width = SeekBar.CLASSIC_THUMB_WIDTH_DP.dp.toPx()
                val tall = SeekBar.CLASSIC_THUMB_HEIGHT_DP.dp.toPx().coerceAtMost(size.height)
                val x = (head - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
                val y = centreY - tall / 2f
                // Raised rather than sunk: the same two edges, the other way round.
                drawRect(Color(SeekBar.CLASSIC_FACE), Offset(x, y), Size(width, tall))
                drawRect(light, Offset(x, y), Size(width, edge))
                drawRect(light, Offset(x, y), Size(edge, tall))
                drawRect(Color.Black.copy(alpha = .7f), Offset(x, y + tall - edge), Size(width, edge))
                drawRect(Color.Black.copy(alpha = .7f), Offset(x + width - edge, y), Size(edge, tall))
            }
        }

        ProgressBarStyle.LUNA -> {
            /*
             * Windows XP's progress bar with its trackbar's thumb, as Classic is 98's: a rounded white well
             * in a grey edge, filling with green blocks that are pale at their top and bottom and deep
             * through the middle. XP's green whatever the accent, because the green is what the bar was.
             * On a dark page the well is a pale glass rather than white paper, which glared like a lamp.
             */
            val height = SeekBar.LUNA_WELL_DP.dp.toPx()
            val top = centreY - height / 2f
            val edge = 1.dp.toPx().coerceAtLeast(1f)
            drawLunaWell(Offset(0f, top), Size(size.width, height), edge, pale)

            val inset = edge * 3f
            val block = SeekBar.LUNA_BLOCK_DP.dp.toPx()
            val step = block + SeekBar.LUNA_BLOCK_GAP_DP.dp.toPx()
            val blockTop = top + inset
            val blockHeight = height - inset * 2f
            val green = lunaGreen(blockTop, blockHeight)
            val end = size.width - inset
            var left = inset
            while (left < head && left < end) {
                drawRect(green, Offset(left, blockTop), Size(minOf(block, end - left), blockHeight))
                left += step
            }

            if (showHead) {
                val width = SeekBar.LUNA_THUMB_WIDTH_DP.dp.toPx()
                drawLunaThumb(
                    // Kept inside the well at either end, as the classic thumb is.
                    centreX = (head - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f)) + width / 2f,
                    centreY = centreY,
                    width = width,
                    tall = SeekBar.LUNA_THUMB_HEIGHT_DP.dp.toPx().coerceAtMost(size.height),
                )
            }
        }
    }
}

/**
 * How much of the drawn seek bar's touch area its canvas takes. The bars stand taller than any of the
 * others, with their reflection under them, so they have all of it; the rest sit in the middle.
 */
internal fun ProgressBarStyle.drawnHeight(): Dp = if (this == ProgressBarStyle.BARS) 36.dp else 24.dp

/**
 * The thin line of progress in the player bars, drawn as a small copy of the seek bar chosen.
 *
 * Only for the styles with a shape that survives being a few points tall: a row of bars, a string of beads,
 * a lit tube, a ruler's minutes and XP's green blocks. The others are a plain line or blocks, drawn where
 * they always were. [track] is what has not played yet, as the plain line draws it.
 */
internal fun DrawScope.drawPlaybackLine(
    style: ProgressBarStyle,
    fraction: Float,
    track: Color,
    filled: Color,
    shapes: SeekBarShapes,
    pale: Boolean,
) {
    val head = size.width * fraction.coerceIn(0f, 1f)
    val centreY = size.height / 2f
    when (style) {
        ProgressBarStyle.BARS -> {
            val pitch = LINE_BARS_PITCH.toPx()
            val width = pitch * .66f
            val count = (size.width / pitch).toInt()
            repeat(count) { index ->
                val left = index * pitch
                val height = size.height * shapes.barAt(index, count)
                val colour = if (left < head) filled else track
                drawRect(colour, Offset(left, size.height - height), Size(width, height))
            }
        }

        ProgressBarStyle.BEADS -> {
            val count = (size.width / LINE_BEADS_PITCH.toPx()).toInt().coerceAtLeast(4)
            val pitch = size.width / count
            val radius = size.height * .26f
            repeat(count) { index ->
                val x = (index + .5f) * pitch
                drawCircle(if (x <= head) filled else track, radius, Offset(x, centreY))
            }
            if (head > 0f) drawCircle(filled, size.height * .46f, Offset(head, centreY))
        }

        ProgressBarStyle.NEON -> {
            val line = size.height / 3f
            val bright = neonColour(filled, pale)
            if (head < size.width) {
                val period = line * 5f
                drawLine(
                    track,
                    Offset(head + line, centreY),
                    Offset(size.width, centreY),
                    line,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(period * .6f, period * .4f), (head + line) % period),
                )
            }
            if (head > 0f) {
                drawLine(bright.copy(alpha = .22f), Offset(0f, centreY), Offset(head, centreY), size.height)
                drawLine(bright, Offset(0f, centreY), Offset(head, centreY), line)
            }
        }

        ProgressBarStyle.RULER -> {
            val hair = 1.dp.toPx().coerceAtLeast(1f)
            val baseline = size.height - hair / 2f
            drawLine(track, Offset(0f, baseline), Offset(size.width, baseline), hair)
            if (head > 0f) drawLine(filled, Offset(0f, baseline), Offset(head, baseline), hair * 2f)
            shapes.ticks.forEach { tick ->
                val x = tick.fraction * size.width
                val tall = if (tick.major) size.height else size.height * .5f
                drawLine(if (x <= head) filled else track, Offset(x, baseline), Offset(x, baseline - tall), hair)
            }
        }

        ProgressBarStyle.LUNA -> {
            val edge = 1.dp.toPx().coerceAtLeast(1f)
            drawLunaWell(Offset.Zero, size, edge, pale, corner = size.height / 3f)
            val inset = edge * 1.5f
            val block = size.height * .9f
            val step = block + edge
            val green = lunaGreen(inset, size.height - inset * 2f)
            var left = inset
            while (left < head && left < size.width - inset) {
                drawRect(green, Offset(left, inset), Size(minOf(block, size.width - inset - left), size.height - inset * 2f))
                left += step
            }
        }

        else -> Unit
    }
}

/** How tall [drawPlaybackLine] is for each style it draws: enough for the shape, and no more. */
internal fun ProgressBarStyle.lineHeight(): Dp = when (this) {
    ProgressBarStyle.BARS -> 6.dp
    ProgressBarStyle.NEON -> 4.dp
    else -> 5.dp
}

/** Whether the thin line in the player bars is a small copy of this style rather than a plain line. */
internal val ProgressBarStyle.hasOwnLine: Boolean
    get() = this == ProgressBarStyle.BARS || this == ProgressBarStyle.BEADS || this == ProgressBarStyle.NEON ||
        this == ProgressBarStyle.RULER || this == ProgressBarStyle.LUNA

/**
 * What the Bars and the Ruler are drawn from for one song at one width: the song's row of heights, and where
 * its ticks fall. Worked out when the song or the width changes rather than on every frame of a drag.
 */
@Immutable
internal class SeekBarShapes(val bars: FloatArray, val ticks: List<SeekBar.RulerTick>) {
    /**
     * The height of bar [index] of [count], from 0 to 1: the song's own where there is a song, and an even
     * row where there is none, rather than somebody else's pattern. Read across when the row was made for
     * a slightly different width, which keeps the song's shape.
     */
    fun barAt(index: Int, count: Int): Float =
        if (bars.isEmpty() || count <= 0) EVEN_BAR else bars[(index * bars.size / count).coerceIn(0, bars.lastIndex)]

    companion object {
        val NONE = SeekBarShapes(FloatArray(0), emptyList())
    }
}

/**
 * The shapes for [style] at [widthPx], for the song keyed [seed] that is [durationMs] long.
 *
 * [barPitch] and [tickRoom] are how far apart the bars and the ticks are: the seek bar's own, or the
 * closer ones of the thin line in the player bars.
 */
@Composable
internal fun rememberSeekBarShapes(
    style: ProgressBarStyle,
    seed: String,
    durationMs: Long,
    widthPx: Int,
    barPitch: Dp = SeekBar.BARS_PITCH_DP.dp,
    tickRoom: Dp = RULER_TICK_ROOM,
): SeekBarShapes {
    val density = LocalDensity.current
    return remember(style, seed, durationMs, widthPx, barPitch, tickRoom, density) {
        when (style) {
            ProgressBarStyle.BARS -> {
                val count = (widthPx / with(density) { barPitch.toPx() }).toInt()
                SeekBarShapes(if (seed.isBlank() || count <= 0) FloatArray(0) else SeekBar.barHeights(seed, count), emptyList())
            }
            ProgressBarStyle.RULER -> SeekBarShapes(
                FloatArray(0),
                SeekBar.rulerTicks(durationMs, (widthPx / with(density) { tickRoom.toPx() }).toInt()),
            )
            else -> SeekBarShapes.NONE
        }
    }
}

/**
 * XP's well: white inside a rounded grey edge, with the faint shadow its top edge cast inwards. On a dark
 * page it is a pale glass instead, since a strip of white paper there glares.
 */
private fun DrawScope.drawLunaWell(topLeft: Offset, size: Size, edge: Float, pale: Boolean, corner: Float = 3.dp.toPx()) {
    val radius = CornerRadius(corner, corner)
    drawRoundRect(if (pale) Color.White else Color.White.copy(alpha = .13f), topLeft, size, radius)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (pale) .10f else .25f),
            .35f to Color.Transparent,
            startY = topLeft.y,
            endY = topLeft.y + size.height,
        ),
        topLeft,
        size,
        radius,
    )
    drawRoundRect(
        LUNA_WELL_EDGE,
        topLeft + Offset(edge / 2f, edge / 2f),
        Size(size.width - edge, size.height - edge),
        CornerRadius(corner - edge / 2f, corner - edge / 2f),
        style = Stroke(edge),
    )
}

/** XP's green, pale at the top and the foot of a block and deep through its middle. */
private fun lunaGreen(top: Float, height: Float): Brush = Brush.verticalGradient(
    0f to Color(WindowsXpColours.PROGRESS_LIGHT),
    .5f to Color(WindowsXpColours.PROGRESS),
    1f to Color(WindowsXpColours.PROGRESS_LIGHT),
    startY = top,
    endY = top + height,
)

/**
 * XP's trackbar thumb: a slab with rounded shoulders that comes to a point at its foot, white fading to
 * the light grey of Luna's buttons, edged in a dark blue-grey, with the green of a thumb that is pointing at
 * something along the inside of its point. Also the 98 and XP skins' slider handle.
 */
internal fun DrawScope.drawLunaThumb(centreX: Float, centreY: Float, width: Float, tall: Float) {
    val x = centreX - width / 2f
    val y = centreY - tall / 2f
    val point = width / 2f
    val shoulder = 2.dp.toPx()
    val shape = Path().apply {
        moveTo(x + shoulder, y)
        lineTo(x + width - shoulder, y)
        quadraticTo(x + width, y, x + width, y + shoulder)
        lineTo(x + width, y + tall - point)
        lineTo(centreX, y + tall)
        lineTo(x, y + tall - point)
        lineTo(x, y + shoulder)
        quadraticTo(x, y, x + shoulder, y)
        close()
    }
    drawPath(
        shape,
        Brush.verticalGradient(0f to Color.White, 1f to Color(WindowsXpColours.BUTTON_FOOT), startY = y, endY = y + tall),
    )
    val edge = 1.dp.toPx().coerceAtLeast(1f)
    // The green runs up the sides from the point, just inside the edge, so it outlines the point rather than
    // drawing a tick inside it.
    val inset = edge * 1.6f
    val glow = Path().apply {
        moveTo(x + inset, y + tall * .45f)
        lineTo(x + inset, y + tall - point - inset * .2f)
        lineTo(centreX, y + tall - inset * 1.4f)
        lineTo(x + width - inset, y + tall - point - inset * .2f)
        lineTo(x + width - inset, y + tall * .45f)
    }
    drawPath(glow, Color(WindowsXpColours.PROGRESS).copy(alpha = .75f), style = Stroke(edge, join = StrokeJoin.Round))
    drawPath(shape, LUNA_THUMB_EDGE, style = Stroke(edge, join = StrokeJoin.Round))
}

/** The neon's light: the accent brightened towards white on a dark page, and left as it is on a pale one. */
private fun neonColour(accent: Color, pale: Boolean): Color = if (pale) accent else lerp(accent, Color.White, .3f)

/** The height of every bar in an even row: what Bars draws while there is no song to take a shape from. */
private const val EVEN_BAR = .42f

/** How much of a bar its reflection is, and how faint against the bar. */
private const val BARS_REFLECTION = .32f
private const val BARS_REFLECTION_ALPHA = .3f

/** How many times the neon line is drawn wider to make its glow, and how strongly each time. */
private const val NEON_GLOW_LAYERS = 5
private const val NEON_GLOW_ALPHA = .085f

/** The ruler's ticks are never closer than this, so a short song is not a comb. */
private val RULER_TICK_ROOM = 8.dp

/** The thin line's bars and beads, closer together than the seek bar's, as the line is a few points tall. */
internal val LINE_BARS_PITCH = 3.dp
internal val LINE_BEADS_PITCH = 6.dp
internal val LINE_TICK_ROOM = 14.dp

/** The grey round XP's well, and the dark blue-grey round its thumb. */
private val LUNA_WELL_EDGE = Color(0xFF8E8F8F)
private val LUNA_THUMB_EDGE = Color(WindowsXpColours.SCROLL_ARROW)
