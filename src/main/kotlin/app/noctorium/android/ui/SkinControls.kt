package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.noctorium.settings.ThemeSkin

/*
 * Material's controls, dressed by the skin in force.
 *
 * Each takes exactly the arguments its Material counterpart was being given, and for the ordinary themes is
 * that counterpart, called with them unchanged -- so nothing outside the Windows themes looks or behaves any
 * differently for going through here. Under 98 and XP each is its Windows equivalent: a button a push button,
 * a switch a checkbox, a slider a trackbar, a chip a button that latches, a dialog a window.
 */

/** Material's filled button; a push button under the Windows skins. */
@Composable
internal fun SkinnedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        Button(onClick, modifier, enabled, content = content)
    } else {
        PushButton(onClick, modifier, enabled, content = content)
    }
}

/** Material's outlined button; a push button under the Windows skins. */
@Composable
internal fun SkinnedOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        OutlinedButton(onClick, modifier, enabled, content = content)
    } else {
        PushButton(onClick, modifier, enabled, content = content)
    }
}

/** Material's tonal button; a push button under the Windows skins. */
@Composable
internal fun SkinnedTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        FilledTonalButton(onClick, modifier, enabled, content = content)
    } else {
        PushButton(onClick, modifier, enabled, content = content)
    }
}

/**
 * Material's text button; a push button under the Windows skins too, since a dialog's OK and Cancel and a
 * field's Save were push buttons there, and a word that is pressed is not a thing either desktop had.
 */
@Composable
internal fun SkinnedTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        TextButton(onClick, modifier, enabled, content = content)
    } else {
        PushButton(onClick, modifier, enabled, compact = true, content = content)
    }
}

/**
 * A Windows push button. 98's is a grey slab, raised, that sinks under the finger with its label a pixel
 * down and to the right; XP's is Luna's: rounded, edged in dark blue, white fading to beige, darker while
 * held. A button that cannot be pressed has its label greyed, as both had.
 */
@Composable
internal fun PushButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    latched: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val win98 = Win98
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val down = held || latched
    val ink = when {
        !enabled -> if (xp) Luna.GreyText else win98.GreyText
        xp -> Luna.Text
        else -> win98.Text
    }
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier
            .defaultMinSize(minWidth = if (compact) 56.dp else 76.dp, minHeight = if (compact) 34.dp else 38.dp)
            .then(
                if (xp) {
                    Modifier
                        .clip(shape)
                        .background(if (down) Luna.ButtonPressed else Luna.ButtonFace)
                        .border(1.dp, if (enabled) Luna.ButtonEdge else Luna.GreyText, shape)
                        .then(
                            if (latched) {
                                Modifier.drawBehind {
                                    drawRoundRect(Luna.Hot, Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(2.dp.toPx()))
                                }
                            } else {
                                Modifier
                            },
                        )
                } else {
                    Modifier
                        .background(if (latched) win98.Dither else Brush.linearGradient(listOf(win98.Face, win98.Face)))
                        .drawWithContent {
                            drawContent()
                            drawEdge98(if (down) Edge98.PRESSED else Edge98.RAISED, win98)
                        }
                },
            )
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 6.dp)
            .then(if (down && !xp) Modifier.offset(1.dp, 1.dp) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal)) { content() }
        }
    }
}

/** Material's switch; a checkbox under the Windows skins. */
@Composable
internal fun SkinnedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        Switch(checked, onCheckedChange, modifier, enabled = enabled)
    } else {
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .then(
                    if (onCheckedChange == null) {
                        Modifier
                    } else {
                        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Checkbox(checked, enabled) }
    }
}

/**
 * A Windows checkbox, thirteen points square: 98's sunk into the page, white, with a black tick; XP's in its
 * dark blue edge, white fading to grey, with a green one.
 */
@Composable
internal fun Checkbox(checked: Boolean, enabled: Boolean = true) {
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val win98 = Win98
    Canvas(Modifier.size(CHECKBOX)) {
        if (xp) {
            drawRect(Brush.linearGradient(listOf(Color(0xFFDCDCD7), Color.White), start = Offset(size.width, size.height), end = Offset.Zero))
            drawRect(if (enabled) XP_CHECK_EDGE else Luna.GreyText, style = Stroke(1.dp.toPx()))
        } else {
            drawRect(if (enabled) win98.Window else win98.Face)
            drawEdge98(Edge98.SUNKEN, win98)
        }
        if (checked) {
            val tick = Path().apply {
                moveTo(size.width * .24f, size.height * .5f)
                lineTo(size.width * .42f, size.height * .7f)
                lineTo(size.width * .77f, size.height * .3f)
            }
            val colour = when {
                !enabled -> if (xp) Luna.GreyText else win98.GreyText
                xp -> XP_TICK
                else -> win98.Text
            }
            drawPath(tick, colour, style = Stroke(size.width * .16f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
    }
}

/** Material's radio button; Windows' own under the skins. */
@Composable
internal fun SkinnedRadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        RadioButton(selected, onClick, modifier, enabled)
        return
    }
    val xp = LocalSkin.current == ThemeSkin.WINDOWS_XP
    val win98 = Win98
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(if (onClick == null) Modifier else Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(CHECKBOX)) {
            val radius = size.minDimension / 2f
            if (xp) {
                drawCircle(Brush.linearGradient(listOf(Color(0xFFDCDCD7), Color.White), start = Offset(size.width, size.height), end = Offset.Zero), radius)
                drawCircle(if (enabled) XP_CHECK_EDGE else Luna.GreyText, radius - .5f, style = Stroke(1.dp.toPx()))
                if (selected) {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF7DD87D), XP_TICK), center = center - Offset(radius * .15f, radius * .15f), radius = radius * .45f), radius * .38f)
                }
            } else {
                drawCircle(if (enabled) win98.Window else win98.Face, radius)
                // Lit from the top left like every 98 edge: the upper left half of the ring dark, the rest light.
                drawArc(win98.Shadow, 135f, 180f, false, style = Stroke(1f), topLeft = Offset(.5f, .5f), size = Size(size.width - 1f, size.height - 1f))
                drawArc(win98.DarkShadow, 135f, 180f, false, style = Stroke(1f), topLeft = Offset(1.5f, 1.5f), size = Size(size.width - 3f, size.height - 3f))
                drawArc(win98.Highlight, 315f, 180f, false, style = Stroke(1f), topLeft = Offset(.5f, .5f), size = Size(size.width - 1f, size.height - 1f))
                drawArc(win98.Light, 315f, 180f, false, style = Stroke(1f), topLeft = Offset(1.5f, 1.5f), size = Size(size.width - 3f, size.height - 3f))
                if (selected) drawCircle(if (enabled) win98.Text else win98.GreyText, radius * .32f)
            }
        }
    }
}

/** Material's slider; a trackbar under the Windows skins, answering a tap or a drag along its length. */
@Composable
internal fun SkinnedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    if (skin == ThemeSkin.STANDARD) {
        Slider(value, onValueChange, modifier, enabled, valueRange, onValueChangeFinished = onValueChangeFinished)
        return
    }
    val win98 = Win98
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    var width by remember { mutableIntStateOf(1) }
    val span = valueRange.endInclusive - valueRange.start
    fun at(x: Float) = valueRange.start + (x / width).coerceIn(0f, 1f) * span
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    change(at(offset.x))
                    finish?.invoke()
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> change(at(offset.x)) },
                    onDragEnd = { finish?.invoke() },
                    onDragCancel = { finish?.invoke() },
                    onHorizontalDrag = { pointer, _ ->
                        pointer.consume()
                        change(at(pointer.position.x))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) { drawTrackbar(skin == ThemeSkin.WINDOWS_XP, win98, fraction, showHead = true) }
    }
}

/**
 * A trackbar: a narrow groove along the middle and a thumb that points down at its place in it. 98's groove
 * is sunk into the page and its thumb is a raised slab of [win98]'s face with a pointed foot; XP's groove is a
 * pale channel and its thumb is Luna's. Also the Material seek bar's look under the skins, which would
 * otherwise be the one modern thing on a 98 screen.
 */
internal fun DrawScope.drawTrackbar(xp: Boolean, win98: Win98Paint, fraction: Float, showHead: Boolean) {
    val centreY = size.height / 2f
    val thumbWidth = 11.dp.toPx()
    val thumbHeight = 21.dp.toPx().coerceAtMost(size.height)
    val head = thumbWidth / 2f + (size.width - thumbWidth) * fraction.coerceIn(0f, 1f)
    if (xp) {
        val groove = 4.dp.toPx()
        val top = centreY - groove / 2f
        drawRoundRect(Color(0xFFF3F3EF), Offset(0f, top), Size(size.width, groove), CornerRadius(groove / 2f))
        drawRoundRect(Color(0xFF9D9C99), Offset(.5f, top + .5f), Size(size.width - 1f, groove - 1f), CornerRadius(groove / 2f), style = Stroke(1f))
        if (showHead) drawLunaThumb(head, centreY, thumbWidth, thumbHeight)
    } else {
        val groove = 4f
        drawEdge98(Edge98.SUNKEN, win98, Offset(0f, centreY - groove / 2f), Size(size.width, groove))
        if (showHead) drawThumb98(win98, head, centreY, thumbWidth, thumbHeight)
    }
}

/** 98's trackbar thumb: a slab of the face with a pointed foot, lit from the top left like every other edge there. */
private fun DrawScope.drawThumb98(win98: Win98Paint, centreX: Float, centreY: Float, width: Float, tall: Float) {
    val x = (centreX - width / 2f).let { kotlin.math.round(it) }
    val y = (centreY - tall / 2f).let { kotlin.math.round(it) }
    val w = kotlin.math.round(width)
    val point = kotlin.math.round(w / 2f)
    val body = Path().apply {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + tall - point)
        lineTo(x + w / 2f, y + tall)
        lineTo(x, y + tall - point)
        close()
    }
    drawPath(body, win98.Face)
    fun stroke(colour: Color, points: List<Offset>) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
        }
        drawPath(path, colour, style = Stroke(1f))
    }
    // The highlight outermost along the top, the left and the left of the point -- white, in 98's own scheme --
    // and the light inside it.
    stroke(win98.Highlight, listOf(Offset(x + .5f, y + tall - point), Offset(x + .5f, y + .5f), Offset(x + w - .5f, y + .5f)))
    stroke(win98.Highlight, listOf(Offset(x + .5f, y + tall - point), Offset(x + w / 2f, y + tall - .5f)))
    stroke(win98.Light, listOf(Offset(x + 1.5f, y + tall - point), Offset(x + 1.5f, y + 1.5f), Offset(x + w - 1.5f, y + 1.5f)))
    // The dark shadow outermost down the right and the right of the point, and the shadow inside it.
    stroke(win98.DarkShadow, listOf(Offset(x + w - .5f, y + .5f), Offset(x + w - .5f, y + tall - point), Offset(x + w / 2f, y + tall - .5f)))
    stroke(win98.Shadow, listOf(Offset(x + w - 1.5f, y + 1.5f), Offset(x + w - 1.5f, y + tall - point), Offset(x + w / 2f, y + tall - 1.5f)))
}

/** Material's filter chip; a button that latches down while chosen, under the Windows skins. */
@Composable
internal fun SkinnedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        FilterChip(selected, onClick, label, modifier, enabled, leadingIcon)
        return
    }
    PushButton(onClick, modifier.padding(vertical = 3.dp), enabled, compact = true, latched = selected) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
        }
        label()
    }
}

/**
 * Material's alert dialog; a window under the Windows skins: a title bar whose close button works, the
 * message on the dialog grey, and the buttons along the foot -- OK first and Cancel after it, the Windows
 * way round, where Material puts the one that leaves on the left.
 */
@Composable
internal fun SkinnedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title?.let { { androidx.compose.material3.Text(it, fontSize = titleSize) } },
            text = text,
            containerColor = containerColor,
        )
        return
    }
    // Its own width rather than the platform's, which differs from phone to phone: as wide as a dialog of the time
    // was in proportion, with the page showing either side.
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        SkinWindow(title ?: "Noctorium", modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp).fillMaxWidth(), close = onDismissRequest) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (icon != null) {
                            Box(Modifier.padding(end = 12.dp, top = 2.dp)) { icon() }
                        }
                        Box(Modifier.weight(1f)) { text?.invoke() }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                    confirmButton()
                    dismissButton?.invoke()
                }
            }
        }
    }
}

/**
 * Material's dropdown menu; Windows' own under the skins. 98's is the dialog grey, framed as a window was;
 * XP's is white, edged in the beige-grey it greyed text with, with the soft shadow XP put to the right of and
 * below its menus. Either opens below what it hangs from, or above it where there is no room below -- from a
 * taskbar, that is always.
 */
@Composable
internal fun SkinnedDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val skin = LocalSkin.current
    if (skin == ThemeSkin.STANDARD) {
        DropdownMenu(expanded, onDismissRequest, content = content)
        return
    }
    if (!expanded) return
    val xp = skin == ThemeSkin.WINDOWS_XP
    Popup(popupPositionProvider = MenuPlacement, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Box(if (xp) Modifier.drawBehind { drawMenuShadow() }.padding(end = MENU_SHADOW, bottom = MENU_SHADOW) else Modifier) {
            Column(
                Modifier
                    .widthIn(min = MENU_WIDTH)
                    .width(IntrinsicSize.Max)
                    .then(
                        if (xp) {
                            Modifier.background(Luna.Window).border(1.dp, Luna.GreyText)
                        } else {
                            val win98 = Win98
                            Modifier.background(win98.Face).drawWithContent {
                                drawContent()
                                drawEdge98(Edge98.WINDOW, win98)
                            }
                        },
                    )
                    .padding(if (xp) 2.dp else 3.dp),
                content = content,
            )
        }
    }
}

/**
 * Material's menu item; under the skins one of Windows': lit -- 98's navy, XP's blue -- while the finger is on
 * it, with a tick in the margin before the words for one that is [checked]. A null [checked] is an item that
 * is neither ticked nor unticked, only done.
 */
@Composable
internal fun SkinnedDropdownMenuItem(text: String, onClick: () -> Unit, checked: Boolean? = null) {
    val skin = LocalSkin.current
    val ticked = if (checked == null) Modifier else Modifier.semantics { toggleableState = ToggleableState(checked) }
    if (skin == ThemeSkin.STANDARD) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = onClick,
            modifier = ticked,
            leadingIcon = if (checked == true) ({ Icon(Icons.Default.Check, null) }) else null,
        )
        return
    }
    val xp = skin == ThemeSkin.WINDOWS_XP
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val ink = when {
        held -> if (xp) Luna.SelectionText else Win98.SelectionText
        xp -> Luna.Text
        else -> Win98.Text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .background(if (!held) Color.Transparent else if (xp) Luna.Selection else Win98.Selection)
            .clickable(interaction, indication = null, onClick = onClick)
            .then(ticked)
            .padding(end = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(MENU_MARGIN), contentAlignment = Alignment.Center) {
            if (checked == true) {
                // Windows' menu tick: short and heavy, its long stroke rising to the right.
                Canvas(Modifier.size(9.dp)) {
                    val tick = Path().apply {
                        moveTo(0f, size.height * .55f)
                        lineTo(size.width * .38f, size.height * .9f)
                        lineTo(size.width, size.height * .1f)
                    }
                    drawPath(tick, ink, style = Stroke(size.width * .22f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                }
            }
        }
        Text(text, color = ink, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * Where a Windows menu opens: below what it hangs from, or above it where there is no room below; starting
 * level with its leading edge, or ending level with its trailing one where it would run off the side.
 */
private object MenuPlacement : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val leading = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - width
        val trailing = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - width else anchorBounds.left
        val x = (if (leading >= 0 && leading + width <= windowSize.width) leading else trailing)
            .coerceIn(0, (windowSize.width - width).coerceAtLeast(0))
        val y = if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) {
            anchorBounds.bottom
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

/** XP's shadow under a menu: soft, and to the right and below, as if lit from the top left like everything else. */
private fun DrawScope.drawMenuShadow() {
    val reach = MENU_SHADOW.toPx()
    val menu = Size(size.width - reach, size.height - reach)
    for (step in 1..MENU_SHADOW_STEPS) {
        val by = reach * step / MENU_SHADOW_STEPS
        drawRect(Color.Black.copy(alpha = .07f), Offset(by, by), menu)
    }
}

/**
 * A menu's least width; its rows' height, a finger's where Windows' were a pointer's; the margin its ticks
 * sit in; and how far XP's shadow reaches, in how many steps it fades.
 */
private val MENU_WIDTH = 168.dp
private val MENU_ITEM_HEIGHT = 40.dp
private val MENU_MARGIN = 30.dp
private val MENU_SHADOW = 4.dp
private const val MENU_SHADOW_STEPS = 4

/** A checkbox's side, as Windows drew it at its own size: thirteen. */
private val CHECKBOX = 13.dp

/** XP's checkbox edge, a darker blue than a field's, and the green of its tick. */
private val XP_CHECK_EDGE = Color(0xFF1C5180)
private val XP_TICK = Color(0xFF21A121)
