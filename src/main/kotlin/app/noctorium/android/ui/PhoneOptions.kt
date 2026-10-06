package app.noctorium.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.playback.MAX_SPEED
import app.noctorium.playback.MIN_SPEED
import app.noctorium.settings.DataSaver
import app.noctorium.settings.SettingsState
import kotlin.math.roundToInt

/**
 * The settings that only exist because this is a phone.
 *
 * Separate from Customization, which is the desktop's own list carried across. Everything here is about
 * something a window has not got: a touch screen, a battery, a metered connection, a screen that turns
 * itself off. None of it would mean anything on a desktop and none of it is offered there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneOptionsCard(settings: SettingsState, state: AppState) {
    val phone = settings.preferences.phone

    SettingsCardShell {
        CardHeading(Icons.Default.PhoneAndroid, "On this phone")
        Spacer(Modifier.height(10.dp))

        Toggle(
            "Labels under the tabs",
            "Off leaves the icons alone, which buys a little height.",
            phone.navigationLabels,
        ) { state.updatePhone { copy(navigationLabels = it) } }

        Toggle(
            "Keep the screen on",
            "While something is playing. Useful on a desk, hard on a battery everywhere else.",
            phone.keepScreenOn,
        ) { state.updatePhone { copy(keepScreenOn = it) } }

        Toggle(
            "Download on Wi-Fi only",
            "Refuses downloads on mobile data, so an album cannot quietly spend your allowance.",
            phone.downloadOnWifiOnly,
        ) { state.updatePhone { copy(downloadOnWifiOnly = it) } }

        OptionRow("Data saver") {
            DataSaver.entries.forEach { saver ->
                FilterChip(
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

        Toggle(
            "Haptics",
            "A short tick when a control does something.",
            phone.haptics,
        ) { state.updatePhone { copy(haptics = it) } }
    }
}

/**
 * How the music itself plays, and the gestures that move through it.
 *
 * The speed is Noctorium's own setting now rather than the phone's, which is what lets the player be told
 * of it the way it is told of the equaliser. It is a slider, since anything from half to double can be set
 * and a row of chips can only offer some of it; nothing is saved until the finger lifts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackOptionsCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    val phone = preferences.phone
    var heldSpeed by remember { mutableStateOf<Float?>(null) }
    val speed = heldSpeed ?: preferences.playbackSpeed

    SettingsCardShell {
        CardHeading(Icons.Default.GraphicEq, "Playback")
        Spacer(Modifier.height(10.dp))

        Toggle(
            "Swipe the player bar",
            "Sideways to move through the queue, without opening the full screen.",
            phone.swipeToChangeTrack,
        ) { state.updatePhone { copy(swipeToChangeTrack = it) } }

        OptionRow("Double tap the cover jumps") {
            SEEK_STEPS.forEach { seconds ->
                FilterChip(
                    selected = phone.seekStepSeconds == seconds,
                    onClick = { state.updatePhone { copy(seekStepSeconds = seconds) } },
                    label = { Text("${seconds}s", fontSize = 11.sp) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                speedLabel(speed),
                color = if (speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
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
                TextButton({ state.setPlaybackSpeed(1f) }) { Text("Normal") }
            }
        }
        Spacer(Modifier.height(6.dp))

        Toggle(
            "Skip silence",
            "Shortens gaps and long quiet intros. Leaves the music alone.",
            phone.skipSilence,
        ) { state.updatePhone { copy(skipSilence = it) } }

        Toggle(
            "Skip the parts of a YouTube video that are not the music",
            "Intros, outros, sponsor reads and talking, as marked by SponsorBlock's contributors. YouTube " +
                "Music tracks are never touched.",
            preferences.skipNonMusic,
            onChange = state::setSkipNonMusic,
        )

        Toggle(
            "Keep playing when the queue ends",
            "Songs like the last one follow it: Bandcamp and VK suggest their own, and YouTube Music's radio " +
                "answers for everything else.",
            preferences.autoplay,
        ) { state.setAutoplay(it) }

        OptionRow("A sleep timer fades out over") {
            sleepFadeChoices(preferences.sleepFadeSeconds).forEach { seconds ->
                FilterChip(
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

/**
 * Which tabs sit along the bottom.
 *
 * Only the four that can go are offered (see [HIDEABLE_TABS]); Home, Queue and Settings are named as
 * staying, so nobody goes looking for a switch that is not there. A hidden tab's page has not gone
 * anywhere, and whatever led to it before still does -- each switch says what that is.
 */
@Composable
internal fun TabsCard(settings: SettingsState, state: AppState) {
    val hidden = settings.preferences.phone.hiddenDestinations

    SettingsCardShell {
        CardHeading(Icons.Default.Tab, "Tabs along the bottom")
        Text(
            "Home, Queue and Settings always stay.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
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
}

/** The offered jumps. Small enough to catch a missed word, large enough to clear an intro. */
private val SEEK_STEPS = listOf(5, 10, 30)


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
        Switch(checked, onChange, enabled = enabled)
    }
}

/** How faded a control that cannot be used right now is drawn, as Material draws its own. */
internal const val DISABLED_ALPHA = .38f
