package app.noctorium.android.ui

import androidx.compose.ui.graphics.Color
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.SeekBar
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.Windows98Colours
import app.noctorium.settings.Windows98Palette
import app.noctorium.settings.contrastRatio
import app.noctorium.settings.windows98Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The 98 skin in its two schemes: 98's own, which has to paint exactly the colours it always did, and
 * Noctorium 98's night, which the theme in force picks -- and the few things round the skin that have to know
 * which way round it is.
 */
class Windows98Test {

    @Test
    fun `98's own scheme paints exactly the colours 98 always had`() {
        val paint = Win98Paint.STANDARD
        assertEquals(Color(Windows98Colours.FACE), paint.Face)
        assertEquals(Color(Windows98Colours.HIGHLIGHT), paint.Highlight)
        assertEquals(Color(Windows98Colours.LIGHT), paint.Light)
        assertEquals(Color(Windows98Colours.SHADOW), paint.Shadow)
        assertEquals(Color(Windows98Colours.DARK_SHADOW), paint.DarkShadow)
        assertEquals(Color(Windows98Colours.WINDOW), paint.Window)
        assertEquals(Color(Windows98Colours.TEXT), paint.Text)
        assertEquals(Color(Windows98Colours.GREY_TEXT), paint.GreyText)
        assertEquals(Color(Windows98Colours.SELECTION), paint.Selection)
        assertEquals(Color(Windows98Colours.SELECTION_TEXT), paint.SelectionText)
        assertEquals(Color(Windows98Colours.TITLE), paint.Title)
        assertEquals(Color(Windows98Colours.TITLE_END), paint.TitleEnd)
        assertEquals(Color(Windows98Colours.INACTIVE_TITLE), paint.InactiveTitle)
        assertEquals(Color(Windows98Colours.INACTIVE_TITLE_END), paint.InactiveTitleEnd)
        assertEquals(Color(Windows98Colours.TITLE_TEXT), paint.TitleText)
        assertEquals(Color(Windows98Colours.TOOLTIP), paint.Tooltip)
        assertEquals(Color(Windows98Colours.DESKTOP), paint.Desktop)
    }

    /** The Classic seek bar draws in 98's scheme under every theme but Noctorium 98, and must not change for it. */
    @Test
    fun `the Classic seek bar keeps its grey thumb and its black and white bevel under 98's own scheme`() {
        assertEquals(Color(SeekBar.CLASSIC_FACE), Win98Paint.STANDARD.Face)
        assertEquals(Color.White, Win98Paint.STANDARD.Highlight)
        assertEquals(Color.Black, Win98Paint.STANDARD.DarkShadow)
    }

    @Test
    fun `Noctorium 98 is drawn in the night and every other theme in 98's grey`() {
        ThemePreset.entries.forEach { theme ->
            val paint = Win98Paint.of(NoctoriumPreferences(theme = theme).windows98Palette)
            val expected = if (theme == ThemePreset.WINDOWS_98_NOCTORIUM) Win98Paint.NOCTORIUM else Win98Paint.STANDARD
            assertSame(expected, paint, "$theme")
        }
        assertEquals(Color(0xFF231B2E), Win98Paint.NOCTORIUM.Face)
        assertEquals(Color(0xFF140A26), Win98Paint.NOCTORIUM.Desktop)
    }

    /** Each scheme's paint is made once, so its dithered face -- a bitmap -- is made once too. */
    @Test
    fun `a scheme the themes use is always the same paint`() {
        assertSame(Win98Paint.STANDARD, Win98Paint.of(Windows98Palette.STANDARD))
        assertSame(Win98Paint.NOCTORIUM, Win98Paint.of(Windows98Palette.NOCTORIUM.copy()))
    }

    @Test
    fun `the theme picker offers Noctorium 98 among the Windows themes, between 98 and XP`() {
        val windows = ThemePreset.entries.groupBy { it.family }.getValue("Windows")
        assertEquals(listOf(ThemePreset.WINDOWS_98, ThemePreset.WINDOWS_98_NOCTORIUM, ThemePreset.WINDOWS_XP), windows)
    }

    /**
     * A cover with no picture shows a note in the scheme's greyed writing. 98's shadow is the same grey, and was
     * what it used to be drawn in; Noctorium 98's shadow is all but its face, and the note would vanish.
     */
    @Test
    fun `a cover's note shows on Noctorium 98's face at least as well as on 98's`() {
        val night = Windows98Palette.NOCTORIUM
        val grey = Windows98Palette.STANDARD
        assertEquals(grey.shadow, grey.greyText)
        assertTrue(contrastRatio(night.shadow, night.face) < 1.5)
        assertTrue(contrastRatio(night.greyText, night.face) >= contrastRatio(grey.greyText, grey.face))
    }

    @Test
    fun `the system bars' icons turn light for Noctorium 98 and stay dark for 98`() {
        fun icons(theme: ThemePreset) = systemBarIcons(NoctoriumPreferences(theme = theme))
        assertEquals(SystemBarIcons(darkStatusBar = true, darkNavigationBar = true), icons(ThemePreset.WINDOWS_98))
        assertEquals(SystemBarIcons(darkStatusBar = false, darkNavigationBar = false), icons(ThemePreset.WINDOWS_98_NOCTORIUM))
        // XP's blue taskbar runs on under the navigation, which wants light icons on its light theme.
        assertEquals(SystemBarIcons(darkStatusBar = true, darkNavigationBar = false), icons(ThemePreset.WINDOWS_XP))
        assertEquals(SystemBarIcons(darkStatusBar = false, darkNavigationBar = false), icons(ThemePreset.NOCTORIUM_NIGHT))
        assertEquals(SystemBarIcons(darkStatusBar = true, darkNavigationBar = true), icons(ThemePreset.NOCTORIUM_DAY))
    }

    @Test
    fun `the Customization tile names a theme once, in full`() {
        assertEquals("Windows 98", themeName(ThemePreset.WINDOWS_98))
        assertEquals("Noctorium 98", themeName(ThemePreset.WINDOWS_98_NOCTORIUM))
        assertEquals("Windows XP", themeName(ThemePreset.WINDOWS_XP))
        assertEquals("Catppuccin Mocha", themeName(ThemePreset.CATPPUCCIN_MOCHA))
        assertEquals("Noctorium Night", themeName(ThemePreset.NOCTORIUM_NIGHT))
        assertEquals("Nord", themeName(ThemePreset.NORD))
        assertEquals("Tokyo Night", themeName(ThemePreset.TOKYO_NIGHT))
        assertEquals("Solarized Dark", themeName(ThemePreset.SOLARIZED_DARK))
        assertEquals("Your own colours", themeName(ThemePreset.CUSTOM))
    }
}
