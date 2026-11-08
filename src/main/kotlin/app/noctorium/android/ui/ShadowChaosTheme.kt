package app.noctorium.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Bundled locally: no download, user-selected picture, or external file needed after installation. */
@Composable
internal fun rememberChaosPicture(enabled: Boolean): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val picture by produceState<ImageBitmap?>(null, enabled, context) {
        value = if (!enabled) null else withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory.decodeResource(context.resources, app.noctorium.android.R.drawable.shadow_chaos)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return picture
}

/** Chaos Control: angular red rails, a broken gold halo, and the full character anchored to the right. */
internal fun DrawScope.drawChaosBackdrop(picture: ImageBitmap?, window: Size) {
    val w = window.width
    val h = window.height
    val unit = min(w, h)
    val portrait = w < h
    val red = Color(0xFFFF3546)
    val gold = Color(0xFFFFC34A)
    drawRect(Brush.linearGradient(listOf(Color(0xFF050608), Color(0xFF18080F)), Offset.Zero, Offset(w, h)), size = window)

    val halo = Offset(w * if (portrait) .73f else .81f, h * .43f)
    val radius = unit * .36f
    drawCircle(Brush.radialGradient(listOf(red.copy(alpha = .18f), Color.Transparent), halo, radius * 1.6f), radius * 1.6f, halo)
    drawArc(gold.copy(alpha = .30f), -65f, 252f, false,
        Offset(halo.x - radius, halo.y - radius), Size(radius * 2, radius * 2), style = Stroke(unit * .004f))
    drawArc(red.copy(alpha = .45f), 130f, 78f, false,
        Offset(halo.x - radius * 1.09f, halo.y - radius * 1.09f), Size(radius * 2.18f, radius * 2.18f), style = Stroke(unit * .012f))

    // Parallel, hard-edged rails evoke the red markings and speed of Shadow's silhouette.
    repeat(3) { index ->
        val x = w * .64f + unit * index * .13f
        val rail = Path().apply {
            moveTo(x, 0f)
            lineTo(x + unit * .055f, 0f)
            lineTo(x - unit * .48f, h)
            lineTo(x - unit * .535f, h)
            close()
        }
        drawPath(rail, Brush.verticalGradient(listOf(red.copy(alpha = .13f), red.copy(alpha = .025f)), endY = h))
    }

    picture?.let {
        // Fit, never crop or stretch: the raised hand and hover shoes remain in frame on both layouts.
        val scale = min(w * (if (portrait) .88f else .48f) / it.width, h * .88f / it.height)
        val shown = IntSize((it.width * scale).roundToInt().coerceAtLeast(1), (it.height * scale).roundToInt().coerceAtLeast(1))
        drawImage(it, dstOffset = IntOffset((w - shown.width - w * .025f).roundToInt(), ((h - shown.height) * .48f).roundToInt()),
            dstSize = shown, alpha = if (portrait) .29f else .48f)
    }
    // Keep text and the bottom transport legible without losing the character silhouette.
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF050608).copy(alpha = .93f), Color(0xFF050608).copy(alpha = .36f), Color.Transparent), endX = w), size = window)
    drawRect(Brush.verticalGradient(0f to Color.Transparent, .65f to Color.Transparent, 1f to Color(0xFF050608).copy(alpha = .90f), endY = h), size = window)
    drawLine(red.copy(alpha = .65f), Offset(w * .06f, h * .94f), Offset(w * .19f, h * .94f), unit * .006f)
    drawLine(gold.copy(alpha = .65f), Offset(w * .20f, h * .94f), Offset(w * .25f, h * .94f), unit * .006f)
}
