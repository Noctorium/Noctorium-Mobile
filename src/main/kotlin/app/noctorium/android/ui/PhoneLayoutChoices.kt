package app.noctorium.android.ui

import app.noctorium.core.Destination
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerButton

/*
 * Which of the phone's buttons and tabs the listener has put away, worked out apart from the screens that
 * draw them, so the rules -- what can never be hidden, what comes back when it is in use -- are written once
 * and can be checked without a screen.
 */

/**
 * The player buttons this phone has, in the order Settings lists them.
 *
 * Every [PlayerButton]. The queue's opens it over Now playing, beside the lyrics: it is a tab along the
 * bottom too, but from the song's own screen that tab is two steps away and the song is lost on the way.
 * Play, pause, next and previous are not PlayerButtons at all, and always stay. Stop is offered rather than
 * shown ([PlayerButton.optIn]): it is there once asked for, and on the Toolbar bar, which is built around it.
 */
internal val PHONE_PLAYER_BUTTONS: List<PlayerButton> = listOf(
    PlayerButton.SHUFFLE,
    PlayerButton.REPEAT,
    PlayerButton.LIKE,
    PlayerButton.LYRICS,
    PlayerButton.QUEUE,
    PlayerButton.SLEEP_TIMER,
    PlayerButton.DEVICES,
    PlayerButton.VOLUME,
    PlayerButton.SPEED,
    PlayerButton.STOP,
)

/**
 * Whether the phone's player shows [button].
 *
 * [inUse] brings a hidden button back while the thing it controls is doing something: a sleep timer that
 * is counting down, or music sent to another device. Hiding the button was a wish not to see it, not a
 * wish to be unable to stop a timer that is about to silence the music, or to fetch the music back.
 *
 * An opt-in button ([PlayerButton.optIn], Stop) shows only once it is among the [shown] ones asked for, except
 * on a bar built around it ([partOfLayout]), where it is there as any other button is; see [PlayerButton.isShown].
 */
internal fun showsPlayerButton(
    hidden: Set<PlayerButton>,
    button: PlayerButton,
    inUse: Boolean = false,
    shown: Set<PlayerButton> = emptySet(),
    partOfLayout: Boolean = false,
): Boolean = button in PHONE_PLAYER_BUTTONS && (inUse || button.isShown(hidden, shown, partOfLayout))

/**
 * The phone's player with [button] switched on or off from Customization.
 *
 * An ordinary button is put away or brought back. An opt-in one is asked for or not, and nothing more: switching
 * Stop off again takes it off the full screen and leaves it on the Toolbar bar, which is built around it, rather
 * than putting it away there as well -- a bar of play, pause and next with a gap where stop was is not a toolbar.
 */
internal fun PhonePreferences.choosingPlayerButton(button: PlayerButton, shown: Boolean): PhonePreferences = if (button.optIn) {
    copy(
        shownPlayerButtons = if (shown) shownPlayerButtons + button else shownPlayerButtons - button,
        hiddenPlayerButtons = hiddenPlayerButtons - button,
    )
} else {
    copy(hiddenPlayerButtons = if (shown) hiddenPlayerButtons - button else hiddenPlayerButtons + button)
}

/** The tabs along the bottom, in the order they sit. */
internal val PHONE_TAB_ORDER: List<Destination> = listOf(
    Destination.HOME,
    Destination.SEARCH,
    Destination.LINK,
    Destination.LIBRARY,
    Destination.DOWNLOADS,
    Destination.QUEUE,
    Destination.CONNECT,
    Destination.SETTINGS,
)

/**
 * The tabs a listener may take off the bar.
 *
 * Not Home, which is where back leads from everywhere. Not Settings, which is the only way to put a tab
 * back. And not Queue, which on a phone is the only way to the queue: there is no queue button on the
 * player here, so hiding the tab would leave the queue with no door at all. Connect can go: somebody with
 * one device has no use for it, and the devices button and the player bar's strip still lead there.
 */
internal val HIDEABLE_TABS: List<Destination> = listOf(
    Destination.SEARCH,
    Destination.LINK,
    Destination.LIBRARY,
    Destination.DOWNLOADS,
    Destination.CONNECT,
)

/**
 * The tabs left on the bar once the hidden ones are taken off, in their usual order.
 *
 * Anything that is not a hideable tab stays whatever the settings file says, so a hand-edited file that
 * names Home or Settings cannot leave the bar with no way home or no way back to this choice. Home, Queue
 * and Settings always remain, which is never fewer than the two a bar needs to be a bar.
 */
internal fun visibleTabs(hidden: Set<Destination>): List<Destination> =
    PHONE_TAB_ORDER.filter { it !in HIDEABLE_TABS || it !in hidden }

/** What a tab is called under its icon, and beside its switch in Settings. */
internal fun tabName(destination: Destination): String = when (destination) {
    Destination.HOME, Destination.NOW_PLAYING -> "Home"
    Destination.SEARCH -> "Search"
    Destination.LINK -> "Link"
    Destination.LIBRARY -> "Library"
    Destination.DOWNLOADS -> "Downloads"
    Destination.QUEUE -> "Queue"
    Destination.CONNECT -> "Connect"
    Destination.SETTINGS -> "Settings"
}

/** Somewhere a hidden tab is still reached from, said in Settings beside the switch that hides it. */
internal fun Destination.stillReachedBy(): String = when (this) {
    Destination.SEARCH -> "Searching happens only here, so with this tab hidden there is nowhere to search from."
    Destination.LINK -> "A link shared to Noctorium from another app still opens it."
    Destination.LIBRARY -> "A playlist opened from Home still opens there."
    Destination.DOWNLOADS -> "Downloaded tracks still play with no connection wherever they turn up; this tab is the list of them."
    Destination.CONNECT ->
        "The devices button on Now playing still leads to it, and so does the player bar while the music is on another device."
    else -> ""
}
