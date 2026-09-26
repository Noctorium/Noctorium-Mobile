package app.noctorium.android.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SeekBar
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
    }
}
