package app.noctorium.android.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import app.noctorium.settings.FontChoice

/** The family each choice means on a phone: the three every Android has, so nothing is downloaded. */
internal fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.DEFAULT -> FontFamily.Default
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
}

/**
 * Material's type scale, set in the chosen family.
 *
 * Applied through the typography rather than at each call site, unlike the text size: almost every `Text`
 * here names a size and nothing else, and a `Text` that names no style takes everything it does not name
 * -- the family included -- from the theme's body style. So one family on every style reaches every line
 * of the interface, chips, fields and tabs among them. The default is Material's own scale untouched,
 * which is exactly what was there before this was a choice.
 */
internal fun noctoriumTypography(font: FontChoice): Typography {
    val base = Typography()
    if (font == FontChoice.DEFAULT) return base
    val family = font.family()
    fun TextStyle.inFamily() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.inFamily(),
        displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(),
        headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(),
        headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = base.titleLarge.inFamily(),
        titleMedium = base.titleMedium.inFamily(),
        titleSmall = base.titleSmall.inFamily(),
        bodyLarge = base.bodyLarge.inFamily(),
        bodyMedium = base.bodyMedium.inFamily(),
        bodySmall = base.bodySmall.inFamily(),
        labelLarge = base.labelLarge.inFamily(),
        labelMedium = base.labelMedium.inFamily(),
        labelSmall = base.labelSmall.inFamily(),
    )
}
