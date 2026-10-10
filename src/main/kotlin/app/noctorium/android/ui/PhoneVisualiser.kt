package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.noctorium.core.AppState
import app.noctorium.playback.AudioAnalysis
import app.noctorium.playback.AudioAnalysisKind
import app.noctorium.playback.Visualiser
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.VisualiserStyle
import app.noctorium.settings.WindowsXpColours

/**
 * The visualiser: the sound itself, as bars from the bass up or as each channel's level, from what the player
 * measures of what it plays ([AppState.audioAnalysis]) -- never a pattern standing in for it.
 *
 * Collected only while this is on the screen and the app is in front, which is what tells the player to measure
 * at all: a visualiser nobody can see costs nothing. Where the player can see less than [style] wants it draws
 * less ([VisualiserStyle.shownWith]): bars become a level meter where only levels can be had, and either becomes
 * nothing while a song plays in Spotify's own app, where nothing can.
 *
 * Bars rise at once and fall away smoothly, with a cap held at each one's last peak for a moment, the way a
 * hardware spectrum's lights did; with animations off they simply stand where each frame puts them.
 */
@Composable
internal fun Visualiser(state: AppState, style: VisualiserStyle, modifier: Modifier = Modifier) {
    if (style == VisualiserStyle.OFF) return
    val frame by state.audioAnalysis.collectAsStateWithLifecycle()
    val available = frame?.kind?.takeIf { it != AudioAnalysisKind.NONE } ?: state.audioAnalysisKind
    val shown = style.shownWith(available)
    if (shown == VisualiserStyle.OFF) return
    val falling = rememberFalling(frame, shown)
    VisualiserDrawing(shown, falling, modifier.semantics { contentDescription = "Visualiser" })
}

/** One look of the visualiser, from [levels] as they stand: for the screen above, and the pictures in Settings. */
@Composable
internal fun VisualiserDrawing(style: VisualiserStyle, levels: VisualiserLevels, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)
    val win98 = Win98
    Canvas(modifier) {
        when (style) {
            VisualiserStyle.BARS -> drawBars(levels, skin, accent, faint, win98)
            VisualiserStyle.LEVEL_METER -> drawMeter(levels, skin, accent, faint, win98)
            VisualiserStyle.OFF -> Unit
        }
    }
}

/** The heights being drawn, and the caps held over them: what one frame of the visualiser is drawn from. */
internal class VisualiserLevels(
    val bars: FloatArray,
    val caps: FloatArray,
    val rms: FloatArray,
    val peaks: FloatArray,
    /** Read where the drawing is, so a frame redraws the canvas without composing anything. */
    private val tick: () -> Long = { 0L },
) {
    fun touch() = tick()

    /** Whether everything has fallen to nothing, so there is nothing left to move. */
    fun isSilent(): Boolean =
        bars.all { it <= 0f } && caps.all { it <= 0f } && rms.all { it <= 0f } && peaks.all { it <= 0f }

    companion object {
        /** A still frame from [analysis], for a picture: the bars and the levels as it says, the caps on them. */
        fun of(analysis: AudioAnalysis): VisualiserLevels {
            val bands = FloatArray(Visualiser.BAND_COUNT) { analysis.bands.getOrElse(it) { 0f } }
            return VisualiserLevels(
                bars = bands,
                caps = bands.copyOf(),
                rms = analysis.rms.toFloatArray(),
                peaks = analysis.peaks.toFloatArray(),
            )
        }
    }
}

/**
 * The levels as they move: each bar up to its frame's height at once, then falling at [FALL_PER_SECOND] until the
 * sound lifts it again; each cap held for [CAP_HOLD_MS] at the highest the bar reached, then falling more slowly.
 * The frames arrive about thirty times a second; this runs at the screen's own rate between them.
 */
@Composable
private fun rememberFalling(frame: AudioAnalysis?, style: VisualiserStyle): VisualiserLevels {
    val moving = LocalMotion.current
    val latest by rememberUpdatedState(frame)
    var tick by remember { mutableLongStateOf(0L) }
    val levels = remember {
        VisualiserLevels(
            bars = FloatArray(Visualiser.BAND_COUNT),
            caps = FloatArray(Visualiser.BAND_COUNT),
            rms = FloatArray(2),
            peaks = FloatArray(2),
            tick = { tick },
        )
    }
    val capHeld = remember { LongArray(Visualiser.BAND_COUNT) }
    LaunchedEffect(moving, style) {
        var last = 0L
        while (true) {
            // Still once everything has fallen and nothing is playing: no frames are drawn until the sound returns.
            if (latest == null && levels.isSilent()) {
                snapshotFlow { latest }.first { it != null }
                last = 0L
            }
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.1f)
                last = now
                val target = latest
                for (band in 0 until Visualiser.BAND_COUNT) {
                    val wanted = target?.bands?.getOrNull(band) ?: 0f
                    val bar = if (!moving || wanted >= levels.bars[band]) wanted else (levels.bars[band] - FALL_PER_SECOND * seconds).coerceAtLeast(wanted)
                    levels.bars[band] = bar
                    if (bar >= levels.caps[band] || !moving) {
                        levels.caps[band] = bar
                        capHeld[band] = now
                    } else if ((now - capHeld[band]) / 1_000_000 > CAP_HOLD_MS) {
                        levels.caps[band] = (levels.caps[band] - CAP_FALL_PER_SECOND * seconds).coerceAtLeast(bar)
                    }
                }
                for (channel in 0 until 2) {
                    val rms = target?.rms?.getOrNull(channel) ?: target?.rms?.firstOrNull() ?: 0f
                    val peak = target?.peaks?.getOrNull(channel) ?: target?.peaks?.firstOrNull() ?: 0f
                    levels.rms[channel] = if (!moving || rms >= levels.rms[channel]) rms else (levels.rms[channel] - FALL_PER_SECOND * seconds).coerceAtLeast(rms)
                    levels.peaks[channel] = if (!moving || peak >= levels.peaks[channel]) peak else (levels.peaks[channel] - CAP_FALL_PER_SECOND * seconds).coerceAtLeast(peak)
                }
                tick = now
            }
        }
    }
    return levels
}

/** How fast a bar falls once the sound under it drops: its whole height in a little under half a second. */
private const val FALL_PER_SECOND = 2.4f

/** How long a cap stays at a peak, and how fast it falls after. */
private const val CAP_HOLD_MS = 450
private const val CAP_FALL_PER_SECOND = .7f

/**
 * The bars, one for each of the ten bands, from the bass on the left. In the ordinary themes they are rounded
 * columns in the accent, deepening towards their foot, with a thin cap held over each; under 98 and XP they are
 * the lit blocks of the media players of the time, in a well sunk into the window -- the selection's colour under
 * 98, and Luna's green under XP.
 */
private fun DrawScope.drawBars(levels: VisualiserLevels, skin: ThemeSkin, accent: Color, faint: Color, win98: Win98Paint) {
    levels.touch()
    val count = levels.bars.size
    if (skin != ThemeSkin.STANDARD) {
        val xp = skin == ThemeSkin.WINDOWS_XP
        val inner = drawWell(xp, win98)
        val gap = 2.dp.toPx()
        val width = (inner.size.width - gap * (count + 1)) / count
        val block = 3.dp.toPx()
        val blockGap = 1.dp.toPx()
        val rows = ((inner.size.height - gap * 2 + blockGap) / (block + blockGap)).toInt().coerceAtLeast(1)
        val lit = if (xp) Color(WindowsXpColours.PROGRESS) else win98.Selection
        val unlit = if (xp) Color(WindowsXpColours.PROGRESS).copy(alpha = .12f) else win98.Selection.copy(alpha = .1f)
        for (band in 0 until count) {
            val x = inner.topLeft.x + gap + band * (width + gap)
            val on = (levels.bars[band] * rows).toInt()
            val cap = (levels.caps[band] * rows).toInt()
            for (row in 0 until rows) {
                val y = inner.topLeft.y + inner.size.height - gap - (row + 1) * (block + blockGap) + blockGap
                val colour = when {
                    row < on -> lit
                    row == cap && cap > 0 -> lit.copy(alpha = .7f)
                    else -> unlit
                }
                drawRect(colour, Offset(x, y), Size(width, block))
            }
        }
        return
    }
    val gap = size.width * .045f
    val width = (size.width - gap * (count - 1)) / count
    val corner = CornerRadius(width * .3f, width * .3f)
    val capHeight = 2.dp.toPx()
    for (band in 0 until count) {
        val x = band * (width + gap)
        drawRoundRect(faint, Offset(x, size.height - capHeight), Size(width, capHeight), corner)
        val height = (size.height - capHeight * 2) * levels.bars[band]
        if (height > .5f) {
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(lerp(accent, Color.White, .22f), accent),
                    startY = size.height - height,
                    endY = size.height,
                ),
                Offset(x, size.height - height),
                Size(width, height),
                corner,
            )
        }
        val capY = size.height - capHeight - (size.height - capHeight * 2) * levels.caps[band] - capHeight
        if (levels.caps[band] > .02f) drawRoundRect(accent.copy(alpha = .75f), Offset(x, capY), Size(width, capHeight), corner)
    }
}

/**
 * The level meter: a row for each channel, left over right, lit as far as its RMS -- what the ear hears as
 * loudness -- with a mark at its peak, the way a mixing desk shows them. The last fifth, the part near full scale,
 * is drawn warmer.
 */
private fun DrawScope.drawMeter(levels: VisualiserLevels, skin: ThemeSkin, accent: Color, faint: Color, win98: Win98Paint) {
    levels.touch()
    val skinned = skin != ThemeSkin.STANDARD
    val xp = skin == ThemeSkin.WINDOWS_XP
    val area = if (skinned) drawWell(xp, win98) else WellArea(Offset.Zero, size)
    val gap = (if (skinned) 3.dp else 4.dp).toPx()
    val rows = 2
    val height = ((area.size.height - gap * (rows + 1)) / rows).coerceAtMost(10.dp.toPx())
    val top = area.topLeft.y + (area.size.height - (height * rows + gap * (rows - 1))) / 2f
    val warm = Color(0xFFE5484D)
    val lit = when {
        xp -> Color(WindowsXpColours.PROGRESS)
        skinned -> win98.Selection
        else -> accent
    }
    for (row in 0 until rows) {
        val y = top + row * (height + gap)
        val left = area.topLeft.x + gap
        val width = area.size.width - gap * 2
        val rms = levels.rms[row].coerceIn(0f, 1f)
        val peak = levels.peaks[row].coerceIn(0f, 1f)
        val corner = if (skinned) CornerRadius.Zero else CornerRadius(height / 2f, height / 2f)
        drawRoundRect(if (skinned) lit.copy(alpha = .1f) else faint, Offset(left, y), Size(width, height), corner)
        if (rms > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(0f to lit, .78f to lit, 1f to warm, startX = left, endX = left + width),
                Offset(left, y),
                Size(width * rms, height),
                corner,
            )
        }
        if (peak > .01f) drawRect(if (peak > .9f) warm else lit, Offset(left + width * peak - 1.5.dp.toPx(), y), Size(1.5.dp.toPx(), height))
    }
}

/** Where a skin's well leaves room to draw. */
private class WellArea(val topLeft: Offset, val size: Size)

/** The well a skin's visualiser sits in: sunk into 98's face, or a pale Luna field. What is inside it is returned. */
private fun DrawScope.drawWell(xp: Boolean, win98: Win98Paint): WellArea {
    if (xp) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(Color(0xFF7F9DB9), cornerRadius = CornerRadius(2.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        return WellArea(Offset(1f, 1f), Size(size.width - 2f, size.height - 2f))
    }
    drawRect(win98.Window)
    drawEdge98(Edge98.SUNKEN, win98)
    return WellArea(Offset(2f, 2f), Size(size.width - 4f, size.height - 4f))
}

/** A spectrum to draw the visualiser's pictures in Settings from: music, roughly, with its weight in the bass. */
internal val SAMPLE_ANALYSIS = AudioAnalysis(
    bands = listOf(.62f, .84f, .78f, .66f, .71f, .58f, .52f, .44f, .33f, .2f),
    peaks = listOf(.88f, .84f),
    rms = listOf(.7f, .66f),
)
