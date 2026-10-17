package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.MusicPlace
import app.noctorium.connect.NowPlaying
import app.noctorium.core.AppState
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.QueueState
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemeSkin

/*
 * Noctorium Connect on the phone, outside its own tab: what the player reads to be another device's remote
 * control, the strip on the player bar saying where the music is, the devices button's short picker, and the
 * card in Settings. The tab itself is PhoneConnectTab.kt.
 *
 * Connect only ever lists the listener's own devices. Two Noctoriums recognise each other by a secret derived from
 * the account they are both signed in to, so somebody else on the same Wi-Fi is not merely hidden -- they cannot be
 * seen, and cannot be answered.
 */

/**
 * Connect and the player as one picture, for the screenshots: the devices and who drives whom, where the music
 * is, what the player shows and this phone's own playback, with the clock the other devices' places are read at.
 *
 * Null in the app, where all of it is read from [AppState] as it changes. A still life cannot be given another
 * device -- Connect would go looking for real ones on the network -- so the pictures of a phone driving a
 * computer hand the screens one of these instead.
 */
@Immutable
internal data class ConnectPicture(
    val connect: ConnectState,
    val place: MusicPlace,
    /** What the player shows: the driven device's song while there is one, as [AppState.shownPlayback] has it. */
    val shown: PlaybackState,
    val shownQueue: QueueState,
    /** This phone's own playback, whatever the player is showing. */
    val here: PlaybackState = PlaybackState(),
    val nowMs: Long = 0,
    val onWifi: Boolean = true,
    val backgroundUnrestricted: Boolean = true,
    /**
     * Whether Connect is switched on here. A picture's own, because the still life starts every state with Connect
     * switched off in its settings -- so that nothing goes looking for real devices -- whatever the picture shows.
     */
    val switchedOn: Boolean = true,
)

internal val LocalConnectPicture = staticCompositionLocalOf<ConnectPicture?> { null }

/** The devices, and which is being driven or driving this one. */
@Composable
internal fun AppState.connectNow(): ConnectState {
    LocalConnectPicture.current?.let { return it.connect }
    val connect by connect.collectAsState()
    return connect
}

/** Whether Noctorium Connect is switched on on this phone: the switch at the top of the Connect tab, and in Settings. */
@Composable
internal fun AppState.connectSwitchedOn(): Boolean =
    LocalConnectPicture.current?.switchedOn ?: settings.collectAsState().value.preferences.connect.enabled

/** What the Connect switch says Connect does, on or off: the desktop's words, with a phone in them. */
internal fun connectSwitchLine(on: Boolean): String =
    if (on) {
        "This phone finds your other devices, and they can find it."
    } else {
        "Off: this phone is not looking for your other devices, and they cannot see it."
    }

/**
 * What switching Connect off would end, said under the switch while there is something: this phone's control of the
 * device it is the remote of -- whose music plays on there -- or another device's control of this phone. Null the
 * rest of the time.
 */
internal fun connectSwitchEnds(connect: ConnectState): String? = when {
    connect.target != null -> "Switching it off ends this phone's control of ${connect.target?.name}. The music plays on there."
    connect.controlledBy != null -> "Switching it off ends ${connect.controlledBy}'s control of this phone."
    else -> null
}

/**
 * Connect on or off, with what that means under it: at the top of the Connect tab, where somebody looking for the
 * switch looks, and at the head of its card in Settings. The whole row is the switch, the words as good to tap as
 * the switch itself; under the Windows skins it is a checkbox, the box first and the words after it, as theirs were.
 *
 * Off stops Connect outright -- nothing is announced on the network, nothing looked for, nothing listened to -- and
 * the remote control with it, either way round. The music itself plays on wherever it is.
 */
@Composable
internal fun ConnectSwitch(on: Boolean, connect: ConnectState, switch: (Boolean) -> Unit) {
    val skinned = LocalSkin.current != ThemeSkin.STANDARD
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(on, role = if (skinned) Role.Checkbox else Role.Switch, onValueChange = switch)
            .padding(vertical = 4.dp),
        verticalAlignment = if (skinned) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (skinned) {
            Box(Modifier.padding(top = 3.dp, end = 10.dp)) { Checkbox(on) }
        } else {
            Icon(Glyph.DEVICES.image, null, Modifier.size(22.dp), tint = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Noctorium Connect",
                fontSize = if (skinned) 13.sp else 15.sp,
                fontWeight = if (skinned) FontWeight.Normal else FontWeight.SemiBold,
            )
            // Set close, as a line under a title is: the theme's own spacing is for paragraphs, and on a narrow phone
            // it stood the two lines of a wrapped sentence a line apart.
            val size = if (skinned) 11.sp else 12.sp
            val spacing = if (skinned) 15.sp else 16.sp
            Text(connectSwitchLine(on), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = size, lineHeight = spacing)
            connectSwitchEnds(connect)?.takeIf { on }?.let {
                Text(it, color = accent, fontSize = size, lineHeight = spacing, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (!skinned) {
            // A gap before the switch, as Toggle keeps, so the last word of the line does not read as printed on it.
            Spacer(Modifier.width(12.dp))
            Switch(on, onCheckedChange = null)
        }
    }
}

/**
 * Where the music is: as Base says, except that the song the kept queue was left on counts as music here. Base
 * reads the player, which has nothing in it after a launch until play is pressed; the player bar shows that song,
 * paused, and Play on sends it with its queue, so the tab says it is here rather than that nothing is playing.
 */
@Composable
internal fun AppState.musicPlaceNow(): MusicPlace {
    val place = LocalConnectPicture.current?.place ?: musicPlace.collectAsState().value
    return place.withKeptSong(playbackHere())
}

/** [this], or here when it is nowhere but this phone has a song waiting in its player: see [musicPlaceNow]. */
internal fun MusicPlace.withKeptSong(here: PlaybackState): MusicPlace =
    if (this == MusicPlace.Nowhere && here.track != null) MusicPlace.Here else this

/**
 * What the player shows: this phone's playback, or the driven device's while this phone is its remote control;
 * and when nothing is playing here after a launch, the song the kept queue was left on, paused (see [shownPlayback]).
 */
@Composable
internal fun AppState.playerPlayback(): PlaybackState {
    val picture = LocalConnectPicture.current
    val reported = picture?.shown ?: shownPlayback.collectAsState().value
    return shownPlayback(reported, playerQueue())
}

/** The queue the player shows: this phone's, or the driven device's as far as it has said. */
@Composable
internal fun AppState.playerQueue(): QueueState = LocalConnectPicture.current?.shownQueue ?: shownQueue.collectAsState().value

/** This phone's own playback, the kept queue's song included, whatever the player is showing. */
@Composable
internal fun AppState.playbackHere(): PlaybackState {
    LocalConnectPicture.current?.let { return it.here }
    val reported by playback.collectAsState()
    val queue by this.queue.state.collectAsState()
    return shownPlayback(reported, queue)
}

/**
 * The strip along the player bar while the music is not simply this phone's: "Playing on Desk" while this phone is
 * the remote control of another device, "Controlled from Desk" while another device is driving this one. Null
 * otherwise, and then there is no strip.
 */
internal fun connectStrip(connect: ConnectState): String? = when {
    connect.target != null -> "Playing on ${connect.target?.name}"
    connect.controlledBy != null -> "Controlled from ${connect.controlledBy}"
    else -> null
}

/**
 * The strip itself: a band along the foot of the player bar, in the accent, that opens the Connect tab. Under the
 * Windows themes it is a status bar's panel, sunk into the bar's face.
 */
@Composable
internal fun ConnectStrip(state: AppState, glass: Boolean, open: () -> Unit) {
    val connect = state.connectNow()
    val text = connectStrip(connect) ?: return
    val skin = LocalSkin.current
    val win98 = Win98
    val ink = when (skin) {
        ThemeSkin.STANDARD -> MaterialTheme.colorScheme.primary
        ThemeSkin.WINDOWS_98 -> win98.Text
        ThemeSkin.WINDOWS_XP -> Luna.Text
    }
    val band = when (skin) {
        ThemeSkin.STANDARD -> Modifier.background(if (glass) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = .14f))
        ThemeSkin.WINDOWS_98 -> Modifier
            .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
            .background(win98.Face)
            .drawWithContent {
                drawContent()
                drawEdge98(Edge98.SHALLOW, win98)
            }
        ThemeSkin.WINDOWS_XP -> Modifier
            .background(Luna.Face)
            .drawWithContent {
                drawContent()
                drawRect(Luna.TabEdge.copy(alpha = .7f), Offset.Zero, Size(size.width, 1f))
            }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(band)
            .clickable(onClickLabel = "Open Connect", role = Role.Button, onClick = open)
            .height(STRIP_HEIGHT)
            .padding(start = if (glass) 24.dp else 14.dp, end = if (glass) 20.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (connect.target != null) connect.target!!.kind.glyph().image else Glyph.DEVICES.image,
            null,
            Modifier.size(15.dp),
            tint = ink,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = ink,
            fontSize = 12.sp,
            fontWeight = if (skin == ThemeSkin.STANDARD) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = ink, fontSize = 15.sp)
    }
}

private val STRIP_HEIGHT = 26.dp

/** The icon for a kind of device: a computer's screen, a phone, a terminal's prompt. */
internal fun DeviceKind.glyph(): Glyph = when (this) {
    DeviceKind.DESKTOP -> Glyph.COMPUTER
    DeviceKind.PHONE -> Glyph.PHONE_ANDROID
    DeviceKind.TERMINAL -> Glyph.TERMINAL
}

/** What a kind of device is called under its name. */
internal fun kindName(kind: DeviceKind): String = when (kind) {
    DeviceKind.DESKTOP -> "Computer"
    DeviceKind.PHONE -> "Phone"
    DeviceKind.TERMINAL -> "Terminal"
}

/**
 * The devices button on Now playing: a short picker of where the music can play -- this phone and each device
 * found, the one with the music ticked -- and the way into the Connect tab, which has the rest.
 *
 * Choosing a device moves the music there, from wherever it is, at the same second and with the whole queue; the
 * player then drives it from here. It used to open a popup with a remote control of its own, a second player beside
 * the real one. The player is the remote now, and the popup has gone.
 */
@Composable
internal fun DevicesButton(state: AppState, haptics: Haptics, openConnect: () -> Unit) {
    val connect = state.connectNow()
    val place = state.musicPlaceNow()
    var open by remember { mutableStateOf(false) }
    if (!connect.available && connect.devices.isEmpty() && connect.target == null) return

    Box {
        IconButton({ haptics.tick(); open = true }, Modifier.litWash(connect.target != null)) {
            Icon(
                Glyph.DEVICES.image,
                connectStrip(connect) ?: "Devices",
                tint = if (connect.target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        SkinnedDropdownMenu(open, { open = false }) {
            SkinnedDropdownMenuItem(
                "This phone",
                checked = place == MusicPlace.Here,
                onClick = {
                    open = false
                    if (place.isElsewhere() && !connect.busy) {
                        haptics.tick()
                        state.playHere()
                    }
                },
            )
            connect.devices.forEach { peer ->
                SkinnedDropdownMenuItem(
                    peer.name,
                    checked = place.isOn(peer),
                    onClick = {
                        open = false
                        if (canPlayOn(place, peer, connect.busy)) {
                            haptics.tick()
                            state.playOn(peer)
                        }
                    },
                )
            }
            if (LocalSkin.current == ThemeSkin.STANDARD) HorizontalDivider() else SkinSeparator(Modifier.padding(vertical = 3.dp))
            SkinnedDropdownMenuItem("Open Connect", onClick = { open = false; openConnect() })
        }
    }
}

/** Whether the music is on another device: one this phone drives, or one playing by itself. */
internal fun MusicPlace.isElsewhere(): Boolean = this is MusicPlace.Driving || this is MusicPlace.Elsewhere

/** Whether the music is on [peer]. */
internal fun MusicPlace.isOn(peer: ConnectPeer): Boolean = when (this) {
    is MusicPlace.Driving -> this.peer.id == peer.id
    is MusicPlace.Elsewhere -> this.peer.id == peer.id
    else -> false
}

/** Whether "Play on" [peer] has anything to do: music somewhere that is not there already, and no move under way. */
internal fun canPlayOn(place: MusicPlace, peer: ConnectPeer, busy: Boolean): Boolean =
    !busy && place != MusicPlace.Nowhere && !place.isOn(peer)

/** "Lanterns Over the Harbour — The Quiet Hours", and "Paused · " before it while it is: what a device is playing. */
internal fun deviceDetail(nowPlaying: NowPlaying?): String {
    nowPlaying ?: return "Idle"
    val song = listOf(nowPlaying.title, nowPlaying.artist).filter(String::isNotBlank).joinToString(" — ")
    return if (nowPlaying.playing) song else "Paused · $song"
}

/** The same for this phone, from its own player. */
internal fun deviceDetail(here: PlaybackState): String {
    val track = here.track ?: return "Idle"
    return deviceDetail(NowPlaying(track.title, track.artistLine, playing = here.isPlaying))
}

/**
 * Who is driving whom, said under a device: "Controlled from Desk", or "Controlling Desk". This phone is "this
 * phone" in it rather than its name, which reads as somebody else's.
 */
internal fun relationLine(peer: ConnectPeer, thisDeviceId: String): String? {
    fun name(id: String, name: String) = if (id == thisDeviceId && thisDeviceId.isNotBlank()) "this phone" else name
    peer.drivenBy?.let { return "Controlled from ${name(it.id, it.name)}" }
    peer.driving?.let { return "Controlling ${name(it.id, it.name)}" }
    return null
}

/** The same for this phone: which device it is the remote control of, or which is driving it. */
internal fun relationHere(connect: ConnectState): String? = when {
    connect.target != null -> "Controlling ${connect.target?.name}"
    connect.controlledBy != null -> "Controlled from ${connect.controlledBy}"
    else -> null
}

/**
 * What Connect has to say, unless the strip and the tab say it already: after a move it says "Playing on Desk.",
 * which is the strip's own line over again.
 */
internal fun connectNote(connect: ConnectState): String? {
    val message = connect.message?.takeIf(String::isNotBlank) ?: return null
    val target = connect.target ?: return message
    return message.takeUnless { it.trimEnd('.') == "Playing on ${target.name}" }
}

/**
 * An address typed to look for a device at: "192.168.1.20:41234", "[fe80::1]:41234" or "desk.local 41234". Null
 * for anything without a port, which the device's Connect cannot be found without.
 */
internal fun parseConnectAddress(text: String): Pair<String, Int>? {
    val typed = text.trim()
    if (typed.isEmpty()) return null
    val (host, port) = when {
        typed.startsWith("[") -> typed.substringAfter('[').substringBefore(']') to typed.substringAfter("]:", "")
        ' ' in typed -> typed.substringBefore(' ').trim() to typed.substringAfterLast(' ').trim()
        typed.count { it == ':' } == 1 -> typed.substringBefore(':') to typed.substringAfter(':')
        else -> return null
    }
    val number = port.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
    return host.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }?.let { it to number }
}

/**
 * Connect switched on or off -- the same switch, in the same words, as the top of the Connect tab -- and naming this
 * phone. The switch heads the card, so the card has no heading of its own saying the same name again.
 */
@Composable
internal fun ConnectCard(settings: SettingsState, state: AppState) {
    val connect = state.connectNow()
    val on = state.connectSwitchedOn()
    var name by remember(settings.preferences.connect.deviceName) {
        mutableStateOf(settings.preferences.connect.deviceName)
    }

    SettingsCardShell {
        SettingAnchor(SettingIds.CONNECT_ENABLED) {
            ConnectSwitch(on, connect, state::setConnectEnabled)
            if (on) {
                Text(
                    if (connect.available) {
                        "Your devices on this Wi-Fi can hand the music to each other. This one appears as " +
                            "\"${connect.thisDevice}\". " +
                            if (connect.devices.isEmpty()) "Nothing else is found yet." else "${pluralDevices(connect.devices.size)} found on this network."
                    } else {
                        "Sign in to your Noctorium account, and your devices will find each other on this Wi-Fi."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingAnchor(SettingIds.CONNECT_NAME) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name this phone") },
                placeholder = { Text(connect.thisDevice) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                SkinnedTextButton({ state.renameThisDevice(name) }) { Text("Save") }
            }
        }

        // Where the devices are, and the music: the tab has it all, and the settings are only these two.
        SettingAnchor(SettingIds.CONNECT_TAB) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Where the music is, every device and what each is playing, and moving the music between them are on " +
                    "the Connect tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            SkinnedTextButton({ state.navigate(app.noctorium.core.Destination.CONNECT) }) { Text("Open Connect") }
        }
    }
}

internal fun pluralDevices(count: Int): String = if (count == 1) "1 device" else "$count devices"
