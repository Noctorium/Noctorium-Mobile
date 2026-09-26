package app.noctorium.android.ui

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.SeekBar
import app.noctorium.settings.TimeDisplay
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * One track, filling the screen.
 *
 * The desktop shows this beside everything else; a phone has no beside. So it comes up over the whole
 * interface and goes away again, which is also why the only way out is the chevron rather than a tab —
 * leaving by tapping something else would lose the track you were looking at.
 */
@Composable
internal fun NowPlayingScreen(state: AppState, close: () -> Unit) {
    val playback by state.playback.collectAsState()
    val queue by state.queue.state.collectAsState()
    val likes by state.likes.collectAsState()
    val lyrics by state.lyrics.collectAsState()
    val settings by state.settings.collectAsState()
    val track = playback.track ?: return

    var showLyrics by remember { mutableStateOf(false) }
    val haptics = rememberHaptics(state)

    // Lyrics are fetched only when asked for. Eight providers get queried, and doing that for a track
    // nobody is reading along to is somebody's data spent on nothing.
    LaunchedEffect(track.queueKey, showLyrics) {
        if (showLyrics) state.loadLyrics(track)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize()) {
        if (settings.preferences.ambientBackdrop) AmbientBackdrop(track.artworkUrl)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(close) { Icon(Icons.Default.ExpandMore, "Close") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (showLyrics) "Lyrics" else "Now playing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                ConnectButton(state, haptics)
                SleepTimerButton(state, haptics)
                IconButton({ showLyrics = !showLyrics }) {
                    Text(if (showLyrics) "♪" else "Aa", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            /*
             * fillMaxWidth is what makes the centring mean anything.
             *
             * A Column child is as wide as its content unless told otherwise, so this Box was exactly as
             * wide as the 300dp cover inside it and had nothing to centre it in. The Column then laid the
             * Box out at its default Start, and the cover sat hard against the left margin while the
             * title, the seek bar and the controls all ran the full width.
             */
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showLyrics) {
                    LyricsPane(lyrics, playback.positionMs, state)
                } else {
                    /*
                     * Double tapping the left or right of the cover jumps back or forward.
                     *
                     * The alternative on a phone is dragging a seek bar three hundred pixels wide across
                     * a whole track, which cannot express ten seconds. The current position is read
                     * through rememberUpdatedState rather than captured: the gesture handler is built
                     * once and would otherwise seek relative to wherever the track was when this screen
                     * opened.
                     */
                    val live by rememberUpdatedState(playback)
                    val step = settings.preferences.phone.seekStepSeconds * 1_000L
                    Box(
                        Modifier.size(300.dp).pointerInput(step) {
                            detectTapGestures(onDoubleTap = { at ->
                                val forward = at.x > size.width / 2
                                haptics.tick()
                                state.seekTo(
                                    (live.positionMs + if (forward) step else -step)
                                        .coerceIn(0L, live.durationMs.coerceAtLeast(0L)),
                                )
                            })
                        },
                    ) {
                        Artwork(
                            track.artworkUrl,
                            300.dp,
                            // A percentage of the side, not a fixed radius, so Circle really is one.
                            corner = 300.dp * settings.preferences.phone.artworkShape.cornerPercent / 100,
                        )
                    }
                }
            }

            Text(
                track.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistLine.ifBlank { "Unknown artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            playback.errorMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Seekbar(
                playback.positionMs,
                playback.durationMs,
                settings.preferences.timeDisplay,
                settings.preferences.progressBarStyle,
                playback.isPlaying,
                state::seekTo,
            )

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                IconButton(state::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                PlayPauseButton(playback, state, size = 44.dp)
                IconButton(state::next) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(34.dp)) }
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
            }

            // The like and the menu are about this track, so they stay under the middle of it. Volume is
            // not about the track at all, and sitting in that group it read as a third thing of the same
            // kind; out at the edge, under the end of the seek bar, it is plainly its own.
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (likes.supports(track)) {
                        val liked = likes.isLiked(track)
                        IconButton({ state.toggleLike(track) }, enabled = !likes.isBusy(track)) {
                            Icon(
                                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (liked) "Remove from likes" else "Like",
                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TrackMenuButton(track, state)
                }
                VolumeButton(playback, state, Modifier.align(Alignment.CenterEnd))
            }
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
                Slider(
                    value = playback.volume.coerceIn(0f, 1f),
                    onValueChange = state::setVolume,
                    valueRange = 0f..1f,
                )
                // Taller than the same two chips on the desktop. Thirty density-independent pixels is a
                // comfortable click and an awkward tap; this is what a thumb wants.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = playback.isMuted,
                        onClick = state::toggleMute,
                        label = { Text(if (playback.isMuted) "Muted" else "Mute", fontSize = 12.sp) },
                        modifier = Modifier.height(38.dp),
                    )
                    FilterChip(
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
private fun Seekbar(
    positionMs: Long,
    durationMs: Long,
    display: TimeDisplay,
    style: ProgressBarStyle,
    playing: Boolean,
    seekTo: (Long) -> Unit,
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
                onScrub = { dragging = it },
                onScrubFinished = {
                    dragging?.let { if (durationMs > 0) seekTo((it * durationMs).toLong()) }
                    dragging = null
                },
            )
        } else {
        Slider(
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
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = SeekBar.TRACK_ALPHA)
    val filled = MaterialTheme.colorScheme.primary

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
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            drawSeekBar(style, fraction, canSeek, track, filled, phase, amplitude)
        }
    }
}

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
