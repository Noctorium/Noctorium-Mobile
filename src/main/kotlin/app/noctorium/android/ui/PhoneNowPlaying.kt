package app.noctorium.android.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.domain.Track
import app.noctorium.lyrics.LyricsUiState
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.QueueState
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SeekBar
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.TimeDisplay
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One track, filling the screen.
 *
 * The desktop shows this beside everything else; a phone has no beside. So it comes up over the whole
 * interface and goes away again, which is also why the only way out is the chevron rather than a tab —
 * leaving by tapping something else would lose the track you were looking at.
 *
 * How it is arranged is the listener's choice of [PhoneNowPlayingLayout]. Every layout is made of the same
 * parts below -- the bar along the top, the track, the seek bar, the controls and the tools -- so each has
 * every button the others have, and a button added to one is added to all of them.
 */
@Composable
internal fun NowPlayingScreen(state: AppState, close: () -> Unit) {
    val reported by state.playback.collectAsState()
    val queue by state.queue.state.collectAsState()
    // A queue kept from the last session shows its song here too, paused, until play picks it up.
    val playback = shownPlayback(reported, queue)
    val lyrics by state.lyrics.collectAsState()
    val settings by state.settings.collectAsState()
    val track = playback.track ?: return
    val layout = settings.preferences.phone.nowPlayingLayout

    val hidden = settings.preferences.phone.hiddenPlayerButtons
    // A hidden lyrics button closes the lyrics too, or they would be open with no way of closing them. Sing
    // along is the one layout that opens on the lyrics, and there the button puts the cover in their place.
    var lyricsAsked by remember { mutableStateOf(false) }
    val lyricsButton = showsPlayerButton(hidden, PlayerButton.LYRICS)
    val showLyrics = if (layout == PhoneNowPlayingLayout.SING_ALONG) !(lyricsAsked && lyricsButton) else lyricsAsked && lyricsButton
    val haptics = rememberHaptics(state)

    // Lyrics are fetched only when asked for. Eight providers get queried, and doing that for a track
    // nobody is reading along to is somebody's data spent on nothing.
    LaunchedEffect(track.queueKey, showLyrics) {
        if (showLyrics) state.loadLyrics(track)
    }

    val screen = NowPlaying(
        state = state,
        track = track,
        playback = playback,
        queue = queue,
        settings = settings,
        lyrics = lyrics,
        hidden = hidden,
        haptics = haptics,
        onSpotify = playsOnSpotify(track, settings.spotify),
        showLyrics = showLyrics,
        toggleLyrics = { lyricsAsked = !lyricsAsked },
        close = close,
    )
    val laidOut: @Composable () -> Unit = {
        when (layout) {
            PhoneNowPlayingLayout.CLASSIC -> ClassicNowPlaying(screen)
            PhoneNowPlayingLayout.FULL_COVER -> FullCoverNowPlaying(screen)
            PhoneNowPlayingLayout.RECORD -> RecordNowPlaying(screen)
            PhoneNowPlayingLayout.COVER_FLOW -> CoverFlowNowPlaying(screen)
            PhoneNowPlayingLayout.SING_ALONG -> SingAlongNowPlaying(screen)
            PhoneNowPlayingLayout.BIG_TYPE -> BigTypeNowPlaying(screen)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize()) {
        if (LocalSkin.current == ThemeSkin.STANDARD) {
            // The full cover is its own backdrop, and the wash behind it would only be painted over.
            if (settings.preferences.ambientBackdrop && layout != PhoneNowPlayingLayout.FULL_COVER) AmbientBackdrop(track.artworkUrl)
            laidOut()
        } else {
            /*
             * The Windows themes put Now playing in a window, standing on the desktop -- 98's teal or XP's
             * hill -- or on the wash of the cover when the backdrop is on, with the desktop's taskbar under
             * it. The window's close button and its minimise both send it back down to the bar, which is where
             * a minimised window went, and so does its button on the taskbar; the start button goes Home. The
             * buttons the bar along the top had are on the window's toolbar.
             */
            if (settings.preferences.ambientBackdrop) AmbientBackdrop(track.artworkUrl) else SkinDesktop()
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))) {
                SkinWindow(
                    title = "Now Playing",
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    close = close,
                    minimise = close,
                    toolbar = { NowPlayingToolbar(screen) },
                ) {
                    // Cut to the window, as a window's contents were: the cover flow's outer covers would
                    // otherwise spill over its frame onto the desktop.
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        CompositionLocalProvider(LocalInSkinWindow provides true) { laidOut() }
                    }
                }
                TaskbarStrip {
                    StartButton(pressed = false, named = true, description = "Home") {
                        state.navigate(Destination.HOME)
                        close()
                    }
                    Spacer(Modifier.width(6.dp))
                    TaskbarButton(chosen = true, description = "Now Playing", modifier = Modifier.weight(1f), onClick = close) { ink ->
                        WindowIcon(16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Now Playing", color = ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(6.dp))
                    Tray { ink -> TrayClock(ink) }
                }
            }
        }
      }
    }
}

/** Whether the now playing screen is inside a Windows skin's window, which has the bar along the top's work. */
private val LocalInSkinWindow = staticCompositionLocalOf { false }

/**
 * The bar along the top's buttons as a Windows toolbar under the window's title bar: what the window is
 * showing, then Connect, the sleep timer and the lyrics.
 */
@Composable
private fun NowPlayingToolbar(screen: NowPlaying) {
    val connect by screen.state.connect.collectAsState()
    val sleepTimer by screen.state.sleepTimer.collectAsState()
    val hidden = screen.hidden
    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (screen.showLyrics) "Lyrics" else screen.track.provider.displayName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showsPlayerButton(hidden, PlayerButton.DEVICES, inUse = connect.target != null)) {
            ConnectButton(screen.state, screen.haptics)
        }
        if (showsPlayerButton(hidden, PlayerButton.SLEEP_TIMER, inUse = sleepTimer != null)) {
            SleepTimerButton(screen.state, screen.haptics)
        }
        if (showsPlayerButton(hidden, PlayerButton.LYRICS)) {
            IconButton(screen.toggleLyrics) {
                Text(if (screen.showLyrics) "♪" else "Aa", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Everything the parts of the now playing screen are drawn from, gathered once for whichever layout is
 * drawing them, so no part reads the state a second time and disagrees with its neighbour.
 */
@Stable
internal class NowPlaying(
    val state: AppState,
    val track: Track,
    val playback: PlaybackState,
    val queue: QueueState,
    val settings: SettingsState,
    val lyrics: LyricsUiState,
    val hidden: Set<PlayerButton>,
    val haptics: Haptics,
    /** The song is playing in the account's own Spotify app rather than on this phone. */
    val onSpotify: Boolean,
    val showLyrics: Boolean,
    val toggleLyrics: () -> Unit,
    val close: () -> Unit,
) {
    /** Back or forward by the double tap's step, from where the song is now, and never past either end. */
    fun jump(forward: Boolean) {
        val step = settings.preferences.phone.seekStepSeconds * 1_000L
        haptics.tick()
        state.seekTo(
            (playback.positionMs + if (forward) step else -step)
                .coerceIn(0L, playback.durationMs.coerceAtLeast(0L)),
        )
    }
}

/** The screen as it has always been: the cover, then the track, the seek bar and the controls. */
@Composable
private fun ClassicNowPlaying(screen: NowPlaying) {
    NowPlayingColumn {
        NowPlayingTopBar(screen)

        /*
         * fillMaxWidth is what makes the centring mean anything.
         *
         * A Column child is as wide as its content unless told otherwise, so this Box was exactly as
         * wide as the 300dp cover inside it and had nothing to centre it in. The Column then laid the
         * Box out at its default Start, and the cover sat hard against the left margin while the
         * title, the seek bar and the controls all ran the full width.
         */
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (screen.showLyrics) {
                LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            } else {
                Box(Modifier.size(300.dp).seekOnDoubleTap(screen)) {
                    Artwork(
                        screen.track.artworkUrl,
                        300.dp,
                        // A percentage of the side, not a fixed radius, so Circle really is one.
                        corner = 300.dp * screen.settings.preferences.phone.artworkShape.cornerPercent / 100,
                    )
                }
            }
        }

        TrackHeading(screen)
        Spacer(Modifier.height(14.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
    }
}

/** The column every layout but the full cover is laid out in: clear of the system bars, with a margin each side. */
@Composable
internal fun NowPlayingColumn(content: @Composable ColumnScope.() -> Unit) {
    // In a skin's window the window has taken the system bars already, and its frame is the margin.
    if (LocalInSkinWindow.current) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp), content = content)
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .padding(horizontal = 24.dp),
        content = content,
    )
}

/** The way down, what the screen is showing, and the buttons about the listening rather than the song. */
@Composable
internal fun NowPlayingTopBar(screen: NowPlaying) {
    // A skin's window has these on its title bar and its toolbar instead.
    if (LocalInSkinWindow.current) return
    val connect by screen.state.connect.collectAsState()
    val sleepTimer by screen.state.sleepTimer.collectAsState()
    val hidden = screen.hidden
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(screen.close) { Icon(Icons.Default.ExpandMore, "Close") }
        Spacer(Modifier.weight(1f))
        Text(
            if (screen.showLyrics) "Lyrics" else "Now playing",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        if (showsPlayerButton(hidden, PlayerButton.DEVICES, inUse = connect.target != null)) {
            ConnectButton(screen.state, screen.haptics)
        }
        if (showsPlayerButton(hidden, PlayerButton.SLEEP_TIMER, inUse = sleepTimer != null)) {
            SleepTimerButton(screen.state, screen.haptics)
        }
        if (showsPlayerButton(hidden, PlayerButton.LYRICS)) {
            IconButton(screen.toggleLyrics) {
                Text(if (screen.showLyrics) "♪" else "Aa", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Double tapping the left or right of whatever this is on jumps back or forward.
 *
 * The alternative on a phone is dragging a seek bar three hundred pixels wide across a whole track, which
 * cannot express ten seconds. The current position is read through rememberUpdatedState rather than
 * captured: the gesture handler is built once and would otherwise seek relative to wherever the track was
 * when this screen opened.
 */
@Composable
internal fun Modifier.seekOnDoubleTap(screen: NowPlaying): Modifier {
    val live by rememberUpdatedState(screen)
    return pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { at -> live.jump(forward = at.x > size.width / 2) })
    }
}

/**
 * The title over the artist, and under them where the song is playing when it is not here, the artist to
 * follow, and what went wrong. [title] false leaves the first two to a layout that sets them itself, and
 * [centred] lines everything up under a cover in the middle.
 */
@Composable
internal fun TrackHeading(screen: NowPlaying, title: Boolean = true, centred: Boolean = false, titleSize: TextUnit = 22.sp) {
    val track = screen.track
    Column(
        if (centred) Modifier.fillMaxWidth() else Modifier,
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (title) {
            Text(
                track.title,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
            )
            Text(
                track.artistLine.ifBlank { "Unknown artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (screen.onSpotify) OnSpotifyLine(spotifyDeviceName(screen.settings.spotify), Modifier.padding(top = 4.dp))
        PhoneFollowArtistChip(track, screen.state, Modifier.padding(top = 4.dp))
        screen.playback.errorMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

/** The seek bar the listener chose, and its two times. */
@Composable
internal fun NowPlayingSeekbar(screen: NowPlaying) {
    Seekbar(
        screen.playback.positionMs,
        screen.playback.durationMs,
        screen.settings.preferences.timeDisplay,
        screen.settings.preferences.progressBarStyle,
        screen.playback.isPlaying,
        screen.state::seekTo,
        seed = screen.track.queueKey,
    )
}

/** Shuffle, previous, play, next and repeat. */
@Composable
internal fun TransportRow(screen: NowPlaying) {
    val state = screen.state
    val queue = screen.queue
    val hidden = screen.hidden
    // A hidden shuffle or repeat leaves its place empty rather than closing up, so play stays in the
    // middle of the screen under the thumb that knows where it is.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsPlayerButton(hidden, PlayerButton.SHUFFLE)) {
            IconButton(state::toggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    "Shuffle",
                    tint = if (queue.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        IconButton(state::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(34.dp)) }
        PlayPauseButton(screen.playback, state, size = 44.dp)
        IconButton(state::next, enabled = queue.hasNext) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(34.dp)) }
        if (showsPlayerButton(hidden, PlayerButton.REPEAT)) {
            IconButton(state::cycleRepeat) {
                Icon(
                    if (queue.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (queue.repeatMode == RepeatMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/** The speed, the like and the song's menu, and the volume. */
@Composable
internal fun ToolsRow(screen: NowPlaying) {
    val likes by screen.state.likes.collectAsState()
    val state = screen.state
    val track = screen.track
    val hidden = screen.hidden
    // The like and the menu are about this track, so they stay under the middle of it. Volume is
    // not about the track at all, and sitting in that group it read as a third thing of the same
    // kind; out at the edge, under the end of the seek bar, it is plainly its own. The speed is not
    // about the track either, and takes the other edge.
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        // Not for a song Spotify is playing, which plays at Spotify's own speed whatever is set here.
        if (!screen.onSpotify) {
            SpeedButton(screen.settings.preferences.playbackSpeed, state, Modifier.align(Alignment.CenterStart))
        }
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (likes.supports(track) && showsPlayerButton(hidden, PlayerButton.LIKE)) {
                val liked = likes.isLiked(track)
                // The heart pops as it fills, so a like is seen to land and not only to change colour.
                IconButton({ state.toggleLike(track) }, Modifier.popOn(liked, pop = liked), enabled = !likes.isBusy(track)) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (liked) "Remove from likes" else "Like",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TrackMenuButton(track, state)
        }
        if (showsPlayerButton(hidden, PlayerButton.VOLUME)) {
            VolumeButton(screen.playback, state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/**
 * Where a song playing in the account's own Spotify app is playing: on Spotify, and on which of its devices
 * when Spotify has said. Without it, the screen would look like this phone's player while the sound came
 * out of a speaker in another room.
 */
@Composable
internal fun OnSpotifyLine(device: String?, modifier: Modifier = Modifier) {
    val green = app.noctorium.domain.ProviderType.SPOTIFY.badgeColour()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Speaker, null, Modifier.size(15.dp), tint = green)
        Spacer(Modifier.width(5.dp))
        Text(
            listOfNotNull("On Spotify", device).joinToString(" · "),
            color = green,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The speed, from the full screen: a dial at normal speed, and the speed itself otherwise, so a song is never
 * quietly playing fast. The menu offers the usual steps; Settings has the slider for anything between.
 */
@Composable
private fun SpeedButton(speed: Float, state: AppState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton({ open = true }) {
            if (speed == 1f) {
                Icon(Icons.Default.Speed, "Speed: normal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(speedLabel(speed), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEED_STEPS.forEach { step ->
                DropdownMenuItem(
                    text = { Text(speedLabel(step)) },
                    trailingIcon = {
                        if (abs(step - speed) < 0.001f) Icon(Icons.Default.Check, "Chosen", tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { state.setPlaybackSpeed(step); open = false },
                )
            }
        }
    }
}

/**
 * Volume, mute and the boost behind one icon, the way the desktop's player bar does it.
 *
 * The phone has volume keys already, and for a long time that was the argument for not having this at
 * all: they set the stream, which is the loudest the phone can be. What they cannot do is set Noctorium
 * relative to everything else -- turning a podcast down without turning the next notification down with
 * it -- and they cannot reach the boost.
 *
 * Behind a button rather than spread across a row of its own, which is what it was first: that cost a
 * line of a screen with less of it to spare, and left a slider directly under the transport buttons for
 * a thumb reaching for pause to catch. Closed, the icon still says which of muted, quiet or loud it is.
 *
 * The boost is the same bargain as the desktop's: not more gain past unity, which only clips, but
 * Android's `LoudnessEnhancer` lifting the quiet parts. Off when the platform will not give it, and the
 * chip follows what actually happened rather than what was asked for.
 */
@Composable
private fun VolumeButton(playback: PlaybackState, state: AppState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton({ open = true }) {
            Icon(
                when {
                    playback.isMuted || playback.volume <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                    playback.volume < .5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                "Volume",
                tint = if (playback.isMuted || playback.volumeBoostEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        DropdownMenu(open, { open = false }) {
            Column(Modifier.width(260.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${(playback.volume * 100).roundToInt()}%",
                        color = if (playback.volumeBoostEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SkinnedSlider(
                    value = playback.volume.coerceIn(0f, 1f),
                    onValueChange = state::setVolume,
                    valueRange = 0f..1f,
                )
                // Taller than the same two chips on the desktop. Thirty density-independent pixels is a
                // comfortable click and an awkward tap; this is what a thumb wants.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkinnedFilterChip(
                        selected = playback.isMuted,
                        onClick = state::toggleMute,
                        label = { Text(if (playback.isMuted) "Muted" else "Mute", fontSize = 12.sp) },
                        modifier = Modifier.height(38.dp),
                    )
                    SkinnedFilterChip(
                        selected = playback.volumeBoostEnabled,
                        onClick = state::toggleVolumeBoost,
                        label = { Text("Boost", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) },
                        modifier = Modifier.height(38.dp),
                    )
                }
            }
        }
    }
}

/**
 * The bar, and the two numbers either side of it.
 *
 * While a finger is down the bar follows the finger rather than the player, or every frame of the drag
 * would be fought by the position ticker underneath it.
 */
@Composable
internal fun Seekbar(
    positionMs: Long,
    durationMs: Long,
    display: TimeDisplay,
    style: ProgressBarStyle,
    playing: Boolean,
    seekTo: (Long) -> Unit,
    /** The song's key, which its row of Bars is made from. Blank draws an even row. */
    seed: String = "",
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: playbackFraction(positionMs, durationMs)

    Column {
        if (style.isDrawn) {
            DrawnSeekbar(
                style = style,
                fraction = fraction,
                canSeek = durationMs > 0,
                moving = playing && dragging == null,
                seed = seed,
                durationMs = durationMs,
                onScrub = { dragging = it },
                onScrubFinished = {
                    dragging?.let { if (durationMs > 0) seekTo((it * durationMs).toLong()) }
                    dragging = null
                },
            )
        } else {
        SkinnedSlider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { if (durationMs > 0) seekTo((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
        )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration((fraction * durationMs).toLong()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Text(
                trailingTimeFor((fraction * durationMs).toLong(), durationMs, display),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}


/**
 * The seek bars that are drawn rather than handed to Material's slider.
 *
 * A thumb is not a mouse pointer, so the touch area is 36dp tall whatever the bar inside it is: the
 * Capsule is ten of those and the hairline is three, and neither would be reliably hittable at its own
 * height. Tapping seeks straight there, which is what everybody tries first on a phone.
 */
@Composable
private fun DrawnSeekbar(
    style: ProgressBarStyle,
    fraction: Float,
    canSeek: Boolean,
    moving: Boolean,
    seed: String,
    durationMs: Long,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = SeekBar.TRACK_ALPHA)
    val filled = MaterialTheme.colorScheme.primary
    val pale = MaterialTheme.colorScheme.background.luminance() > .5f
    val shapes = rememberSeekBarShapes(style, seed, durationMs, widthPx)

    // The neon spark breathes while the music plays, and holds its breath while it is paused or while
    // nothing is to move at all. Read in the drawing rather than here, so a breath redraws the bar and
    // does not recompose it.
    val pulse: State<Float> = if (style == ProgressBarStyle.NEON && moving && LocalMotion.current) {
        rememberInfiniteTransition(label = "neon").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(NEON_BREATH_MS, easing = FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "neonPulse",
        )
    } else {
        remember { mutableFloatStateOf(.5f) }
    }

    // The wave travels one wavelength per cycle, and fades to flat rather than stopping when the music
    // does -- a wave frozen mid-crest looks like something broken rather than like a paused song.
    val travel = rememberInfiniteTransition(label = "wave")
    val phase by travel.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((SeekBar.WAVE_SECONDS_PER_CYCLE * 1000).toInt(), easing = LinearEasing),
        ),
        label = "wavePhase",
    )
    val amplitude by animateFloatAsState(
        if (style == ProgressBarStyle.WAVE && moving) 1f else 0f,
        tween(450),
        label = "waveAmplitude",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(canSeek, widthPx) {
                if (!canSeek) return@pointerInput
                detectTapGestures { offset ->
                    onScrub((offset.x / widthPx).coerceIn(0f, 1f))
                    onScrubFinished()
                }
            }
            .pointerInput(canSeek, widthPx) {
                if (!canSeek) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onScrub((offset.x / widthPx).coerceIn(0f, 1f)) },
                    onDragEnd = { onScrubFinished() },
                    onDragCancel = { onScrubFinished() },
                    onHorizontalDrag = { change, _ ->
                        onScrub((change.position.x / widthPx).coerceIn(0f, 1f))
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(style.drawnHeight())) {
            drawSeekBar(style, fraction, canSeek, track, filled, phase, amplitude, shapes, pulse.value, pale)
        }
    }
}

/** One breath of the neon spark, in and out again. Slower than the wave: a glow, not a flicker. */
private const val NEON_BREATH_MS = 1_300

/**
 * The artwork, blurred and dimmed, behind the track it belongs to.
 *
 * The desktop's ambient backdrop, which on a phone matters more: the screen is almost entirely this one
 * view, and a flat black rectangle behind a square of cover art is a lot of nothing. Blur needs API 31, so
 * below that it is scale and a heavy scrim — still ambient, just softer by a different means.
 *
 * The scrim is not optional at either version. Text over an unmuted photograph is unreadable about a third
 * of the time, and which third depends on the album.
 */
@Composable
private fun AmbientBackdrop(artworkUrl: String?) {
    if (artworkUrl == null) return
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = .5f,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier,
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            background.copy(alpha = .72f),
                            background.copy(alpha = .88f),
                            background,
                        ),
                    ),
                ),
        )
    }
}
