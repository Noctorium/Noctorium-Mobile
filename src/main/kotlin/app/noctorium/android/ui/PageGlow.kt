package app.noctorium.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.MaterialTheme
import app.noctorium.settings.GlowShape
import app.noctorium.settings.ThemeGlow
import kotlin.math.max

/**
 * The light a Glow theme lays across its page, as the phone draws it: Afterglow's and Cinder's from behind the
 * cover and up beside it, Velvet's and Smoke's across the top.
 *
 * The page's colour, then this, then everything else. The glow is part of the page -- under a screen's writing, in
 * the gaps between its covers -- and it is under every panel and card, which cover it in their own colours: the
 * player bar, the tabs and Settings' cards are not lit, the page round them is. Each of its placement's shapes is
 * one gradient of one colour with only its opacity falling away, as [ThemeGlow] says a player is to draw it, so the
 * light on the phone is the desktop's.
 *
 * Laid on the whole window, under the status bar and the navigation bar, since the page runs on under both: a light
 * across the top starts at the top of the glass rather than under the clock, and the bars' own panels -- the tabs,
 * the player bar -- stand over the foot of it as they stand over the page.
 */
@Stable
internal class PageGlow(val glow: ThemeGlow, colour: State<Color>) {
    /** The light's colour as it stands this frame: Afterglow's glides from one cover's to the next with the accent. */
    val colour: Color by colour
}

/** The glow in force, from [NoctoriumTheme]: null on every theme without one, which is every theme but the Glow ones. */
internal val LocalPageGlow = compositionLocalOf<PageGlow?> { null }

/**
 * The stops of the one gradient [shape] of [glow] is drawn as, in [colour]: [GlowShape.FALLOFF]'s, each as opaque
 * as the glow's strength times the shape's own times what the falloff leaves there. The colour never changes along
 * it, only how much of it there is, which is what keeps Compose's gradient the same as a browser's.
 */
internal fun glowStops(glow: ThemeGlow, shape: GlowShape, colour: Color): Array<Pair<Float, Color>> {
    val strength = glow.strength * when (shape) {
        is GlowShape.Pool -> shape.strength
        is GlowShape.Band -> shape.strength
    }
    return GlowShape.FALLOFF.map { (out, left) -> out to colour.copy(alpha = strength * left) }.toTypedArray()
}

/**
 * [shape]'s gradient on a page [page] in size: a round pool measured against the page's longer side, or a band down
 * from the top. Clear past its rim, since the last stop is.
 */
internal fun glowBrush(glow: ThemeGlow, shape: GlowShape, colour: Color, page: Size): Brush {
    val stops = glowStops(glow, shape, colour)
    return when (shape) {
        is GlowShape.Pool -> Brush.radialGradient(
            *stops,
            center = Offset(shape.x * page.width, shape.y * page.height),
            radius = (shape.radius * max(page.width, page.height)).coerceAtLeast(1f),
        )
        is GlowShape.Band -> Brush.verticalGradient(*stops, startY = 0f, endY = (shape.depth * page.height).coerceAtLeast(1f))
    }
}

/**
 * The page's glow behind whatever this holds, the whole of it standing for the page: nothing on a theme without one.
 *
 * The gradients are made again only when the page changes size or the light its colour, which while a song plays is
 * never; so a seek bar's wave redrawing the screen sixty times a second redraws three gradients, and makes none.
 */
@Composable
internal fun Modifier.pageGlow(): Modifier {
    val glow = LocalPageGlow.current ?: return this
    return drawWithCache {
        val colour = glow.colour
        val brushes = glow.glow.placement.shapes.map { glowBrush(glow.glow, it, colour, size) }
        onDrawBehind { brushes.forEach { drawRect(it) } }
    }
}

/** The page's glow on its own, filling what it is in, for a screen that lays its own page down over the window's. */
@Composable
internal fun PageGlowLayer() {
    Box(Modifier.fillMaxSize().pageGlow())
}

/**
 * [colour] made solid as a card laid on the page shows it, on a theme with a glow.
 *
 * Settings' cards and tiles are a faint tint of the card colour over the page, which on a flat page is the same as a
 * solid colour. Over a glow it is not: the light would come through them, and a card is not lit. So where there is a
 * glow they are the solid colour they would have been over the bare page, and on every other theme they are exactly
 * what they were, see-through glass and all.
 */
@Composable
internal fun cardColour(colour: Color): Color =
    if (LocalPageGlow.current == null) colour else colour.compositeOver(MaterialTheme.colorScheme.background)
