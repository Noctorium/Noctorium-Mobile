package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.Destination
import app.noctorium.settings.ThemeSkin
import coil.compose.AsyncImage

/*
 * The Windows skins' chrome: title bars and their caption buttons, windows, the desktop, the taskbar, group
 * boxes and list boxes. Only ever drawn under a skin; the ordinary themes never reach any of it.
 */

/** The glyph a title bar's button shows: Windows' close cross and its minimise bar. */
internal enum class Caption { CLOSE, MINIMISE }

/**
 * A title bar: the window's icon, its name, and its caption buttons at the end.
 *
 * 98's runs from navy to a lighter blue across, with the name in bold white; XP's is Luna's deep blue, lit
 * along its top and darker at its foot, rounded at the two top corners, with the name in white bold over a
 * shadow.
 */
@Composable
internal fun SkinTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    buttons: @Composable RowScope.() -> Unit = {},
) {
    val skin = LocalSkin.current
    val xp = skin == ThemeSkin.WINDOWS_XP
    val background = if (xp) {
        Modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(Brush.verticalGradient(0f to Luna.TitleTop, .12f to Luna.Title, .82f to Luna.TitleLow, 1f to Luna.TitleFoot))
    } else {
        Modifier.background(Brush.horizontalGradient(listOf(Win98.Title, Win98.TitleEnd)))
    }
    Row(
        modifier.fillMaxWidth().height(if (xp) 34.dp else 30.dp).then(background).padding(start = if (xp) 8.dp else 5.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowIcon(if (xp) 18.dp else 16.dp)
        Spacer(Modifier.width(if (xp) 7.dp else 6.dp))
        Text(
            title,
            color = if (xp) Luna.TitleText else Win98.TitleText,
            style = TextStyle(
                fontSize = if (xp) 15.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                shadow = if (xp) Shadow(Color(0xFF0A246A), Offset(2f, 2f), 1.5f) else null,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(if (xp) 3.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    }
}

/**
 * A screen's title under the skins: the screen is a window, so its name is a title bar across the top, with
 * what the page is and its own buttons on a toolbar under it, the way Explorer's windows had theirs.
 */
@Composable
internal fun SkinScreenTitle(title: String, subtitle: String?, close: (() -> Unit)?, action: @Composable (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        SkinTitleBar(title) {
            if (close != null) CaptionButton(Caption.CLOSE, "Close", close)
        }
        if (subtitle != null || action != null) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    subtitle.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            SkinSeparator()
        }
    }
}

/** Noctorium's N on a tile of the launcher icon's black, the size of a window's icon. */
@Composable
internal fun WindowIcon(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 6)).background(Color(0xFF0B0B10)),
        contentAlignment = Alignment.Center,
    ) {
        NoctoriumMark(Color.White, Modifier.size(size * .62f))
    }
}

/**
 * One of a title bar's buttons: 98's raised grey square with a black glyph, or XP's rounded blue one with a
 * white glyph -- and XP's close in its red. Wider to the touch than it looks, since a title bar's buttons
 * were made for a mouse pointer: the whole of the bar's height round it, and a little either side.
 */
@Composable
internal fun CaptionButton(caption: Caption, description: String, onClick: () -> Unit) {
    CaptionButtonFrame(description, onClick, red = caption == Caption.CLOSE) { colour ->
        Canvas(Modifier.size(10.dp)) { drawCaption(caption, colour) }
    }
}

@Composable
private fun CaptionButtonFrame(description: String, onClick: () -> Unit, red: Boolean, glyph: @Composable (Color) -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxHeight()
            .width(if (xp) 32.dp else 30.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (xp) {
            val (top, foot) = when {
                red && pressed -> Color(0xFFB2381A) to Color(0xFFD9653F)
                red -> Color(0xFFEB8B65) to Luna.Close
                pressed -> Color(0xFF1A4FBF) to Color(0xFF3A79F0)
                else -> Color(0xFF5596FF) to Luna.CaptionButton
            }
            Box(
                Modifier
                    .size(25.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(top, foot)))
                    .border(1.dp, Color.White.copy(alpha = .85f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) { glyph(Color.White) }
        } else {
            Box(
                Modifier
                    .size(width = 24.dp, height = 21.dp)
                    .background(Win98.Face)
                    .drawWithContent {
                        drawContent()
                        drawEdge98(if (pressed) Edge98.PRESSED else Edge98.RAISED)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(if (pressed) Modifier.offset(1.dp, 1.dp) else Modifier) { glyph(Win98.Text) }
            }
        }
    }
}

/** Windows' glyphs, drawn rather than set in a font: the close cross and the minimise bar. */
private fun DrawScope.drawCaption(caption: Caption, colour: Color) {
    val stroke = size.width * .2f
    when (caption) {
        Caption.CLOSE -> {
            drawLine(colour, Offset(stroke / 2, stroke / 2), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Square)
            drawLine(colour, Offset(size.width - stroke / 2, stroke / 2), Offset(stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Square)
        }
        Caption.MINIMISE -> drawRect(colour, Offset(size.width * .1f, size.height - stroke * 1.4f), Size(size.width * .62f, stroke * 1.2f))
    }
}

/**
 * A window: a title bar with its close button working, an optional toolbar under it, and [content] on the
 * dialog grey. 98's frame is its raised window edge; XP's is Luna's blue, a few pixels wide down the sides
 * and along the foot, under the rounded title bar.
 */
@Composable
internal fun SkinWindow(
    title: String,
    modifier: Modifier = Modifier,
    close: (() -> Unit)? = null,
    minimise: (() -> Unit)? = null,
    toolbar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val face = if (xp) Luna.Face else Win98.Face
    Column(
        if (xp) {
            modifier
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Luna.Frame)
                .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
        } else {
            modifier
                .background(Win98.Face)
                .drawWithContent {
                    drawContent()
                    drawEdge98(Edge98.WINDOW)
                }
                .padding(3.dp)
        },
    ) {
        SkinTitleBar(title) {
            if (minimise != null) CaptionButton(Caption.MINIMISE, "Minimise", minimise)
            if (close != null) CaptionButton(Caption.CLOSE, "Close", close)
        }
        Column(Modifier.background(face)) {
            if (toolbar != null) {
                toolbar()
                SkinSeparator()
            }
            CompositionLocalProvider(LocalContentColor provides if (xp) Luna.Text else Win98.Text) {
                content()
            }
        }
    }
}

/**
 * A strip of a window's own face, for the player bar: 98's grey, raised from the page, or Luna's toolbar,
 * white fading into the beige with a pale rule along its top.
 */
@Composable
internal fun Modifier.skinPanel(): Modifier = if (LocalSkin.current == ThemeSkin.WINDOWS_XP) {
    background(Brush.verticalGradient(listOf(Color(0xFFFDFDFB), Luna.Face)))
        .drawBehind { drawRect(Luna.TabEdge.copy(alpha = .7f), Offset.Zero, Size(size.width, 1f)) }
} else {
    background(Win98.Face).drawWithContent {
        drawContent()
        drawEdge98(Edge98.RAISED)
    }
}

/** The line under a toolbar: 98's etched groove, or Luna's pale rule. */
@Composable
internal fun SkinSeparator(modifier: Modifier = Modifier) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        if (xp) {
            drawRect(Luna.GroupEdge, Offset(0f, size.height / 2f - .5f), Size(size.width, 1f))
        } else {
            drawRect(Win98.Shadow, Offset(0f, size.height / 2f - 1f), Size(size.width, 1f))
            drawRect(Win98.Highlight, Offset(0f, size.height / 2f), Size(size.width, 1f))
        }
    }
}

/**
 * The desktop, for behind the now playing window when there is no wash of the cover: 98's teal, as it was
 * installed, or XP's blue sky over a green hill -- drawn here from Luna's colours, a hill of its own rather
 * than a copy of the photograph XP shipped.
 */
@Composable
internal fun SkinDesktop(modifier: Modifier = Modifier) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Canvas(modifier.fillMaxSize()) {
        if (xp) drawXpDesktop() else drawRect(Win98.Desktop)
    }
}

/** A sky deepening upwards with a few soft clouds in it, and a smooth hill across the foot, lit from the left. */
private fun DrawScope.drawXpDesktop() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(0f to Luna.Sky, .58f to Luna.SkyLow, 1f to Luna.SkyLow))
    CLOUDS.forEach { (x, y, across) ->
        val centre = Offset(w * x, h * y)
        val radius = w * across
        drawOval(
            Brush.radialGradient(0f to Color.White.copy(alpha = .5f), 1f to Color.White.copy(alpha = 0f), center = centre, radius = radius),
            topLeft = Offset(centre.x - radius, centre.y - radius * .42f),
            size = Size(radius * 2f, radius * .84f),
        )
    }
    val hill = Path().apply {
        moveTo(0f, h * .74f)
        cubicTo(w * .22f, h * .6f, w * .55f, h * .55f, w, h * .66f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(hill, Brush.verticalGradient(0f to lerp(Luna.Hill, Color.White, .12f), .45f to Luna.Hill, 1f to Luna.HillShade, startY = h * .55f, endY = h))
    // The light catching the crest, from the sun somewhere up to the left.
    drawPath(hill, Brush.radialGradient(0f to Color(0x55E8FFB0), 1f to Color.Transparent, center = Offset(w * .32f, h * .6f), radius = w * .55f))
    // A second, nearer slope across the right of the foot, darker, for a little depth.
    val near = Path().apply {
        moveTo(w * .35f, h)
        cubicTo(w * .6f, h * .82f, w * .82f, h * .8f, w, h * .84f)
        lineTo(w, h)
        close()
    }
    drawPath(near, Brush.verticalGradient(0f to Luna.HillShade.copy(alpha = .55f), 1f to Luna.HillShade, startY = h * .8f, endY = h))
}

/** Where the clouds sit, as fractions of the sky: across, down, and how wide each is against the width. */
private val CLOUDS = listOf(
    Triple(.18f, .14f, .26f),
    Triple(.62f, .09f, .3f),
    Triple(.88f, .27f, .2f),
    Triple(.38f, .33f, .22f),
)

/** A heading between groups of Settings' rows: its name, then a groove running on to the edge. */
@Composable
internal fun SkinSectionLabel(text: String) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (xp) Luna.GroupTitle else Win98.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        SkinSeparator(Modifier.weight(1f))
    }
}

/**
 * One of Settings' rows under the skins: a wide push button, raised and sinking under the finger in 98 and
 * Luna's rounded white-to-beige in XP, with its icon, what it is, where it stands, and a way in.
 */
@Composable
internal fun SkinTile(icon: ImageVector, title: String, subtitle: String, tint: Color?, active: Boolean, open: () -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .then(
                if (xp) {
                    Modifier
                        .clip(shape)
                        .background(if (held) Luna.ButtonPressed else Luna.ButtonFace)
                        .border(1.dp, Luna.ButtonEdge.copy(alpha = .55f), shape)
                } else {
                    Modifier
                        .background(Win98.Face)
                        .drawWithContent {
                            drawContent()
                            drawEdge98(if (held) Edge98.PRESSED else Edge98.RAISED)
                        }
                },
            )
            .clickable(interaction, indication = null, role = Role.Button, onClick = open)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .then(if (held && !xp) Modifier.offset(1.dp, 1.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (active) {
            Spacer(Modifier.width(8.dp))
            Checkbox(checked = true)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Whether a group box is round what is being drawn, so its heading knows to sit in its edge. */
internal val LocalInGroupBox = staticCompositionLocalOf { false }

/**
 * A group box: 98's etched rectangle, or XP's rounded pale one, with the heading set into its top edge.
 * The edge runs through the middle of the first line, which [GroupBoxHeading] sits on.
 */
@Composable
internal fun GroupBox(content: @Composable ColumnScope.() -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val top = GROUP_EDGE_AT.toPx()
                    if (xp) {
                        drawRoundRect(
                            Luna.GroupEdge,
                            Offset(.5f, top),
                            Size(size.width - 1f, size.height - top - .5f),
                            CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(1f),
                        )
                    } else {
                        drawEdge98(Edge98.ETCHED, Offset(0f, top), Size(size.width, size.height - top))
                    }
                }
                .padding(start = 12.dp, end = 12.dp, top = GROUP_CONTENT_AT, bottom = 12.dp),
        ) {
            CompositionLocalProvider(LocalInGroupBox provides true) { content() }
        }
    }
}

/**
 * A group box's heading, lifted into the box's top edge with the page behind it, so the edge stops either
 * side of it as 98's and XP's did. It takes no room where it was put, so whatever followed it in the card
 * still follows the edge.
 */
@Composable
internal fun GroupBoxHeading(icon: ImageVector, title: String, tint: Color?) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val lift = GROUP_CONTENT_AT.roundToPx() - GROUP_EDGE_AT.roundToPx() + placeable.height / 2
                layout(placeable.width, (placeable.height - lift).coerceAtLeast(0)) { placeable.place(0, -lift) }
            }
            .background(if (xp) Luna.Face else Win98.Face)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint ?: if (xp) Luna.GroupTitle else Win98.Text)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            color = if (xp) Luna.GroupTitle else Win98.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/** Where a group box's edge runs, and where what it holds begins, from its top. */
private val GROUP_EDGE_AT = 10.dp
private val GROUP_CONTENT_AT = 22.dp

/**
 * The tabs along the bottom as a taskbar: a start button that is Home, a button for each of the other
 * tabs -- held down for the one showing, as the window in use is -- and the tray with the clock.
 *
 * 98's is grey slabs and the grey start button with the name in bold; XP's is Luna's blue bar with its green
 * start button, the name in white italic. Noctorium's own N and its own name stand where Windows put its
 * flag and its word, and nothing of Microsoft's is drawn. The buttons give up their names, and then the start
 * button its own, before any of them is too narrow to press. [clock] false leaves the tray out, for when the
 * player bar above is a taskbar with a clock of its own or the clock has been put away; its room goes to the
 * buttons, and with it, on a narrow phone, the start button's name can come back.
 */
@Composable
internal fun SkinTaskbar(
    current: Destination,
    labels: Boolean,
    tabs: List<Destination>,
    clock: Boolean,
    icon: (Destination) -> ImageVector,
    go: (Destination) -> Unit,
) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val home = current == Destination.HOME || current == Destination.NOW_PLAYING
    val others = tabs.filter { it != Destination.HOME }
    // The name and the clock are measured rather than guessed at, since both grow with the text size: a
    // guess made at the ordinary size left the tabs a few points wide on a phone set to the largest.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    val name = with(density) { measurer.measure("Noctorium", base.merge(startNameStyle(xp))).size.width.toDp() }
    val time = with(density) { measurer.measure("88:88", base.merge(TextStyle(fontSize = TRAY_CLOCK_SIZE))).size.width.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tray = if (clock) time + TRAY_PADDING * 2 + 4.dp else 0.dp
        val gaps = TASKBAR_GAP * (others.size + 1) + 12.dp
        val start = name + if (xp) START_AROUND_XP else START_AROUND_98
        val roomy = (maxWidth - start - tray - gaps) / others.size.coerceAtLeast(1) >= NARROWEST_BUTTON
        TaskbarStrip {
            StartButton(pressed = home, named = roomy, description = "Home") { go(Destination.HOME) }
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(TASKBAR_GAP),
            ) {
                others.forEach { destination ->
                    val named = labels && roomy && others.size <= 3
                    val label = tabName(destination)
                    TaskbarButton(current == destination, label, Modifier.weight(1f), onClick = { go(destination) }) { ink ->
                        Icon(icon(destination), null, Modifier.size(20.dp), tint = ink)
                        if (named) {
                            Spacer(Modifier.width(5.dp))
                            Text(label, color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (clock) Tray { ink -> TrayClock(ink) }
        }
    }
}

/**
 * The bar itself, which the buttons stand in: 98's grey with the raised edge along its top, or Luna's blue,
 * lit along its top and darker at its foot. XP's runs on down under the phone's own navigation, so the blue
 * meets the edge of the screen rather than stopping short of it on a beige band.
 */
@Composable
internal fun TaskbarStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Row(
        modifier
            .fillMaxWidth()
            .height(TASKBAR_HEIGHT)
            .then(
                if (xp) {
                    Modifier
                        .drawBehind { drawRect(Luna.TaskbarFoot, Offset(0f, size.height - 1f), Size(size.width, BELOW_THE_EDGE.toPx())) }
                        .background(Brush.verticalGradient(0f to Color(0xFF3F8CF3), .06f to Luna.TaskbarTop, .4f to Luna.Taskbar, 1f to Luna.TaskbarFoot))
                } else {
                    Modifier.background(Win98.Face).drawBehind {
                        drawRect(Win98.Light, Offset.Zero, Size(size.width, 1f))
                        drawRect(Win98.Highlight, Offset(0f, 1f), Size(size.width, 1f))
                    }
                },
            )
            .padding(
                start = if (xp) 0.dp else 3.dp,
                end = if (xp) 0.dp else 3.dp,
                top = if (xp) 0.dp else 4.dp,
                bottom = if (xp) 0.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private val TASKBAR_HEIGHT = 48.dp

/** As far as the phone's own navigation can reach below the taskbar. */
private val BELOW_THE_EDGE = 120.dp

/**
 * What the start button is beside its name -- its mark, and the room round both -- the tray's room either side
 * of its clock, and the gap between buttons: what, with the name and the clock as measured, must leave each of
 * the others at least [NARROWEST_BUTTON] for the start button to keep its name.
 */
private val START_AROUND_98 = 40.dp
private val START_AROUND_XP = 50.dp
private val TRAY_PADDING = 9.dp
private val TASKBAR_GAP = 3.dp
private val NARROWEST_BUTTON = 30.dp

/** The start button's name, as each skin set it: 98's bold, XP's bold and italic. */
private fun startNameStyle(xp: Boolean): TextStyle =
    if (xp) {
        TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    } else {
        TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

/**
 * A start button: XP's green, rounded at its right end, with the name in white italic, or 98's grey slab with
 * the name in bold. [pressed] holds it down, as the start button stayed down while its menu was open; [named]
 * false leaves only the N, for a narrow bar.
 */
@Composable
internal fun StartButton(pressed: Boolean, named: Boolean, description: String, onClick: () -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val down = pressed || held
    val click = Modifier
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }
    if (xp) {
        val shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        Row(
            Modifier
                .fillMaxHeight()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        if (down) {
                            listOf(lerp(Luna.Start, Color.Black, .25f), Luna.Start, lerp(Luna.Start, Color.Black, .1f))
                        } else {
                            listOf(Luna.StartLight, Luna.Start, lerp(Luna.Start, Color.Black, .22f))
                        },
                    ),
                )
                .then(click)
                .padding(start = 9.dp, end = if (named) 15.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                NoctoriumMark(Color.Black.copy(alpha = .35f), Modifier.size(19.dp).offset(1.dp, 1.dp))
                NoctoriumMark(Color.White, Modifier.size(19.dp))
            }
            if (named) {
                Spacer(Modifier.width(7.dp))
                Text(
                    "Noctorium",
                    color = Color.White,
                    style = startNameStyle(xp = true).copy(shadow = Shadow(Color(0xFF1D5E1D), Offset(2f, 2f), 2f)),
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxHeight()
                .background(if (down) Win98.Dither else Brush.linearGradient(listOf(Win98.Face, Win98.Face)))
                .drawWithContent {
                    drawContent()
                    drawEdge98(if (down) Edge98.PRESSED else Edge98.RAISED)
                }
                .then(click)
                .padding(start = 6.dp, end = if (named) 8.dp else 6.dp)
                .then(if (down) Modifier.offset(1.dp, 1.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowIcon(20.dp)
            if (named) {
                Spacer(Modifier.width(6.dp))
                Text("Noctorium", color = Win98.Text, style = startNameStyle(xp = false), maxLines = 1)
            }
        }
    }
}

/**
 * A taskbar button: raised, or held down for the window in use -- 98's with the grey and white checkerboard
 * of a button that is down for good, XP's a deeper blue. [content] is given the colour to write in.
 */
@Composable
internal fun TaskbarButton(
    chosen: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    /** Drawn along the inside of the button's foot, under what it says: the song's progress, in the player bar. */
    foot: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.(ink: Color) -> Unit,
) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val down = chosen || held
    val ink = if (xp) Color.White else Win98.Text
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = if (xp) 5.dp else 0.dp)
            .then(
                if (xp) {
                    val shape = RoundedCornerShape(3.dp)
                    Modifier
                        .clip(shape)
                        .background(
                            Brush.verticalGradient(
                                if (down) {
                                    listOf(Color(0xFF1E4FB8), Color(0xFF1A4AAE), Color(0xFF2257C9))
                                } else {
                                    listOf(Color(0xFF4C93F8), Color(0xFF3A7CEC), Color(0xFF2F6CE0))
                                },
                            ),
                        )
                        .border(1.dp, if (down) Color(0xFF15398C) else Color(0xFF6AA5FF), shape)
                } else {
                    Modifier
                        .background(if (down) Win98.Dither else Brush.linearGradient(listOf(Win98.Face, Win98.Face)))
                        .drawWithContent {
                            drawContent()
                            drawEdge98(if (down) Edge98.PRESSED else Edge98.RAISED)
                        }
                },
            )
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp).then(if (down && !xp) Modifier.offset(1.dp, 1.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) { content(ink) }
        if (foot != null) Box(Modifier.align(Alignment.BottomCenter).padding(start = 4.dp, end = 4.dp, bottom = 3.dp)) { foot() }
    }
}

/** The tray at the end of a taskbar, sunk into it in 98 and Luna's lighter blue in XP. */
@Composable
internal fun Tray(content: @Composable RowScope.(ink: Color) -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    Row(
        Modifier
            .fillMaxHeight()
            .then(
                if (xp) {
                    Modifier
                        .background(Brush.verticalGradient(listOf(Color(0xFF16A2F8), Luna.Tray, Color(0xFF0B76D8))))
                        .drawBehind {
                            drawRect(Luna.TrayEdge, Offset.Zero, Size(1.dp.toPx(), size.height))
                            drawRect(Color(0xFF63BBFF), Offset(1.dp.toPx(), 0f), Size(1f, size.height))
                        }
                        .padding(horizontal = TRAY_PADDING)
                } else {
                    Modifier
                        .padding(vertical = 2.dp)
                        .drawWithContent {
                            drawContent()
                            drawEdge98(Edge98.SHALLOW)
                        }
                        .padding(horizontal = TRAY_PADDING)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (xp) Color.White else Win98.Text
        CompositionLocalProvider(LocalContentColor provides ink) { content(ink) }
    }
}

/**
 * A list's box: white, sunk into the page by 98's two lines or edged by XP's pale blue, a little in from the
 * sides and filling what is left of the screen. For the ordinary themes it is nothing at all, and the list is
 * laid out exactly as it always was.
 */
@Composable
internal fun Modifier.skinList(): Modifier = when (LocalSkin.current) {
    ThemeSkin.STANDARD -> this
    ThemeSkin.WINDOWS_98 -> this
        .fillMaxSize()
        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)
        .background(Win98.Window)
        .drawWithContent {
            drawContent()
            drawEdge98(Edge98.SUNKEN)
        }
        .padding(2.dp)
    ThemeSkin.WINDOWS_XP -> this
        .fillMaxSize()
        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)
        .background(Luna.Window)
        .border(1.dp, Luna.FieldEdge)
        .padding(1.dp)
}

/**
 * The row that is chosen in a list -- the song playing, in a list of songs -- as Windows marked it: filled
 * with the selection's blue, and everything in it written in white.
 */
@Composable
internal fun SkinSelected(content: @Composable () -> Unit) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val ink = if (xp) Luna.SelectionText else Win98.SelectionText
    Box(Modifier.background(if (xp) Luna.Selection else Win98.Selection)) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(primary = ink, onSurface = ink, onSurfaceVariant = ink.copy(alpha = .85f)),
        ) {
            CompositionLocalProvider(LocalContentColor provides ink) { content() }
        }
    }
}

/**
 * A cover under the skins: 98's square, sunk into the page in a frame of its two lines, and XP's in a thin pale
 * blue edge with Luna's small rounding. A round one -- an artist, the Circle shape -- stays round in both.
 */
@Composable
internal fun SkinArtwork(url: String?, size: Dp, corner: Dp) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val round = corner * 2 >= size
    val shape = when {
        round -> RoundedCornerShape(50)
        xp -> RoundedCornerShape(minOf(corner, 3.dp))
        else -> RoundedCornerShape(0.dp)
    }
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (xp) Luna.Face else Win98.Face)
            .then(
                when {
                    round -> Modifier
                    xp -> Modifier.border(1.dp, Luna.FieldEdge, shape)
                    else -> Modifier.drawWithContent {
                        drawContent()
                        drawEdge98(Edge98.SUNKEN)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(
                Icons.Default.MusicNote,
                null,
                Modifier.size(size / 2),
                tint = if (xp) Luna.GreyText else Win98.Shadow,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
