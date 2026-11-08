package app.noctorium.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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
import app.noctorium.settings.SHADOW_GOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The Shadow theme's page, beyond its colours: the listener's own picture, if they gave one, dimmed behind
 * everything, and over it a few crimson streaks swept in from the right and a gold ring -- drawn here, Noctorium's
 * own, with nobody's artwork in it. Laid on wherever the theme's glow is, so it is one page the window over.
 */

/** What the Shadow theme draws behind the pages: the listener's picture, or null for the streaks alone. */
@Immutable
internal data class ShadowBackdrop(val picture: ImageBitmap?, val chaos: Boolean = false)

/** The Shadow theme's backdrop while it is the theme, null on every other. */
internal val LocalShadowBackdrop = compositionLocalOf<ShadowBackdrop?> { null }

/** The picture kept at [path], decoded off the main thread; null while it is, and for none or one unreadable. */
@Composable
internal fun rememberShadowPicture(path: String): ImageBitmap? {
    val picture by produceState<ImageBitmap?>(null, path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { decodeShadowPicture(File(path)) }.getOrNull()
        }
    }
    return picture
}

/** [backdrop] across a [window]-sized page, as much of it as falls here. */
internal fun DrawScope.drawShadowBackdrop(backdrop: ShadowBackdrop, window: Size) {
    if (window.width <= 0f || window.height <= 0f) return
    if (backdrop.chaos) {
        drawChaosBackdrop(backdrop.picture, window)
        return
    }
    backdrop.picture?.let { picture ->
        // Filling the window, cropped rather than squashed, and dim enough that writing reads over it.
        val scale = max(window.width / picture.width, window.height / picture.height)
        val shown = IntSize((picture.width * scale).roundToInt(), (picture.height * scale).roundToInt())
        drawImage(
            picture,
            dstOffset = IntOffset(((window.width - shown.width) / 2).roundToInt(), ((window.height - shown.height) / 2).roundToInt()),
            dstSize = shown,
            alpha = .3f,
        )
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .35f), 1f to Color.Black.copy(alpha = .7f)), size = window)
    }
    drawShadowStreaks(window)
}

/** The crimson streaks and the gold ring, the Shadow theme's own. */
internal fun DrawScope.drawShadowStreaks(window: Size) {
    val crimson = Color(0xFFE3262F)
    // Sized by the shorter side, so on a tall phone the ring sits in the corner rather than over the page's title.
    val unit = minOf(window.width, window.height)
    // Long thin blades swept up and left from the lower right, each fading as it goes.
    listOf(
        Triple(.94f, .78f, .5f), Triple(.99f, .62f, .38f), Triple(1.03f, .9f, .3f), Triple(.9f, .97f, .22f),
    ).forEach { (x, y, opacity) ->
        val tip = Offset(window.width * x - unit * 1.05f, window.height * y - unit * .55f)
        val root = Offset(window.width * x, window.height * y)
        val width = unit * .028f
        val blade = Path().apply {
            moveTo(root.x, root.y - width)
            quadraticBezierTo((root.x + tip.x) / 2 + width * 2, (root.y + tip.y) / 2 - width * 3, tip.x, tip.y)
            quadraticBezierTo((root.x + tip.x) / 2, (root.y + tip.y) / 2 + width, root.x, root.y + width)
            close()
        }
        drawPath(blade, Brush.linearGradient(listOf(crimson.copy(alpha = .26f * opacity / .5f), Color.Transparent), root, tip))
    }
    // A gold ring, high on the right, as if dropped there.
    val ring = Offset(window.width - unit * .16f, unit * .16f)
    val radius = unit * .055f
    drawCircle(Color(SHADOW_GOLD).copy(alpha = .12f), radius * 1.6f, ring)
    drawCircle(Color(SHADOW_GOLD).copy(alpha = .55f), radius, ring, style = Stroke(unit * .012f))
}

/** The picture at [file], made small enough for a phone's screen to draw every frame without strain. */
private fun decodeShadowPicture(file: File): ImageBitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return android.graphics.BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
