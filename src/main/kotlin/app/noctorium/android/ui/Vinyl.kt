package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

/**
 * The cover as a record: black vinyl with its grooves, the cover as the label in the middle, and the hole
 * through it. It turns while [spinning] and stops where it is when the music does, rather than winding back
 * to where it started -- a record lifted off mid-turn does not jump to its label's upright.
 *
 * The light on it does not turn with it. The sheen is drawn over the spinning disc, still, the way light
 * from a lamp sits in one place on a record however fast it goes round; it is what makes the turning read
 * as turning, since a plain black disc spinning looks exactly like one standing still.
 */
@Composable
internal fun Record(artworkUrl: String?, size: Dp, spinning: Boolean, modifier: Modifier = Modifier) {
    val angle = rememberRecordAngle(spinning)
    val hole = MaterialTheme.colorScheme.background
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawVinyl() }
            Box(
                Modifier
                    .size(size * LABEL_FRACTION)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUrl == null) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(size * LABEL_FRACTION * .45f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    AsyncImage(artworkUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawSheen(hole) }
    }
}

/**
 * How far round the record is, in degrees, moving at thirty-three and a third while [spinning] -- and still
 * whenever nothing is to move. Read where it is drawn, so each frame of the turn is a turn of a layer and
 * nothing is laid out or recomposed for it.
 */
@Composable
internal fun rememberRecordAngle(spinning: Boolean): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    val turning = spinning && LocalMotion.current
    LaunchedEffect(turning) {
        if (!turning) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * DEGREES_PER_SECOND) % 360f
                last = now
            }
        }
    }
    return angle
}

/** The disc: black, a slightly lighter rim, and the grooves, with the wider gaps between songs. */
private fun DrawScope.drawVinyl() {
    val radius = size.minDimension / 2f
    val centre = center
    drawCircle(VINYL, radius, centre)
    drawCircle(Color.White.copy(alpha = .07f), radius * .985f, centre, style = Stroke(radius * .02f))
    val groove = (radius * .004f).coerceAtLeast(.6f)
    var r = radius * .95f
    var lane = 0
    while (r > radius * (LABEL_FRACTION + .03f)) {
        drawCircle(Color.White.copy(alpha = if (lane % 2 == 0) .055f else .03f), r, centre, style = Stroke(groove))
        r -= radius * .018f
        lane++
    }
    // The quiet runs between one song and the next, darker and smooth.
    SONG_GAPS.forEach { gap -> drawCircle(Color.Black.copy(alpha = .55f), radius * gap, centre, style = Stroke(radius * .012f)) }
    drawCircle(Color.Black.copy(alpha = .5f), radius * (LABEL_FRACTION + .012f), centre, style = Stroke(radius * .02f))
}

/** The light on the record, which stays put while the record turns under it, and the hole in the middle. */
private fun DrawScope.drawSheen(hole: Color) {
    val radius = size.minDimension / 2f
    val label = radius * LABEL_FRACTION
    val band = radius - label
    drawCircle(
        Brush.sweepGradient(
            0f to Color.Transparent,
            .1f to Color.White.copy(alpha = .13f),
            .2f to Color.Transparent,
            .5f to Color.Transparent,
            .6f to Color.White.copy(alpha = .1f),
            .7f to Color.Transparent,
            1f to Color.Transparent,
            center = center,
        ),
        radius = label + band / 2f,
        center = center,
        style = Stroke(band),
    )
    drawCircle(hole, radius * .035f + 1f, center)
    drawCircle(Color.Black.copy(alpha = .35f), radius * .035f + 1f, center, style = Stroke(1f))
}

/** How much of the record the label is, across. */
private const val LABEL_FRACTION = .4f

/** Thirty-three and a third turns a minute. */
private const val DEGREES_PER_SECOND = 200f

/** Where the gaps between songs fall, as a share of the record's radius. */
private val SONG_GAPS = listOf(.62f, .76f, .88f)

private val VINYL = Color(0xFF121214)
