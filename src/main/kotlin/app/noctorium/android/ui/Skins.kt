package app.noctorium.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.Windows98Palette
import app.noctorium.settings.WindowsXpColours

/*
 * The Windows themes' skins: what they are drawn with beyond their six colours.
 *
 * Every theme but three is a palette, and those draw exactly as they always have -- every part here asks
 * [LocalSkin] first and, for the ordinary themes, hands straight back to Material's own control with the
 * same arguments it was always given. 98, Noctorium 98 and XP are not palettes. 98 is bevelled grey slabs lit
 * from the top left, navy title bars and a teal desktop, and Noctorium 98 is the same at night; XP is Luna's
 * rounded blue, its beige dialogs and its green start button. Those are drawn here, from the system colours
 * the core keeps in [Windows98Palette] and [WindowsXpColours] so that the desktop draws the same grey, the
 * same violet and the same blue.
 */

/** The skin in force: provided once at the root, from the theme. */
val LocalSkin = staticCompositionLocalOf { ThemeSkin.STANDARD }

/**
 * A 98 scheme's system colours, ready to paint with: 98's own grey, or Noctorium 98's night. Every part of the
 * 98 skin takes its colours from here and none from a grey of its own, as every part of 98 took them from the
 * scheme chosen in Display Properties.
 */
@Immutable
internal class Win98Paint private constructor(palette: Windows98Palette) {
    val Face = Color(palette.face)
    val Highlight = Color(palette.highlight)
    val Light = Color(palette.light)
    val Shadow = Color(palette.shadow)
    val DarkShadow = Color(palette.darkShadow)
    val Window = Color(palette.window)
    val Text = Color(palette.text)
    val GreyText = Color(palette.greyText)
    val Selection = Color(palette.selection)
    val SelectionText = Color(palette.selectionText)
    val Title = Color(palette.title)
    val TitleEnd = Color(palette.titleEnd)
    val InactiveTitle = Color(palette.inactiveTitle)
    val InactiveTitleEnd = Color(palette.inactiveTitleEnd)
    val TitleText = Color(palette.titleText)
    val Tooltip = Color(palette.tooltip)
    val Desktop = Color(palette.desktop)

    /**
     * The face of a button held down for good -- the taskbar button of the window in use, a toggle that is
     * on -- which 98 drew as a checkerboard of its face and its highlight, one pixel to a square: white and
     * grey in 98's own scheme. Made the first time it is wanted, once for each scheme.
     */
    val Dither: Brush by lazy {
        val tile = ImageBitmap(2, 2)
        val canvas = androidx.compose.ui.graphics.Canvas(tile)
        val paint = Paint()
        paint.color = Face
        canvas.drawRect(0f, 0f, 2f, 2f, paint)
        paint.color = Highlight
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        canvas.drawRect(1f, 1f, 2f, 2f, paint)
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }

    companion object {
        val STANDARD = Win98Paint(Windows98Palette.STANDARD)
        val NOCTORIUM = Win98Paint(Windows98Palette.NOCTORIUM)

        /** The paint for [palette]: the same one each time for the two the themes use, so each dithers once. */
        fun of(palette: Windows98Palette): Win98Paint = when (palette) {
            Windows98Palette.STANDARD -> STANDARD
            Windows98Palette.NOCTORIUM -> NOCTORIUM
            else -> Win98Paint(palette)
        }
    }
}

/**
 * The 98 scheme in force: provided once at the root from the theme, beside [LocalSkin].
 *
 * A composition local rather than a global the theme sets, so the scheme changes the way everything else in
 * the look does -- the root recomposes with the new theme and the whole tree follows -- and a part of the
 * screen can be drawn in another scheme by providing it there. The ordinary themes get 98's own, which is
 * what the Classic seek bar draws its thumb in under them.
 */
internal val LocalWin98 = staticCompositionLocalOf { Win98Paint.STANDARD }

/**
 * The 98 colours in force, for anything being composed: `Win98.Face` reads as it did when 98 had only the one
 * scheme.
 *
 * Drawing cannot read it. A `drawBehind`, a `drawWithContent` or a Canvas runs after composition, outside
 * it, where a composition local is not there to be read -- so the composable round a drawing reads the paint
 * here, once, and hands it in, which is why [drawEdge98], [drawTrackbar] and the rest take one. The compiler
 * holds to this: a drawing that reached for `Win98` itself would not build.
 */
internal val Win98: Win98Paint
    @Composable @ReadOnlyComposable
    get() = LocalWin98.current

/** Luna's colours, ready to paint with. */
internal object Luna {
    val Face = Color(WindowsXpColours.FACE)
    val Window = Color(WindowsXpColours.WINDOW)
    val Text = Color(WindowsXpColours.TEXT)
    val GreyText = Color(WindowsXpColours.GREY_TEXT)
    val Selection = Color(WindowsXpColours.SELECTION)
    val SelectionText = Color(WindowsXpColours.SELECTION_TEXT)
    val TitleTop = Color(WindowsXpColours.TITLE_TOP)
    val Title = Color(WindowsXpColours.TITLE)
    val TitleLow = Color(WindowsXpColours.TITLE_LOW)
    val TitleFoot = Color(WindowsXpColours.TITLE_FOOT)
    val InactiveTitle = Color(WindowsXpColours.INACTIVE_TITLE)
    val TitleText = Color(WindowsXpColours.TITLE_TEXT)
    val Frame = Color(WindowsXpColours.FRAME)
    val CaptionButton = Color(WindowsXpColours.CAPTION_BUTTON)
    val Close = Color(WindowsXpColours.CLOSE)
    val ButtonEdge = Color(WindowsXpColours.BUTTON_EDGE)
    val ButtonFoot = Color(WindowsXpColours.BUTTON_FOOT)
    val Hot = Color(WindowsXpColours.HOT)
    val Focus = Color(WindowsXpColours.FOCUS)
    val FieldEdge = Color(WindowsXpColours.FIELD_EDGE)
    val GroupEdge = Color(WindowsXpColours.GROUP_EDGE)
    val GroupTitle = Color(WindowsXpColours.GROUP_TITLE)
    val TabEdge = Color(WindowsXpColours.TAB_EDGE)
    val TabChosen = Color(WindowsXpColours.TAB_CHOSEN)
    val ProgressLight = Color(WindowsXpColours.PROGRESS_LIGHT)
    val Progress = Color(WindowsXpColours.PROGRESS)
    val TaskbarTop = Color(WindowsXpColours.TASKBAR_TOP)
    val Taskbar = Color(WindowsXpColours.TASKBAR)
    val TaskbarFoot = Color(WindowsXpColours.TASKBAR_FOOT)
    val Start = Color(WindowsXpColours.START)
    val StartLight = Color(WindowsXpColours.START_LIGHT)
    val Tray = Color(WindowsXpColours.TRAY)
    val TrayEdge = Color(WindowsXpColours.TRAY_EDGE)
    val TaskPaneTop = Color(WindowsXpColours.TASK_PANE_TOP)
    val TaskPaneFoot = Color(WindowsXpColours.TASK_PANE_FOOT)
    val TaskPanel = Color(WindowsXpColours.TASK_PANEL)
    val TaskPanelTitle = Color(WindowsXpColours.TASK_PANEL_TITLE)
    val Sky = Color(WindowsXpColours.SKY)
    val SkyLow = Color(WindowsXpColours.SKY_LOW)
    val Hill = Color(WindowsXpColours.HILL)
    val HillShade = Color(WindowsXpColours.HILL_SHADE)

    /** A button's face: white at the top, fading to Luna's grey-beige at its foot. */
    val ButtonFace: Brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F3EE), ButtonFoot))

    /** The same held down: darker, and lit from below rather than above. */
    val ButtonPressed: Brush = Brush.verticalGradient(listOf(Color(0xFFE2E1D6), Color(0xFFE6E5DC), Color(0xFFF2F1EA)))
}

/**
 * How a 98 edge is lit, as Windows drew them: two lines on each side, one device pixel apiece.
 *
 * A button is raised with white outermost along its top and left; a window's frame has the light grey
 * outside and the white within. Held down, a button sinks: black outermost along the top and left. A field
 * or a list is sunk into its face, grey then black above and to the left, white then light grey below and
 * to the right. A tray or a status bar's panel is sunk by a single line, and a group box is etched: a grey
 * line with a white one a pixel inside it.
 */
internal enum class Edge98 { RAISED, WINDOW, PRESSED, SUNKEN, SHALLOW, ETCHED }

/** Draws [edge] round [size] at [topLeft] in [win98]'s colours, over whatever is already there. */
internal fun DrawScope.drawEdge98(edge: Edge98, win98: Win98Paint, topLeft: Offset = Offset.Zero, size: Size = this.size) {
    val l = topLeft.x
    val t = topLeft.y
    val w = size.width
    val h = size.height
    fun line(colour: Color, x: Float, y: Float, width: Float, height: Float) {
        if (width > 0f && height > 0f) drawRect(colour, Offset(x, y), Size(width, height))
    }
    when (edge) {
        Edge98.SHALLOW -> {
            line(win98.Shadow, l, t, w - PIXEL, PIXEL)
            line(win98.Shadow, l, t, PIXEL, h - PIXEL)
            line(win98.Highlight, l, t + h - PIXEL, w, PIXEL)
            line(win98.Highlight, l + w - PIXEL, t, PIXEL, h)
        }
        Edge98.ETCHED -> {
            line(win98.Shadow, l, t, w - PIXEL, PIXEL)
            line(win98.Shadow, l, t, PIXEL, h - PIXEL)
            line(win98.Shadow, l, t + h - PIXEL * 2, w - PIXEL, PIXEL)
            line(win98.Shadow, l + w - PIXEL * 2, t, PIXEL, h - PIXEL)
            line(win98.Highlight, l + PIXEL, t + PIXEL, w - PIXEL * 3, PIXEL)
            line(win98.Highlight, l + PIXEL, t + PIXEL, PIXEL, h - PIXEL * 3)
            line(win98.Highlight, l, t + h - PIXEL, w, PIXEL)
            line(win98.Highlight, l + w - PIXEL, t, PIXEL, h)
        }
        else -> {
            val (outerLit, outerDark, innerLit, innerDark) = when (edge) {
                Edge98.RAISED -> listOf(win98.Highlight, win98.DarkShadow, win98.Light, win98.Shadow)
                Edge98.WINDOW -> listOf(win98.Light, win98.DarkShadow, win98.Highlight, win98.Shadow)
                Edge98.PRESSED -> listOf(win98.DarkShadow, win98.Highlight, win98.Shadow, win98.Light)
                else -> listOf(win98.Shadow, win98.Highlight, win98.DarkShadow, win98.Light)
            }
            line(outerLit, l, t, w - PIXEL, PIXEL)
            line(outerLit, l, t, PIXEL, h - PIXEL)
            line(outerDark, l, t + h - PIXEL, w, PIXEL)
            line(outerDark, l + w - PIXEL, t, PIXEL, h)
            line(innerLit, l + PIXEL, t + PIXEL, w - PIXEL * 3, PIXEL)
            line(innerLit, l + PIXEL, t + PIXEL, PIXEL, h - PIXEL * 3)
            line(innerDark, l + PIXEL, t + h - PIXEL * 2, w - PIXEL * 2, PIXEL)
            line(innerDark, l + w - PIXEL * 2, t + PIXEL, PIXEL, h - PIXEL * 2)
        }
    }
}

/** One device pixel: a 98 edge is drawn in whole pixels, never in density-independent ones. */
private const val PIXEL = 1f

/**
 * Material's shapes for each skin: 98 had no rounded corner anywhere, and Luna rounded its buttons by three
 * pixels and its windows a little more. The ordinary themes keep the listener's own corners.
 */
internal fun skinShapes(skin: ThemeSkin): Shapes? = when (skin) {
    ThemeSkin.STANDARD -> null
    ThemeSkin.WINDOWS_98 -> Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )
    ThemeSkin.WINDOWS_XP -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(3.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
}
