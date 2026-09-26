package app.noctorium.android.ui

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.noctorium.settings.CornerStyle
import app.noctorium.settings.Glass
import coil.compose.AsyncImage
import kotlin.math.min

/**
 * How much of the screen the floating glass covers, so a list can pad its ends by it.
 *
 * Content scrolls underneath the glass -- that is the entire point of it -- which means the last row of
 * every list would otherwise finish underneath the tab bar with no way to scroll it into view.
 */
@Immutable
data class ChromeInsets(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

val LocalChromeInsets = compositionLocalOf { ChromeInsets() }

/**
 * A list's own end padding, plus whatever the floating glass covers at each end.
 *
 * Under a solid theme the insets are zero and this is exactly the padding the list asked for.
 */
@Composable
fun chromePadding(bottom: Dp): PaddingValues {
    val chrome = LocalChromeInsets.current
    return PaddingValues(top = chrome.top, bottom = bottom + chrome.bottom)
}

/**
 * What the glass sees through: the content, recorded, so every pane can redraw its own part of it.
 *
 * This is the step the first version believed could not be done. A composable cannot sample what
 * happens to be behind it -- but it does not have to. The content is recorded into a layer as it draws,
 * and each pane draws that layer again underneath itself, translated so its own patch lines up, and
 * runs the lens over it. The pane is looking at a copy, and the copy is exact.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the recorded content sits on the screen, so a pane can find its own part of it. */
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Records whatever this draws, for the panes in front of it.
 *
 * The panes must not be inside what this records, or each would be drawing itself through itself. They
 * are siblings drawn after it, which is also why this sits in its own layer: redrawing a pane must never
 * send this back round to record again.
 *
 * Nothing tells the panes when this re-records, and nothing has to. A pane draws this layer by
 * reference -- on Android a layer is a render node, and a display list that draws one holds the node,
 * not a copy of what was in it -- so re-recording here changes what every pane shows on the very next
 * frame without any pane being redrawn. The first version bumped a counter that the panes read, which
 * was not only unnecessary but twice as expensive: the counter's change landed a frame late, so every
 * change behind the glass cost two frames, and both re-ran the frost and the lens. Measured on the phone
 * with music playing and nothing moving, that was 44 frames in ten seconds against 23 without glass.
 */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .graphicsLayer()
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * A pane of liquid glass over [backdrop], holding [content].
 *
 * Built in three layers, bottom to top. What is behind it, frosted lightly and put through the lens --
 * bent at the rim, clear in the middle. Then the pane's own tint, a sheen from above and the specular
 * rim. Then whatever it holds, which is never itself blurred or bent: the title in the mini player has
 * to stay a title.
 *
 * The lens wants API 33; from 31 the pane is frosted without bending, and below that it is the tint and
 * the rim alone. All three still float, and all three still read as a pane.
 *
 * [cornerRadius] of null makes a pill: the ends are exactly half the height, whatever the height is.
 */
@Composable
fun GlassPane(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // Where the lens's copy of the backdrop starts, which is not where the pane starts: see below.
    var viewOrigin by remember { mutableStateOf(Offset.Zero) }
    val shape = if (cornerRadius == null) RoundedCornerShape(50) else RoundedCornerShape(cornerRadius)
    val tint = MaterialTheme.colorScheme.surfaceContainer
    val lens = remember { newLens() }
    Box(
        modifier
            // Floating needs a shadow: it is the difference between a pane over the list and a hole in it.
            .shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .40f), spotColor = Color.Black.copy(alpha = .55f))
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        /*
         * The lens's view of the backdrop, deliberately larger than the pane.
         *
         * The rim samples from beyond the pane's edge -- that is what wraps the neighbouring content round
         * it -- and a copy cut exactly to the pane has nothing beyond its edge to sample. So this is
         * measured a margin wider on every side and placed that far up and to the left, the lens is told
         * where inside it the pane really is, and the pane's own clip trims the excess afterwards.
         */
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val margin = LENS_MARGIN.roundToPx()
                    val wide = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(
                            constraints.maxWidth + margin * 2,
                            constraints.maxHeight + margin * 2,
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { wide.place(-margin, -margin) }
                }
                .onGloballyPositioned { viewOrigin = it.positionInRoot() }
                .graphicsLayer {
                    clip = true
                    this.shape = RectangleShape
                    val margin = LENS_MARGIN.toPx()
                    val pane = Size(size.width - margin * 2, size.height - margin * 2)
                    renderEffect = glassEffect(lens, Offset(margin, margin), pane, radiusFor(cornerRadius, pane))
                }
                .drawBehind {
                    translate(backdrop.origin.x - viewOrigin.x, backdrop.origin.y - viewOrigin.y) {
                        drawLayer(backdrop.layer)
                    }
                },
        )
        Box(Modifier.matchParentSize().drawBehind { drawGlassFace(radiusFor(cornerRadius, size), tint) })
        content()
    }
}

private fun androidx.compose.ui.unit.Density.radiusFor(cornerRadius: Dp?, size: Size): Float =
    cornerRadius?.toPx() ?: (min(size.width, size.height) / 2f)

/**
 * How far past the pane's edge the lens can see: the furthest the rim reaches out, plus room for the
 * frost to spread without pulling in the empty edge of the copy.
 */
private val LENS_MARGIN = (Glass.REFRACTION_DP + Glass.FROST_DP * 3 + 4).dp

/** A lens for one pane, or nothing on a phone too old to run one. Each pane keeps its own uniforms. */
private fun newLens(): Any? =
    if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(Glass.LENS_SHADER) }.getOrNull() else null

private fun GraphicsLayerScope.glassEffect(lens: Any?, origin: Offset, pane: Size, radius: Float): RenderEffect? {
    val frost = Glass.FROST_DP.dp.toPx()
    return when {
        Build.VERSION.SDK_INT >= 33 && lens != null -> lensEffect(lens as RuntimeShader, origin, pane, radius, frost)
        Build.VERSION.SDK_INT >= 31 -> BlurEffect(frost, frost, TileMode.Clamp)
        else -> null
    }
}

/**
 * Frost first, then the lens: blurring after bending would soften the very edge that makes it glass.
 *
 * Clamped rather than decal at the borders, so the frost does not pull in the transparent black from
 * outside the pane and leave the rim looking singed.
 */
@RequiresApi(33)
private fun GraphicsLayerScope.lensEffect(
    shader: RuntimeShader,
    origin: Offset,
    pane: Size,
    radius: Float,
    frost: Float,
): RenderEffect {
    shader.setFloatUniform("origin", origin.x, origin.y)
    shader.setFloatUniform("size", pane.width, pane.height)
    shader.setFloatUniform("radius", radius)
    shader.setFloatUniform("bezel", Glass.BEZEL_DP.dp.toPx())
    shader.setFloatUniform("strength", Glass.REFRACTION_DP.dp.toPx())
    shader.setFloatUniform("dispersion", Glass.DISPERSION)
    val bend = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    val blur = android.graphics.RenderEffect.createBlurEffect(frost, frost, Shader.TileMode.CLAMP)
    return android.graphics.RenderEffect.createChainEffect(bend, blur).asComposeRenderEffect()
}

/**
 * The pane's own face: a tint so it has a colour, a sheen from above, and the specular rim.
 *
 * The rim is brightest along the top and fades down the sides, with a second, dimmer catch along the
 * bottom -- light from overhead, which is where everybody's light is. It is what makes the edge read as
 * the edge of something thick. Without it the lens alone looks like a distortion filter with no object.
 */
private fun DrawScope.drawGlassFace(radius: Float, tint: Color) {
    val corner = CornerRadius(radius, radius)
    drawRoundRect(tint.copy(alpha = Glass.TINT_ALPHA), cornerRadius = corner)
    drawRoundRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = .10f), .55f to Color.Transparent),
        cornerRadius = corner,
    )
    val line = 1.dp.toPx()
    val inset = line / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = Glass.RIM_ALPHA),
            .45f to Color.White.copy(alpha = .06f),
            .75f to Color.White.copy(alpha = .03f),
            1f to Color.White.copy(alpha = Glass.RIM_ALPHA * .45f),
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(radius - inset, radius - inset),
        style = Stroke(line),
    )
}

/**
 * The page behind the content under glass: the cover that is playing, blurred and pulled most of the way
 * back toward the theme's own background.
 *
 * Not glass itself, and not trying to be. It is what the page is made of, so that what scrolls beneath
 * the panes -- and the gaps between covers -- carry the colour of the record rather than flat black.
 * With nothing playing there is no cover and no wash, only the theme's page, which is the honest answer.
 */
@Composable
fun GlassWash(artworkUrl: String?, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        if (artworkUrl == null) return@Box
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 1f - Glass.BACKDROP_TOWARD_BACKGROUND,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= 31) Modifier.blur(Glass.BACKDROP_BLUR_DP.dp) else Modifier,
                ),
        )
    }
}

/**
 * Material's shape scale, multiplied.
 *
 * One knob for the whole application rather than a number per control: what is being chosen is not the
 * radius of a chip but whether Noctorium looks machined or soft, and corners that disagree with each
 * other read as an oversight rather than a taste. Soft is 1, which is the set of shapes that were there
 * before this was a choice.
 */
fun noctoriumShapes(corner: CornerStyle): Shapes {
    fun radius(dp: Int) = RoundedCornerShape((dp * corner.scale).dp)
    return Shapes(
        extraSmall = radius(4),
        small = radius(8),
        medium = radius(12),
        large = radius(16),
        extraLarge = radius(28),
    )
}
