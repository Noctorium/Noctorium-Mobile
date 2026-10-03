package app.noctorium.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.resolvedAccent
import app.noctorium.settings.themeColours

/**
 * Noctorium's look, worked out from the shared preferences: the theme's colours with the accent in force,
 * the corners, the typeface, the text size, whether things move, and whether the phone can shape the sound.
 *
 * The theme and the accent are the shared preferences, so a theme picked on the desktop is the theme here.
 * Kept apart from the activity so that the screenshots rendered on the JVM are drawn in exactly this theme
 * rather than in a copy of it that could drift.
 */
@Composable
internal fun NoctoriumTheme(
    preferences: NoctoriumPreferences,
    equalizerAvailable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    MaterialTheme(
        // A colour still being dragged on the accent picker comes first, so the whole interface recolours
        // under the finger before anything is saved; see AccentPreview.
        colorScheme = noctoriumColors(preferences.themeColours(), Color(AccentPreview.argb ?: preferences.resolvedAccent(null))),
        shapes = noctoriumShapes(preferences.cornerStyle),
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
            content = content,
        )
    }
}
