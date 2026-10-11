package app.noctorium.android.ui

import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.RemoteState
import app.noctorium.connect.WireArtist
import app.noctorium.connect.WireTrack
import app.noctorium.domain.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the Connect dialog says, which on a phone was nothing at all whenever a handover failed. */
class PhoneConnectTest {

    private val desk = ConnectPeer("desk", "Desk", DeviceKind.DESKTOP, "192.168.1.20", 41234, 0)

    @Test
    fun `the dialog says what is happening, sending first`() {
        assertEquals("Sending the music…", connectHeadline(ConnectState(busy = true, target = desk)))
        assertEquals("Playing on Desk.", connectHeadline(ConnectState(target = desk)))
        assertEquals("Desk is controlling this phone.", connectHeadline(ConnectState(controlledBy = "Desk")))
        assertEquals("Your devices on this wifi.", connectHeadline(ConnectState()))
    }

    @Test
    fun `the remote control says where the other device is in its song and its queue`() {
        val remote = RemoteState(
            deviceId = "desk",
            deviceName = "Desk",
            kind = DeviceKind.DESKTOP,
            playing = false,
            track = WireTrack(
                provider = ProviderType.SOUNDCLOUD,
                id = "1",
                title = "Harbour Lights",
                artists = listOf(WireArtist("a", "Night Tram", ProviderType.SOUNDCLOUD)),
                sourceUrl = "https://soundcloud.com/1",
            ),
            positionMs = 62_000,
            durationMs = 225_000,
            queueSize = 9,
            queueIndex = 1,
        )
        assertEquals("Night Tram — 1:02 of 3:45 · 2 of 9 · paused", remotePosition(remote))
        assertEquals("", remotePosition(remote.copy(track = null)))
    }
}
