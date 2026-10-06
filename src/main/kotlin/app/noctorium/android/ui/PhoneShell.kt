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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import app.noctorium.playback.RepeatMode
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import app.noctorium.settings.PhonePlayerBarStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.draw.blur
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.graphics.vector.ImageVector
import app.noctorium.settings.PlayerButton
import androidx.compose.material3.LocalContentColor
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
    val reported by state.playback.collectAsState()
    val queue by state.queue.state.collectAsState()
    // The queue kept from the last session puts its song in the player at launch, paused, so the music
    // is where it was left rather than nowhere; play picks it up at the place it stopped.
    val playback = shownPlayback(reported, queue)
    val library by state.library.collectAsState()
    val settings by state.settings.collectAsState()
    // Asked once, the first time something plays on a phone that could stop it when the screen locks.
    KeepPlayingPrompt(state, playing = playback.isPlaying)
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
        // One tab giving way to the next with a short fade and rise, rather than the screen cutting.
        MotionContent(ui.destination, Modifier.fillMaxSize()) { destination ->
            when (destination) {
                Destination.SEARCH -> SearchScreen(state)
                Destination.LINK -> LinkScreen(state)
                Destination.LIBRARY -> LibraryScreen(state)
                Destination.DOWNLOADS -> DownloadsScreen(state)
                // Only to a service with a sign-in page of its own: the page knows YouTube, SoundCloud and VK by
                // name, and anything else -- Bandcamp, which needs only a name -- would fall through to Google's.
                Destination.SETTINGS -> SettingsScreen(state, backEnabled = !overlaid && !nowPlayingOpen) { provider ->
                    if (hasSignInPage(provider)) signingInTo = provider
                }
                Destination.QUEUE -> QueueScreen(state)
                // Now playing is a sheet here rather than a destination, so anything that asks for it lands on
                // Home with the sheet open instead of on an empty screen.
                Destination.HOME, Destination.NOW_PLAYING -> HomeScreen(state)
            }
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
                            settings.preferences.phone.playerBarStyle,
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
                            settings.preferences.phone.playerBarStyle,
                        ) { nowPlayingOpen = true }
                    }
                    PhoneNavigation(
                        ui.destination,
                        settings.preferences.phone.navigationLabels,
                        // A hidden tab is only off the bar. Its page is still drawn when something leads
                        // there -- a shared link opening Link, a playlist on Home opening Library -- and
                        // simply has no tab lit while it is showing.
                        visibleTabs(settings.preferences.phone.hiddenDestinations),
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
            // Up from the bar it was opened from and back down to it, easing to a stop; instant with animations off.
            val moving = LocalMotion.current
            AnimatedVisibility(
                visible = signingInTo == null && nowPlayingOpen && playback.track != null,
                enter = if (moving) {
                    slideInVertically(tween(MotionTiming.STANDARD + 60, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(MotionTiming.STANDARD))
                } else {
                    EnterTransition.None
                },
                exit = if (moving) {
                    slideOutVertically(tween(MotionTiming.STANDARD, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(MotionTiming.STANDARD))
                } else {
                    ExitTransition.None
                },
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

    // While the keyboard is up the page stops where the panes begin, instead of running on beneath them.
    // A text field that is given focus scrolls itself into the page's view, and a page that ran beneath the
    // panes would count the strip they cover as in view: the field typed into would sit behind the tab bar.
    // The panes ride on the keyboard, so what they cover then is measured with it.
    val typing = WindowInsets.ime.getBottom(density) > 0
    Box(Modifier.fillMaxSize().glassSource(backdrop)) {
        GlassWash(playback.track?.artworkUrl)
        Box(Modifier.fillMaxSize().padding(bottom = if (typing) with(density) { bottomCover.toDp() } else 0.dp)) {
            CompositionLocalProvider(
                LocalChromeInsets provides with(density) {
                    ChromeInsets(topCover.toDp(), if (typing) 0.dp else bottomCover.toDp())
                },
            ) { screens() }
        }
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
                    settings.preferences.phone.playerBarStyle,
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
                    settings.preferences.phone.playerBarStyle,
                    glass = true,
                    open = openNowPlaying,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        GlassPane(backdrop) {
            GlassNavigation(
                destination,
                settings.preferences.phone.navigationLabels,
                visibleTabs(settings.preferences.phone.hiddenDestinations),
                state::navigate,
            )
        }
    }
}

/**
 * Each place's icon, shared by the ordinary tab bar and the glass one so they cannot drift apart. Which
 * places are on the bar, and in what order, is [visibleTabs]; what each is called is [tabName].
 */
private fun Destination.tabIcon(): ImageVector = when (this) {
    Destination.HOME, Destination.NOW_PLAYING -> Icons.Default.Home
    Destination.SEARCH -> Icons.Default.Search
    Destination.LINK -> Icons.Default.Link
    Destination.LIBRARY -> Icons.Default.LibraryMusic
    Destination.DOWNLOADS -> Icons.Default.DownloadForOffline
    Destination.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    Destination.SETTINGS -> Icons.Default.Settings
}

/**
 * More tabs than fit a name each at a readable size. See [GlassNavigation].
 *
 * Counted from the tabs actually on the bar, so somebody who hides a few gets every name back under its
 * icon; with all seven it is the same answer as always.
 */
private fun crowded(tabs: List<Destination>): Boolean = tabs.size > 5

/**
 * The tabs as they sit inside a pane of glass.
 *
 * Not Material's navigation bar with its background taken away: that bar is eighty pixels tall by rule
 * and pads itself for the system's navigation, both of which are wrong inside a floating pill that has
 * already been padded. This is the same five tabs at the height a pill wants, with the chosen one marked
 * by a lighter lozenge under it -- the way a lens looks when something inside the glass is pressed.
 */
@Composable
private fun GlassNavigation(current: Destination, labels: Boolean, tabs: List<Destination>, go: (Destination) -> Unit) {
    val crowded = crowded(tabs)
    Row(
        Modifier.fillMaxWidth().height(if (labels && !crowded) 60.dp else 52.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { destination ->
            val icon = destination.tabIcon()
            val label = tabName(destination)
            // Now playing is a sheet, so Home stays lit underneath it rather than nothing being lit.
            val selected = current == destination ||
                (destination == Destination.HOME && current == Destination.NOW_PLAYING)
            val colour = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            // Seven tabs leave fifty-odd points each, and "Downloads" and "Settings" were cut to "Downlo"
            // and "Setting". So with labels on, the chosen tab widens into a pill with its name beside the
            // icon, and the others give up theirs: every name that shows is whole.
            // The pill is as wide as its name and the rest share what is left, rather than the pill taking
            // a fixed share: at a large system font size a fixed share still cut "Downloads" short.
            val widened = labels && crowded && selected
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
                        if (labels && !crowded) Text(label, fontSize = 10.sp, color = colour, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneNavigation(current: Destination, labels: Boolean, tabs: List<Destination>, go: (Destination) -> Unit) {
    val crowded = crowded(tabs)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        tabs.forEach { destination ->
            val icon = destination.tabIcon()
            val label = tabName(destination)
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
                alwaysShowLabel = !crowded,
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
 * The strip above the tabs: what is playing, and the controls worth having at a thumb's reach.
 *
 * Tapping anywhere but the buttons opens the full screen, and swiping it walks the queue, whichever
 * [layout] it is in. The layouts are what the strip is for: Classic is the cover, the track, play and next;
 * Slim gives the list above it as much of the screen as it can; Controls adds previous and a seek bar for
 * somebody who drives the music from here rather than from the full screen; Spotlight is about the record.
 * Floating lifts Classic off the edge; Line is the least a bar can be; Record turns; Taskbar is a desktop's.
 */
@Composable
private fun PlayerBar(
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    swipeToChangeTrack: Boolean,
    layout: PhonePlayerBarStyle,
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
    val settings by state.settings.collectAsState()
    val queue by state.queue.state.collectAsState()
    // Next is greyed where it would go nowhere: the end of a queue with nothing lined up after it.
    val hasNext = queue.hasNext
    // A song the account's own Spotify app is playing says so, or the bar would read as this phone playing it.
    val onSpotify = playsOnSpotify(track, settings.spotify)
    // The line along the edge, for the bars without one of their own: Controls carries a seek bar, and a
    // line under a seek bar is the same thing twice; Line is little more than its line, Taskbar keeps it in
    // the song's button, and Floating along the foot of its card -- which under glass is the pane's foot.
    val line = when (layout) {
        PhonePlayerBarStyle.CONTROLS, PhonePlayerBarStyle.LINE, PhonePlayerBarStyle.TASKBAR -> false
        PhonePlayerBarStyle.FLOATING -> glass
        else -> true
    }
    // Floating draws its own card, so the strip it floats in is the page's own colour.
    val floating = layout == PhonePlayerBarStyle.FLOATING && !glass
    val gestures = Modifier
        .clickable(onClick = open)
        // Swiping the bar walks the queue. A drag threshold rather than a tap target, so it cannot be
        // triggered by the small movement that comes with an ordinary press.
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
        )
    Surface(
        color = if (glass || floating) Color.Transparent else MaterialTheme.colorScheme.surface,
        tonalElevation = if (glass || floating) 0.dp else 3.dp,
    ) {
        Box {
            if (layout == PhonePlayerBarStyle.SPOTLIGHT) SpotlightBackdrop(track.artworkUrl, glass)
            if (floating) {
                FloatingBar(track, playback, state, style, onSpotify, hasNext, gestures)
                return@Box
            }
            Column(gestures) {
                if (!glass && line) PlaybackLine(playback, style)
                when (layout) {
                    PhonePlayerBarStyle.CLASSIC, PhonePlayerBarStyle.FLOATING -> ClassicBar(track, playback, state, glass, onSpotify, hasNext)
                    PhonePlayerBarStyle.SLIM -> SlimBar(track, playback, state, glass, onSpotify = onSpotify)
                    PhonePlayerBarStyle.SLIM_LEFT -> SlimBar(track, playback, state, glass, controlsFirst = true, onSpotify = onSpotify)
                    PhonePlayerBarStyle.CONTROLS -> ControlsBar(track, playback, state, style, glass, onSpotify, hasNext)
                    PhonePlayerBarStyle.SPOTLIGHT -> SpotlightBar(track, playback, state, glass, onSpotify, hasNext)
                    PhonePlayerBarStyle.LINE -> LineBar(track, playback, state, style, glass, onSpotify)
                    PhonePlayerBarStyle.RECORD -> RecordBar(track, playback, state, glass, onSpotify, hasNext)
                    PhonePlayerBarStyle.TASKBAR -> TaskbarBar(track, playback, state, style, glass, open)
                }
                // Along the bottom and kept clear of the curve at each end: a line that ran into the pill's
                // rounded ends would be cut off at an angle, which reads as a mistake.
                if (glass && line) {
                    Box(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 5.dp).clip(RoundedCornerShape(50))) {
                        PlaybackLine(playback, style)
                    }
                }
            }
        }
    }
}

/** The cover, the track, play and next. The bar as it has always been. */
@Composable
internal fun ClassicBar(track: Track, playback: PlaybackState, state: AppState, glass: Boolean, onSpotify: Boolean, hasNext: Boolean) {
    val haptics = rememberHaptics(state)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (glass) 10.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, 44.dp, corner = if (glass) 22.dp else 8.dp)
        Spacer(Modifier.width(11.dp))
        TrackLines(track, playback, onSpotify, Modifier.weight(1f))
        PlayPauseButton(playback, state)
        IconButton({ haptics.tick(); state.next() }, enabled = hasNext) {
            Icon(Icons.Default.SkipNext, "Next track")
        }
    }
}

/**
 * The track in two small lines and every control, about two thirds the height of Classic.
 *
 * The first version had play alone, to keep it short, and short is no use if turning shuffle on means
 * opening the full screen. So it has all five -- shuffle, previous, play, next, repeat -- and the song's
 * line is what gives way on a narrow phone. [controlsFirst] is Slim left: the controls at the start, where
 * a left thumb reaches, and the song after them.
 */
@Composable
private fun SlimBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    glass: Boolean,
    controlsFirst: Boolean = false,
    onSpotify: Boolean = false,
) {
    val edge = if (glass) 10.dp else 12.dp
    Row(
        Modifier.fillMaxWidth().padding(
            start = if (controlsFirst) 2.dp else edge,
            end = if (controlsFirst) edge else 2.dp,
            top = 3.dp,
            bottom = 3.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controlsFirst) {
            SlimControls(playback, state)
            Spacer(Modifier.width(6.dp))
        } else {
            Artwork(track.artworkUrl, 32.dp, corner = if (glass) 16.dp else 6.dp)
            Spacer(Modifier.width(10.dp))
        }
        // Two small lines rather than one: at a large font size "title  ·  artist" on one line was
        // squeezed to "Archangel · …", a dot leading nowhere. The row is as tall as its buttons either way.
        MotionContent(track, Modifier.weight(1f), kind = MotionKind.TRACK, contentKey = { it.queueKey }) { shown ->
            Column {
                Text(
                    shown.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    barDetail(shown, playback, onSpotify),
                    color = if (playback.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (controlsFirst) {
            Spacer(Modifier.width(10.dp))
            Artwork(track.artworkUrl, 32.dp, corner = if (glass) 16.dp else 6.dp)
        } else {
            Spacer(Modifier.width(4.dp))
            SlimControls(playback, state)
        }
    }
}

/**
 * Shuffle, previous, play, next and repeat, at the size a slim strip can carry and a thumb can still hit.
 *
 * Shuffle and repeat go when the listener has put them away, and the song's line takes the room they
 * leave; the three in the middle always stay.
 */
@Composable
private fun SlimControls(playback: PlaybackState, state: AppState) {
    val haptics = rememberHaptics(state)
    val queue by state.queue.state.collectAsState()
    val settings by state.settings.collectAsState()
    val hidden = settings.preferences.phone.hiddenPlayerButtons
    val lit = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showsPlayerButton(hidden, PlayerButton.SHUFFLE)) {
            IconButton({ haptics.tick(); state.toggleShuffle() }, Modifier.size(SLIM_BUTTON)) {
                Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(19.dp), tint = if (queue.shuffleEnabled) lit else quiet)
            }
        }
        IconButton({ haptics.tick(); state.previous() }, Modifier.size(SLIM_BUTTON)) {
            Icon(Icons.Default.SkipPrevious, "Previous track", Modifier.size(22.dp))
        }
        Box(Modifier.size(SLIM_BUTTON), contentAlignment = Alignment.Center) { PlayPauseButton(playback, state, size = 24.dp) }
        IconButton({ haptics.tick(); state.next() }, Modifier.size(SLIM_BUTTON), enabled = queue.hasNext) {
            Icon(Icons.Default.SkipNext, "Next track", Modifier.size(22.dp))
        }
        if (showsPlayerButton(hidden, PlayerButton.REPEAT)) {
            IconButton({ haptics.tick(); state.cycleRepeat() }, Modifier.size(SLIM_BUTTON)) {
                Icon(
                    if (queue.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    when (queue.repeatMode) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    Modifier.size(19.dp),
                    tint = if (queue.repeatMode != RepeatMode.OFF) lit else quiet,
                )
            }
        }
    }
}

/** Five of these and the song's line still gets the better part of a phone's width. */
private val SLIM_BUTTON = 38.dp

/**
 * Previous, play and next beside the track, and a seek bar beneath that can be dragged.
 *
 * The seek bar takes its own gestures, so dragging it scrubs the song rather than swiping to the next one,
 * and tapping it seeks rather than opening the full screen.
 */
@Composable
private fun ControlsBar(
    track: Track,
    playback: PlaybackState,
    state: AppState,
    style: ProgressBarStyle,
    glass: Boolean,
    onSpotify: Boolean,
    hasNext: Boolean,
) {
    val haptics = rememberHaptics(state)
    val settings by state.settings.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = if (glass) 10.dp else 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, 44.dp, corner = if (glass) 22.dp else 8.dp)
            Spacer(Modifier.width(11.dp))
            TrackLines(track, playback, onSpotify, Modifier.weight(1f))
            IconButton({ haptics.tick(); state.previous() }) {
                Icon(Icons.Default.SkipPrevious, "Previous track")
            }
            PlayPauseButton(playback, state)
            IconButton({ haptics.tick(); state.next() }, enabled = hasNext) {
                Icon(Icons.Default.SkipNext, "Next track")
            }
        }
        Box(Modifier.padding(horizontal = if (glass) 14.dp else 4.dp)) {
            Seekbar(
                playback.positionMs,
                playback.durationMs,
                settings.preferences.timeDisplay,
                style,
                playback.isPlaying,
                state::seekTo,
                seed = track.queueKey,
            )
        }
    }
}

/** A larger cover and a larger title, over the artwork itself blurred behind them. */
@Composable
private fun SpotlightBar(track: Track, playback: PlaybackState, state: AppState, glass: Boolean, onSpotify: Boolean, hasNext: Boolean) {
    val haptics = rememberHaptics(state)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (glass) 10.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, 62.dp, corner = if (glass) 31.dp else 12.dp)
        Spacer(Modifier.width(13.dp))
        MotionContent(track, Modifier.weight(1f), kind = MotionKind.TRACK, contentKey = { it.queueKey }) { shown ->
            Column {
                Text(shown.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    barDetail(shown, playback, onSpotify),
                    color = if (playback.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        FilledIconButton(state::togglePlayback, Modifier.size(48.dp)) {
            when {
                playback.status == PlaybackStatus.RESOLVING ->
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else -> PlayPauseIcon(playback.isPlaying, 28.dp)
            }
        }
        IconButton({ haptics.tick(); state.next() }, enabled = hasNext) {
            Icon(Icons.Default.SkipNext, "Next track")
        }
    }
}

/**
 * The cover, blurred, behind the Spotlight bar, and dimmed enough that the title over it stays readable.
 *
 * The now playing screen's ambient backdrop at the size of a strip. Blur needs Android 12; before that
 * the picture is only faded, which the scrim still makes readable.
 */
@Composable
private fun BoxScope.SpotlightBackdrop(artworkUrl: String?, glass: Boolean) {
    if (artworkUrl == null) return
    AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = if (glass) .35f else .6f,
        modifier = Modifier
            .matchParentSize()
            .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
    )
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = if (glass) .25f else .45f)))
}

/**
 * The title over the artist, or over what went wrong, in red, when something did.
 *
 * A new track's name rises into place as the last one lifts away, so a skip is seen as well as heard.
 */
@Composable
internal fun TrackLines(track: Track, playback: PlaybackState, onSpotify: Boolean, modifier: Modifier) {
    MotionContent(track, modifier, kind = MotionKind.TRACK, contentKey = { it.queueKey }) { shown ->
        Column {
            Text(
                shown.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                barDetail(shown, playback, onSpotify),
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
    }
}

/**
 * The bar's second line: what went wrong, or the artist -- after "On Spotify", in Spotify's green, while the
 * song is playing in the account's own Spotify app rather than on this phone.
 */
internal fun barDetail(track: Track, playback: PlaybackState, onSpotify: Boolean): AnnotatedString {
    playback.errorMessage?.let { return AnnotatedString(it) }
    val artist = track.artistLine.ifBlank { "Unknown artist" }
    if (!onSpotify) return AnnotatedString(artist)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = ProviderType.SPOTIFY.badgeColour(), fontWeight = FontWeight.SemiBold)) {
            append("On Spotify")
        }
        append(" · $artist")
    }
}

@Composable
internal fun PlayPauseButton(playback: PlaybackState, state: AppState, size: Dp = 24.dp) {
    IconButton(state::togglePlayback) {
        when {
            playback.status == PlaybackStatus.RESOLVING ->
                CircularProgressIndicator(Modifier.size(size * .8f), strokeWidth = 2.dp)
            else -> PlayPauseIcon(playback.isPlaying, size)
        }
    }
}

/** Play or pause, the one giving way to the other with a small scale rather than a cut. */
@Composable
internal fun PlayPauseIcon(playing: Boolean, size: Dp, tint: Color = LocalContentColor.current) {
    MotionContent(playing, kind = MotionKind.ICON, contentAlignment = Alignment.Center) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            Modifier.size(size),
            tint = tint,
        )
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
    // Bars, Beads, Neon, Ruler and Luna come down to a few points as well: a row of bars, a string of beads,
    // a lit tube, a ruler's minutes and XP's green blocks, each still the choice made for the seek bar.
    if (style.hasOwnLine) {
        val filled = MaterialTheme.colorScheme.primary
        // The writing colour, faint, as the seek bar's own track is: the panel's colour, which the plain line
        // uses, left the unplayed bars and beads all but invisible on a dark theme, and they are the shape.
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = SeekBar.TRACK_ALPHA)
        val pale = MaterialTheme.colorScheme.background.luminance() > .5f
        val fraction = playbackFraction(playback.positionMs, playback.durationMs)
        var width by remember { mutableIntStateOf(0) }
        val shapes = rememberSeekBarShapes(
            style,
            playback.track?.queueKey.orEmpty(),
            playback.durationMs,
            width,
            barPitch = LINE_BARS_PITCH,
            tickRoom = LINE_TICK_ROOM,
        )
        Canvas(Modifier.fillMaxWidth().height(style.lineHeight()).onSizeChanged { width = it.width }) {
            drawPlaybackLine(style, fraction, track, filled, shapes, pale)
        }
        return
    }
    // Segments and Classic are the styles with a shape small enough to survive being two pixels tall --
    // both are blocks -- and it is what makes the mini bar recognisably the same choice as the one on the
    // now playing screen. The rest come down to a thickness here: this is a line under a title, not the
    // seek bar itself, and a travelling wave or a fat capsule in the player bar is noise in every corner.
    if (style == ProgressBarStyle.SEGMENTS || style == ProgressBarStyle.CLASSIC) {
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
