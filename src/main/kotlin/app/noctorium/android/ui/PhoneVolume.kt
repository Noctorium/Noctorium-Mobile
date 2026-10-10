package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.VolumeBoost
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemeSkin
import kotlin.math.roundToInt

/**
 * Volume, mute and the booster behind one icon, the way the desktop's player bar does it.
 *
 * The phone has volume keys already, and for a long time that was the argument for not having this at all: they
 * set the stream, which is the loudest the phone can be. What they cannot do is set Noctorium relative to
 * everything else -- turning a podcast down without turning the next notification down with it -- and they cannot
 * go past the loudest, which the booster does.
 *
 * Behind a button rather than spread across a row of its own, which is what it was first: that cost a line of a
 * screen with less of it to spare, and left a slider directly under the transport buttons for a thumb reaching for
 * pause to catch. Closed, the icon still says which of muted, quiet or loud it is, and while the booster is on it
 * says by how much -- "×4" -- so a boost left on from yesterday is never a mystery.
 */
@Composable
internal fun VolumeButton(
    playback: PlaybackState,
    state: AppState,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    /** One of the Toolbar bar's flat buttons, drawn as the others are, rather than an icon button of its own. */
    flat: Boolean = false,
) {
    val settings by state.settings.collectAsState()
    var open by remember { mutableStateOf(false) }
    val boost = shownBoost(settings, playback)
    val lit = playback.isMuted || boost != null
    val icon = when {
        playback.isMuted || playback.volume <= 0f -> Glyph.VOLUME_OFF.image
        playback.volume < .5f -> Glyph.VOLUME_DOWN.image
        else -> Glyph.VOLUME_UP.image
    }
    Box(modifier) {
        if (flat) {
            FlatButton(volumeDescription(playback, boost), lit = lit, enabled = true, onClick = { open = true }) { ink ->
                Icon(icon, null, Modifier.size(size), tint = ink)
            }
        } else {
            IconButton({ open = true }, Modifier.semantics { contentDescription = volumeDescription(playback, boost) }) {
                Icon(
                    icon,
                    null,
                    Modifier.size(size),
                    tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Over the button rather than in it, which would cut it to its circle; taps go through to the button.
        if (boost != null) BoostMark(boost, Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-5).dp))
        SkinnedDropdownMenu(open, { open = false }) {
            Column(Modifier.width(272.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                VolumeControls(playback, state, settings)
            }
        }
    }
}

/** The booster's level as the volume button shows it while it is on and can be heard: null otherwise. */
internal fun shownBoost(settings: SettingsState, playback: PlaybackState): Int? =
    settings.preferences.volumeBoostPercent.takeIf { VolumeBoost.isOn(it) && boostAvailable(settings, playback) }

/**
 * Whether the booster can reach the song playing: not where the player says it cannot, and not a song playing in
 * the account's own Spotify app -- said from the song as well as from the player, so the booster never offers a
 * slider for a moment while the player is still changing over.
 */
internal fun boostAvailable(settings: SettingsState, playback: PlaybackState): Boolean =
    playback.volumeBoostAvailable && !playsOnSpotify(playback.track, settings.spotify)

/** What the volume button is, said to somebody listening rather than looking. */
private fun volumeDescription(playback: PlaybackState, boost: Int?): String = buildString {
    append("Volume")
    if (playback.isMuted) append(", muted")
    if (boost != null) append(", boosted to ${VolumeBoost.percentLabel(boost)}")
}

/** "×4", small, in the accent, at the foot of the volume icon. */
@Composable
private fun BoostMark(percent: Int, modifier: Modifier = Modifier) {
    val skinned = LocalSkin.current != ThemeSkin.STANDARD
    // The size of the icon it sits on, whatever the text size: grown with the text it hid the icon under it.
    val size = with(LocalDensity.current) { 9.dp.toSp() }
    Text(
        VolumeBoost.timesLabel(percent),
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = size,
        lineHeight = size * 1.15f,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(if (skinned) 0.dp else 6.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

/** The volume, mute and the booster: the volume button's popover, and its card in Settings. */
@Composable
internal fun VolumeControls(playback: PlaybackState, state: AppState, settings: SettingsState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Volume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            "${(playback.volume * 100).roundToInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    SkinnedSlider(
        value = playback.volume.coerceIn(0f, 1f),
        onValueChange = state::setVolume,
        valueRange = 0f..1f,
    )
    val level = settings.preferences.volumeBoostPercent
    val available = boostAvailable(settings, playback)
    val on = VolumeBoost.isOn(level) && available
    // Taller than the same two chips on the desktop. Thirty density-independent pixels is a comfortable click
    // and an awkward tap; this is what a thumb wants.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkinnedFilterChip(
            selected = playback.isMuted,
            onClick = state::toggleMute,
            label = { Text(if (playback.isMuted) "Muted" else "Mute", fontSize = 12.sp) },
            modifier = Modifier.height(38.dp),
        )
        SkinnedFilterChip(
            selected = on,
            onClick = state::toggleVolumeBoost,
            enabled = available,
            label = { Text(if (on) "Boost ${VolumeBoost.timesLabel(level)}" else "Boost", fontSize = 12.sp) },
            leadingIcon = { Icon(Glyph.BOLT.image, null, Modifier.size(16.dp)) },
            modifier = Modifier.height(38.dp),
        )
    }
    BoosterLevel(level, available, state)
}

/**
 * The booster's own slider, from 100% to 1000%, once it is on; the warning past 200%; and where the sound is made
 * somewhere Noctorium cannot reach -- a song playing in Spotify's own app -- a plain line saying so in its place.
 *
 * Nothing is saved while the finger is down: the level is held here and drawn from here, and written once when the
 * finger lifts, so a drag is one change to the settings and to the player rather than a hundred.
 */
@Composable
internal fun BoosterLevel(level: Int, available: Boolean, state: AppState) {
    if (!available) {
        Text(
            VolumeBoost.UNAVAILABLE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    if (!VolumeBoost.isOn(level)) return
    var held by remember { mutableStateOf<Int?>(null) }
    val shown = held ?: level
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Boost", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            "${VolumeBoost.percentLabel(shown)} · ${VolumeBoost.timesLabel(shown)}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    SkinnedSlider(
        value = shown.toFloat(),
        onValueChange = { held = VolumeBoost.snap(it) },
        onValueChangeFinished = {
            held?.let(state::setVolumeBoostPercent)
            held = null
        },
        valueRange = VolumeBoost.OFF_PERCENT.toFloat()..VolumeBoost.MAX_PERCENT.toFloat(),
        modifier = Modifier.semantics { contentDescription = "Volume boost, ${VolumeBoost.percentLabel(shown)}" },
    )
    if (VolumeBoost.isLoud(shown)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyph.ERROR_OUTLINE.image, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(5.dp))
            Text(VolumeBoost.LOUD_WARNING, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }
}

/**
 * The booster in Settings, under Sound: the same switch and slider as the volume button's, where a search for
 * "louder" can find it.
 */
@Composable
internal fun VolumeBoosterCard(settings: SettingsState, state: AppState) {
    val playback by state.playback.collectAsState()
    val level = settings.preferences.volumeBoostPercent
    val available = boostAvailable(settings, playback)
    SettingsCardShell {
        CardHeading(Glyph.BOLT.image, "Volume booster")
        Text(
            "Louder than the phone's loudest, up to ten times, through the phone's own loudness enhancer: quiet songs " +
                "come up and loud ones are held at full scale rather than broken. It stays on until you switch it off, " +
                "and the volume button says so while it is.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        SettingAnchor(SettingIds.VOLUME_BOOSTER) {
            Toggle(
                "Boost the volume",
                if (VolumeBoost.isOn(level)) {
                    "On, at ${VolumeBoost.percentLabel(level)}."
                } else {
                    val next = VolumeBoost.clamp(settings.preferences.lastVolumeBoostPercent).takeIf(VolumeBoost::isOn) ?: VolumeBoost.FIRST_PERCENT
                    "Off. Switched on, it goes to ${VolumeBoost.percentLabel(next)}."
                },
                VolumeBoost.isOn(level),
                enabled = available,
            ) { state.toggleVolumeBoost() }
            BoosterLevel(level, available, state)
        }
    }
}
