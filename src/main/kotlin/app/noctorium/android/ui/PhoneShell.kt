package app.noctorium.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import app.noctorium.settings.Glass
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.noctorium.settings.SeekBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import app.noctorium.settings.ThemeColours
import app.noctorium.settings.ThemePreset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.BackgroundDepth
import app.noctorium.settings.PlayerBarPosition
import app.noctorium.settings.ProgressBarStyle
import app.noctorium.settings.TimeDisplay
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import coil.compose.AsyncImage

/**
 * The same near-black the desktop uses.
 *
 * Taken from Noctorium's own palette rather than from Material's dynamic colour, so the two read as one
 * application rather than as a phone app that happens to share a name. On an OLED screen the background is
 * also the cheapest thing there is to draw.
 */
val NoctoriumDark = noctoriumColors(ThemePreset.NOCTORIUM_NIGHT.colours!!, Color(0xFFB47CFF))

/** Blends toward [other]; used to derive a whole scheme from a theme's six colours. */
private fun Color.mix(other: Color, ratio: Float): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
)

/**
 * Material's palette, worked out from a theme's six colours and the accent in force -- the same sums the
 * desktop does, so a theme picked on either reads the same on both.
 *
 * The theme says where the page, a panel and a card sit and what writing looks like on them; the accent
 * is whatever the listener chose. "Match the artwork" has no palette of its own on the phone yet, so it
 * falls back to the theme's accent. A light theme comes out as light rather than as a dark theme with a
 * white page.
 */
fun noctoriumColors(theme: ThemeColours, accent: Color): ColorScheme {
    val background = Color(theme.background)
    val panel = Color(theme.panel)
    val card = Color(theme.card)
    val text = Color(theme.text)
    val subtext = Color(theme.subtext)
    val onAccent = if (accent.luminance() > .35f) accent.mix(Color.Black, .84f) else Color.White
    val base = if (theme.light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.mix(background, .74f),
        onPrimaryContainer = accent.mix(text, .55f),
        inversePrimary = accent.mix(text, .3f),
        secondary = accent.mix(text, .38f),
        onSecondary = background,
        secondaryContainer = accent.mix(background, .82f),
        onSecondaryContainer = text,
        tertiary = accent.mix(subtext, .5f),
        onTertiary = background,
        tertiaryContainer = card,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = panel,
        onSurface = text,
        surfaceVariant = card,
        onSurfaceVariant = subtext,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = if (theme.light) Color(0xFFB3261E) else Color(0xFFFF8A8A),
        onError = if (theme.light) Color.White else Color(0xFF2A0008),
        errorContainer = if (theme.light) Color(0xFFFFDAD6) else Color(0xFF3D0713),
        onErrorContainer = if (theme.light) Color(0xFF410002) else Color(0xFFFFDAD6),
        outline = subtext.mix(background, .45f),
        outlineVariant = subtext.mix(background, .72f),
        surfaceBright = card,
        surfaceDim = background,
        surfaceContainer = panel,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card.mix(text, .06f),
        surfaceContainerLow = panel.mix(background, .5f),
        surfaceContainerLowest = background,
    )
}

/**
 * Noctorium on a phone: four places, a bar, and the now playing screen over the top of it all.
 *
 * Routing goes through `AppState.destination` — the same field the desktop's sidebar drives — so the two
 * are navigating one application rather than each keeping its own idea of where the listener is. What
 * differs is only the shape: a sidebar there, a bottom bar here, and a full-screen player instead of a
 * panel, because a phone has one hand and no room.
 */
@Composable
fun NoctoriumPhone(state: AppState) {
    val ui by state.ui.collectAsState()
    val playback by state.playback.collectAsState()
    val library by state.library.collectAsState()
    val settings by state.settings.collectAsState()
    /*
     * Start page, including the one the phone was quietly dropping.
     *
     * core turns the setting into a destination, and the phone renders NOW_PLAYING as Home because the
     * full screen here is an overlay rather than a page. So "Start on: Now playing" moved the
     * destination and changed nothing anyone could see. Read once, at first composition, which is the
     * only moment a start page means anything.
     */
    var nowPlayingOpen by remember { mutableStateOf(ui.destination == Destination.NOW_PLAYING) }
    var signingInTo by remember { mutableStateOf<ProviderType?>(null) }

    /*
     * Back means "up one level", in the order things were opened.
     *
     * Without these the system handler takes every press and closes the app — which it did, from the now
     * playing screen, mid-track. A phone treats back as the primary way out of anything, so every layer
     * that can be opened has to be able to answer it.
     */
    // Sign-in handles its own back, since the page inside it has history of its own to walk first.
    val overlaid = signingInTo != null
    BackHandler(enabled = !overlaid && nowPlayingOpen) { nowPlayingOpen = false }
    BackHandler(enabled = !overlaid && !nowPlayingOpen && library.openPlaylist != null) {
        state.closePlaylist()
    }
    BackHandler(enabled = !overlaid && !nowPlayingOpen && library.openLocalPlaylist != null) {
        state.closeLocalPlaylist()
    }
    BackHandler(
        enabled = !overlaid &&
            !nowPlayingOpen &&
            library.openPlaylist == null &&
            library.openLocalPlaylist == null &&
            ui.destination != Destination.HOME,
    ) { state.navigate(Destination.HOME) }

    val glass = settings.preferences.surfaceStyle.isGlass
    val screens: @Composable () -> Unit = {
        when (ui.destination) {
            Destination.SEARCH -> SearchScreen(state)
            Destination.LINK -> LinkScreen(state)
            Destination.LIBRARY -> LibraryScreen(state)
            Destination.DOWNLOADS -> DownloadsScreen(state)
            Destination.SETTINGS -> SettingsScreen(state) { signingInTo = it }
            Destination.QUEUE -> QueueScreen(state)
            // Now playing is a sheet here rather than a destination, so anything that asks for it lands on
            // Home with the sheet open instead of on an empty screen.
            Destination.HOME, Destination.NOW_PLAYING -> HomeScreen(state)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            /*
             * The player bar at the head of the screen, for anyone who asked for it there.
             *
             * The desktop has offered this all along and the phone quietly did not, on the theory that a
             * phone has no top to speak of. It has one: it is where the thumb is not, which for a bar
             * that is mostly looked at is a reasonable place. Up here it takes the status-bar inset itself
             * and tells the screens beneath that the inset is spent, or each would pad for it again.
             */
            val barAtTop = settings.preferences.playerBarPosition == PlayerBarPosition.TOP && playback.track != null
            if (glass) {
                GlassChrome(
                    state = state,
                    playback = playback,
                    settings = settings,
                    destination = ui.destination,
                    barAtTop = barAtTop,
                    openNowPlaying = { nowPlayingOpen = true },
                    screens = screens,
                )
            } else Column(Modifier.fillMaxSize()) {
                if (barAtTop) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        PlayerBar(
                            playback,
                            state,
                            settings.preferences.progressBarStyle,
                            settings.preferences.phone.swipeToChangeTrack,
                        ) { nowPlayingOpen = true }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (barAtTop) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                ) { screens() }

                Column(Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                    if (playback.track != null && !barAtTop) {
                        PlayerBar(
                            playback,
                            state,
                            settings.preferences.progressBarStyle,
                            settings.preferences.phone.swipeToChangeTrack,
                        ) { nowPlayingOpen = true }
                    }
                    PhoneNavigation(
                        ui.destination,
                        settings.preferences.phone.navigationLabels,
                        state::navigate,
                    )
                }
            }

            // The service's own sign-in page, over everything including the tabs. A half-finished login
            // that can be tabbed away from leaves a session nobody knows the state of.
            signingInTo?.let { provider ->
                SignInScreen(provider, state) { signingInTo = null }
            }

            // Over everything too: while a track is open it is the only thing being looked at, and a row
            // of tabs underneath it is just somewhere to lose your place.
            AnimatedVisibility(
                visible = signingInTo == null && nowPlayingOpen && playback.track != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(state) { nowPlayingOpen = false }
            }

            // Last, so it sits over the sheet as well: the launch check found something newer and this is
            // the once it gets to say so. Not while signing in, though -- that page is somebody halfway
            // through handing over a password, and it is no moment to put a dialog in front of them.
            if (signingInTo == null) UpdatePrompt(state)
        }
    }
}

/**
 * The screen under liquid glass: content running the full height, and the player and the tabs floating
 * over it as panes.
 *
 * The content goes *underneath*, which is the whole reason for the arrangement. A pane over a flat patch
 * of page has nothing to bend; a pane over a list of covers that is being scrolled bends every one of
 * them as it passes. The lists are told how much of them the panes cover, through [LocalChromeInsets],
 * so their last rows can still be scrolled clear of the tab bar.
 *
 * The panes are drawn after the content and outside what is recorded for them, or each would be looking
 * at itself.
 */
@Composable
private fun BoxScope.GlassChrome(
    state: AppState,
    playback: PlaybackState,
    settings: app.noctorium.settings.SettingsState,
    destination: Destination,
    barAtTop: Boolean,
    openNowPlaying: () -> Unit,
    screens: @Composable () -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val density = LocalDensity.current
    var topCover by remember { mutableIntStateOf(0) }
    var bottomCover by remember { mutableIntStateOf(0) }
    val inset = Glass.FLOAT_INSET_DP.dp

    Box(Modifier.fillMaxSize().glassSource(backdrop)) {
        GlassWash(playback.track?.artworkUrl)
        CompositionLocalProvider(
            LocalChromeInsets provides with(density) { ChromeInsets(topCover.toDp(), bottomCover.toDp()) },
        ) { screens() }
    }

    if (barAtTop) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                // Measured inside the status-bar padding: the screens pad the status bar themselves, and
                // counting it here as well would leave a strip of nothing above every list.
                .onSizeChanged { topCover = it.height }
                .padding(horizontal = inset, vertical = 6.dp),
        ) {
            GlassPane(backdrop) {
                PlayerBar(
                    playback,
                    state,
                    settings.preferences.progressBarStyle,
                    settings.preferences.phone.swipeToChangeTrack,
                    glass = true,
                    open = openNowPlaying,
                )
            }
        }
    }

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            // Measured outside the navigation-bar padding: nothing else pads for it under glass, so the
            // lists have to be told about it along with the panes.
            .onSizeChanged { bottomCover = it.height }
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = inset, end = inset, bottom = inset / 2),
    ) {
        if (playback.track != null && !barAtTop) {
            GlassPane(backdrop) {
                PlayerBar(
                    playback,
                    state,
                    settings.preferences.progressBarStyle,
                    settings.preferences.phone.swipeToChangeTrack,
                    glass = true,
                    open = openNowPlaying,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        GlassPane(backdrop) {
            GlassNavigation(destination, settings.preferences.phone.navigationLabels, state::navigate)
        }
    }
}

/** The places, shared by the ordinary tab bar and the glass one so they cannot drift apart. */
private val PHONE_TABS = listOf(
    Triple(Destination.HOME, Icons.Default.Home, "Home"),
    Triple(Destination.SEARCH, Icons.Default.Search, "Search"),
    Triple(Destination.LINK, Icons.Default.Link, "Link"),
    Triple(Destination.LIBRARY, Icons.Default.LibraryMusic, "Library"),
    Triple(Destination.DOWNLOADS, Icons.Default.DownloadForOffline, "Downloads"),
    Triple(Destination.QUEUE, Icons.AutoMirrored.Filled.QueueMusic, "Queue"),
    Triple(Destination.SETTINGS, Icons.Default.Settings, "Settings"),
)

/** More tabs than fit a name each at a readable size. See [GlassNavigation]. */
private val CROWDED = PHONE_TABS.size > 5

/**
 * The tabs as they sit inside a pane of glass.
 *
 * Not Material's navigation bar with its background taken away: that bar is eighty pixels tall by rule
 * and pads itself for the system's navigation, both of which are wrong inside a floating pill that has
 * already been padded. This is the same five tabs at the height a pill wants, with the chosen one marked
 * by a lighter lozenge under it -- the way a lens looks when something inside the glass is pressed.
 */
@Composable
private fun GlassNavigation(current: Destination, labels: Boolean, go: (Destination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (labels && !CROWDED) 60.dp else 52.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PHONE_TABS.forEach { (destination, icon, label) ->
            // Now playing is a sheet, so Home stays lit underneath it rather than nothing being lit.
            val selected = current == destination ||
                (destination == Destination.HOME && current == Destination.NOW_PLAYING)
            val colour = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            // Seven tabs leave fifty-odd points each, and "Downloads" and "Settings" were cut to "Downlo"
            // and "Setting". So with labels on, the chosen tab widens into a pill with its name beside the
            // icon, and the others give up theirs: every name that shows is whole.
            // The pill is as wide as its name and the rest share what is left, rather than the pill taking
            // a fixed share: at a large system font size a fixed share still cut "Downloads" short.
            val widened = labels && CROWDED && selected
            val tab = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = .12f) else Color.Transparent)
                .clickable { go(destination) }
            Box(
                if (widened) tab.animateContentSize().padding(horizontal = 12.dp) else Modifier.weight(1f).then(tab),
                contentAlignment = Alignment.Center,
            ) {
                if (widened) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = colour, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(label, fontSize = 12.sp, color = colour, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = colour, modifier = Modifier.size(22.dp))
                        if (labels && !CROWDED) Text(label, fontSize = 10.sp, color = colour, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneNavigation(current: Destination, labels: Boolean, go: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        PHONE_TABS.forEach { (destination, icon, label) ->
            NavigationBarItem(
                // Now playing is a sheet, so Home stays lit underneath it rather than nothing being lit.
                selected = current == destination ||
                    (destination == Destination.HOME && current == Destination.NOW_PLAYING),
                onClick = { go(destination) },
                icon = { Icon(icon, label) },
                label = if (labels) {
                    { Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false) }
                } else {
                    null
                },
                // The same answer to seven tabs as the glass bar's: only the chosen one is named.
                alwaysShowLabel = !CROWDED,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

/**
 * The strip above the tabs: what is playing, and the two controls worth having at a thumb's reach.
 *
 * Tapping anywhere but the buttons opens the full screen. Skip-previous is deliberately absent — there is
 * room for two icons at this size, and next is the one people reach for.
 */
@Composable
private fun PlayerBar(
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    swipeToChangeTrack: Boolean,
    /**
     * Inside a pane of glass, which changes three things: no background of its own, the progress line
     * along the bottom inset from the curve instead of across a top edge that a pill does not have, and
     * the cover round, so it sits in the pill's end rather than jutting into it.
     */
    glass: Boolean = false,
    open: () -> Unit,
) {
    val track = playback.track ?: return
    val haptics = rememberHaptics(state)
    Surface(
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        tonalElevation = if (glass) 0.dp else 3.dp,
    ) {
        Column(
            Modifier
                .clickable(onClick = open)
                // Swiping the bar walks the queue. A drag threshold rather than a tap target, so it
                // cannot be triggered by the small movement that comes with an ordinary press.
                .then(
                    if (!swipeToChangeTrack) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures { _, drag ->
                                if (drag < -SWIPE_THRESHOLD) { haptics.tick(); state.next() }
                                if (drag > SWIPE_THRESHOLD) { haptics.tick(); state.previous() }
                            }
                        }
                    },
                ),
        ) {
            if (!glass) PlaybackLine(playback, style)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (glass) 10.dp else 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artworkUrl, 44.dp, corner = if (glass) 22.dp else 8.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playback.errorMessage ?: track.artistLine.ifBlank { "Unknown artist" },
                        color = if (playback.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPauseButton(playback, state)
                IconButton({ haptics.tick(); state.next() }) {
                    Icon(Icons.Default.SkipNext, "Next track")
                }
            }
            // Along the bottom and kept clear of the curve at each end: a line that ran into the pill's
            // rounded ends would be cut off at an angle, which reads as a mistake.
            if (glass) {
                Box(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 5.dp).clip(RoundedCornerShape(50))) {
                    PlaybackLine(playback, style)
                }
            }
        }
    }
}

@Composable
internal fun PlayPauseButton(playback: PlaybackState, state: AppState, size: Dp = 24.dp) {
    IconButton(state::togglePlayback) {
        when {
            playback.status == PlaybackStatus.RESOLVING ->
                CircularProgressIndicator(Modifier.size(size * .8f), strokeWidth = 2.dp)
            playback.isPlaying -> Icon(Icons.Default.Pause, "Pause", Modifier.size(size))
            else -> Icon(Icons.Default.PlayArrow, "Play", Modifier.size(size))
        }
    }
}

/**
 * How far into the track we are.
 *
 * Zero width when the length is not known yet, rather than a full bar — a bar drawn against an unknown
 * length reads as a track that has already finished.
 */
@Composable
internal fun PlaybackLine(playback: PlaybackState, style: ProgressBarStyle = ProgressBarStyle.MINIMAL) {
    // Segments is the one style with a shape small enough to survive being two pixels tall, and it is
    // what makes the mini bar recognisably the same choice as the one on the now playing screen. The
    // rest come down to a thickness here: this is a line under a title, not the seek bar itself, and
    // a travelling wave or a fat capsule in the player bar is noise in the corner of every screen.
    if (style == ProgressBarStyle.SEGMENTS) {
        val filled = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceVariant
        val fraction = playbackFraction(playback.positionMs, playback.durationMs)
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val count = (size.width / SeekBar.SEGMENT_PITCH_DP.dp.toPx()).toInt().coerceAtLeast(4)
            val pitch = size.width / count
            val width = (pitch * (1f - SeekBar.SEGMENT_GAP_RATIO)).coerceAtLeast(1f)
            val head = size.width * fraction
            repeat(count) { index ->
                val left = index * pitch
                drawRect(
                    color = if (left < head) filled else track,
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                )
            }
        }
        return
    }
    LinearProgressIndicator(
        progress = { playbackFraction(playback.positionMs, playback.durationMs) },
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when (style) {
                    ProgressBarStyle.MATERIAL, ProgressBarStyle.CAPSULE -> 5.dp
                    else -> 2.dp
                },
            ),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/**
 * The elapsed and total readouts, as the listener asked for them.
 *
 * "Time remaining" counts down and carries a minus, which is the convention everywhere it appears and the
 * only thing that tells it apart from elapsed at a glance.
 */
internal fun trailingTimeFor(positionMs: Long, durationMs: Long, display: TimeDisplay): String = when {
    durationMs <= 0 -> "--:--"
    display == TimeDisplay.REMAINING -> "-" + formatDuration((durationMs - positionMs).coerceAtLeast(0))
    else -> formatDuration(durationMs)
}

/**
 * How far a finger has to travel before it counts as a swipe rather than a press.
 *
 * Per drag event rather than cumulative, so a deliberate flick clears it and a thumb resting on the bar
 * never does.
 */
private const val SWIPE_THRESHOLD = 28f

internal fun playbackFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

@Composable
internal fun Artwork(url: String?, size: Dp, corner: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(
                Icons.Default.MusicNote,
                null,
                Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A track as a row: art, title, and the one line of detail that fits. */
@Composable
internal fun TrackRow(
    track: Track,
    state: AppState,
    isCurrent: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                // An empty artist line means the service did not say. Shown as such rather than filled in
                // with the provider's name, which is how a track once came to be credited to "YouTube Music".
                listOfNotNull(
                    track.artistLine.ifBlank { "Unknown artist" },
                    track.provider.displayName,
                    track.durationMs?.let(::formatDuration),
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke() ?: TrackMenuButton(track, state)
    }
}

internal fun formatDuration(ms: Long): String {
    val seconds = ms / 1_000
    val minutes = seconds / 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

/** A heading with nothing under it yet, said in a way that suggests what to do about it. */
@Composable
internal fun EmptyNote(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
