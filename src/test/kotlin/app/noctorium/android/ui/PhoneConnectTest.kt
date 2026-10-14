package app.noctorium.android.ui

import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.DeviceRef
import app.noctorium.connect.MusicPlace
import app.noctorium.connect.NowPlaying
import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Connect tab, the player bar's strip and the devices button say and offer: where the music is, what each
 * device is playing, who is driving whom, and which moves can be made.
 */
class PhoneConnectTest {

    private val harbour = NowPlaying("Harbour Lights", "Night Tram", playing = true, positionMs = 62_000, durationMs = 225_000)
    private val desk = ConnectPeer("desk", "Desk", DeviceKind.DESKTOP, "192.168.1.20", 41234, lastSeenAtMs = 1_000_000, nowPlaying = harbour)
    private val laptop = ConnectPeer("laptop", "Laptop", DeviceKind.DESKTOP, "192.168.1.21", 41235, 0)

    private val song = Track(
        provider = ProviderType.SOUNDCLOUD,
        id = "1",
        title = "Low Tide Static",
        artists = listOf(Artist("a", "Odile Brandt", ProviderType.SOUNDCLOUD)),
        durationMs = 305_000,
        sourceUrl = "https://example.invalid/1",
    )

    @Test
    fun `the strip says where the music is when it is not simply this phone's`() {
        assertEquals("Playing on Desk", connectStrip(ConnectState(target = desk)))
        assertEquals("Controlled from Desk", connectStrip(ConnectState(controlledBy = "Desk")))
        assertNull(connectStrip(ConnectState(devices = listOf(desk))))
        // Driving another device is what the player is doing, so it is what the strip says.
        assertEquals("Playing on Desk", connectStrip(ConnectState(target = desk, controlledBy = "Laptop")))
    }

    @Test
    fun `a move's own message is not said twice, and anything else is`() {
        assertNull(connectNote(ConnectState(target = desk, message = "Playing on Desk.")))
        assertEquals(
            "Desk did not answer. Its firewall may be keeping Noctorium out on this network.",
            connectNote(ConnectState(message = "Desk did not answer. Its firewall may be keeping Noctorium out on this network.")),
        )
        assertEquals("Brought here from Desk.", connectNote(ConnectState(message = "Brought here from Desk.")))
        assertNull(connectNote(ConnectState()))
    }

    @Test
    fun `each device says what it is playing, or that it is idle`() {
        assertEquals("Idle", deviceDetail(null as NowPlaying?))
        assertEquals("Harbour Lights — Night Tram", deviceDetail(harbour))
        assertEquals("Paused · Harbour Lights — Night Tram", deviceDetail(harbour.copy(playing = false)))
        assertEquals("Harbour Lights", deviceDetail(harbour.copy(artist = "")))
    }

    @Test
    fun `this phone says what it is playing from its own player`() {
        assertEquals("Idle", deviceDetail(PlaybackState()))
        assertEquals("Low Tide Static — Odile Brandt", deviceDetail(PlaybackState(status = PlaybackStatus.PLAYING, track = song)))
        assertEquals("Paused · Low Tide Static — Odile Brandt", deviceDetail(PlaybackState(status = PlaybackStatus.PAUSED, track = song)))
    }

    @Test
    fun `who drives whom is said under each device, this phone by what it is rather than its name`() {
        assertEquals("Controlled from this phone", relationLine(desk.copy(drivenBy = DeviceRef("me", "Pocket")), thisDeviceId = "me"))
        assertEquals("Controlled from Laptop", relationLine(desk.copy(drivenBy = DeviceRef("laptop", "Laptop")), thisDeviceId = "me"))
        assertEquals("Controlling this phone", relationLine(desk.copy(driving = DeviceRef("me", "Pocket")), thisDeviceId = "me"))
        assertEquals("Controlling Laptop", relationLine(desk.copy(driving = DeviceRef("laptop", "Laptop")), thisDeviceId = "me"))
        assertNull(relationLine(desk, thisDeviceId = "me"))
        assertEquals("Controlling Desk", relationHere(ConnectState(target = desk)))
        assertEquals("Controlled from Desk", relationHere(ConnectState(controlledBy = "Desk")))
        assertNull(relationHere(ConnectState()))
    }

    @Test
    fun `Play on is offered where the music can go, and not where it already is`() {
        assertTrue(canPlayOn(MusicPlace.Here, desk, busy = false))
        assertTrue(canPlayOn(MusicPlace.Driving(desk), laptop, busy = false))
        assertTrue(canPlayOn(MusicPlace.Elsewhere(desk), laptop, busy = false))
        assertFalse(canPlayOn(MusicPlace.Driving(desk), desk, busy = false), "it is there already")
        assertFalse(canPlayOn(MusicPlace.Elsewhere(desk), desk, busy = false), "it is there already")
        assertFalse(canPlayOn(MusicPlace.Nowhere, desk, busy = false), "there is no music to move")
        assertFalse(canPlayOn(MusicPlace.Here, desk, busy = true), "a move is under way")
    }

    @Test
    fun `the music card says where the music is and who controls it`() {
        val playing = SongThere("Harbour Lights", "Night Tram", null, playing = true, positionMs = 0, durationMs = 0)
        assertEquals("Playing on this phone", whereTheMusicIs(MusicPlace.Here, playing))
        assertEquals("Paused on Desk", whereTheMusicIs(MusicPlace.Driving(desk), playing.copy(playing = false)))
        assertEquals("Playing on Desk", whereTheMusicIs(MusicPlace.Elsewhere(desk), playing))
        assertEquals("Nothing is playing", whereTheMusicIs(MusicPlace.Nowhere, null))

        assertEquals("Desk is controlling this phone.", whoControls(MusicPlace.Here, ConnectState(controlledBy = "Desk")))
        assertNull(whoControls(MusicPlace.Here, ConnectState()))
        assertEquals("This phone is its remote: the player here drives it.", whoControls(MusicPlace.Driving(desk), ConnectState(target = desk)))
        assertEquals("Playing by itself.", whoControls(MusicPlace.Elsewhere(desk), ConnectState()))
        assertEquals(
            "Controlled from Laptop.",
            whoControls(MusicPlace.Elsewhere(desk.copy(drivenBy = DeviceRef("laptop", "Laptop"))), ConnectState(thisDeviceId = "me")),
        )
    }

    @Test
    fun `another device's song moves on by the clock while it plays there`() {
        val there = songAt(MusicPlace.Elsewhere(desk), PlaybackState(), PlaybackState(), nowMs = 1_004_000)!!
        assertEquals("Harbour Lights", there.title)
        assertEquals(66_000, there.positionMs)
        assertEquals(225_000, there.durationMs)
        val paused = songAt(MusicPlace.Elsewhere(desk.copy(nowPlaying = harbour.copy(playing = false))), PlaybackState(), PlaybackState(), 1_004_000)!!
        assertEquals(62_000, paused.positionMs)
        assertNull(songAt(MusicPlace.Nowhere, PlaybackState(), PlaybackState(), 0))
    }

    @Test
    fun `the driven device's song is the player's, which moves by the quarter second`() {
        val shown = PlaybackState(status = PlaybackStatus.PLAYING, track = song, positionMs = 90_250, durationMs = 305_000)
        val driven = songAt(MusicPlace.Driving(desk), PlaybackState(), shown, nowMs = 1_004_000)!!
        assertEquals("Low Tide Static", driven.title)
        assertEquals(90_250, driven.positionMs)
        assertTrue(driven.playing)
        // Before the player has heard from it, what the device last announced.
        assertEquals("Harbour Lights", songAt(MusicPlace.Driving(desk), PlaybackState(), PlaybackState(status = PlaybackStatus.RESOLVING), 1_000_000)?.title)
    }

    @Test
    fun `an address is typed with its port`() {
        assertEquals("192.168.1.20" to 41234, parseConnectAddress(" 192.168.1.20:41234 "))
        assertEquals("desk.local" to 41234, parseConnectAddress("desk.local 41234"))
        assertEquals("fe80::1" to 41234, parseConnectAddress("[fe80::1]:41234"))
        assertNull(parseConnectAddress("192.168.1.20"), "no port")
        assertNull(parseConnectAddress("192.168.1.20:99999"), "no such port")
        assertNull(parseConnectAddress("fe80::1"), "an IPv6 address needs its brackets to be told from its port")
        assertNull(parseConnectAddress(""))
        assertEquals("192.168.1.20:41234", typedAddress("192.168.1.20" to 41234))
        assertEquals("[fe80::1]:41234", typedAddress("fe80::1" to 41234))
    }

    @Test
    fun `each kind of device has a name and an icon of its own`() {
        assertEquals(listOf("Computer", "Phone", "Terminal"), DeviceKind.entries.map(::kindName))
        assertEquals(DeviceKind.entries.size, DeviceKind.entries.map { it.glyph() }.toSet().size)
    }

    @Test
    fun `the tab's picture is said in a line, for the debug log`() {
        val connect = ConnectState(
            devices = listOf(desk.copy(drivenBy = DeviceRef("me", "Pocket")), laptop),
            target = desk,
            thisDeviceId = "me",
        )
        assertEquals(
            "music on Desk, driven from here; Desk: Harbour Lights — Night Tram (Controlled from this phone); Laptop: Idle",
            connectSummary(MusicPlace.Driving(desk), connect),
        )
    }

    /** After a launch the player holds nothing until play is pressed, but the bar shows the kept queue's song. */
    @Test
    fun `the kept queue's song is music here, not nothing`() {
        val kept = PlaybackState(status = PlaybackStatus.PAUSED, track = song)
        assertEquals(MusicPlace.Here, MusicPlace.Nowhere.withKeptSong(kept))
        assertEquals(MusicPlace.Nowhere, MusicPlace.Nowhere.withKeptSong(PlaybackState()))
        assertEquals(MusicPlace.Elsewhere(desk), MusicPlace.Elsewhere(desk).withKeptSong(kept))
    }

    @Test
    fun `the switch says what on and off mean, and what switching off would end`() {
        assertEquals("This phone finds your other devices, and they can find it.", connectSwitchLine(on = true))
        assertEquals("Off: this phone is not looking for your other devices, and they cannot see it.", connectSwitchLine(on = false))
        assertEquals(
            "Switching it off ends this phone's control of Desk. The music plays on there.",
            connectSwitchEnds(ConnectState(target = desk)),
        )
        assertEquals("Switching it off ends Desk's control of this phone.", connectSwitchEnds(ConnectState(controlledBy = "Desk")))
        assertNull(connectSwitchEnds(ConnectState(devices = listOf(desk, laptop))), "nothing is driven, so nothing ends")
    }

    @Test
    fun `the music is somewhere else while a device is driven or playing by itself`() {
        assertTrue(MusicPlace.Driving(desk).isElsewhere())
        assertTrue(MusicPlace.Elsewhere(desk).isElsewhere())
        assertFalse(MusicPlace.Here.isElsewhere())
        assertFalse(MusicPlace.Nowhere.isElsewhere())
        assertTrue(MusicPlace.Driving(desk).isOn(desk.copy(name = "Renamed")), "the same device, by its id")
        assertFalse(MusicPlace.Driving(desk).isOn(laptop))
    }
}
