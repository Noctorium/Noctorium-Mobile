package app.noctorium.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.playback.MAX_SPEED
import app.noctorium.playback.MIN_SPEED
import app.noctorium.settings.AutoplaySource
import app.noctorium.settings.DataSaver
import app.noctorium.settings.HomePlay
import app.noctorium.settings.SettingsState
import app.noctorium.settings.ThemeSkin
import kotlin.math.roundToInt

/**
 * The settings that only exist because this is a phone.
 *
 * Separate from the Look section, which is mostly the desktop's own list carried across. Everything here is
 * about something a window has not got: a battery, a metered connection, a screen that turns itself off, a
 * motor to buzz. None of it would mean anything on a desktop and none of it is offered there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneOptionsCard(settings: SettingsState, state: AppState) {
    val phone = settings.preferences.phone

    SettingsCardShell {
        CardHeading(Glyph.PHONE_ANDROID.image, "On this phone")
        Spacer(Modifier.height(10.dp))

        SettingAnchor(SettingIds.KEEP_SCREEN_ON) {
            Toggle(
                "Keep the screen on",
                "While something is playing. Useful on a desk, hard on a battery everywhere else.",
                phone.keepScreenOn,
            ) { state.updatePhone { copy(keepScreenOn = it) } }
        }

        SettingAnchor(SettingIds.WIFI_ONLY) {
            Toggle(
                "Download on Wi-Fi only",
                "Refuses downloads on mobile data, so an album cannot quietly spend your allowance.",
                phone.downloadOnWifiOnly,
            ) { state.updatePhone { copy(downloadOnWifiOnly = it) } }
        }

        SettingAnchor(SettingIds.DATA_SAVER) {
            OptionRow("Data saver") {
                DataSaver.entries.forEach { saver ->
                    SkinnedFilterChip(
                        selected = phone.dataSaver == saver,
                        onClick = { state.setDataSaver(saver) },
                        label = { Text(saver.displayName, fontSize = 11.sp) },
                    )
                }
            }
            // Which services it reaches, said plainly: the rest offer one quality, or play somewhere else.
            Text(
                "${phone.dataSaver.description} YouTube and SoundCloud offer more than one quality, so those are " +
                    "the songs it changes, downloads included.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SettingAnchor(SettingIds.HAPTICS) {
            Toggle(
                "Haptics",
                "A short tick when a control does something.",
                phone.haptics,
            ) { state.updatePhone { copy(haptics = it) } }
        }
    }
}

/**
 * How the music itself plays, and the gestures that move through it.
 *
 * The speed is Noctorium's own setting now rather than the phone's, which is what lets the player be told
 * of it the way it is told of the equaliser. It is a slider, since anything from half to double can be set
 * and a row of chips can only offer some of it; nothing is saved until the finger lifts.
 *
 * What happens when the queue runs out has a page of its own now, [AutoplayCard]: this card had grown to the
 * longest in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackOptionsCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    val phone = preferences.phone
    var heldSpeed by remember { mutableStateOf<Float?>(null) }
    val speed = heldSpeed ?: preferences.playbackSpeed

    SettingsCardShell {
        CardHeading(Glyph.GRAPHIC_EQ.image, "Playback")
        Spacer(Modifier.height(10.dp))

        SettingAnchor(SettingIds.SWIPE_BAR) {
            Toggle(
                "Swipe the player bar",
                "Sideways to move through the queue, without opening the full screen.",
                phone.swipeToChangeTrack,
            ) { state.updatePhone { copy(swipeToChangeTrack = it) } }
        }

        SettingAnchor(SettingIds.DOUBLE_TAP) {
            OptionRow("Double tap the cover jumps") {
                SEEK_STEPS.forEach { seconds ->
                    SkinnedFilterChip(
                        selected = phone.seekStepSeconds == seconds,
                        onClick = { state.updatePhone { copy(seekStepSeconds = seconds) } },
                        label = { Text("${seconds}s", fontSize = 11.sp) },
                    )
                }
            }
        }

        SettingAnchor(SettingIds.SPEED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    speedLabel(speed),
                    color = if (speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SkinnedSlider(
                value = speed,
                // To the twentieth, which is what core keeps, so the thumb and the label show what will be saved.
                // Snapped here rather than with steps: thirty steps drew thirty dots along the track.
                onValueChange = { heldSpeed = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = {
                    heldSpeed?.let(state::setPlaybackSpeed)
                    heldSpeed = null
                },
                valueRange = MIN_SPEED..MAX_SPEED,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Faster or slower, keeping the pitch. A song playing on Spotify keeps Spotify's own speed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                if (preferences.playbackSpeed != 1f) {
                    SkinnedTextButton({ state.setPlaybackSpeed(1f) }) { Text("Normal") }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        SettingAnchor(SettingIds.SKIP_SILENCE) {
            Toggle(
                "Skip silence",
                "Shortens gaps and long quiet intros. Leaves the music alone.",
                phone.skipSilence,
            ) { state.updatePhone { copy(skipSilence = it) } }
        }

        SettingAnchor(SettingIds.SKIP_NON_MUSIC) {
            Toggle(
                "Skip the parts of a YouTube video that are not the music",
                "Intros, outros, sponsor reads and talking, as marked by SponsorBlock's contributors. YouTube " +
                    "Music tracks are never touched.",
                preferences.skipNonMusic,
                onChange = state::setSkipNonMusic,
            )
        }

        SettingAnchor(SettingIds.SLEEP_FADE) {
            OptionRow("A sleep timer fades out over") {
                sleepFadeChoices(preferences.sleepFadeSeconds).forEach { seconds ->
                    SkinnedFilterChip(
                        selected = preferences.sleepFadeSeconds == seconds,
                        onClick = { state.setSleepFade(seconds) },
                        label = { Text(if (seconds == 0) "Off" else "$seconds s", fontSize = 11.sp) },
                    )
                }
            }
            Text(
                "The volume comes back once the music has stopped, so the next song is not silent.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * What happens when the queue runs out, what a song picked on Home brings after it, and whether the queue is
 * there again next time: autoplay, its source and its manners, Home's songs, and keeping the queue.
 */
@Composable
internal fun AutoplayCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Glyph.ALL_INCLUSIVE.image, "Autoplay and the queue")
        Spacer(Modifier.height(10.dp))

        SettingAnchor(SettingIds.AUTOPLAY) {
            Toggle(
                "Keep playing when the queue ends",
                autoplayDetail(preferences.autoplayFrom),
                preferences.autoplay,
            ) { state.setAutoplay(it) }
        }

        // What autoplay plays and what it leaves out, which only mean something while it is on.
        SettingAnchor(SettingIds.AUTOPLAY_FROM) {
            Text(
                "Where autoplay's songs come from",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).alpha(if (preferences.autoplay) 1f else DISABLED_ALPHA),
            )
            Column(Modifier.selectableGroup()) {
                AutoplaySource.entries.forEach { source ->
                    ChoiceRadioRow(source.displayName, source.description, selected = preferences.autoplayFrom == source, enabled = preferences.autoplay) {
                        state.setAutoplayFrom(source)
                    }
                }
            }
        }
        SettingAnchor(SettingIds.AUTOPLAY_AVOID_RECENT) {
            Toggle(
                "Skip songs played lately",
                "Autoplay leaves out what you have heard lately, so it brings something new rather than the same " +
                    "songs again.",
                preferences.autoplayAvoidRecent,
                enabled = preferences.autoplay,
                onChange = state::setAutoplayAvoidRecent,
            )
        }

        // A song picked on Home: a radio from it, or the rest of its shelf -- and with autoplay off, always the shelf.
        SettingAnchor(SettingIds.HOME_PLAY) {
            Text("Playing a song from Home", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.selectableGroup()) {
                HomePlay.entries.forEach { choice ->
                    ChoiceRadioRow(choice.displayName, choice.description, selected = preferences.homePlay == choice, enabled = true) {
                        state.setHomePlay(choice)
                    }
                }
            }
            if (!preferences.autoplay && preferences.homePlay == HomePlay.AUTOPLAY) {
                Text(
                    "Autoplay is off, so the rest of the shelf plays after it until autoplay is back on.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        SettingAnchor(SettingIds.KEEP_QUEUE) {
            Toggle(
                "Keep the queue between launches",
                "Noctorium opens with the queue it closed with, on the song it was playing, and play carries on " +
                    "where it stopped. Off, the kept queue is forgotten.",
                preferences.keepQueue,
                onChange = state::setKeepQueue,
            )
        }
    }
}

/**
 * Which tabs sit along the bottom, and whether they are named.
 *
 * Only the four that can go are offered (see [HIDEABLE_TABS]); Home, Queue and Settings are named as
 * staying, so nobody goes looking for a switch that is not there. A hidden tab's page has not gone
 * anywhere, and whatever led to it before still does -- each switch says what that is.
 */
@Composable
internal fun TabsCard(settings: SettingsState, state: AppState) {
    val phone = settings.preferences.phone
    val hidden = phone.hiddenDestinations

    SettingsCardShell {
        CardHeading(Glyph.TAB.image, "Tabs along the bottom")
        Text(
            "Home, Queue and Settings always stay.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        SettingAnchor(SettingIds.TABS) {
            HIDEABLE_TABS.forEach { destination ->
                Toggle(
                    tabName(destination),
                    destination.stillReachedBy(),
                    destination !in hidden,
                ) { shown ->
                    state.updatePhone {
                        copy(hiddenDestinations = if (shown) hiddenDestinations - destination else hiddenDestinations + destination)
                    }
                }
            }
        }
        SettingAnchor(SettingIds.TAB_LABELS) {
            Toggle(
                "Labels under the tabs",
                "Off leaves the icons alone, which buys a little height.",
                phone.navigationLabels,
            ) { state.updatePhone { copy(navigationLabels = it) } }
        }
    }
}

/** The offered jumps. Small enough to catch a missed word, large enough to clear an intro. */
private val SEEK_STEPS = listOf(5, 10, 30)

/** One of a few choices, with what it means, as a radio button in a group: where autoplay takes its songs from, say. */
@Composable
private fun ChoiceRadioRow(title: String, description: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = choose)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        // The row is what is tapped; the button only shows which one is chosen.
        SkinnedRadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}


/** A labelled row of chips. Shared with the other settings cards, so every choice in Settings is the same shape. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OptionRow(label: String, chips: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/**
 * A switch with what it does written beside it, shared the same way. Not [enabled], it is drawn faded and
 * cannot be moved, and the detail is where to say why.
 */
@Composable
internal fun Toggle(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    if (LocalSkin.current != ThemeSkin.STANDARD) {
        // Windows' way round: the box first and what it does after it, and the words as good as the box to tap.
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
                .padding(vertical = 6.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 3.dp, end = 10.dp)) { Checkbox(checked, enabled) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        // A gap before the switch: without it a long line of detail ran right up to the switch's edge, and
        // the last word read as if it were printed on the switch.
        Spacer(Modifier.width(12.dp))
        SkinnedSwitch(checked, onChange, enabled = enabled)
    }
}

/** How faded a control that cannot be used right now is drawn, as Material draws its own. */
internal const val DISABLED_ALPHA = .38f
