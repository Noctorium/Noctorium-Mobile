package app.noctorium.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.settings.parseHexColour
import app.noctorium.settings.toHexColour

/**
 * The accent while a finger is still on the picker, before it is saved.
 *
 * The whole interface takes its colours from the saved settings, and saving on every frame of a drag would
 * write the settings file sixty times a second. So the drag paints through here instead -- the theme at the
 * root reads it ahead of the saved accent, and everything recolours under the finger -- and the colour is
 * saved once, when the finger lifts.
 */
internal object AccentPreview {
    var argb: Long? by mutableStateOf(null)
}

/**
 * The listener's own accent: a square for how strong and how bright, a strip for which hue, and the colour
 * as hex for anybody who already knows the one they want.
 *
 * A wheel was the other choice. It packs the same three numbers into a circle that a thumb covers half of on
 * a phone; a square and a strip each have one job and can be dragged along an edge without the colour
 * jumping to somewhere unexpected.
 */
@Composable
internal fun AccentPicker(current: Long, apply: (Long) -> Unit) {
    var hsv by remember { mutableStateOf(argbToHsv(current)) }
    var hex by remember { mutableStateOf(current.toHexColour()) }

    // Follows a colour that arrived from elsewhere -- typed as hex, chosen on another device -- without
    // throwing away the hue the picker is holding for a grey, and without retyping a box being typed in.
    LaunchedEffect(current) {
        val opaque = current or 0xFF000000L
        if (hsv.toArgb() != opaque) hsv = argbToHsv(opaque, keepHue = hsv.hue)
        if (parseHexColour(hex) != opaque) hex = opaque.toHexColour()
    }
    // A picker taken off screen in the middle of a drag must not leave the whole interface previewing a
    // colour nobody saved.
    DisposableEffect(Unit) { onDispose { AccentPreview.argb = null } }

    fun preview(next: Hsv) {
        hsv = next
        val argb = next.toArgb()
        AccentPreview.argb = argb
        hex = argb.toHexColour()
    }

    fun commit() {
        AccentPreview.argb?.let(apply)
        AccentPreview.argb = null
    }

    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your colour", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        SaturationBrightnessSquare(hsv, onChange = ::preview, onDone = ::commit)
        HueStrip(hsv, onChange = ::preview, onDone = ::commit)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = Color(hsv.toArgb()),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(30.dp),
            ) {}
            val parsed = parseHexColour(hex)
            OutlinedTextField(
                hex,
                { typed ->
                    hex = typed.take(9)
                    // Applied the moment it reads as a colour, so the interface answers the typing; a half
                    // typed one is left alone rather than guessed at.
                    parseHexColour(hex)?.let { argb -> if (argb != (current or 0xFF000000L)) apply(argb) }
                },
                label = { Text("Hex", fontSize = 11.sp) },
                placeholder = { Text("#B47CFF") },
                singleLine = true,
                isError = parsed == null,
                supportingText = if (parsed == null) {
                    { Text("Six digits, like #B47CFF.", fontSize = 11.sp) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Saturation across, brightness down: white at the top left, the pure hue at the top right, black along the
 * bottom. Touching anywhere goes straight there, and dragging carries on from it.
 */
@Composable
private fun SaturationBrightnessSquare(hsv: Hsv, onChange: (Hsv) -> Unit, onDone: () -> Unit) {
    val latest by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val pure = Color(Hsv(hsv.hue, 1f, 1f).toArgb())

    fun at(position: Offset, size: IntSize) = latest.copy(
        saturation = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
        value = 1f - (position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) { dragAnywhere(onMove = { change(at(it, size)) }, onEnd = { done() }) },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, pure)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        // Drawn outside the square's rounded clip, or a fully saturated colour -- whose place is the top
        // right corner -- would show only a quarter of the ring that says where it is.
        Canvas(Modifier.matchParentSize()) {
            val centre = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height)
            drawCircle(Color.Black.copy(alpha = .45f), radius = 11.dp.toPx(), center = centre, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, radius = 11.dp.toPx(), center = centre, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Every hue, red round to red again. */
@Composable
private fun HueStrip(hsv: Hsv, onChange: (Hsv) -> Unit, onDone: () -> Unit) {
    val latest by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val rainbow = remember { (0..6).map { Color(Hsv(it * 60f, 1f, 1f).toArgb()) } }

    Box(
        Modifier
            .fillMaxWidth()
            // Taller than it is drawn, so a thumb can find it; the strip itself sits in the middle.
            .height(32.dp)
            .pointerInput(Unit) {
                dragAnywhere(
                    onMove = { position ->
                        // Kept short of 360, which is red again and would read back as 0 on the next frame.
                        val hue = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 359.9f
                        // A grey has no hue to show, so moving the hue also lends it some colour to show it with.
                        val shown = latest.let { if (it.saturation < .05f || it.value < .05f) it.copy(saturation = 1f, value = 1f) else it }
                        change(shown.copy(hue = hue))
                    },
                    onEnd = { done() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(rainbow)),
        )
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(hsv.hue / 360f * size.width, size.height / 2)
            drawCircle(Color.Black.copy(alpha = .45f), radius = 11.dp.toPx(), center = centre, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, radius = 11.dp.toPx(), center = centre, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * A press and whatever drag follows it, reported as positions, then once when the finger lifts.
 *
 * Every change is consumed, which is what keeps Settings' list from scrolling away under a finger that is
 * choosing a colour.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragAnywhere(
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onMove(down.position)
        drag(down.id) { moved ->
            moved.consume()
            onMove(moved.position)
        }
        onEnd()
    }
}
