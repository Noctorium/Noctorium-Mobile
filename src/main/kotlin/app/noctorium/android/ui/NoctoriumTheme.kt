package app.noctorium.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.noctorium.android.ArtworkColours
import app.noctorium.android.artworkColour
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.contrastRatio
import app.noctorium.settings.resolvedAccent
import app.noctorium.settings.themeGlow
import app.noctorium.settings.themeSkin
import app.noctorium.settings.themeColours
import app.noctorium.settings.windows98Palette

/**
 * Noctorium's look, worked out from the shared preferences: the theme's colours with the accent in force,
 * the corners, the typeface, the text size, whether things move, whether the phone can shape the sound, the
 * skin -- the rest of the look the Windows themes draw beyond their colours -- the 98 scheme that skin is drawn
 * in, 98's grey or Noctorium 98's night, the icon set every icon is drawn from, whether the services' badges
 * carry their marks or their letters, and the light a Glow theme lays across its page.
 *
 * The theme and the accent are the shared preferences, so a theme picked on the desktop is the theme here.
 * Kept apart from the activity so that the screenshots rendered on the JVM are drawn in exactly this theme
 * rather than in a copy of it that could drift.
 */
@Composable
internal fun NoctoriumTheme(
    preferences: NoctoriumPreferences,
    equalizerAvailable: Boolean = true,
    /**
     * The cover of the song the player shows, null with none: the colour "Match the artwork" takes for the accent,
     * and Afterglow for its light.
     */
    artworkUrl: String? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val skin = preferences.themeSkin
    val glow = preferences.themeGlow
    // Only looked at when something takes its colour from it, so every other theme and accent decodes nothing.
    val artwork = rememberArtworkColour(artworkUrl.takeIf { preferences.accent == AccentPreset.ARTWORK || glow?.followsArtwork == true })
    // A colour taken from the cover glides to the next song's, the accent and the light together, as on the desktop.
    // Any other change of colour -- a theme picked, an accent chosen -- is there at once, as it always was.
    val accent by easedColour(preferences.resolvedAccent(artwork), preferences.accent == AccentPreset.ARTWORK && preferences.animations)
    val light = easedColour(glow?.resolvedColour(artwork) ?: 0L, glow?.followsArtwork == true && preferences.animations)
    val pageGlow = remember(glow, light) { glow?.let { PageGlow(it, light) } }
    MaterialTheme(
        // A colour still being dragged on the accent picker comes first, so the whole interface recolours
        // under the finger before anything is saved; see AccentPreview.
        colorScheme = noctoriumColors(preferences.themeColours(), AccentPreview.argb?.let { Color(it) } ?: accent),
        // The Windows themes bring their own corners: none at all for 98, and Luna's few pixels for XP.
        shapes = skinShapes(skin) ?: noctoriumShapes(preferences.cornerStyle),
        typography = remember(preferences.font) { noctoriumTypography(preferences.font) },
    ) {
        /*
         * Text size, applied to the density rather than to the typography.
         *
         * Almost every size in this application is written at the call site as a literal `.sp`, so scaling
         * MaterialTheme's typography would have moved a handful of labels and left the rest exactly where
         * they were. fontScale is the one lever that reaches every `.sp` there is. It multiplies what Android
         * is already asking for, so a phone set to large text and Noctorium set to large text get both, which
         * is what somebody who set both meant.
         */
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * preferences.textSize.scale),
            LocalMotion provides preferences.animations,
            LocalEqualizerAvailable provides equalizerAvailable,
            LocalSkin provides skin,
            LocalWin98 provides Win98Paint.of(preferences.windows98Palette),
            LocalIconSet provides preferences.iconSet,
            LocalServiceBadgeStyle provides preferences.serviceBadgeStyle,
            LocalPageGlow provides pageGlow,
            content = content,
        )
    }
}

/** [argb] as a colour that glides to each new value where it [eases], and is simply there where it does not. */
@Composable
private fun easedColour(argb: Long, eases: Boolean): State<Color> =
    if (eases) {
        animateColorAsState(Color(argb), tween(ARTWORK_GLIDE_MS, easing = FastOutSlowInEasing), label = "artwork colour")
    } else {
        rememberUpdatedState(Color(argb))
    }

/** How long a colour taken from the cover takes to become the next song's: the desktop's time, so the two agree. */
private const val ARTWORK_GLIDE_MS = 600

/**
 * The colour of the cover at [url], as [artworkColour] works it out: null with no cover, for a cover with no colour in
 * it, and until it is known. Kept from one song to the next while the next one's is worked out, so the colour glides
 * from the last cover's to the new one's rather than by way of the theme's own.
 */
@Composable
internal fun rememberArtworkColour(url: String?): Long? {
    val context = LocalContext.current
    val colour by produceState(ArtworkColours.known(url)?.colour, url) { value = url?.let { artworkColour(context, it) } }
    return colour
}

/**
 * Whether [accent] is the writing's own colour, or near enough that nobody could tell the two apart: the white of
 * Afterglow, Velvet and Smoke on their white writing, or anybody's own accent picked as pale.
 *
 * Whatever is told from its neighbours by being in the accent -- the tab chosen, shuffle switched on, the song playing
 * in a list -- would then look the same as everything round it, so where this is true those places mark it some other
 * way as well. Set below the least contrast between any other theme's accent and its writing, Catppuccin's at nearly
 * 1.4, by enough that none of them is touched.
 */
internal fun accentReadsAsWriting(accent: Color, writing: Color): Boolean =
    contrastRatio(accent.toArgb().toLong() and 0xFFFFFFFFL, writing.toArgb().toLong() and 0xFFFFFFFFL) < ACCENT_AS_WRITING

/**
 * [accentReadsAsWriting] for the accent in force and the writing on a panel. Never under the Windows skins, which mark
 * what is chosen their own way -- a button held down, a row in the selection's blue -- and inside such a row draw the
 * accent and the writing in the one ink on purpose.
 */
@Composable
internal fun accentIsWriting(): Boolean =
    LocalSkin.current == ThemeSkin.STANDARD && MaterialTheme.colorScheme.let { accentReadsAsWriting(it.primary, it.onSurface) }

private const val ACCENT_AS_WRITING = 1.2

/**
 * A button that is on, standing in a faint disc of the accent while the accent is the writing's colour and being
 * drawn in it says nothing: shuffle, repeat, a lit toolbar button. On any other accent it is left as it was, lit by
 * its colour alone.
 */
@Composable
internal fun Modifier.litWash(lit: Boolean, shape: Shape = CircleShape): Modifier =
    if (lit && accentIsWriting()) clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = LIT_WASH_ALPHA)) else this

/** How strong the disc behind a lit button is on a white accent, and the lozenge under the tab chosen. */
internal const val LIT_WASH_ALPHA = .16f

/**
 * Which way round the phone's own bars are drawn over the theme: whether the status bar's icons, and then the
 * navigation bar's, should be the dark ones for a pale page behind them.
 *
 * The page behind them is the theme's own, except under the Windows skins. Under 98 it is the scheme's face,
 * which is grey in 98's own and dark violet in Noctorium 98's, so the icons stay dark on the one and turn light
 * on the other. Under XP the taskbar's blue runs on beneath the navigation, which wants light icons on a light
 * theme too. Got wrong, a light theme has white icons on a white bar, or a dark one black on black.
 */
internal fun systemBarIcons(preferences: NoctoriumPreferences): SystemBarIcons {
    val pale = when (preferences.themeSkin) {
        ThemeSkin.WINDOWS_98 -> !preferences.windows98Palette.dark
        else -> preferences.themeColours().light
    }
    return SystemBarIcons(
        darkStatusBar = pale,
        darkNavigationBar = pale && preferences.themeSkin != ThemeSkin.WINDOWS_XP,
    )
}

/** What [systemBarIcons] decided: true where that bar's icons are drawn dark, for a pale page behind them. */
internal data class SystemBarIcons(val darkStatusBar: Boolean, val darkNavigationBar: Boolean)
