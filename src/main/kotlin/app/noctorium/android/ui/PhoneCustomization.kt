package app.noctorium.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.lyrics.LyricsProviderId
import app.noctorium.settings.AccentPreset
import app.noctorium.settings.ArtworkShape
import app.noctorium.settings.FontChoice
import app.noctorium.settings.HomePart
import app.noctorium.settings.PhonePlayerBarStyle
import app.noctorium.settings.LyricsAlignment
import app.noctorium.settings.LyricsSize
import app.noctorium.settings.PlayerButton
import app.noctorium.settings.ThemeColours
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.contrastRatio
import app.noctorium.settings.parseHexColour
import app.noctorium.settings.themeColours
import app.noctorium.settings.toHexColour
import app.noctorium.settings.BadgePolicy
import app.noctorium.settings.CardSize
import app.noctorium.settings.CornerStyle
import app.noctorium.settings.SurfaceStyle
import app.noctorium.settings.TextSize
import app.noctorium.settings.PlayerBarPosition
import app.noctorium.settings.ScrobbleConnectionStatus
import app.noctorium.settings.SettingsState
import app.noctorium.settings.StartPage
import app.noctorium.settings.TimeDisplay

/**
 * How Noctorium looks, with the desktop's own choices reaching the phone.
 *
 * The same preferences object drives both, so anything here is a setting the desktop already has — accent,
 * background depth, card size, badges, the progress bar, which screen opens first, and the time readout.
 *
 * One of the desktop's is left out because it describes a thing a phone has not got: hover controls need
 * a pointer to hover. The player bar's position used to be left out on the same reasoning and was not
 * the same case at all -- a phone screen has a top, and some people want the bar there.
 *
 * Then the ways of making it one's own: an accent of the listener's own colour, the typeface, what Home is
 * made of, and which of the player's buttons are there at all.
 */
internal fun LazyListScope.customizationCards(settings: SettingsState, state: AppState) {
    item { ProfileNameCard(settings, state) }
    item { ThemeCard(settings, state) }
    item { ColourCard(settings, state) }
    item { TextAndLayoutCard(settings, state) }
    item { HomeLookCard(settings, state) }
    item { PlayerBarCard(settings, state) }
    item { PlayerButtonsCard(settings, state) }
    item { NowPlayingLookCard(settings, state) }
}

/**
 * What Home is made of. Each part can be put away and brought back; the service rows go together by
 * service, since a listener who does not want SoundCloud's suggestions does not want any of them.
 */
@Composable
internal fun HomeLookCard(settings: SettingsState, state: AppState) {
    val hidden = settings.preferences.hiddenHomeParts
    SettingsCardShell {
        CardHeading(Icons.Default.Home, "Home")
        Spacer(Modifier.height(6.dp))
        HomePart.entries.forEach { part ->
            Toggle(part.displayName, part.description, part !in hidden) { shown ->
                state.setHomePartHidden(part, hidden = !shown)
            }
        }
    }
}

/**
 * The buttons on the phone's player, the bar and the full screen both, each of which can go.
 *
 * Play, pause, next and previous are not offered: a player without them is not one. Nor is the queue,
 * which on a phone is a tab rather than a button. The sleep timer and Connect come back by themselves
 * while they are in use, and the card says so, so a hidden one is never a timer nobody can find.
 */
@Composable
internal fun PlayerButtonsCard(settings: SettingsState, state: AppState) {
    val hidden = settings.preferences.phone.hiddenPlayerButtons
    SettingsCardShell {
        CardHeading(Icons.Default.Tune, "Player buttons")
        Text(
            "Play, pause, next and previous always stay. A hidden sleep timer or Connect button still " +
                "appears while it is in use.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        PHONE_PLAYER_BUTTONS.forEach { button ->
            Toggle(button.displayName, playerButtonWhere(button), button !in hidden) { shown ->
                state.updatePhone {
                    copy(hiddenPlayerButtons = if (shown) hiddenPlayerButtons - button else hiddenPlayerButtons + button)
                }
            }
        }
    }
}

/** Where on the phone's player each button sits, so switching it off is not a guess at what goes. */
private fun playerButtonWhere(button: PlayerButton): String = when (button) {
    PlayerButton.SHUFFLE -> "On the full screen, and on the Slim player bars."
    PlayerButton.REPEAT -> "On the full screen, and on the Slim player bars."
    PlayerButton.LIKE -> "The heart under the track on the full screen."
    PlayerButton.LYRICS -> "The Aa at the top of the full screen."
    PlayerButton.SLEEP_TIMER -> "The moon at the top of the full screen."
    PlayerButton.DEVICES -> "Noctorium Connect, at the top of the full screen."
    PlayerButton.VOLUME -> "Volume, mute and the boost, beside the heart on the full screen."
    PlayerButton.QUEUE -> "Beside the Aa at the top of the full screen; it opens the queue over the song."
}

/** What Noctorium calls you, on the home screen's greeting. */
@Composable
private fun ProfileNameCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    var name by remember(preferences.profileName) { mutableStateOf(preferences.profileName) }
    SettingsCardShell {
        CardHeading(Icons.Default.AccountCircle, "Your name")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            name,
            { name = it.take(60) },
            label = { Text("Your name") },
            singleLine = true,
            trailingIcon = {
                if (name != preferences.profileName) {
                    SkinnedTextButton({ state.setProfileName(name) }) { Text("Save") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Icons.Default.Palette, "Theme")
        Text(
            "Noctorium's own, and the palettes you may know from your editor. The desktop and the phone share it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        // The themes, by family: "Mocha" on its own means nothing and "Catppuccin Mocha" does. Each chip
        // carries the theme's page with its accent on it, so the choice can be made by eye.
        ThemePreset.entries.groupBy { it.family }.forEach { (family, presets) ->
            ChoiceRow("Theme · $family") {
                presets.forEach { preset ->
                    val colours = preset.colours ?: preferences.customTheme
                    SkinnedFilterChip(
                        selected = preferences.theme == preset,
                        onClick = { state.setTheme(preset) },
                        label = { Text(preset.displayName, fontSize = 11.sp) },
                        leadingIcon = {
                            Surface(
                                color = Color(colours.background),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(16.dp),
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.padding(4.dp).size(8.dp),
                                ) {
                                    Surface(color = Color(colours.accent), shape = RoundedCornerShape(50), modifier = Modifier.size(8.dp)) {}
                                }
                            }
                        },
                    )
                }
            }
        }
        if (preferences.theme == ThemePreset.CUSTOM) {
            CustomThemeEditor(preferences.customTheme, state::setCustomTheme)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun ColourCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Icons.Default.ColorLens, "Colour and surfaces")
        Spacer(Modifier.height(10.dp))

        ChoiceRow("Accent") {
            AccentPreset.entries.forEach { accent ->
                val swatch = accent.argb
                    ?: preferences.themeColours().accent.takeIf { accent == AccentPreset.THEME }
                    ?: (AccentPreview.argb ?: preferences.customAccent).takeIf { accent == AccentPreset.CUSTOM }
                SkinnedFilterChip(
                    selected = preferences.accent == accent,
                    onClick = { state.setAccent(accent) },
                    label = { Text(accent.displayName, fontSize = 11.sp) },
                    leadingIcon = swatch?.let { argb ->
                        {
                            Surface(
                                color = Color(argb.toInt()),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.size(12.dp),
                            ) {}
                        }
                    },
                )
            }
        }
        // The colour itself, only once "Your own" is the accent: offered to everybody, it would be a large
        // square of controls in the way of the rest of the card for a choice most people never make.
        if (preferences.accent == AccentPreset.CUSTOM) {
            AccentPicker(preferences.customAccent, state::setCustomAccent)
        }

        ChoiceRow("Surfaces") {
            SurfaceStyle.entries.forEach { style ->
                SkinnedFilterChip(
                    selected = preferences.surfaceStyle == style,
                    onClick = { state.setSurfaceStyle(style) },
                    label = { Text(style.displayName, fontSize = 11.sp) },
                )
            }
        }
        Text(
            preferences.surfaceStyle.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        ChoiceRow("Corners") {
            CornerStyle.entries.forEach { corner ->
                SkinnedFilterChip(
                    selected = preferences.cornerStyle == corner,
                    onClick = { state.setCornerStyle(corner) },
                    label = { Text(corner.displayName, fontSize = 11.sp) },
                )
            }
        }
        // Said rather than left to be found out: under a Windows theme the two choices above wait for another one.
        if (LocalSkin.current != ThemeSkin.STANDARD) {
            Text(
                "The Windows themes draw their own corners, bars and taskbar, so the glass and the corners come " +
                    "back with any other theme.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun TextAndLayoutCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Icons.Default.TextFields, "Text and layout")
        Spacer(Modifier.height(10.dp))

        ChoiceRow("Text size") {
            TextSize.entries.forEach { size ->
                SkinnedFilterChip(
                    selected = preferences.textSize == size,
                    onClick = { state.setTextSize(size) },
                    label = { Text(size.displayName, fontSize = 11.sp) },
                )
            }
        }
        Text(
            "Everything at once, on top of the phone's own text size.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        // Each chip is set in its own typeface, so the choice is made by looking rather than by name.
        ChoiceRow("Typeface") {
            FontChoice.entries.forEach { font ->
                SkinnedFilterChip(
                    selected = preferences.font == font,
                    onClick = { state.setFont(font) },
                    label = { Text(font.displayName, fontSize = 12.sp, fontFamily = font.family()) },
                    leadingIcon = { Text("Aa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = font.family()) },
                )
            }
        }
        Text(
            preferences.font.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        ChoiceRow("Card size") {
            CardSize.entries.forEach { size ->
                SkinnedFilterChip(
                    selected = preferences.cardSize == size,
                    onClick = { state.setCardSize(size) },
                    label = { Text(size.displayName, fontSize = 11.sp) },
                )
            }
        }

        ChoiceRow("Service badges") {
            BadgePolicy.entries.forEach { policy ->
                SkinnedFilterChip(
                    selected = preferences.badgePolicy == policy,
                    onClick = { state.setBadgePolicy(policy) },
                    label = { Text(policy.displayName, fontSize = 11.sp) },
                )
            }
        }

        ChoiceRow("Opens on") {
            StartPage.entries.forEach { page ->
                SkinnedFilterChip(
                    selected = preferences.startPage == page,
                    onClick = { state.setStartPage(page) },
                    label = { Text(page.displayName, fontSize = 11.sp) },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Animations", fontSize = 13.sp)
                Text(
                    "Screens ease in, pages slide, and a new track's name rises into place. Off makes every change instant.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            SkinnedSwitch(preferences.animations, state::setAnimations)
        }
    }
}

@Composable
internal fun PlayerBarCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    val playback by state.playback.collectAsState()
    SettingsCardShell {
        CardHeading(Icons.Default.SmartDisplay, "Player bar")
        Spacer(Modifier.height(10.dp))

        // Each drawn as it will be, the Bars from the song that is playing, so the choice is made by eye.
        Text("Seek bar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "On the now playing screen, and as the thin line along the player bar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SeekBarPicker(preferences.progressBarStyle, playback.track?.queueKey ?: PREVIEW_SEED, state::setProgressBarStyle)
        Text(
            preferences.progressBarStyle.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        ChoiceRow("Where it sits") {
            PlayerBarPosition.entries.forEach { position ->
                SkinnedFilterChip(
                    selected = preferences.playerBarPosition == position,
                    onClick = { state.setPlayerBarPosition(position) },
                    label = { Text(position.displayName, fontSize = 11.sp) },
                )
            }
        }

        Text("Layout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        PlayerBarPicker(preferences.phone.playerBarStyle) { layout -> state.updatePhone { copy(playerBarStyle = layout) } }
        Text(
            preferences.phone.playerBarStyle.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // Only while there is a taskbar to have a clock: the Taskbar bar, or under the Windows themes the tabs.
        if (LocalSkin.current != ThemeSkin.STANDARD || preferences.phone.playerBarStyle == PhonePlayerBarStyle.TASKBAR) {
            Toggle(
                "Show the clock",
                "The time in the taskbar's tray, as Windows showed it. Off gives its room to the buttons beside it.",
                preferences.taskbarClock,
                onChange = state::setTaskbarClock,
            )
            Spacer(Modifier.height(6.dp))
        }

        ChoiceRow("Time shows") {
            TimeDisplay.entries.forEach { display ->
                SkinnedFilterChip(
                    selected = preferences.timeDisplay == display,
                    onClick = { state.setTimeDisplay(display) },
                    label = { Text(display.displayName, fontSize = 11.sp) },
                )
            }
        }
    }
}

/** What the Bars preview is made from while nothing is playing: any song's row is better than an even one. */
private const val PREVIEW_SEED = "noctorium"

/**
 * How the full-screen player looks: how it is laid out, the cover's shape, and whether its colours spill onto
 * the page.
 */
@Composable
internal fun NowPlayingLookCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Icons.Default.Album, "Now playing")
        Spacer(Modifier.height(10.dp))

        Text("Layout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        NowPlayingPicker(preferences.phone.nowPlayingLayout) { layout -> state.updatePhone { copy(nowPlayingLayout = layout) } }
        Text(
            preferences.phone.nowPlayingLayout.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        ChoiceRow("Cover shape") {
            ArtworkShape.entries.forEach { shape ->
                SkinnedFilterChip(
                    selected = preferences.phone.artworkShape == shape,
                    onClick = { state.updatePhone { copy(artworkShape = shape) } },
                    label = { Text(shape.displayName, fontSize = 11.sp) },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ambient backdrop", fontSize = 13.sp)
                Text(
                    "Tints the now playing screen with the artwork behind it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            SkinnedSwitch(preferences.ambientBackdrop, state::setAmbientBackdrop)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, chips: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/**
 * Last.fm and ListenBrainz, which count what is listened to wherever it is listened to.
 *
 * Both are the desktop's own flows: Last.fm approves in a browser and then wants the button pressed again,
 * ListenBrainz takes a token pasted in. Neither needed changing for a phone.
 */
@Composable
internal fun LastFmCard(settings: SettingsState, state: AppState) {
    val scrobbling = settings.scrobbling

    SettingsCardShell {
        CardHeading(Icons.Default.History, "Last.fm")
        Spacer(Modifier.height(6.dp))
        Text(
            scrobbling.lastFm.username?.let { "Connected as $it" }
                ?: when (scrobbling.lastFm.status) {
                    ScrobbleConnectionStatus.AWAITING_APPROVAL ->
                        "Approve Noctorium in the browser, then press Finish."
                    ScrobbleConnectionStatus.ERROR -> scrobbling.lastFm.message ?: "Something went wrong."
                    else -> "Not connected."
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (scrobbling.lastFm.status) {
                ScrobbleConnectionStatus.CONNECTED ->
                    SkinnedOutlinedButton(state::disconnectLastFm) { Text("Disconnect") }
                ScrobbleConnectionStatus.AWAITING_APPROVAL ->
                    SkinnedButton(state::finishLastFmLogin) { Text("Finish") }
                else -> SkinnedButton(state::beginLastFmLogin) { Text("Connect") }
            }
        }
    }
}

@Composable
internal fun ListenBrainzCard(settings: SettingsState, state: AppState) {
    val scrobbling = settings.scrobbling
    var token by remember { mutableStateOf("") }

    SettingsCardShell {
        CardHeading(Icons.Default.History, "ListenBrainz")
        Spacer(Modifier.height(6.dp))
        Text(
            scrobbling.listenBrainz.username?.let { "Connected as $it" } ?: "Not connected.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        if (scrobbling.listenBrainz.status == ScrobbleConnectionStatus.CONNECTED) {
            Spacer(Modifier.height(8.dp))
            SkinnedOutlinedButton(state::disconnectListenBrainz) { Text("Disconnect") }
        } else {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                token,
                { token = it.trim() },
                label = { Text("User token") },
                singleLine = true,
                supportingText = { Text("From listenbrainz.org, under Settings.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SkinnedButton({ state.connectListenBrainz(token) }, enabled = token.isNotBlank()) { Text("Connect") }
        }
    }
}

/**
 * Which lyric source every song opens on. The rest are still asked; this one is shown first when it has an
 * answer. The same choice the row of sources above the lyrics makes, shown here so it can be put back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricsCard(settings: SettingsState, state: AppState) {
    val preferred = settings.preferences.lyricsProvider

    SettingsCardShell {
        CardHeading(Icons.Default.Lyrics, "Open lyrics on")
        Spacer(Modifier.height(6.dp))
        Text(
            "Eight sources are asked at once. Choose one to read first whenever it has the song, or let the " +
                "best answer win. Tapping a source above the lyrics changes this too.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkinnedFilterChip(
                selected = preferred == null,
                onClick = state::clearPreferredLyricsProvider,
                label = { Text("The best answer", fontSize = 11.sp) },
            )
            LyricsProviderId.entries.forEach { provider ->
                SkinnedFilterChip(
                    selected = preferred == provider,
                    onClick = { state.selectLyricsProvider(provider) },
                    label = { Text(provider.displayName, fontSize = 11.sp) },
                )
            }
        }
    }
}

/**
 * How the lyrics are set: their size, the edge they sit against, and whether the lines not being sung step
 * back. Three lines drawn exactly as the lyrics will be sit above the choices, since "Large" and "Huge"
 * mean little until they are seen at the size of a phone.
 */
@Composable
internal fun LyricsLookCard(settings: SettingsState, state: AppState) {
    val look = settings.preferences.lyrics

    SettingsCardShell {
        CardHeading(Icons.Default.FormatSize, "How lyrics look")
        Spacer(Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = .6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                LyricLine("A line already sung", active = false, synced = true, look = look)
                LyricLine("The line being sung", active = true, synced = true, look = look)
                LyricLine("A line still to come", active = false, synced = true, look = look)
            }
        }

        ChoiceRow("Size") {
            LyricsSize.entries.forEach { size ->
                SkinnedFilterChip(
                    selected = look.size == size,
                    onClick = { state.updateLyricsLook { copy(size = size) } },
                    label = { Text(size.displayName, fontSize = 11.sp) },
                )
            }
        }
        ChoiceRow("Lined up") {
            LyricsAlignment.entries.forEach { alignment ->
                SkinnedFilterChip(
                    selected = look.alignment == alignment,
                    onClick = { state.updateLyricsLook { copy(alignment = alignment) } },
                    label = { Text(alignment.displayName, fontSize = 11.sp) },
                )
            }
        }
        Toggle(
            "Dim the other lines",
            "So the eye finds the one being sung. Off sets every line at full strength, with that one in the accent.",
            look.dimOtherLines,
        ) { dim -> state.updateLyricsLook { copy(dimOtherLines = dim) } }
    }
}

/**
 * Six hex colours and a switch: the listener's own theme.
 *
 * Applied only when every field reads as a colour, and warned about -- not refused -- when the writing
 * would sit too close to the page to read. Six boxes that take the values people copy out of a palette's
 * README are what a theme actually arrives as.
 */
@Composable
private fun CustomThemeEditor(current: ThemeColours, apply: (ThemeColours) -> Unit) {
    var background by remember(current) { mutableStateOf(current.background.toHexColour()) }
    var panel by remember(current) { mutableStateOf(current.panel.toHexColour()) }
    var card by remember(current) { mutableStateOf(current.card.toHexColour()) }
    var text by remember(current) { mutableStateOf(current.text.toHexColour()) }
    var subtext by remember(current) { mutableStateOf(current.subtext.toHexColour()) }
    var accent by remember(current) { mutableStateOf(current.accent.toHexColour()) }
    var light by remember(current) { mutableStateOf(current.light) }

    val parsed = listOf(background, panel, card, text, subtext, accent).map(::parseHexColour)
    val complete = parsed.all { it != null }
    val readable = complete && contrastRatio(parsed[3]!!, parsed[0]!!) >= 4.5

    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your colours", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField("Background", background, Modifier.weight(1f)) { background = it }
            HexField("Panel", panel, Modifier.weight(1f)) { panel = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField("Card", card, Modifier.weight(1f)) { card = it }
            HexField("Text", text, Modifier.weight(1f)) { text = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField("Muted text", subtext, Modifier.weight(1f)) { subtext = it }
            HexField("Accent", accent, Modifier.weight(1f)) { accent = it }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Light theme", fontSize = 13.sp)
                Text("Dark writing on a pale page.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            SkinnedSwitch(light, { light = it })
        }
        if (complete && !readable) {
            Text(
                "The text and the background are too close to read comfortably (contrast " +
                    "${"%.1f".format(contrastRatio(parsed[3]!!, parsed[0]!!))}, where 4.5 is the floor).",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
            )
        }
        SkinnedButton(
            onClick = { apply(ThemeColours(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!, parsed[4]!!, parsed[5]!!, light)) },
            enabled = complete,
        ) { Text("Apply") }
    }
}

@Composable
private fun HexField(label: String, value: String, modifier: Modifier, change: (String) -> Unit) {
    val colour = parseHexColour(value)
    OutlinedTextField(
        value,
        { change(it.take(9)) },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        isError = colour == null,
        leadingIcon = {
            Surface(
                color = colour?.let { Color(it) } ?: Color.Transparent,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(14.dp),
            ) {}
        },
        modifier = modifier,
    )
}
