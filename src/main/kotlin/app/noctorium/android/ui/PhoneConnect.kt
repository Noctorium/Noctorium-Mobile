package app.noctorium.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.RemoteState
import app.noctorium.core.AppState
import app.noctorium.settings.SettingsState
import kotlinx.coroutines.delay

/**
 * Noctorium Connect, from the phone.
 *
 * The same feature as on the desktop and the same button, sized for a thumb. Tapping a device moves the
 * music there from the second it is at; while something else is playing it, this is its remote control and
 * how it is taken back.
 *
 * The dialog stays open once a device has been tapped, and says how it went. It used to close at once, and
 * a handover that failed -- which, from a phone, was every one of them -- failed with nothing on the screen.
 *
 * It only ever lists the listener's own devices. Two Noctoriums recognise each other by a secret derived
 * from the account they are both signed in to, so somebody else on the same wifi is not merely hidden from
 * this list -- they cannot be seen, and cannot be answered.
 */
@Composable
internal fun ConnectButton(state: AppState, haptics: Haptics) {
    val connect by state.connect.collectAsState()
    val playback by state.playback.collectAsState()
    var open by remember { mutableStateOf(false) }

    if (!connect.available && connect.devices.isEmpty() && connect.target == null) return

    IconButton({ haptics.tick(); open = true }) {
        Icon(
            Glyph.DEVICES.image,
            if (connect.target != null) "Playing on ${connect.target?.name}" else "Noctorium Connect",
            tint = if (connect.target != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    if (!open) return
    val close = {
        open = false
        // Read by now, or it would greet the next opening as though it were news.
        state.dismissConnectMessage()
    }
    SkinnedAlertDialog(
        onDismissRequest = close,
        title = "Noctorium Connect",
        titleSize = 16.sp,
        text = {
            Column {
                Text(
                    connectHeadline(connect),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                connect.message?.takeIf { it.trimEnd('.') != connectHeadline(connect).trimEnd('.') }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))

                if (connect.target != null) {
                    RemoteControls(state, connect.remote, haptics)
                    Spacer(Modifier.height(6.dp))
                }

                DeviceRow(
                    name = connect.thisDevice.ifBlank { "This phone" },
                    detail = localDetail(connect.target != null, playback.isPlaying),
                    kind = DeviceKind.PHONE,
                    current = connect.target == null,
                ) {
                    if (connect.target != null && !connect.busy) {
                        haptics.tick()
                        state.bringPlaybackBack()
                    }
                }

                connect.devices.forEach { peer ->
                    HorizontalDivider()
                    DeviceRow(
                        name = peer.name,
                        detail = when {
                            connect.target?.id == peer.id -> "Playing here"
                            connect.busy -> "Wait a moment"
                            else -> "Tap to play here"
                        },
                        kind = peer.kind,
                        current = connect.target?.id == peer.id,
                    ) {
                        if (connect.target?.id != peer.id && !connect.busy) {
                            haptics.tick()
                            state.playOn(peer)
                        }
                    }
                }

                if (connect.devices.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (connect.available) {
                            "Nothing else yet. Open Noctorium on your computer, on this wifi, " +
                                "signed in to the same account."
                        } else {
                            "Sign in to your Noctorium account under Settings to use Connect."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (connect.target != null) {
                SkinnedTextButton({ state.bringPlaybackBack(); close() }) { Text("Bring it back") }
            } else {
                SkinnedTextButton(close) { Text("Close") }
            }
        },
        dismissButton = {
            if (connect.target != null) {
                SkinnedTextButton({ state.stopControlling(); close() }) { Text("Leave it playing") }
            }
        },
    )
}

/**
 * The other device's play button, skips and volume, and what it is playing.
 *
 * Drawn from what that device says about itself, read every second, so it shows what is really happening
 * there rather than what was last asked for.
 */
@Composable
private fun RemoteControls(state: AppState, remote: RemoteState?, haptics: Haptics) {
    val track = remote?.track
    Text(
        track?.title ?: "Nothing playing there yet",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        remotePosition(remote),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val duration = remote?.durationMs ?: 0
    if (duration > 0) {
        // Where the thumb was let go, until the other device has had a moment to get there and say so.
        var sought by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(sought) {
            if (sought != null) {
                delay(SEEK_SETTLES_MS)
                sought = null
            }
        }
        var dragging by remember { mutableStateOf<Float?>(null) }
        SkinnedSlider(
            value = dragging ?: sought ?: (remote!!.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { fraction ->
                    state.connectSeekTo((fraction * duration).toLong())
                    sought = fraction
                }
                dragging = null
            },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ haptics.tick(); state.connectPrevious() }) { Icon(Glyph.SKIP_PREVIOUS.image, "Previous, there") }
        IconButton({ haptics.tick(); state.connectPlayPause() }) {
            Icon(
                if (remote?.playing == true) Glyph.PAUSE.image else Glyph.PLAY_ARROW.image,
                if (remote?.playing == true) "Pause, there" else "Play, there",
                Modifier.size(32.dp),
            )
        }
        IconButton({ haptics.tick(); state.connectNext() }) { Icon(Glyph.SKIP_NEXT.image, "Next, there") }
    }
    // Sent when the thumb lets go, not on every step of the drag: each one is a request to the other device.
    var volume by remember(remote?.volume) { mutableFloatStateOf(remote?.volume ?: 1f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyph.VOLUME_DOWN.image, "Volume, there", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SkinnedSlider(
            value = volume,
            onValueChange = { volume = it },
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            onValueChangeFinished = { state.connectSetVolume(volume) },
        )
        Icon(Glyph.VOLUME_UP.image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRow(
    name: String,
    detail: String,
    kind: DeviceKind,
    current: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (kind == DeviceKind.PHONE) Glyph.PHONE_ANDROID.image else Glyph.COMPUTER.image,
            null,
            Modifier.size(20.dp),
            tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.sp,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/** Naming this phone, and switching Connect off for people who would rather it did not announce itself. */
@Composable
internal fun ConnectCard(settings: SettingsState, state: AppState) {
    val connect by state.connect.collectAsState()
    var name by remember(settings.preferences.connect.deviceName) {
        mutableStateOf(settings.preferences.connect.deviceName)
    }

    SettingsCardShell {
        CardHeading(Glyph.DEVICES.image, "Noctorium Connect")
        Text(
            if (connect.available) {
                "Your devices on this wifi can hand playback to each other. " +
                    "This one appears as \"${connect.thisDevice}\"."
            } else {
                "Sign in to your Noctorium account, below, and your devices will find each other on this wifi."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))

        SettingAnchor(SettingIds.CONNECT_NAME) { OutlinedTextField(
            name,
            { name = it },
            label = { Text("Name this phone") },
            placeholder = { Text(connect.thisDevice) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            SkinnedTextButton({ state.renameThisDevice(name) }) { Text("Save") }
        } }

        SettingAnchor(SettingIds.CONNECT_ENABLED) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Let my other devices find this one", fontSize = 13.sp)
                Text(
                    if (connect.devices.isEmpty()) {
                        "Nothing else found yet."
                    } else {
                        pluralDevices(connect.devices.size) + " on this network."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            SkinnedSwitch(settings.preferences.connect.enabled, state::setConnectEnabled)
        } }

        connect.controlledBy?.let {
            Text(
                "$it is controlling this phone.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The line at the top of the dialog: what is happening, said before anything else. */
internal fun connectHeadline(connect: ConnectState): String = when {
    connect.busy -> "Sending the music…"
    connect.target != null -> "Playing on ${connect.target?.name}."
    connect.controlledBy != null -> "${connect.controlledBy} is controlling this phone."
    else -> "Your devices on this wifi."
}

/** Where the other device is in its song, and in its queue: "1:02 of 3:45 · 2 of 9". */
internal fun remotePosition(remote: RemoteState?): String {
    if (remote?.track == null) return ""
    val artists = remote.track?.artists.orEmpty().joinToString { it.name }
    val clock = if (remote.durationMs > 0) {
        "${formatDuration(remote.positionMs)} of ${formatDuration(remote.durationMs)}"
    } else {
        formatDuration(remote.positionMs)
    }
    val place = if (remote.queueSize > 1 && remote.queueIndex >= 0) " · ${remote.queueIndex + 1} of ${remote.queueSize}" else ""
    val state = if (remote.playing) "" else " · paused"
    return listOf(artists, clock + place + state).filter(String::isNotBlank).joinToString(" — ")
}

/** What this phone is doing, said accurately rather than optimistically. */
private fun localDetail(elsewhere: Boolean, playing: Boolean): String = when {
    elsewhere -> "Idle"
    playing -> "Playing here"
    else -> "Ready"
}

private fun pluralDevices(count: Int): String = if (count == 1) "1 device" else "$count devices"

/** Long enough for the other device to have sought and been asked again, so the bar does not jump back. */
private const val SEEK_SETTLES_MS = 2_000L
