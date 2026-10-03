package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.settings.Equalizer
import app.noctorium.settings.EqualizerPreset
import app.noctorium.settings.EqualizerSettings
import app.noctorium.settings.SeekBar
import app.noctorium.settings.SettingsState
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Whether this phone lets Noctorium shape its sound, provided once at the root from the player.
 *
 * True until the player finds otherwise, which is also what a preview or a test without a player sees.
 */
val LocalEqualizerAvailable = compositionLocalOf { true }

/** How far the preamp goes, the same either way as a band: past that it is not shaping the sound any more. */
private const val PREAMP_RANGE_DB = Equalizer.MAX_GAIN_DB

/**
 * The equaliser: a switch, the presets, ten bands and a preamp, and the curve they make.
 *
 * Nothing is saved while a finger is still on a slider. The band or the preamp being dragged is held here
 * and drawn from here, and written once when the finger lifts -- otherwise every frame of a drag would be
 * a write to the settings file and a new curve handed to the phone's equaliser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoundCard(settings: SettingsState, state: AppState) {
    val equalizer = settings.preferences.equalizer
    val available = LocalEqualizerAvailable.current
    var heldBand by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var heldPreamp by remember { mutableStateOf<Float?>(null) }
    val gains = heldBand?.let { (index, gain) -> equalizer.gains.toMutableList().also { it[index] = gain } } ?: equalizer.gains
    val preamp = heldPreamp ?: equalizer.preampDb.coerceIn(-PREAMP_RANGE_DB, PREAMP_RANGE_DB)

    SettingsCardShell {
        CardHeading(Icons.Default.Equalizer, "Equaliser")
        Text(
            "Shapes everything Noctorium plays, through this phone's own equaliser. Its bands are not these " +
                "ten, so each of them follows the curve.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        if (!available) {
            Text(
                "This phone does not offer an equaliser to apps.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        } else {
            // Not called "Equaliser" again: the card's heading says that already, right above it.
            Toggle(
                "Shape the sound",
                if (equalizer.enabled) {
                    "On. Switching it off leaves the curve here for next time."
                } else {
                    "Off. Choosing a preset or moving a band switches it on."
                },
                equalizer.enabled,
            ) { on -> state.updateEqualizer { copy(enabled = on) } }

            OptionRow("Presets") {
                EqualizerPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = equalizer.preset == preset,
                        onClick = { state.setEqualizerPreset(preset) },
                        label = { Text(preset.displayName, fontSize = 11.sp) },
                    )
                }
            }

            EqualizerCurve(gains, lit = equalizer.enabled)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Equalizer.BANDS_HZ.forEachIndexed { index, hz ->
                    BandSlider(
                        label = Equalizer.label(hz),
                        spoken = spokenFrequency(hz),
                        gain = gains[index],
                        lit = equalizer.enabled,
                        onChange = { heldBand = index to it },
                        onDone = {
                            heldBand?.let { (band, gain) -> state.setEqualizerBand(band, gain) }
                            heldBand = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Drag a band to move it; double tap one to set it back to flat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preamp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${decibelsLabel(preamp)} dB",
                    color = if (preamp != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Slider(
                value = preamp,
                onValueChange = { heldPreamp = roundToHalf(it) },
                onValueChangeFinished = {
                    heldPreamp?.let { value -> state.updateEqualizer { copy(preampDb = value, enabled = true) } }
                    heldPreamp = null
                },
                valueRange = -PREAMP_RANGE_DB..PREAMP_RANGE_DB,
            )
            Text(
                "A curve that lifts is already turned down by half its highest band, so it does not clip. This " +
                    "turns everything further down, or up as far as the phone's equaliser reaches.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            OutlinedButton(
                onClick = { state.updateEqualizer { copy(preset = EqualizerPreset.FLAT, preampDb = 0f) } },
                enabled = equalizer.preset != EqualizerPreset.FLAT || equalizer.preampDb != 0f,
            ) { Text("Reset to flat") }
        }
    }
}

/** What the Sound tile says about the equaliser, in the few words a tile has room for. */
internal fun equalizerSummary(equalizer: EqualizerSettings, available: Boolean): String = when {
    !available -> "This phone has no equaliser for apps"
    !equalizer.enabled -> "Equaliser off"
    equalizer.preampDb != 0f -> "Equaliser: ${equalizer.preset.displayName} · preamp ${decibelsLabel(equalizer.preampDb)} dB"
    else -> "Equaliser: ${equalizer.preset.displayName}"
}

/**
 * The curve the bands make, drawn above them so each band's point sits over its own slider.
 *
 * The bands are an octave apart, so frequency runs on a doubling scale here and every band gets the same
 * width -- which is what lines the curve up with ten sliders of equal width underneath.
 */
@Composable
private fun EqualizerCurve(gains: List<Float>, lit: Boolean) {
    val line = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val zero = MaterialTheme.colorScheme.outlineVariant
    // As tall as can be spared above the sliders: at less than half their height a curve of six decibels
    // looked like a gentle wave, and did not read as the same curve the sliders were showing.
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val middle = size.height / 2
        val reach = middle - 3.dp.toPx()
        drawLine(zero, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())
        val path = Path()
        val steps = 96
        for (step in 0..steps) {
            val x = size.width * step / steps
            // Band i's centre is at (i + 0.5) tenths of the width; 31.25 Hz doubled i times is band i.
            val hz = 31.25 * 2.0.pow(x / size.width * Equalizer.BANDS_HZ.size - .5)
            val y = middle - Equalizer.gainAt(gains, hz) / Equalizer.MAX_GAIN_DB * reach
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * One band, upright: its level above, the slider, and its frequency below.
 *
 * Material's slider only lies down, and ten of those stacked would be a page of their own. Upright, ten fit
 * across a phone the way every equaliser lays them out. It answers a drag rather than a touch, so a thumb
 * resting on it while scrolling past does not move the band.
 */
@Composable
private fun BandSlider(
    label: String,
    spoken: String,
    gain: Float,
    lit: Boolean,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val accent = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = SeekBar.TRACK_ALPHA)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken
            stateDescription = "${decibelsLabel(gain)} decibels"
            progressBarRangeInfo = ProgressBarRangeInfo(gain, -Equalizer.MAX_GAIN_DB..Equalizer.MAX_GAIN_DB)
            setProgress { target ->
                change(roundToHalf(target.coerceIn(-Equalizer.MAX_GAIN_DB, Equalizer.MAX_GAIN_DB)))
                done()
                true
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(decibelsLabel(gain), fontSize = 9.sp, color = if (gain != 0f) accent else quiet, maxLines = 1, softWrap = false)
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT)
                .pointerInput(Unit) {
                    val inset = THUMB_RADIUS.toPx()
                    fun gainAt(y: Float): Float {
                        val usable = (size.height - 2 * inset).coerceAtLeast(1f)
                        val fraction = ((y - inset) / usable).coerceIn(0f, 1f)
                        return roundToHalf(Equalizer.MAX_GAIN_DB - fraction * 2 * Equalizer.MAX_GAIN_DB)
                    }
                    detectVerticalDragGestures(
                        onDragStart = { change(gainAt(it.y)) },
                        onDragEnd = { done() },
                        onDragCancel = { done() },
                        onVerticalDrag = { moved, _ ->
                            moved.consume()
                            change(gainAt(moved.position.y))
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        change(0f)
                        done()
                    })
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = THUMB_RADIUS.toPx()
                val x = size.width / 2
                val top = inset
                val bottom = size.height - inset
                val middle = (top + bottom) / 2
                val thumb = middle - gain / Equalizer.MAX_GAIN_DB * (middle - top)
                val width = 4.dp.toPx()
                drawLine(track, Offset(x, top), Offset(x, bottom), strokeWidth = width, cap = StrokeCap.Round)
                drawLine(quiet.copy(alpha = .6f), Offset(x - 6.dp.toPx(), middle), Offset(x + 6.dp.toPx(), middle), strokeWidth = 1.dp.toPx())
                if (abs(thumb - middle) > .5f) {
                    drawLine(accent, Offset(x, middle), Offset(x, thumb), strokeWidth = width, cap = StrokeCap.Round)
                }
                drawCircle(accent, radius = inset, center = Offset(x, thumb))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = quiet, maxLines = 1, softWrap = false)
    }
}

/** Tall enough to set a band to the half decibel with a thumb, short enough that ten of them and the rest fit. */
private val BAND_HEIGHT = 132.dp
private val THUMB_RADIUS = 7.dp

/** Half a decibel is finer than anybody hears, and keeps the numbers under the sliders short. */
private fun roundToHalf(value: Float): Float = (value * 2).roundToInt() / 2f

/** "+3", "-2.5", "0": a level as an equaliser prints it, the sign always there unless it is nothing. */
internal fun decibelsLabel(value: Float): String {
    val rounded = roundToHalf(value)
    if (rounded == 0f) return "0"
    val digits = if (rounded % 1f == 0f) "${abs(rounded).toInt()}" else "${abs(rounded)}"
    return (if (rounded > 0) "+" else "-") + digits
}

/** "1k" is fine to read and wrong to hear; a screen reader is given the frequency in words. */
private fun spokenFrequency(hz: Int): String =
    if (hz >= 1_000) "${hz / 1_000} kilohertz" else "$hz hertz"
