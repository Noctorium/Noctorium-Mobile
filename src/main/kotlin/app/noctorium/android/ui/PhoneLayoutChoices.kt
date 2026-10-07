package app.noctorium.android.ui

import app.noctorium.core.Destination
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
 * Play, pause, next and previous are not PlayerButtons at all, and always stay.
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
)

/**
 * Whether the phone's player shows [button].
 *
 * [inUse] brings a hidden button back while the thing it controls is doing something: a sleep timer that
 * is counting down, or music sent to another device. Hiding the button was a wish not to see it, not a
 * wish to be unable to stop a timer that is about to silence the music, or to fetch the music back.
 */
internal fun showsPlayerButton(hidden: Set<PlayerButton>, button: PlayerButton, inUse: Boolean = false): Boolean =
    button in PHONE_PLAYER_BUTTONS && (inUse || button !in hidden)

/** The tabs along the bottom, in the order they sit. */
internal val PHONE_TAB_ORDER: List<Destination> = listOf(
    Destination.HOME,
    Destination.SEARCH,
    Destination.LINK,
    Destination.LIBRARY,
    Destination.DOWNLOADS,
    Destination.QUEUE,
    Destination.SETTINGS,
)

/**
 * The tabs a listener may take off the bar.
 *
 * Not Home, which is where back leads from everywhere. Not Settings, which is the only way to put a tab
 * back. And not Queue, which on a phone is the only way to the queue: there is no queue button on the
 * player here, so hiding the tab would leave the queue with no door at all.
 */
internal val HIDEABLE_TABS: List<Destination> = listOf(
    Destination.SEARCH,
    Destination.LINK,
    Destination.LIBRARY,
    Destination.DOWNLOADS,
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
    Destination.SETTINGS -> "Settings"
}

/** Somewhere a hidden tab is still reached from, said in Settings beside the switch that hides it. */
internal fun Destination.stillReachedBy(): String = when (this) {
    Destination.SEARCH -> "Searching happens only here, so with this tab hidden there is nowhere to search from."
    Destination.LINK -> "A link shared to Noctorium from another app still opens it."
    Destination.LIBRARY -> "A playlist opened from Home still opens there."
    Destination.DOWNLOADS -> "Downloaded tracks still play with no connection wherever they turn up; this tab is the list of them."
    else -> ""
}
