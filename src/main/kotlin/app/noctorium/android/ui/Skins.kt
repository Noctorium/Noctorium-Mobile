package app.noctorium.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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
import app.noctorium.settings.Windows98Colours
import app.noctorium.settings.WindowsXpColours

/*
 * The Windows themes' skins: what they are drawn with beyond their six colours.
 *
 * Every theme but two is a palette, and those draw exactly as they always have -- every part here asks
 * [LocalSkin] first and, for the ordinary themes, hands straight back to Material's own control with the
 * same arguments it was always given. 98 and XP are not palettes. 98 is bevelled grey slabs lit from the top
 * left, navy title bars and a teal desktop; XP is Luna's rounded blue, its beige dialogs and its green start
 * button. Those are drawn here, from the system colours the core keeps in [Windows98Colours] and
 * [WindowsXpColours] so that the desktop draws the same grey and the same blue.
 */

/** The skin in force: provided once at the root, from the theme. */
val LocalSkin = staticCompositionLocalOf { ThemeSkin.STANDARD }

/** Windows 98's system colours, ready to paint with. */
internal object Win98 {
    val Face = Color(Windows98Colours.FACE)
    val Highlight = Color(Windows98Colours.HIGHLIGHT)
    val Light = Color(Windows98Colours.LIGHT)
    val Shadow = Color(Windows98Colours.SHADOW)
    val DarkShadow = Color(Windows98Colours.DARK_SHADOW)
    val Window = Color(Windows98Colours.WINDOW)
    val Text = Color(Windows98Colours.TEXT)
    val GreyText = Color(Windows98Colours.GREY_TEXT)
    val Selection = Color(Windows98Colours.SELECTION)
    val SelectionText = Color(Windows98Colours.SELECTION_TEXT)
    val Title = Color(Windows98Colours.TITLE)
    val TitleEnd = Color(Windows98Colours.TITLE_END)
    val InactiveTitle = Color(Windows98Colours.INACTIVE_TITLE)
    val InactiveTitleEnd = Color(Windows98Colours.INACTIVE_TITLE_END)
    val TitleText = Color(Windows98Colours.TITLE_TEXT)
    val Tooltip = Color(Windows98Colours.TOOLTIP)
    val Desktop = Color(Windows98Colours.DESKTOP)

    /**
     * The face of a button held down for good -- the taskbar button of the window in use, a toggle that is
     * on -- which 98 drew as a checkerboard of white and grey, one pixel to a square.
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
}

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

/** Draws [edge] round [size] at [topLeft], over whatever is already there. */
internal fun DrawScope.drawEdge98(edge: Edge98, topLeft: Offset = Offset.Zero, size: Size = this.size) {
    val l = topLeft.x
    val t = topLeft.y
    val w = size.width
    val h = size.height
    fun line(colour: Color, x: Float, y: Float, width: Float, height: Float) {
        if (width > 0f && height > 0f) drawRect(colour, Offset(x, y), Size(width, height))
    }
    when (edge) {
        Edge98.SHALLOW -> {
            line(Win98.Shadow, l, t, w - PIXEL, PIXEL)
            line(Win98.Shadow, l, t, PIXEL, h - PIXEL)
            line(Win98.Highlight, l, t + h - PIXEL, w, PIXEL)
            line(Win98.Highlight, l + w - PIXEL, t, PIXEL, h)
        }
        Edge98.ETCHED -> {
            line(Win98.Shadow, l, t, w - PIXEL, PIXEL)
            line(Win98.Shadow, l, t, PIXEL, h - PIXEL)
            line(Win98.Shadow, l, t + h - PIXEL * 2, w - PIXEL, PIXEL)
            line(Win98.Shadow, l + w - PIXEL * 2, t, PIXEL, h - PIXEL)
            line(Win98.Highlight, l + PIXEL, t + PIXEL, w - PIXEL * 3, PIXEL)
            line(Win98.Highlight, l + PIXEL, t + PIXEL, PIXEL, h - PIXEL * 3)
            line(Win98.Highlight, l, t + h - PIXEL, w, PIXEL)
            line(Win98.Highlight, l + w - PIXEL, t, PIXEL, h)
        }
        else -> {
            val (outerLit, outerDark, innerLit, innerDark) = when (edge) {
                Edge98.RAISED -> listOf(Win98.Highlight, Win98.DarkShadow, Win98.Light, Win98.Shadow)
                Edge98.WINDOW -> listOf(Win98.Light, Win98.DarkShadow, Win98.Highlight, Win98.Shadow)
                Edge98.PRESSED -> listOf(Win98.DarkShadow, Win98.Highlight, Win98.Shadow, Win98.Light)
                else -> listOf(Win98.Shadow, Win98.Highlight, Win98.DarkShadow, Win98.Light)
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
