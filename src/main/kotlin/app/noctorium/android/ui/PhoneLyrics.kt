package app.noctorium.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.Track
import app.noctorium.lyrics.LyricsProviderId
import app.noctorium.lyrics.LyricsProviderOutcome
import app.noctorium.lyrics.LyricsProviderStatus
import app.noctorium.lyrics.LyricsUiState
import kotlinx.coroutines.delay

/**
 * Lyrics that keep up with the music.
 *
 * The line being sung sits in the middle of the screen, large and bright, and the page moves to keep it
 * there -- the way every other player does it, and the way a phone on a desk across the room has to work,
 * since nobody is going to walk over and scroll. A reader who does scroll is left alone for a few seconds
 * and then the page finds its place again.
 *
 * The sources sit in a row above the words, so switching is a tap on the lyrics themselves rather than a
 * trip to Settings and back. Picking one also makes it the source every later song opens on, whenever it
 * has an answer -- the same choice Settings shows.
 */
@Composable
internal fun LyricsPane(lyrics: LyricsUiState, positionMs: Long, track: Track, state: AppState) {
    val selected = lyrics.outcomes.firstOrNull { it.provider == lyrics.selectedProvider }
    val result = selected?.result

    Column(Modifier.fillMaxSize()) {
        LyricsSources(lyrics, refresh = { state.loadLyrics(track, forceRefresh = true) }, choose = state::selectLyricsProvider)
        // The gap underneath keeps the last line in view clear of the song's title, which sits right below.
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
            when {
                lyrics.loading && result == null -> CircularProgressIndicator(Modifier.padding(16.dp), strokeWidth = 3.dp)
                result != null && result.lines.isNotEmpty() -> FollowingLyrics(result, positionMs, state)
                result?.sourceUrl != null -> LyricsElsewhere(result, state)
                selected != null -> LyricsNote(nothingFrom(selected))
                else -> LyricsNote(lyrics.errorMessage ?: "No lyrics found for this one.")
            }
        }
    }
}

/**
 * Every source, with the ones that found something first.
 *
 * Sorted rather than left in asking order, so what can actually be read is in reach of a thumb without
 * scrolling, and the sources with nothing sit at the end, dimmed, still there to try.
 */
@Composable
private fun LyricsSources(lyrics: LyricsUiState, refresh: () -> Unit, choose: (LyricsProviderId) -> Unit) {
    val ordered = remember(lyrics.outcomes) { lyrics.outcomes.sortedBy { usefulness(it) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(ordered, key = { it.provider.name }) { outcome ->
                val empty = outcome.status != LyricsProviderStatus.FOUND && outcome.status != LyricsProviderStatus.LINK_ONLY &&
                    outcome.status != LyricsProviderStatus.SEARCHING
                FilterChip(
                    selected = lyrics.selectedProvider == outcome.provider,
                    onClick = { choose(outcome.provider) },
                    label = {
                        Text(
                            outcome.provider.displayName + if (outcome.result?.synced == true) " · Synced" else "",
                            fontSize = 11.sp,
                            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f) else Color.Unspecified,
                        )
                    },
                    leadingIcon = { SourceStatus(outcome.status) },
                )
            }
        }
        IconButton(refresh, Modifier.size(36.dp)) {
            Icon(Icons.Default.Refresh, "Ask every source again", Modifier.size(18.dp))
        }
    }
}

/** Lines found, then a link, then still asking, then nothing: the order a listener would try them in. */
private fun usefulness(outcome: LyricsProviderOutcome): Int = when {
    outcome.result?.lines?.isNotEmpty() == true && outcome.result?.synced == true -> 0
    outcome.result?.lines?.isNotEmpty() == true -> 1
    outcome.status == LyricsProviderStatus.LINK_ONLY -> 2
    outcome.status == LyricsProviderStatus.SEARCHING -> 3
    else -> 4
}

@Composable
private fun SourceStatus(status: LyricsProviderStatus) {
    when (status) {
        LyricsProviderStatus.SEARCHING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
        LyricsProviderStatus.FOUND -> Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        LyricsProviderStatus.LINK_ONLY -> Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        LyricsProviderStatus.NEEDS_KEY -> Icon(Icons.Default.Key, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        LyricsProviderStatus.ERROR -> Icon(Icons.Default.ErrorOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        LyricsProviderStatus.NOT_FOUND -> Icon(Icons.Default.RemoveCircleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Fades what scrolls to the top and bottom edges into the page, instead of slicing it off.
 *
 * A line cut straight across at the bottom sat hard against the song's title beneath, and the two read as
 * one run of text. Faded, the lyrics visibly end where the title begins. Drawn off screen and masked, so the
 * fade is in the lyrics themselves and works over any backdrop the now playing screen has behind them.
 */
private fun Modifier.fadingEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val height = size.height.coerceAtLeast(1f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (top.toPx() / height).coerceIn(0f, .45f) to Color.Black,
                (1f - bottom.toPx() / height).coerceIn(.55f, 1f) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Why a source that was picked shows nothing, and what to do instead. */
private fun nothingFrom(outcome: LyricsProviderOutcome): String {
    val name = outcome.provider.displayName
    return when (outcome.status) {
        LyricsProviderStatus.SEARCHING -> "Waiting for $name…"
        LyricsProviderStatus.NEEDS_KEY -> "$name needs its own key before Noctorium can ask it. Pick another source above."
        LyricsProviderStatus.ERROR -> "$name did not answer properly. Refresh to ask again, or pick another source above."
        else -> "$name has nothing for this song. Pick another source above."
    }
}

@Composable
private fun LyricsNote(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** A source that knows where the lyrics are but may not show them here. */
@Composable
private fun LyricsElsewhere(result: app.noctorium.lyrics.LyricsResult, state: AppState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LyricsNote(result.message ?: "${result.provider.displayName} has these lyrics on its own site.")
        Spacer(Modifier.height(12.dp))
        result.sourceUrl?.let { url ->
            FilledTonalButton({ state.openExternalUrl(url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open ${result.provider.displayName}")
            }
        }
    }
}

@Composable
private fun FollowingLyrics(result: app.noctorium.lyrics.LyricsResult, positionMs: Long, state: AppState) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // The last line whose moment has come. Plain lyrics carry no moments, so nothing is lit and the whole
    // thing simply reads as text, which is right.
    val activeIndex = remember(result.lines, positionMs) {
        if (!result.synced) -1 else result.lines.indexOfLast { (it.startTimeMs ?: Long.MAX_VALUE) <= positionMs }
    }

    /*
     * A hand on the page pauses the following.
     *
     * isScrollInProgress cannot tell a finger from the animation below, so the drag interactions are
     * watched instead: only a real touch counts, and following resumes a few seconds after the last one.
     */
    var lastTouchedAt by remember { mutableLongStateOf(0L) }
    var handHeld by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) lastTouchedAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(lastTouchedAt) {
        if (lastTouchedAt == 0L) return@LaunchedEffect
        handHeld = true
        delay(4_000)
        handHeld = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Half a screen of padding at each end lets the first and last lines sit in the middle too.
        val endPadding = maxHeight / 2

        LaunchedEffect(activeIndex, handHeld, result.provider) {
            if (activeIndex < 0 || handHeld) return@LaunchedEffect
            val lineHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }?.size
                ?: with(density) { 52.dp.roundToPx() }
            /*
             * The offset is measured from the padding boundary, not the top of the screen.
             *
             * With half a viewport of padding above the first line, an item scrolled to offset zero has
             * its top exactly at the centre line; half its own height further up puts its middle there.
             * The first version added another half viewport on top of the padding and put the line being
             * sung one full screen down -- always the first line just below the visible ones, which is why
             * it moved and yet nothing ever looked highlighted.
             */
            listState.animateScrollToItem(activeIndex, scrollOffset = lineHeight / 2)
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (result.synced) "Synced lyrics, following the song" else "Plain lyrics",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                result.sourceUrl?.let { url ->
                    TextButton({ state.openExternalUrl(url) }) { Text("Source", fontSize = 10.sp) }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().fadingEdges(top = 40.dp, bottom = 72.dp),
                contentPadding = PaddingValues(top = endPadding, bottom = endPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(result.lines) { index, line ->
                    val active = index == activeIndex
                    /*
                     * The line being sung grows and brightens into place, and the one before settles back.
                     *
                     * Drawn at the large size and scaled down when not sung, rather than switching font
                     * size: a size change lays the line out again, which made each new line jump, and a
                     * scale is only drawn -- it can be eased.
                     */
                    val colour by animateColorAsState(
                        when {
                            active -> MaterialTheme.colorScheme.onSurface
                            result.synced -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        motionSpec(MotionTiming.STANDARD),
                        label = "lyric-colour",
                    )
                    val scale by animateFloatAsState(
                        if (active || !result.synced) 1f else .8f,
                        motionSpec(MotionTiming.STANDARD),
                        label = "lyric-scale",
                    )
                    Text(
                        line.text.ifBlank { "♪" },
                        color = colour,
                        fontSize = 24.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                result.attribution?.let { attribution ->
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text(attribution, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}
