package app.noctorium.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.DesktopPreferences
import app.noctorium.settings.GlowShape
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.NowPlayingBackdrop
import app.noctorium.settings.NowPlayingPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.onAccent
import app.noctorium.settings.themeColours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Glow themes on the phone: their light drawn the way core says it is to be drawn, the writing on a white
 * accent, the places a white accent has to mark what is chosen some other way, and Now playing showing the light
 * where the wash would have been -- each without anything changing for the themes there were before.
 */
class GlowThemesTest {

    private val glowThemes = ThemePreset.entries.filter { it.glow != null }

    @Test
    fun `the four Glow themes are the ones with a light, and the theme picker finds them as a family of their own`() {
        assertEquals(listOf(ThemePreset.AFTERGLOW, ThemePreset.CINDER, ThemePreset.VELVET, ThemePreset.SMOKE), glowThemes)
        assertEquals(glowThemes, ThemePreset.entries.groupBy { it.family }.getValue("Glow"))
    }

    /** One colour along each gradient, only its opacity falling away, through core's stops. */
    @Test
    fun `each shape of a glow is one gradient of one colour through core's falloff`() {
        glowThemes.forEach { theme ->
            val glow = assertNotNull(theme.glow)
            val colour = Color(glow.colour)
            glow.placement.shapes.forEach { shape ->
                val stops = glowStops(glow, shape, colour)
                assertEquals(GlowShape.FALLOFF.map { it.first }, stops.map { it.first }, "$theme")
                val strength = glow.strength * when (shape) {
                    is GlowShape.Pool -> shape.strength
                    is GlowShape.Band -> shape.strength
                }
                stops.zip(GlowShape.FALLOFF).forEach { (stop, falloff) ->
                    assertEquals(colour.copy(alpha = 1f), stop.second.copy(alpha = 1f), "$theme keeps one colour")
                    assertEquals(strength * falloff.second, stop.second.alpha, .002f, "$theme at ${falloff.first}")
                }
                assertEquals(0f, stops.last().second.alpha, "$theme is clear at its rim")
            }
        }
    }

    /** Where a single shape is brightest, what is drawn is what core says the light is there. */
    @Test
    fun `the brightest of a light across the top is as strong as core says`() {
        listOf(ThemePreset.VELVET, ThemePreset.SMOKE).forEach { theme ->
            val glow = assertNotNull(theme.glow)
            val band = glow.placement.shapes.single()
            assertEquals(glow.opacityAt(200f, 0f, 400f, 900f), glowStops(glow, band, Color.White).first().second.alpha, .002f)
            // Gone by the depth core gives it, as the gradient's last stop is.
            assertEquals(0f, glow.opacityAt(200f, 900f * .62f + 1f, 400f, 900f))
        }
    }

    /** The sum the phone did for itself before core had one, written out, so core's can be held to its answers. */
    private fun oldOnAccent(accent: Color): Color =
        if (accent.luminance() > .35f) {
            Color(accent.red + (0f - accent.red) * .84f, accent.green + (0f - accent.green) * .84f, accent.blue + (0f - accent.blue) * .84f)
        } else {
            Color.White
        }

    @Test
    fun `writing on the accent is core's, which for every older theme's accent and every named one is what it always was`() {
        val accents = ThemePreset.entries.filter { it.glow == null }.mapNotNull { it.colours?.accent } + AccentPreset.entries.mapNotNull { it.argb }
        accents.forEach { accent ->
            val scheme = noctoriumColors(ThemePreset.NOCTORIUM_NIGHT.colours!!, Color(accent))
            assertEquals(Color(onAccent(accent)), scheme.onPrimary, accent.toString(16))
            assertEquals(oldOnAccent(Color(accent)).toArgb(), scheme.onPrimary.toArgb(), accent.toString(16))
        }
    }

    @Test
    fun `a white accent is written on in near-black, and a red one in white`() {
        val velvet = noctoriumColors(ThemePreset.VELVET.colours!!, Color(ThemePreset.VELVET.colours!!.accent))
        assertEquals(Color(0xFF292929), velvet.onPrimary)
        val afterglow = noctoriumColors(ThemePreset.AFTERGLOW.colours!!, Color(ThemePreset.AFTERGLOW.colours!!.accent))
        assertTrue(afterglow.onPrimary.luminance() < .05f)
        val cinder = noctoriumColors(ThemePreset.CINDER.colours!!, Color(ThemePreset.CINDER.colours!!.accent))
        assertEquals(Color.White, cinder.onPrimary)
    }

    /**
     * The accent reads as the writing on Afterglow, Velvet and Smoke, whose accent is white, and on nothing else: every
     * other theme's own accent is told from its writing by its colour, Catppuccin's lavender on its pale blue included.
     */
    @Test
    fun `only the white accents read as the writing`() {
        ThemePreset.entries.forEach { theme ->
            val colours = NoctoriumPreferences(theme = theme).themeColours()
            val scheme = noctoriumColors(colours, Color(colours.accent))
            val white = theme in setOf(ThemePreset.AFTERGLOW, ThemePreset.VELVET, ThemePreset.SMOKE)
            assertEquals(white, accentReadsAsWriting(scheme.primary, scheme.onSurface), "$theme")
        }
        // The named accents on the dark theme and the light one, none of them white.
        listOf(ThemePreset.NOCTORIUM_NIGHT, ThemePreset.NOCTORIUM_DAY).forEach { theme ->
            AccentPreset.entries.mapNotNull { it.argb }.forEach { accent ->
                val scheme = noctoriumColors(theme.colours!!, Color(accent))
                assertFalse(accentReadsAsWriting(scheme.primary, scheme.onSurface), "$theme with ${accent.toString(16)}")
            }
        }
        // Anybody's own accent picked as pale as the writing reads as it too, on any theme.
        assertTrue(accentReadsAsWriting(Color(0xFFFFFFFF), Color(ThemePreset.NOCTORIUM_NIGHT.colours!!.text)))
        assertTrue(accentReadsAsWriting(Color(0xFF111111), Color(ThemePreset.NOCTORIUM_DAY.colours!!.text)))
    }

    private fun preferences(theme: ThemePreset, ambient: Boolean = true, backdrop: NowPlayingBackdrop = NowPlayingBackdrop.WASH) =
        NoctoriumPreferences(
            theme = theme,
            ambientBackdrop = ambient,
            desktop = DesktopPreferences(nowPlaying = NowPlayingPreferences(backdrop = backdrop)),
        )

    @Test
    fun `on the older themes the cover is blurred behind Now playing exactly when the ambient backdrop is on`() {
        ThemePreset.entries.filter { it.glow == null }.forEach { theme ->
            NowPlayingBackdrop.entries.forEach { backdrop ->
                assertTrue(drawsBlurredCover(preferences(theme, backdrop = backdrop), PhoneNowPlayingLayout.CLASSIC, hasCover = true), "$theme")
                assertFalse(drawsBlurredCover(preferences(theme, ambient = false, backdrop = backdrop), PhoneNowPlayingLayout.CLASSIC, hasCover = true))
            }
            assertFalse(drawsBlurredCover(preferences(theme), PhoneNowPlayingLayout.CLASSIC, hasCover = false))
            assertFalse(drawsBlurredCover(preferences(theme), PhoneNowPlayingLayout.FULL_COVER, hasCover = true))
        }
    }

    @Test
    fun `on a Glow theme Now playing shows the page's light, unless the blurred cover was asked for`() {
        glowThemes.forEach { theme ->
            assertFalse(drawsBlurredCover(preferences(theme), PhoneNowPlayingLayout.SING_ALONG, hasCover = true), "$theme")
            assertFalse(drawsBlurredCover(preferences(theme, backdrop = NowPlayingBackdrop.PLAIN), PhoneNowPlayingLayout.CLASSIC, hasCover = true))
            assertTrue(drawsBlurredCover(preferences(theme, backdrop = NowPlayingBackdrop.COVER), PhoneNowPlayingLayout.CLASSIC, hasCover = true))
            assertFalse(drawsBlurredCover(preferences(theme, ambient = false, backdrop = NowPlayingBackdrop.COVER), PhoneNowPlayingLayout.CLASSIC, hasCover = true))
        }
    }
}
