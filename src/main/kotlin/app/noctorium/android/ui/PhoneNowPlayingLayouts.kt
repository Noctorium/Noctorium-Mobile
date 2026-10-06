package app.noctorium.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.noctorium.domain.Track
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * The now playing layouts beside Classic, each made of the same parts as Classic so that none of them is
 * missing a button: the bar along the top, the track, the seek bar, the controls and the tools.
 */

/**
 * The cover filling the screen behind everything, with a scrim rising from the foot to the page's own colour
 * so the track and the controls read over any sleeve. A little of the scrim at the head too, for the bar
 * along the top. Double tapping the cover anywhere above the track jumps, as it does on the cover elsewhere.
 */
@Composable
internal fun FullCoverNowPlaying(screen: NowPlaying) {
    val page = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        FullBleedCover(screen.track.artworkUrl)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to page.copy(alpha = .62f),
                    .14f to Color.Transparent,
                    .34f to Color.Transparent,
                    .58f to page.copy(alpha = .8f),
                    1f to page.copy(alpha = .97f),
                ),
            ),
        )
        // Lyrics over a sleeve need the sleeve stepped back further than the track does.
        if (screen.showLyrics) Box(Modifier.fillMaxSize().background(page.copy(alpha = .66f)))
        NowPlayingColumn {
            NowPlayingTopBar(screen)
            Box(
                Modifier.weight(1f).fillMaxWidth().then(if (screen.showLyrics) Modifier else Modifier.seekOnDoubleTap(screen)),
                contentAlignment = Alignment.Center,
            ) {
                if (screen.showLyrics) LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            }
            TrackHeading(screen, titleSize = 28.sp)
            Spacer(Modifier.height(14.dp))
            NowPlayingSeekbar(screen)
            TransportRow(screen)
            ToolsRow(screen)
        }
    }
}

/** The cover, cut to fill the screen; with none, the accent deepening down the screen with the note in it. */
@Composable
private fun FullBleedCover(url: String?) {
    if (url != null) {
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        return
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            null,
            Modifier.size(160.dp).offset(y = (-90).dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .4f),
        )
    }
}

/** The cover set into a record that turns while the music plays, in the middle of the screen. */
@Composable
internal fun RecordNowPlaying(screen: NowPlaying) {
    NowPlayingColumn {
        NowPlayingTopBar(screen)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (screen.showLyrics) {
                LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            } else {
                val side = minOf(maxWidth, maxHeight, RECORD_MOST) * .94f
                Record(
                    screen.track.artworkUrl,
                    side,
                    spinning = screen.playback.isPlaying,
                    Modifier.shadow(20.dp, CircleShape).seekOnDoubleTap(screen),
                )
            }
        }
        TrackHeading(screen)
        Spacer(Modifier.height(14.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
    }
}

private val RECORD_MOST = 360.dp

/** The queue's covers in a row with the one playing in the middle, and the track centred under it. */
@Composable
internal fun CoverFlowNowPlaying(screen: NowPlaying) {
    NowPlayingColumn {
        NowPlayingTopBar(screen)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (screen.showLyrics) {
                LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            } else {
                CoverFlow(screen, Modifier.fillMaxSize())
            }
        }
        TrackHeading(screen, centred = true)
        Spacer(Modifier.height(14.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
    }
}

/**
 * The queue as a row of covers, the one playing large and facing out, the rest turned towards it in depth
 * and standing on their reflections, the way a shelf of records was browsed in the years of the iPod.
 *
 * Swiping moves along the row and lets it settle on the nearest cover, which then plays; tapping a cover
 * at the side does the same in one go. When the song changes by any other road -- the end of a song, next
 * on the lock screen -- the row glides to it, or with nothing to move, is simply there. Double tapping the
 * cover in the middle jumps within the song, as on every other layout.
 *
 * Where the row stands is one number, the position along the queue, and every cover is placed from its
 * distance to it as it is drawn: a frame of the glide moves layers and composes nothing.
 */
@Composable
private fun CoverFlow(screen: NowPlaying, modifier: Modifier) {
    val queued = screen.queue.tracks
    val tracks = queued.ifEmpty { listOf(screen.track) }
    val playing = screen.queue.currentIndex.takeIf { queued.isNotEmpty() && it in tracks.indices } ?: 0
    val moving = LocalMotion.current
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(playing.toFloat()) }
    var held by remember { mutableStateOf(false) }
    val live by rememberUpdatedState(screen)

    LaunchedEffect(playing, tracks.size) {
        if (held) return@LaunchedEffect
        if (moving) {
            position.animateTo(playing.toFloat(), tween(GLIDE_MS, easing = FastOutSlowInEasing))
        } else {
            position.snapTo(playing.toFloat())
        }
    }

    /** Glides to the cover at [index] and plays it, when it is not the one playing already. */
    fun settle(index: Int) {
        val target = index.coerceIn(tracks.indices)
        scope.launch {
            if (moving) position.animateTo(target.toFloat(), tween(GLIDE_MS / 2, easing = FastOutSlowInEasing)) else position.snapTo(target.toFloat())
        }
        if (target != playing && queued.isNotEmpty()) {
            live.haptics.tick()
            live.state.jumpToQueueItem(target)
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * .64f, maxHeight / (1f + FLOW_REFLECTION) * .92f)
        val spacing = with(LocalDensity.current) { (side * .6f).toPx() }
        // Which cover is nearest the middle, which changes only as the row passes halfway between two.
        val around by remember { derivedStateOf { position.value.roundToInt() } }
        val drag by rememberUpdatedState { dx: Float, done: Boolean ->
            if (done) {
                held = false
                settle(position.value.roundToInt())
            } else {
                scope.launch { position.snapTo((position.value - dx / spacing).coerceIn(-.45f, tracks.lastIndex + .45f)) }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInputForFlow(tracks.size, spacing, onStart = { held = true }) { dx, done -> drag(dx, done) },
            contentAlignment = Alignment.Center,
        ) {
            for (index in (around - FLOW_SHOWN).coerceAtLeast(0)..(around + FLOW_SHOWN).coerceAtMost(tracks.lastIndex)) {
                key(index, tracks[index].queueKey) {
                    FlowCover(
                        track = tracks[index],
                        side = side,
                        spacing = spacing,
                        offset = { index - position.value },
                        modifier = Modifier.zIndex(-abs(index - around).toFloat()),
                        tap = { settle(index) },
                        doubleTap = { forward -> if (index == playing) live.jump(forward) },
                    )
                }
            }
        }
    }
}

/** One cover in the row, placed, turned and dimmed from its [offset] along the row as it is drawn. */
@Composable
private fun FlowCover(
    track: Track,
    side: Dp,
    spacing: Float,
    offset: () -> Float,
    modifier: Modifier,
    tap: () -> Unit,
    doubleTap: (forward: Boolean) -> Unit,
) {
    val height = side * (1f + FLOW_REFLECTION)
    // The taps are read as they happen, since the gesture is set up once and the queue moves on under it.
    val onTap by rememberUpdatedState(tap)
    val onDoubleTap by rememberUpdatedState(doubleTap)
    Column(
        modifier.graphicsLayer {
            val along = offset()
            val turn = along.coerceIn(-1f, 1f)
            val beyond = (abs(along) - 1f).coerceAtLeast(0f)
            // Each step in gives the next cover a full place; the ones further out crowd in behind it.
            translationX = sign(along) * (minOf(abs(along), 1f) * spacing + beyond * spacing * .38f)
            rotationY = turn * FLOW_TURN
            val scale = 1f - .14f * abs(turn)
            scaleX = scale
            scaleY = scale
            alpha = (1f - (abs(along) - FLOW_SHOWN + 1f).coerceAtLeast(0f)).coerceIn(0f, 1f)
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(.5f, (1f / (1f + FLOW_REFLECTION)) / 2f)
        },
    ) {
        Box(
            Modifier.size(side).pointerInputForCover({ onTap() }, { forward -> onDoubleTap(forward) }),
        ) {
            Artwork(track.artworkUrl, side, corner = FLOW_CORNER)
            // Turned away is turned out of the light.
            Box(Modifier.size(side).drawBehind { drawRect(Color.Black.copy(alpha = .42f * abs(offset().coerceIn(-1f, 1f)))) })
        }
        FlowReflection(track.artworkUrl, side, height - side)
    }
}

/** The cover upside down under itself, fading out as it goes, the way it would stand on a glossy floor. */
@Composable
private fun FlowReflection(url: String?, side: Dp, height: Dp) {
    val painter = rememberAsyncImagePainter(url)
    val blank = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier
            .padding(top = 2.dp)
            .size(side, height - 2.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val cover = side.toPx()
        val corner = FLOW_CORNER.toPx()
        withTransform({
            translate(top = cover)
            scale(1f, -1f, pivot = Offset.Zero)
        }) {
            clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, cover, cover, CornerRadius(corner, corner))) }) {
                if (url == null) drawRect(blank, size = Size(cover, cover)) else with(painter) { draw(Size(cover, cover)) }
            }
        }
        drawRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = .3f), 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** The row's drags: [move] with each step's distance, then once more with done once the finger lifts. */
private fun Modifier.pointerInputForFlow(
    count: Int,
    spacing: Float,
    onStart: () -> Unit,
    move: (dx: Float, done: Boolean) -> Unit,
): Modifier = pointerInput(count, spacing) {
    detectHorizontalDragGestures(
        onDragStart = { onStart() },
        onDragEnd = { move(0f, true) },
        onDragCancel = { move(0f, true) },
        onHorizontalDrag = { change, dx ->
            change.consume()
            move(dx, false)
        },
    )
}

/** A cover's taps: one to go to it, two on either half of the one playing to jump within the song. */
private fun Modifier.pointerInputForCover(tap: () -> Unit, doubleTap: (forward: Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures(
            onTap = { tap() },
            onDoubleTap = { at -> doubleTap(at.x > size.width / 2) },
        )
    }

/** How far a cover at the side is turned, in degrees. */
private const val FLOW_TURN = 56f

/** How many covers are drawn either side of the middle one; the furthest fades out. */
private const val FLOW_SHOWN = 3

/** How long the row takes to glide to a new song. */
private const val GLIDE_MS = MotionTiming.STANDARD * 2

/** How tall a reflection is against its cover. */
private const val FLOW_REFLECTION = .36f

private val FLOW_CORNER = 6.dp

/**
 * The synced lyrics filling the screen, a small cover and the track beneath them, and the controls under
 * that. The lyrics button puts the cover in the lyrics' place, and back again.
 */
@Composable
internal fun SingAlongNowPlaying(screen: NowPlaying) {
    NowPlayingColumn {
        NowPlayingTopBar(screen)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (screen.showLyrics) {
                LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            } else {
                Box(Modifier.size(300.dp).seekOnDoubleTap(screen)) {
                    Artwork(
                        screen.track.artworkUrl,
                        300.dp,
                        corner = 300.dp * screen.settings.preferences.phone.artworkShape.cornerPercent / 100,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.seekOnDoubleTap(screen)) { Artwork(screen.track.artworkUrl, 52.dp, corner = 8.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(screen.track.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    screen.track.artistLine.ifBlank { "Unknown artist" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TrackHeading(screen, title = false)
        Spacer(Modifier.height(10.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
    }
}

/**
 * No cover: the title set as large as it will go in at most three lines, like a poster, the artist under
 * it, and the controls beneath. Double tapping the title jumps within the song, as the cover does elsewhere.
 */
@Composable
internal fun BigTypeNowPlaying(screen: NowPlaying) {
    NowPlayingColumn {
        NowPlayingTopBar(screen)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (screen.showLyrics) {
                LyricsPane(screen.lyrics, screen.playback.positionMs, screen.track, screen.state)
            } else {
                BigType(screen, Modifier.fillMaxSize().seekOnDoubleTap(screen))
            }
        }
        TrackHeading(screen, title = false)
        Spacer(Modifier.height(14.dp))
        NowPlayingSeekbar(screen)
        TransportRow(screen)
        ToolsRow(screen)
    }
}

/**
 * The title as large as fits: the largest of a few sizes at which it takes three lines or fewer, leaves room
 * for the artist, and breaks only between words -- a word split across two lines at poster size reads as a
 * mistake, not as type. Measured before it is drawn rather than by drawing it too big and shrinking, which
 * flickered through every size on the way down.
 */
@Composable
private fun BigType(screen: NowPlaying, modifier: Modifier) {
    val title = screen.track.title
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.copy(fontWeight = FontWeight.Black)
    BoxWithConstraints(modifier.padding(bottom = 12.dp), contentAlignment = Alignment.BottomStart) {
        val room = with(LocalDensity.current) { (maxHeight - BIG_TYPE_BESIDE).roundToPx() }
        val width = constraints.maxWidth
        val chosen = remember(title, width, room, base) {
            BIG_TYPE_SIZES.firstOrNull { size ->
                val laid = measurer.measure(
                    title,
                    base.copy(fontSize = size.sp, lineHeight = (size * 1.02f).sp),
                    maxLines = 3,
                    constraints = Constraints(maxWidth = width.coerceAtLeast(1)),
                )
                !laid.hasVisualOverflow && laid.size.height <= room &&
                    (0 until laid.lineCount - 1).none { line ->
                        val end = laid.getLineEnd(line)
                        end in 1 until title.length && title[end - 1].isLetterOrDigit() && title[end].isLetterOrDigit()
                    }
            } ?: BIG_TYPE_SIZES.last()
        }
        Column {
            // A rule in the accent above the title, as a poster has.
            Box(Modifier.width(36.dp).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = base.copy(fontSize = chosen.sp, lineHeight = (chosen * 1.02f).sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                screen.track.artistLine.ifBlank { "Unknown artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The sizes the big type tries, largest first, in points. */
private val BIG_TYPE_SIZES = listOf(96, 84, 74, 66, 58, 52, 46, 40, 36, 32, 28)

/** What the title leaves room for: the rule above it, and the artist under it. */
private val BIG_TYPE_BESIDE = 100.dp
