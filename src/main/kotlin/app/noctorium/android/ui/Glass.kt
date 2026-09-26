package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.noctorium.settings.CornerStyle
import app.noctorium.settings.Glass
import app.noctorium.settings.SurfaceStyle
import coil.compose.AsyncImage

/**
 * The same palette with its surfaces turned to glass.
 *
 * The sums are the desktop's, for the reason the two colour schemes are the same sums: a look chosen on
 * one should be the look on the other. Applied after the scheme is built rather than folded into it, so
 * Solid goes down exactly the path it always did.
 *
 * Only the surfaces move. The accent, the writing and the error colours keep their opacity, because a
 * warning you can see the wallpaper through is a worse warning.
 */
fun ColorScheme.asGlass(style: SurfaceStyle): ColorScheme {
    if (!style.isGlass) return this
    fun Color.pane(alpha: Float) = copy(alpha = alpha)
    return copy(
        surface = surfaceContainer.pane(Glass.PANEL_ALPHA),
        surfaceVariant = surfaceVariant.pane(Glass.CARD_ALPHA),
        surfaceBright = surfaceBright.pane(Glass.CARD_ALPHA),
        surfaceDim = surfaceDim.pane(Glass.PANEL_ALPHA),
        surfaceContainer = surfaceContainer.pane(Glass.PANEL_ALPHA),
        surfaceContainerHigh = surfaceContainerHigh.pane(Glass.CARD_ALPHA),
        surfaceContainerHighest = surfaceContainerHighest.pane(Glass.CARD_ALPHA),
        surfaceContainerLow = surfaceContainerLow.pane(Glass.PANEL_ALPHA),
        surfaceContainerLowest = surfaceContainerLowest.pane(Glass.PANEL_ALPHA),
    )
}

/**
 * The wash behind everything, which is the half of glass that is easy to forget.
 *
 * Translucent panels over a flat black page are not glass, they are slightly darker panels. There has to
 * be something behind them worth seeing through, and the thing Noctorium always has to hand is the cover
 * that is playing.
 *
 * This is the now playing screen's own backdrop promoted to the whole application, with a heavier scrim:
 * that one sits behind a title and a seek bar, this one sits behind entire lists of text. Blur wants API
 * 31; below it the cover is simply left soft and the scrim carries the rest, which is the same bargain
 * the now playing screen already makes.
 *
 * With nothing playing there is no cover and no wash -- just the theme's page, which is the honest answer
 * rather than a blank slab of glass over nothing.
 */
@Composable
fun GlassBackdrop(artworkUrl: String?, modifier: Modifier = Modifier) {
    // The page colour, which glass deliberately leaves opaque: a full-screen sheet that goes translucent
    // stops being a sheet and becomes a window onto whatever was behind it, which is what this was on
    // first showing -- the now playing screen with the home screen legible through it.
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
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        Modifier.blur(Glass.BACKDROP_BLUR_DP.dp)
                    } else {
                        Modifier
                    },
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
