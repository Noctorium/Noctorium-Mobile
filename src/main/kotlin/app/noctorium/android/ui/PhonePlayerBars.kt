package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.ThemeSkin
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The player bars the core added beside the first five: one lifted off the page, one as slight as a bar can
 * be, one about the record, and one that is a desktop's taskbar. Each is only what is inside the strip; the
 * strip itself -- tapping it to open the full screen, swiping it through the queue, the line of progress the
 * others carry -- is [PlayerBar]'s, so it works the same in all of them.
 */

/**
 * Classic, lifted off the edge of the screen: a rounded card with a margin all round and a soft shadow under
 * it, floating over the page above the tabs, with the song's progress as a hairline along its foot.
 *
 * The card is what is tapped and swiped, given [gestures] by the bar, rather than the strip it floats in: a
 * press on the margin beside it is a press on the page. Under glass the pane is already a floating card, so
 * there the content is Classic's and the pane does the floating.
 */
@Composable
internal fun FloatingBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    onSpotify: Boolean,
    hasNext: Boolean,
    gestures: Modifier,
) {
    val shape = MaterialTheme.shapes.large
    // Under the Windows skins the card is a small window of its own: 98's raised slab, which had no shadow to
    // cast, or a Luna toolbar in its pale blue edge.
    val card = when (LocalSkin.current) {
        ThemeSkin.STANDARD -> Modifier
            .shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .45f), spotColor = Color.Black.copy(alpha = .55f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ThemeSkin.WINDOWS_98 -> Modifier
            .background(Win98.Face)
            .drawWithContent {
                drawContent()
                drawEdge98(Edge98.WINDOW)
            }
        ThemeSkin.WINDOWS_XP -> Modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFB), Luna.Face)))
            .border(1.dp, Luna.FieldEdge, shape)
    }
    Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(card)
                .then(gestures),
        ) {
            ClassicBar(track, playback, state, glass = false, onSpotify = onSpotify, hasNext = hasNext)
            // Inset from the ends, so the line stops short of the rounded corners instead of being cut by them.
            Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 5.dp).clip(RoundedCornerShape(50))) {
                PlaybackLine(playback, style)
            }
        }
    }
}

/**
 * The least that still says what is on: "title · artist" on one line, play at its end, and the progress as
 * a thin line under it. About two thirds of Slim, for somebody who wants the page and nothing in its way.
 */
@Composable
internal fun LineBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    glass: Boolean,
    onSpotify: Boolean,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val wrong = MaterialTheme.colorScheme.error
    Column {
        Row(
            Modifier.fillMaxWidth().height(LINE_BAR_HEIGHT).padding(start = if (glass) 20.dp else 16.dp, end = if (glass) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MotionContent(track, Modifier.weight(1f), kind = MotionKind.TRACK, contentKey = { it.queueKey }) { shown ->
                Text(
                    lineOf(shown, playback, onSpotify, quiet, wrong),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(LINE_BAR_HEIGHT), contentAlignment = Alignment.Center) { PlayPauseButton(playback, state, size = 22.dp) }
        }
        Box(if (glass) Modifier.padding(start = 24.dp, end = 24.dp, bottom = 5.dp).clip(RoundedCornerShape(50)) else Modifier) {
            PlaybackLine(playback, style)
        }
    }
}

/** The title, a dot, then the artist -- or what went wrong -- quieter, as one line that is cut short at its end. */
private fun lineOf(track: Track, playback: PlaybackState, onSpotify: Boolean, quiet: Color, wrong: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(track.title) }
        withStyle(SpanStyle(color = if (playback.errorMessage != null) wrong else quiet)) {
            append("  ·  ")
            append(barDetail(track, playback, onSpotify))
        }
    }

private val LINE_BAR_HEIGHT = 42.dp

/** The cover as a small record, turning while the music plays, beside the track, play and next. */
@Composable
internal fun RecordBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    glass: Boolean,
    onSpotify: Boolean,
    hasNext: Boolean,
) {
    val haptics = rememberHaptics(state)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (glass) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Record(track.artworkUrl, 50.dp, spinning = playback.isPlaying)
        Spacer(Modifier.width(11.dp))
        TrackLines(track, playback, onSpotify, Modifier.weight(1f))
        PlayPauseButton(playback, state)
        IconButton({ haptics.tick(); state.next() }, enabled = hasNext) {
            Icon(Icons.Default.SkipNext, "Next track")
        }
    }
}

/**
 * A desktop's taskbar: a start button with Noctorium's mark that opens Now playing, the song as a taskbar
 * button held down -- the window in use -- and the tray at the end, with play and the time.
 *
 * Flat here, in the theme's own colours; the Windows themes dress it as their own taskbar. The song's
 * button carries the progress along its foot, the way a taskbar button showed a download's.
 */
@Composable
internal fun TaskbarBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    glass: Boolean,
    open: () -> Unit,
) {
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        SkinTaskbarBar(track, playback, state, style, open)
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = if (glass) 10.dp else 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(46.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClickLabel = "Open now playing", onClick = open)
                .semantics { contentDescription = "Now playing" },
            contentAlignment = Alignment.Center,
        ) {
            NoctoriumMark(MaterialTheme.colorScheme.onPrimary, Modifier.size(15.dp))
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(ink.copy(alpha = .09f))
                .border(1.dp, ink.copy(alpha = .1f), shape),
        ) {
            Row(Modifier.fillMaxHeight().padding(start = 5.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, 24.dp, corner = 4.dp)
                Spacer(Modifier.width(8.dp))
                MotionContent(track, Modifier.weight(1f), kind = MotionKind.TRACK, contentKey = { it.queueKey }) { shown ->
                    Text(
                        "${shown.title} - ${shown.artistLine.ifBlank { "Unknown artist" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp)) { PlaybackLine(playback, style) }
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.fillMaxHeight().clip(shape).background(ink.copy(alpha = .05f)).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { PlayPauseButton(playback, state, size = 18.dp) }
            TrayClock(MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * The Taskbar bar in a Windows theme, drawn as that desktop's own taskbar: the start button with the N, which
 * opens Now playing; the song as the button of the window in use, held down, with its progress along the
 * button's foot; and the tray, with play beside the clock.
 */
@Composable
private fun SkinTaskbarBar(track: Track, playback: PlaybackState, state: AppState, style: ProgressBarStyle, open: () -> Unit) {
    TaskbarStrip {
        StartButton(pressed = false, named = false, description = "Now playing", onClick = open)
        Spacer(Modifier.width(6.dp))
        TaskbarButton(
            chosen = true,
            description = track.title,
            modifier = Modifier.weight(1f),
            onClick = open,
            foot = { PlaybackLine(playback, style) },
        ) { ink ->
            Artwork(track.artworkUrl, 22.dp, corner = 2.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                "${track.title} - ${track.artistLine.ifBlank { "Unknown artist" }}",
                color = ink,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(6.dp))
        Tray { ink ->
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { PlayPauseButton(playback, state, size = 18.dp) }
            TrayClock(ink)
        }
    }
}

/**
 * The time as a taskbar's tray showed it, hours and minutes and nothing else -- on the twenty-four hour clock
 * when the phone is set to it -- changing on the minute. A taskbar without its clock is not one.
 */
@Composable
internal fun TrayClock(colour: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.getDefault())
    }
    val time by produceState(format.format(Date()), format) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            value = format.format(Date())
        }
    }
    Text(time, color = colour, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = modifier)
}

/** Noctorium's N, the letter the launcher's themed icon is, filling [modifier]'s height. */
@Composable
internal fun NoctoriumMark(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawNoctoriumMark(Offset((size.width - size.height * MARK_ASPECT) / 2f, 0f), size.height, colour) }
}

/**
 * The N, drawn from the same three shapes as the themed launcher icon: two stems and the diagonal between
 * them, each wound the same way so that where they overlap they fill as one letter.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoctoriumMark(topLeft: Offset, height: Float, colour: Color) {
    val scale = height / 46f
    fun x(at: Float) = topLeft.x + (at - 35f) * scale
    fun y(at: Float) = topLeft.y + (at - 31f) * scale
    val letter = androidx.compose.ui.graphics.Path().apply {
        moveTo(x(35f), y(31f)); lineTo(x(44.5f), y(31f)); lineTo(x(44.5f), y(77f)); lineTo(x(35f), y(77f)); close()
        moveTo(x(63.5f), y(31f)); lineTo(x(73f), y(31f)); lineTo(x(73f), y(77f)); lineTo(x(63.5f), y(77f)); close()
        moveTo(x(35f), y(31f)); lineTo(x(45.5f), y(31f)); lineTo(x(73f), y(67.5f)); lineTo(x(73f), y(77f))
        lineTo(x(62.5f), y(77f)); lineTo(x(35f), y(40.5f)); close()
    }
    drawPath(letter, colour)
}

/** The N is a little narrower than it is tall. */
internal const val MARK_ASPECT = 38f / 46f
