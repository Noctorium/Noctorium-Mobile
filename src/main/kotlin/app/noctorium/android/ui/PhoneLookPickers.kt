package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SeekBar
import app.noctorium.settings.ThemeSkin

/*
 * The pictures the looks in Customization are chosen by, rather than a row of their names.
 *
 * A name like "Beads" says little until it is seen. The seek bars are the real drawing at a smaller size,
 * from the same code the bar itself uses, so what is picked is exactly what appears.
 */

/** Every seek bar, two to a row, each drawn as it will be. [seed] is the song the Bars take their row from. */
@Composable
internal fun SeekBarPicker(selected: ProgressBarStyle, seed: String, choose: (ProgressBarStyle) -> Unit) {
    PickerGrid(ProgressBarStyle.entries, columns = 2) { style, modifier ->
        PickerTile(style.displayName, selected == style, { choose(style) }, modifier) { SeekBarPreview(style, seed) }
    }
}

/**
 * Choices laid out [columns] to a row. A short last row keeps its tiles the size of the rest rather than
 * stretching the one left over across the card, which made it look like a different kind of thing.
 */
@Composable
internal fun <T> PickerGrid(options: List<T>, columns: Int, tile: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.selectableGroup().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One choice: its picture over its name, outlined in the accent once it is the one chosen. */
@Composable
internal fun PickerTile(
    label: String,
    selected: Boolean,
    choose: () -> Unit,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        // A button that latches under the Windows skins, held down for the one chosen, as 98's toolbars held
        // down the view in use.
        PushButton(choose, modifier.semantics { this.selected = selected }, latched = selected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(2.dp)) { picture() }
                Spacer(Modifier.height(5.dp))
                Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    val shape = RoundedCornerShape(12.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = choose)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        picture()
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One seek bar, drawn by the code the bar itself is drawn with, a little under halfway through a song of
 * three and a half minutes so the ruler has its minutes and the bars something lit and something not.
 */
@Composable
private fun SeekBarPreview(style: ProgressBarStyle, seed: String) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = SeekBar.TRACK_ALPHA)
    val filled = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondaryContainer
    val skin = LocalSkin.current
    val pale = MaterialTheme.colorScheme.background.luminance() > .5f
    val win98 = Win98
    var width by remember { mutableIntStateOf(0) }
    val shapes = rememberSeekBarShapes(style, seed, PREVIEW_LENGTH_MS, width)
    Box(Modifier.fillMaxWidth().height(36.dp).onSizeChanged { width = it.width }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(style.drawnHeight())) {
            if (style == ProgressBarStyle.MATERIAL) {
                // The Windows skins have Material's slider as a trackbar, so that is what they preview.
                if (skin == ThemeSkin.STANDARD) drawMaterialSlider(PREVIEW_FRACTION, filled, inactive) else drawTrackbar(skin == ThemeSkin.WINDOWS_XP, win98, PREVIEW_FRACTION, showHead = true)
            } else {
                drawSeekBar(style, PREVIEW_FRACTION, true, track, filled, phase = .2f, amplitude = 1f, shapes = shapes, pale = pale, win98 = win98)
            }
        }
    }
}

/**
 * Material's own slider in miniature, which the Material style hands the real thing to: a thick rounded
 * track, the handle a short upright bar with a gap either side of it, and the stop at the far end.
 */
private fun DrawScope.drawMaterialSlider(fraction: Float, active: Color, inactive: Color) {
    val height = size.height * .42f
    val top = (size.height - height) / 2f
    val head = size.width * fraction
    val gap = 3.dp.toPx()
    val handle = 3.dp.toPx()
    val radius = CornerRadius(height / 2f, height / 2f)
    drawRoundRect(active, Offset(0f, top), Size(head - gap - handle / 2f, height), radius)
    drawRoundRect(inactive, Offset(head + gap + handle / 2f, top), Size(size.width - head - gap - handle / 2f, height), radius)
    drawRoundRect(active, Offset(head - handle / 2f, 0f), Size(handle, size.height), CornerRadius(handle / 2f, handle / 2f))
    drawCircle(active, height * .18f, Offset(size.width - height / 2f, size.height / 2f))
}

/** How far through a song every preview is drawn, and how long that song is: long enough for a few minutes. */
private const val PREVIEW_FRACTION = .4f
private const val PREVIEW_LENGTH_MS = 210_000L
