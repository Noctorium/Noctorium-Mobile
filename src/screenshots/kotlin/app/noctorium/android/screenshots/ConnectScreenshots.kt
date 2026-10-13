package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.noctorium.android.ui.ConnectPicture
import app.noctorium.android.ui.LocalConnectPicture
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NowPlayingScreen
import app.noctorium.android.ui.QueueScreen
import app.noctorium.connect.ConnectPeer
import app.noctorium.connect.ConnectState
import app.noctorium.connect.DeviceKind
import app.noctorium.connect.DeviceRef
import app.noctorium.connect.MusicPlace
import app.noctorium.connect.NowPlaying
import app.noctorium.connect.QueueSnapshot
import app.noctorium.connect.RemoteState
import app.noctorium.connect.toWire
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.core.playbackToShow
import app.noctorium.core.queueToShow
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.QueueState
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerBarPosition
import app.noctorium.settings.SettingsState
import app.noctorium.settings.SurfaceStyle
import app.noctorium.settings.TextSize
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Connect tab, and the player as another device's remote control: the tab with no devices, with three, while
 * this phone drives a computer and while a computer drives it; the player bar's strip in its layouts; Now playing,
 * the queue and the devices button's picker showing a song that is playing on the computer. In a dark theme, a
 * light one, 98, Noctorium 98 and XP, at the ordinary text size and the largest.
 *
 * A still life cannot be given another device -- Connect would go and look for real ones -- so each picture hands
 * the screens a [ConnectPicture] of made-up devices instead: the devices, where the music is, and what the player
 * shows, worked out from them by Base's own sums as the app works them out.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class ConnectScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = connectLooks().map { arrayOf(it) }
    }
}

/** Made-up devices, songs and moments for the pictures. */
internal object ConnectStill {
    /** The moment every picture is taken at, by this phone's clock. */
    const val NOW = 1_760_000_000_000L
    private const val ME = "this-phone"

    private fun nowPlaying(track: Track, playing: Boolean, positionMs: Long) = NowPlaying(
        title = track.title,
        artist = track.artistLine,
        provider = track.provider.name,
        artworkUrl = track.artworkUrl,
        playing = playing,
        positionMs = positionMs,
        durationMs = track.durationMs ?: 0,
    )

    private val deskSong = Covers.on(Still.queue[1])
    private val studioSong = Covers.on(Still.related[2])

    /** A computer, playing a song of its own. */
    val desk = ConnectPeer(
        "desk", "Desk", DeviceKind.DESKTOP, "192.168.1.20", 41234, lastSeenAtMs = NOW - 1_500,
        nowPlaying = nowPlaying(deskSong, playing = true, positionMs = 81_500),
    )

    /** Another phone, with nothing to play. */
    val kitchen = ConnectPeer("kitchen", "Kitchen phone", DeviceKind.PHONE, "192.168.1.31", 38211, lastSeenAtMs = NOW)

    /** A terminal, paused part-way through a song. */
    val studio = ConnectPeer(
        "studio", "Studio", DeviceKind.TERMINAL, "192.168.1.40", 41250, lastSeenAtMs = NOW,
        nowPlaying = nowPlaying(studioSong, playing = false, positionMs = 41_000),
    )

    /** This phone, playing the still life's song a minute and a half in. */
    val playingHere = PlaybackState(
        status = PlaybackStatus.PLAYING,
        track = Covers.on(Still.nowPlaying),
        positionMs = 83_000,
        durationMs = Still.nowPlaying.durationMs ?: 0,
    )

    private fun here(connect: ConnectState, place: MusicPlace = MusicPlace.Here, unrestricted: Boolean = true) = ConnectPicture(
        connect = connect,
        place = place,
        shown = playingHere,
        shownQueue = QueueState(tracks = listOf(playingHere.track!!) + Covers.on(Still.queue), currentIndex = 0),
        here = playingHere,
        nowMs = NOW,
        backgroundUnrestricted = unrestricted,
    )

    private val on = ConnectState(available = true, listening = true, thisDevice = "Pocket", thisDeviceId = ME)

    /** Signed in and looking, with nothing else found -- and the phone not yet allowed to run in the background. */
    val alone = here(on, unrestricted = false)

    /** Three devices besides this phone, and the music here. */
    val three = here(on.copy(devices = listOf(desk, kitchen, studio)))

    /** The music on the desk, by itself. */
    val elsewhere: ConnectPicture = on.copy(devices = listOf(desk, kitchen, studio)).let { connect ->
        ConnectPicture(connect, MusicPlace.Elsewhere(desk), PlaybackState(), QueueState(), PlaybackState(), NOW)
    }

    /** Connect switched off on this phone. */
    val off = here(ConnectState(thisDevice = "Pocket"), place = MusicPlace.Here)

    /**
     * The music sent to the desk, with this phone its remote control: what the desk last said about itself, a moment
     * ago, and its whole queue; the player showing it as Base works it out.
     */
    val driving: ConnectPicture = run {
        val queue = Covers.on(Still.queue)
        val remote = RemoteState(
            deviceId = desk.id,
            deviceName = desk.name,
            kind = DeviceKind.DESKTOP,
            playing = true,
            track = queue[1].toWire(),
            positionMs = 92_000,
            durationMs = queue[1].durationMs ?: 0,
            volume = .7f,
            queueSize = queue.size,
            queueIndex = 1,
            shuffle = true,
            repeat = "ALL",
            controlledBy = "Pocket",
            controlledById = ME,
        )
        val driven = desk.copy(
            nowPlaying = nowPlaying(queue[1], playing = true, positionMs = 92_000),
            lastSeenAtMs = NOW - 500,
            drivenBy = DeviceRef(ME, "Pocket"),
        )
        val connect = on.copy(
            devices = listOf(driven, kitchen, studio),
            target = driven,
            remote = remote,
            remoteAtMs = NOW - 500,
            remoteQueue = QueueSnapshot(queue, 1),
        )
        ConnectPicture(
            connect = connect,
            place = MusicPlace.Driving(driven),
            shown = connect.playbackToShow(PlaybackState(), NOW),
            shownQueue = connect.queueToShow(QueueState()),
            here = PlaybackState(),
            nowMs = NOW,
        )
    }

    /** The desk driving this phone: the music here, and the desk its remote control. */
    val driven: ConnectPicture = here(
        on.copy(
            devices = listOf(desk.copy(nowPlaying = null, driving = DeviceRef(ME, "Pocket")), kitchen, studio),
            controlledBy = "Desk",
            controlledById = desk.id,
        ),
    )
}

/** Every theme the Connect pictures are drawn in. */
private val THEMES = listOf(
    "night" to NoctoriumPreferences(),
    "day" to NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "noctorium-98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98_NOCTORIUM),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
)

private val SIZES = listOf("" to TextSize.DEFAULT, "-largest" to TextSize.LARGEST)

/** [screen] with [picture] handed to it in place of the state's own Connect. */
private fun seeing(picture: ConnectPicture, screen: @Composable (AppState) -> Unit): @Composable (SettingsState, AppState) -> Unit = { _, state ->
    CompositionLocalProvider(LocalConnectPicture provides picture) { screen(state) }
}

private val goToConnect: (AppState) -> Unit = { it.navigate(Destination.CONNECT) }

/** Opens the devices button's picker on Now playing, and lets it finish opening. */
private val openPicker: ComposeContentTestRule.() -> Unit = {
    onNodeWithContentDescription("Playing on Desk").performClick()
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
}

internal fun connectLooks(): List<Look> {
    val tabs = THEMES.flatMap { (theme, preferences) ->
        SIZES.flatMap { (size, text) ->
            // The Windows themes' clock says whatever the time is when the picture is taken.
            val still = preferences.copy(taskbarClock = false, textSize = text)
            listOf(
                "alone" to ConnectStill.alone,
                "three" to ConnectStill.three,
                "driving" to ConnectStill.driving,
                "driven" to ConnectStill.driven,
            ).map { (name, picture) ->
                Look(
                    "connect-tab-$name-$theme$size",
                    still,
                    playing = { if (picture.here.track == null) null else Covers.on(Still.nowPlaying) },
                    prepare = goToConnect,
                    screen = seeing(picture) { NoctoriumPhone(it) },
                )
            }
        }
    }
    val night = NoctoriumPreferences(taskbarClock = false)
    val others = listOf(
        Look("connect-tab-elsewhere-night", night, playing = { null }, prepare = goToConnect, screen = seeing(ConnectStill.elsewhere) { NoctoriumPhone(it) }),
        Look("connect-tab-off-night", night, prepare = goToConnect, screen = seeing(ConnectStill.off) { NoctoriumPhone(it) }),
        Look(
            "connect-tab-driving-glass",
            night.copy(surfaceStyle = SurfaceStyle.GLASS),
            playing = { null },
            prepare = goToConnect,
            screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) },
        ),
    )
    // The strip, in every layout of the bar, while this phone drives the desk.
    val bars = PhonePlayerBarStyle.entries.map { bar ->
        Look(
            "connect-bar-${slug(bar.name)}-night",
            night.copy(phone = PhonePreferences(playerBarStyle = bar)),
            playing = { null },
            screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) },
        )
    } + THEMES.drop(1).flatMap { (theme, preferences) ->
        listOf(
            PhonePlayerBarStyle.CLASSIC,
            PhonePlayerBarStyle.FLOATING,
            PhonePlayerBarStyle.TOOLBAR,
            PhonePlayerBarStyle.TOOLBAR_2,
            PhonePlayerBarStyle.TASKBAR,
        ).map { bar ->
            Look(
                "connect-bar-${slug(bar.name)}-$theme",
                preferences.copy(taskbarClock = false, phone = PhonePreferences(playerBarStyle = bar)),
                playing = { null },
                screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) },
            )
        }
    } + listOf(
        Look("connect-bar-glass-night", night.copy(surfaceStyle = SurfaceStyle.GLASS), playing = { null }, screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) }),
        Look("connect-bar-top-night", night.copy(playerBarPosition = PlayerBarPosition.TOP), playing = { null }, screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) }),
        Look("connect-bar-largest-night", night.copy(textSize = TextSize.LARGEST), playing = { null }, screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) }),
        Look("connect-bar-largest-xp", THEMES.last().second.copy(taskbarClock = false, textSize = TextSize.LARGEST), playing = { null }, screen = seeing(ConnectStill.driving) { NoctoriumPhone(it) }),
    ) + THEMES.map { (theme, preferences) ->
        // And the other way round: the desk driving this phone.
        Look("connect-bar-driven-$theme", preferences.copy(taskbarClock = false), screen = seeing(ConnectStill.driven) { NoctoriumPhone(it) })
    }
    // Now playing, the desk's song: the line saying where it is, the desk's place and volume and shuffle.
    val nowPlaying = THEMES.flatMap { (theme, preferences) ->
        SIZES.map { (size, text) ->
            Look(
                "connect-now-playing-$theme$size",
                preferences.copy(taskbarClock = false, textSize = text, ambientBackdrop = false),
                playing = { null },
                screen = seeing(ConnectStill.driving) { state -> NowPlayingScreen(state) {} },
            )
        }
    } + listOf(PhoneNowPlayingLayout.FULL_COVER, PhoneNowPlayingLayout.PLAYLIST, PhoneNowPlayingLayout.COVER_FLOW).map { layout ->
        Look(
            "connect-now-playing-${slug(layout.name)}-night",
            night.copy(phone = PhonePreferences(nowPlayingLayout = layout)),
            playing = { null },
            screen = seeing(ConnectStill.driving) { state -> NowPlayingScreen(state) {} },
        )
    } + listOf(
        Look(
            "connect-now-playing-driven-night",
            night.copy(ambientBackdrop = false),
            screen = seeing(ConnectStill.driven) { state -> NowPlayingScreen(state) {} },
        ),
        // The devices button's picker, open.
        Look(
            "connect-picker-night",
            night.copy(ambientBackdrop = false),
            playing = { null },
            act = openPicker,
            screen = seeing(ConnectStill.driving) { state -> NowPlayingScreen(state) {} },
        ),
        Look(
            "connect-picker-98",
            THEMES[2].second.copy(taskbarClock = false, ambientBackdrop = false),
            playing = { null },
            act = openPicker,
            screen = seeing(ConnectStill.driving) { state -> NowPlayingScreen(state) {} },
        ),
        // The desk's queue, where a tap plays on the desk.
        Look("connect-queue-night", night, playing = { null }, screen = seeing(ConnectStill.driving) { state -> QueueScreen(state) }),
        Look("connect-queue-xp", THEMES.last().second.copy(taskbarClock = false), playing = { null }, screen = seeing(ConnectStill.driving) { state -> QueueScreen(state) }),
    )
    return tabs + others + bars + nowPlaying
}

private fun slug(name: String) = name.lowercase().replace('_', '-')
