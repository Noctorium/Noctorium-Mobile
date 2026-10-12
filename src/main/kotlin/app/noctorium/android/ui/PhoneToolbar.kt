package app.noctorium.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.RepeatMode
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.ThemeSkin

/**
 * The Toolbar player bar: a desktop music player's toolbar, for a thumb.
 *
 * On a desktop it is one row -- add, previous, play, pause, stop, next, a slider and the time, then repeat, shuffle
 * and the volume. A phone has a third of the width, so it is the same things in three rows: the song with its time,
 * a thin slider, and the buttons, flat and evenly spread. Play and pause are two buttons, as they were on the
 * players it is drawn after, the one in force lit; Stop is there because the bar is built around it, until it is
 * put away. The slider is always the Slider, whatever seek bar Now playing has: it is part of the look.
 *
 * Under the Windows skins the buttons are their toolbars' -- 98's grey slabs, latched down for what is on, and
 * Luna's -- and the slider is their trackbar.
 */
@Composable
internal fun ToolbarBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    glass: Boolean,
    onSpotify: Boolean,
    hasNext: Boolean,
) {
    val settings by state.settings.collectAsState()
    val queue = state.playerQueue()
    val phone = settings.preferences.phone
    val hidden = phone.hiddenPlayerButtons
    val haptics = rememberHaptics(state)
    var adding by remember { mutableStateOf(false) }
    if (adding) AddToPlaylistDialog(track, state) { adding = false }
    val edge = if (glass) 12.dp else 10.dp
    Column(Modifier.fillMaxWidth().padding(start = edge, end = edge, top = 7.dp, bottom = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, 38.dp, corner = if (glass) 19.dp else 6.dp)
            Spacer(Modifier.width(10.dp))
            TrackLines(track, playback, onSpotify, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                toolbarTime(playback.positionMs, playback.durationMs, settings.preferences.timeDisplay),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        ToolbarSlider(playback, state)
        Row(Modifier.fillMaxWidth().height(TOOLBAR_ROW), verticalAlignment = Alignment.CenterVertically) {
            ToolbarButton(Glyph.ADD.image, "Add to playlist") { haptics.tick(); adding = true }
            ToolbarButton(Glyph.SKIP_PREVIOUS.image, "Previous track") { haptics.tick(); state.previous() }
            val resolving = playback.status == PlaybackStatus.RESOLVING
            ToolbarButton(Glyph.PLAY_ARROW.image, "Play", lit = playback.isPlaying, busy = resolving) {
                haptics.tick()
                if (!playback.isPlaying) state.togglePlayback()
            }
            ToolbarButton(Glyph.PAUSE.image, "Pause", lit = playback.status == PlaybackStatus.PAUSED) {
                haptics.tick()
                if (playback.isPlaying || resolving) state.togglePlayback()
            }
            if (showsPlayerButton(hidden, PlayerButton.STOP, shown = phone.shownPlayerButtons, partOfLayout = true)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StopButton(state, size = TOOLBAR_ICON, flat = true) }
            }
            ToolbarButton(Glyph.SKIP_NEXT.image, "Next track", enabled = hasNext) { haptics.tick(); state.next() }
            if (showsPlayerButton(hidden, PlayerButton.REPEAT)) {
                ToolbarButton(
                    if (queue.repeatMode == RepeatMode.ONE) Glyph.REPEAT_ONE.image else Glyph.REPEAT.image,
                    when (queue.repeatMode) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    lit = queue.repeatMode != RepeatMode.OFF,
                ) { haptics.tick(); state.cycleRepeat() }
            }
            if (showsPlayerButton(hidden, PlayerButton.SHUFFLE)) {
                ToolbarButton(Glyph.SHUFFLE.image, "Shuffle", lit = queue.shuffleEnabled) { haptics.tick(); state.toggleShuffle() }
            }
            if (showsPlayerButton(hidden, PlayerButton.VOLUME)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { VolumeButton(playback, state, size = TOOLBAR_ICON, flat = true) }
            }
        }
    }
}

/** "0:58 / 2:31": where the song is, and its length or what is left of it, as the time is set to show. */
internal fun toolbarTime(positionMs: Long, durationMs: Long, display: app.noctorium.settings.TimeDisplay): String =
    formatDuration(positionMs.coerceAtLeast(0)) + " / " + trailingTimeFor(positionMs, durationMs, display)

/** The Slider, thin, across the bar: it seeks where it is tapped or dragged, and a drag on it never skips a song. */
@Composable
private fun ToolbarSlider(playback: PlaybackState, state: AppState) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val durationMs = playback.durationMs
    DrawnSeekbar(
        style = ProgressBarStyle.SLIDER,
        fraction = dragging ?: playbackFraction(playback.positionMs, durationMs),
        canSeek = durationMs > 0,
        moving = false,
        seed = "",
        durationMs = durationMs,
        onScrub = { dragging = it },
        onScrubFinished = {
            dragging?.let { if (durationMs > 0) state.seekTo((it * durationMs).toLong()) }
            dragging = null
        },
        touchHeight = 28.dp,
    )
}

/**
 * One of the toolbar's flat buttons, sharing the row with the rest: an icon in the ordinary themes, lit in the
 * accent while what it stands for is on; a grey slab under 98, latched down while on; Luna's button under XP.
 */
@Composable
private fun RowScope.ToolbarButton(
    icon: ImageVector,
    description: String,
    lit: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        FlatButton(description, lit, enabled, onClick = onClick) { ink ->
            if (busy) {
                CircularProgressIndicator(Modifier.size(TOOLBAR_ICON * .8f), strokeWidth = 2.dp, color = ink)
            } else {
                Icon(icon, null, Modifier.size(TOOLBAR_ICON), tint = ink)
            }
        }
    }
}

/**
 * A flat button: what a toolbar's buttons are. [onLongClick], where there is one, is what holding it down does.
 * The content is given the colour to draw in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlatButton(
    description: String,
    lit: Boolean,
    enabled: Boolean,
    longDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val skin = LocalSkin.current
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val win98 = Win98
    val click = Modifier.combinedClickable(
        interactionSource = interaction,
        indication = if (skin == ThemeSkin.STANDARD) ripple(bounded = false, radius = 22.dp) else null,
        enabled = enabled,
        role = Role.Button,
        onClickLabel = description,
        onLongClickLabel = longDescription,
        onLongClick = onLongClick,
        onClick = onClick,
    )
    val semantics = Modifier.described(description)
    when (skin) {
        ThemeSkin.STANDARD -> {
            val ink = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                lit -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(Modifier.size(TOOLBAR_TOUCH).clip(CircleShape).then(click).then(semantics), contentAlignment = Alignment.Center) { content(ink) }
        }
        ThemeSkin.WINDOWS_98 -> {
            val down = held || lit
            val ink = if (enabled) win98.Text else win98.GreyText
            Box(
                Modifier
                    .size(width = TOOLBAR_SKIN_WIDTH, height = TOOLBAR_SKIN_HEIGHT)
                    .background(if (lit) win98.Dither else Brush.linearGradient(listOf(win98.Face, win98.Face)))
                    .drawWithContent {
                        drawContent()
                        drawEdge98(if (down) Edge98.PRESSED else Edge98.RAISED, win98)
                    }
                    .then(click)
                    .then(semantics),
                contentAlignment = Alignment.Center,
            ) {
                Box(if (down) Modifier.offset(1.dp, 1.dp) else Modifier) { content(ink) }
            }
        }
        ThemeSkin.WINDOWS_XP -> {
            val shape = RoundedCornerShape(3.dp)
            val ink = if (enabled) Luna.Text else Luna.GreyText
            Box(
                Modifier
                    .size(width = TOOLBAR_SKIN_WIDTH, height = TOOLBAR_SKIN_HEIGHT)
                    .clip(shape)
                    .background(if (held || lit) Luna.ButtonPressed else Luna.ButtonFace)
                    .border(1.dp, if (lit) Luna.Hot else Luna.ButtonEdge.copy(alpha = .55f), shape)
                    .then(click)
                    .then(semantics),
                contentAlignment = Alignment.Center,
            ) { content(if (lit && enabled) Color(0xFF1A4FBF) else ink) }
        }
    }
}

/** The button's name for somebody listening, as an icon button has it. */
private fun Modifier.described(description: String): Modifier = semantics { contentDescription = description }

/**
 * Stop: back to the start of the song, paused, with the queue as it was, so play starts it again from the top.
 * Held down, it is "stop after this song" -- nothing more plays once this one ends -- and while that is set the
 * square has a ring round it, lit, so a song that is about to be the last is never a surprise.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StopButton(state: AppState, size: Dp = 24.dp, flat: Boolean = false) {
    val haptics = rememberHaptics(state)
    val stopAfter by state.stopAfterCurrent.collectAsState()
    val description = if (stopAfter) "Stop. Stopping after this song" else "Stop"
    val content: @Composable (Color) -> Unit = { ink ->
        Icon(if (stopAfter) Glyph.STOP_AFTER.image else Glyph.STOP.image, null, Modifier.size(size), tint = ink)
    }
    if (flat) {
        FlatButton(
            description,
            lit = stopAfter,
            enabled = true,
            longDescription = if (stopAfter) "Keep playing after this song" else "Stop after this song",
            onLongClick = { haptics.tick(); state.toggleStopAfterCurrent() },
            onClick = { haptics.tick(); state.stop() },
            content = content,
        )
        return
    }
    val skin = LocalSkin.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                indication = if (skin == ThemeSkin.STANDARD) ripple(bounded = false, radius = 24.dp) else null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClickLabel = "Stop",
                onLongClickLabel = if (stopAfter) "Keep playing after this song" else "Stop after this song",
                onLongClick = { haptics.tick(); state.toggleStopAfterCurrent() },
                onClick = { haptics.tick(); state.stop() },
            )
            .described(description),
        contentAlignment = Alignment.Center,
    ) { content(if (stopAfter) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
}

/** The toolbar's icons, the room each has to a finger, and its row's height. */
private val TOOLBAR_ICON = 22.dp
private val TOOLBAR_TOUCH = 44.dp
private val TOOLBAR_ROW = 46.dp

/** A Windows toolbar button: a little wider than tall, as theirs were. */
private val TOOLBAR_SKIN_WIDTH = 36.dp
private val TOOLBAR_SKIN_HEIGHT = 32.dp
