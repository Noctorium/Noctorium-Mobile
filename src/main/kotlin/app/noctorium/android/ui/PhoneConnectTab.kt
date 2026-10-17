package app.noctorium.android.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.MusicPlace
import app.noctorium.connect.NowPlaying
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.playback.PlaybackState
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.ThemeSkin
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * The Connect tab: Connect switched on or off, where the music is, every one of the listener's devices on this
 * network with what each is playing, and moving the music between them.
 *
 * It reads as the desktop's Connect page does, section for section and word for word: the switch first, saying what
 * on and off mean; the music, big, with the device it is on; then each device -- this phone, then the others -- with
 * what it is playing or "Idle", who is controlling whom, and its one action, "Play here" for this phone and "Play on"
 * for any other, which moves the music there from wherever it is, at the same second and with the whole queue; then,
 * when a device is not found, the honest reasons it may not be, and a way to name one by its address. Switched off,
 * the switch is all there is, over a calm word that Connect is off: none of the reasons a device may not be found
 * apply to Connect that was turned off on purpose.
 *
 * Nothing on it is a remote control. While the music is on another device the player bar and Now playing are that
 * device's player, as they would be for a song playing here.
 */
@Composable
internal fun ConnectScreen(state: AppState) {
    val connect = state.connectNow()
    val on = state.connectSwitchedOn()
    val place = state.musicPlaceNow()
    val here = state.playbackHere()
    val shown = state.playerPlayback()
    val settings by state.settings.collectAsState()
    val account by state.account.collectAsState()
    val picture = LocalConnectPicture.current
    val onWifi = picture?.onWifi ?: rememberOnWifi()
    val unrestricted = picture?.backgroundUnrestricted ?: rememberBackgroundUnrestricted()
    // The other devices' places move on by the clock between what they say, once a second here.
    val now = picture?.nowMs ?: rememberSecondClock()
    val haptics = rememberHaptics(state)
    val style = settings.preferences.progressBarStyle
    val song = songAt(place, here, shown, now)
    val list = rememberLazyListState()
    // A search in Settings for adding a device by its address lands here, at the card, lit for a moment.
    val asked = LocalTabSetting.current
    val highlight = asked.id
    LaunchedEffect(highlight) {
        if (highlight == null) return@LaunchedEffect
        if (highlight == SettingIds.CONNECT_ADDRESS && connect.available) list.animateScrollToItem(ADDRESS_ITEM)
        delay(TAB_HIGHLIGHT_MS)
        asked.id = null
    }

    SaysWhatItShows(place, connect)

    ScreenScaffold {
        ScreenTitle("Connect", "Your devices, and where the music is")
        CompositionLocalProvider(LocalSettingHighlight provides highlight) {
            LazyColumn(state = list, contentPadding = chromePadding(24.dp)) {
                item(key = "switch") {
                    SettingsCardShell {
                        ConnectSwitch(on, connect) { haptics.tick(); state.setConnectEnabled(it) }
                    }
                }
                if (!on) {
                    item(key = "off") { ConnectOff() }
                    return@LazyColumn
                }
                item(key = "music") {
                    MusicCard(place, connect, song, style, state, haptics)
                }
                if (!connect.available) {
                    item(key = "unavailable") {
                        UnavailableCard(signedIn = account.signedIn, onWifi = onWifi, state = state)
                    }
                    return@LazyColumn
                }
                item(key = "devices") {
                    DevicesCard(connect, place, here, now, song, state, haptics)
                }
                item(key = "help") {
                    HelpCard(found = connect.devices.isNotEmpty(), onWifi = onWifi, unrestricted = unrestricted)
                }
                item(key = "address") { AddressCard(state) }
            }
        }
    }
}

/** Where the address card is in the tab's list: the switch, the music, the devices, the help, then it. */
private const val ADDRESS_ITEM = 4

/** Long enough for the list to reach the card and the card to be lit and fade. */
private const val TAB_HIGHLIGHT_MS = 3_500L

/** What the music card shows of the song where the music is, read from wherever that is. */
internal data class SongThere(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * The song where the music is: this phone's own, the driven device's as the player shows it (moving by the quarter
 * second between its answers), or another device's as it last said, moved on by the clock to [nowMs].
 */
internal fun songAt(place: MusicPlace, here: PlaybackState, shown: PlaybackState, nowMs: Long): SongThere? = when (place) {
    MusicPlace.Here -> here.track?.let {
        SongThere(it.title, it.artistLine, it.artworkUrl, here.isPlaying, here.positionMs, here.durationMs)
    }
    is MusicPlace.Driving -> shown.track?.let {
        SongThere(it.title, it.artistLine, it.artworkUrl, shown.isPlaying, shown.positionMs, shown.durationMs)
    } ?: place.peer.nowPlaying?.let {
        SongThere(it.title, it.artist, it.artworkUrl, it.playing, place.peer.positionAt(nowMs), it.durationMs)
    }
    is MusicPlace.Elsewhere -> place.peer.nowPlaying?.let {
        SongThere(it.title, it.artist, it.artworkUrl, it.playing, place.peer.positionAt(nowMs), it.durationMs)
    }
    MusicPlace.Nowhere -> null
}

/** The head of the music card: where the music is, and whether it is playing there. */
internal fun whereTheMusicIs(place: MusicPlace, song: SongThere?): String {
    val doing = if (song?.playing == true) "Playing" else "Paused"
    return when (place) {
        MusicPlace.Here -> "$doing on this phone"
        is MusicPlace.Driving -> "$doing on ${place.peer.name}"
        is MusicPlace.Elsewhere -> "$doing on ${place.peer.name}"
        MusicPlace.Nowhere -> "Nothing is playing"
    }
}

/** Who is controlling whom, said under the song: the line that makes a remote control plain. */
internal fun whoControls(place: MusicPlace, connect: ConnectState): String? = when (place) {
    MusicPlace.Here -> connect.controlledBy?.let { "$it is controlling this phone." }
    is MusicPlace.Driving -> "This phone is its remote: the player here drives it."
    is MusicPlace.Elsewhere -> place.peer.drivenBy?.let { driver ->
        if (driver.id == connect.thisDeviceId) "Controlled from this phone." else "Controlled from ${driver.name}."
    } ?: "Playing by itself."
    MusicPlace.Nowhere -> "Play something here, or on any of your devices, and it shows up here."
}

/**
 * The music, big: the cover, the song, whether it is playing and how far in, moving -- and where it is, with the way
 * to bring it here when it is somewhere else.
 */
@Composable
private fun MusicCard(
    place: MusicPlace,
    connect: ConnectState,
    song: SongThere?,
    style: ProgressBarStyle,
    state: AppState,
    haptics: Haptics,
) {
    val device = when (place) {
        is MusicPlace.Driving -> place.peer.kind
        is MusicPlace.Elsewhere -> place.peer.kind
        else -> DeviceKind.PHONE
    }
    HeroShell(song?.artworkUrl) {
        CardHeading(
            if (place == MusicPlace.Nowhere) Glyph.DEVICES.image else device.glyph().image,
            whereTheMusicIs(place, song),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(song?.artworkUrl, HERO_COVER, corner = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (song == null) {
                    Text("Nothing yet", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(song.title, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist.ifBlank { "Unknown artist" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                PlayingMark(song)
            }
        }
        if (song != null) {
            Spacer(Modifier.height(14.dp))
            val line = PlaybackState(positionMs = song.positionMs, durationMs = song.durationMs)
            PlaybackLine(line, style)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(song.positionMs.coerceAtLeast(0)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, style = FIGURES)
                Text(
                    if (song.durationMs > 0) formatDuration(song.durationMs) else "--:--",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    style = FIGURES,
                )
            }
        }
        whoControls(place, connect)?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = if (connect.controlledBy != null || place is MusicPlace.Driving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (connect.controlledBy != null || place is MusicPlace.Driving) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        connectNote(connect)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        when {
            connect.busy -> {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Sending the music…", fontSize = 13.sp)
                }
            }
            place.isElsewhere() -> {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkinnedButton({ haptics.tick(); state.playHere() }) { Text("Play here") }
                    if (place is MusicPlace.Driving) {
                        SkinnedOutlinedButton({ haptics.tick(); state.stopControlling() }) { Text("Leave it playing") }
                    }
                }
            }
        }
    }
}

/** Playing or paused, said in a word beside a mark the eye finds without reading. */
@Composable
private fun PlayingMark(song: SongThere?) {
    if (song == null) return
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (song.playing) Glyph.GRAPHIC_EQ.image else Glyph.PAUSE.image,
            null,
            Modifier.size(16.dp),
            tint = if (song.playing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (song.playing) "Playing" else "Paused",
            color = if (song.playing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The music card's frame: a settings card with the cover washed in behind it in the ordinary themes, a group box
 * under the Windows ones, which had no such thing.
 */
@Composable
private fun HeroShell(artworkUrl: String?, content: @Composable ColumnScope.() -> Unit) {
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        SettingsCardShell(content = content)
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(cardColour(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))),
    ) {
        CoverWash(artworkUrl)
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** The cover, blurred and faint, behind the music card: the card is about that song. */
@Composable
private fun BoxScope.CoverWash(artworkUrl: String?) {
    if (artworkUrl == null) return
    AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = .32f,
        modifier = Modifier
            .matchParentSize()
            .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(36.dp) else Modifier),
    )
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .45f)))
}

/** Every device: this phone first, then the others found, each with what it is playing and its one action. */
@Composable
private fun DevicesCard(
    connect: ConnectState,
    place: MusicPlace,
    here: PlaybackState,
    nowMs: Long,
    song: SongThere?,
    state: AppState,
    haptics: Haptics,
) {
    SettingsCardShell {
        CardHeading(Glyph.DEVICES.image, "Devices")
        Spacer(Modifier.height(4.dp))
        DeviceRow(
            glyph = Glyph.PHONE_ANDROID,
            name = connect.thisDevice.ifBlank { "This phone" },
            about = "This phone",
            detail = if (place.isElsewhere()) "Idle" else deviceDetail(here),
            relation = relationHere(connect),
            hasMusic = place == MusicPlace.Here,
            playing = here.isPlaying,
            progress = if (place == MusicPlace.Here) here.track?.let { playbackFraction(here.positionMs, here.durationMs) } else null,
        ) {
            if (place.isElsewhere()) {
                SkinnedTonalButton({ haptics.tick(); state.playHere() }, enabled = !connect.busy, modifier = Modifier.heightIn(min = 36.dp)) {
                    Text("Play here", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        connect.devices.forEach { peer ->
            DeviceDivider()
            val music = place.isOn(peer)
            // The device this phone drives is told about by the player, which shows a pause or a skip at once and
            // moves by the quarter second; the others by what they last said.
            val driven = song?.takeIf { place is MusicPlace.Driving && music }
            DeviceRow(
                glyph = peer.kind.glyph(),
                name = peer.name,
                about = listOf(kindName(peer.kind), hostAndPortOf(peer)).joinToString(" · "),
                detail = driven?.let { deviceDetail(NowPlaying(it.title, it.artist, playing = it.playing)) } ?: deviceDetail(peer.nowPlaying),
                relation = relationLine(peer, connect.thisDeviceId),
                hasMusic = music,
                playing = driven?.playing ?: (peer.nowPlaying?.playing == true),
                progress = driven?.let { playbackFraction(it.positionMs, it.durationMs) }
                    ?: peer.nowPlaying?.takeIf { it.durationMs > 0 }?.let { playbackFraction(peer.positionAt(nowMs), it.durationMs) },
            ) {
                if (!music) {
                    SkinnedTonalButton(
                        { haptics.tick(); state.playOn(peer) },
                        enabled = canPlayOn(place, peer, connect.busy),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) { Text("Play on ${peer.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        if (connect.devices.isEmpty()) {
            DeviceDivider()
            Text(
                "Nothing else found yet. Open Noctorium on your computer, on this Wi-Fi, signed in to the same account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

/** "192.168.1.20:41234", the way a device's address and port are typed to find it. */
private fun hostAndPortOf(peer: ConnectPeer): String = typedAddress(peer.address to peer.port)

internal fun typedAddress(address: Pair<String, Int>): String =
    if (':' in address.first) "[${address.first}]:${address.second}" else "${address.first}:${address.second}"

@Composable
private fun DeviceDivider() {
    if (LocalSkin.current == ThemeSkin.STANDARD) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
    } else {
        SkinSeparator()
    }
}

/**
 * One device: its kind's icon -- lit, in a ring of the accent, on the device the music is on -- its name and what it
 * is, what it is playing with how far in, who it is controlling or is controlled from, and its action beneath.
 */
@Composable
private fun DeviceRow(
    glyph: Glyph,
    name: String,
    about: String,
    detail: String,
    relation: String?,
    hasMusic: Boolean,
    /** Whether the music, where it is on this device, is playing rather than paused. */
    playing: Boolean,
    progress: Float?,
    action: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val skinned = LocalSkin.current != ThemeSkin.STANDARD
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(DEVICE_ICON)
                .clip(if (skinned) RoundedCornerShape(0.dp) else CircleShape)
                .background(if (hasMusic) accent.copy(alpha = .18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(glyph.image, null, Modifier.size(22.dp), tint = if (hasMusic) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasMusic) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasMusic) {
                    Spacer(Modifier.width(8.dp))
                    Text(if (playing) "Playing here" else "Paused here", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
            Text(about, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                fontSize = 13.sp,
                color = if (detail == "Idle") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
                }
            }
            relation?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(top = 6.dp)) { action() }
        }
    }
}

/**
 * Connect switched off: what that means, said once and quietly, with the switch above to turn it back on. None of
 * the troubleshooting -- the Wi-Fi, the account, the firewall -- since nothing is wrong.
 */
@Composable
private fun ConnectOff() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Glyph.DEVICES.image, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
        Spacer(Modifier.height(12.dp))
        Text("Noctorium Connect is off", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "This phone plays only its own music, and your computers and other phones do not see it. Switch Connect " +
                "on above to find them again.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Why the music cannot go anywhere yet, with Connect switched on, and the one thing to do about it: no account
 * signed in for the devices to know each other by, or the account's key not fetched yet.
 */
@Composable
private fun UnavailableCard(signedIn: Boolean, onWifi: Boolean, state: AppState) {
    SettingsCardShell {
        when {
            !signedIn -> {
                CardHeading(Glyph.ACCOUNT_CIRCLE.image, "Sign in to use Connect")
                Note(
                    "Your devices find each other through your Noctorium account. Sign in to the same one on each, and " +
                        "they appear here.",
                )
                SkinnedButton({ state.navigate(Destination.SETTINGS) }, Modifier.padding(top = 8.dp)) { Text("Open Settings") }
            }
            else -> {
                CardHeading(Glyph.KEY.image, "Getting ready")
                Note(
                    "Connect is waiting for your account's key from the Noctorium service. It is fetched once, and from " +
                        "then on Connect works without the internet.",
                )
            }
        }
        if (!onWifi) {
            Spacer(Modifier.height(8.dp))
            HelpLine(Glyph.WIFI, NOT_ON_WIFI, warning = true)
        }
    }
}

/**
 * What to look at when a device is not here: the same account, the same Wi-Fi, a computer's firewall, and this phone
 * allowed to run in the background -- with Android's own way to allow it, which is the listener's to choose.
 */
@Composable
private fun HelpCard(found: Boolean, onWifi: Boolean, unrestricted: Boolean) {
    val context = LocalContext.current
    SettingsCardShell {
        CardHeading(Glyph.DEVICES.image, if (found) "Not seeing a device?" else "No other devices yet")
        Spacer(Modifier.height(4.dp))
        HelpLine(Glyph.ACCOUNT_CIRCLE, "Sign each device in to the same Noctorium account.")
        HelpLine(Glyph.WIFI, if (onWifi) "Keep them on the same Wi-Fi as this phone." else NOT_ON_WIFI, warning = !onWifi)
        HelpLine(
            Glyph.COMPUTER,
            "On a Windows computer, let Noctorium through the firewall when Windows asks. A computer that can be seen " +
                "but not reached is nearly always its firewall.",
        )
        if (!unrestricted) {
            HelpLine(
                Glyph.BATTERY_ALERT,
                "For your other devices to start music here while Noctorium is in the background, allow it to run in " +
                    "the background.",
                warning = true,
            )
            SkinnedTextButton({ app.noctorium.android.BackgroundPlayback.ask(context) }, Modifier.padding(start = 26.dp)) { Text("Allow") }
        }
    }
}

private const val NOT_ON_WIFI = "This phone is not on Wi-Fi. Your devices find each other on the same Wi-Fi network."

@Composable
private fun HelpLine(glyph: Glyph, text: String, warning: Boolean = false) {
    val colour = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(glyph.image, null, Modifier.padding(top = 1.dp).size(16.dp), tint = colour)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (warning) colour else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

/**
 * A device named by its address, for a network that keeps devices from hearing each other -- a guest network, a
 * computer inside a virtual machine. Asked who it is every few seconds, and listed for as long as it answers; only
 * a device on the same account answers at all.
 */
@Composable
private fun AddressCard(state: AppState) {
    var typed by remember { mutableStateOf("") }
    var said by remember { mutableStateOf<String?>(null) }
    SettingsCardShell(anchor = SettingIds.CONNECT_ADDRESS) {
        CardHeading(Glyph.ADD.image, "Add a device by its address")
        Note("For a network that keeps your devices from seeing each other. Only a device signed in to your account answers.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                typed,
                { typed = it.take(60); said = null },
                label = { Text("Address and port") },
                placeholder = { Text("192.168.1.20:41234") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SkinnedButton(
                {
                    val found = parseConnectAddress(typed)
                    said = if (found == null) {
                        "Type the address and the port, such as 192.168.1.20:41234."
                    } else {
                        state.addConnectAddress(found.first, found.second)
                        typed = ""
                        "Looking for a device at ${typedAddress(found)}. It appears above once it answers."
                    }
                },
                enabled = typed.isNotBlank(),
            ) { Text("Add") }
        }
        said?.let { Note(it) }
    }
}

/**
 * Whether this phone is on Wi-Fi -- or a wire, which serves as well -- now, and as that changes. Connect's devices
 * find each other on the network they share, and mobile data shares nothing with the computer in the next room.
 */
@Composable
private fun rememberOnWifi(): Boolean {
    val context = LocalContext.current
    var onWifi by remember { mutableStateOf(onWifi(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                onWifi = onWifi(context)
            }

            override fun onLost(network: Network) {
                onWifi = onWifi(context)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return onWifi
}

private fun onWifi(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}.getOrDefault(true)

/** The time, moved on once a second while the tab is on screen. */
@Composable
private fun rememberSecondClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * In a debug build, a line in the log each time the tab's picture of the devices changes, with the phone's clock on
 * it: how a test over the network sees how soon a change on a computer is shown here.
 */
@Composable
private fun SaysWhatItShows(place: MusicPlace, connect: ConnectState) {
    if (!app.noctorium.android.BuildConfig.DEBUG || LocalConnectPicture.current != null) return
    val picture = connectSummary(place, connect)
    LaunchedEffect(picture) { android.util.Log.i("NoctoriumConnectTab", "Shows: $picture") }
}

/** The tab's picture in a line: where the music is, and each device with what it is playing and who drives it. */
internal fun connectSummary(place: MusicPlace, connect: ConnectState): String = buildString {
    append(
        when (place) {
            MusicPlace.Here -> "music here"
            is MusicPlace.Driving -> "music on ${place.peer.name}, driven from here"
            is MusicPlace.Elsewhere -> "music on ${place.peer.name}"
            MusicPlace.Nowhere -> "no music"
        },
    )
    connect.controlledBy?.let { append("; controlled from ").append(it) }
    connect.devices.forEach { peer ->
        append("; ").append(peer.name).append(": ").append(deviceDetail(peer.nowPlaying))
        relationLine(peer, connect.thisDeviceId)?.let { append(" (").append(it).append(")") }
    }
    if (connect.busy) append("; sending")
}

private val HERO_COVER: Dp = 112.dp
private val DEVICE_ICON: Dp = 40.dp
private val FIGURES = TextStyle(fontFeatureSettings = "tnum")
