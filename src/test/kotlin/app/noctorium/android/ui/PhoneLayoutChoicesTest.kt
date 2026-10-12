package app.noctorium.android.ui

import app.noctorium.core.Destination
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.PlayerButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which of the phone's player buttons and tabs are shown, given what the listener put away.
 *
 * The rules that matter are the ones about what cannot go: a bar with no way home or no way back to
 * Settings, or a sleep timer counting down with no button to stop it, would be a trap rather than a choice.
 */
class PhoneLayoutChoicesTest {

    @Test
    fun `every player button is offered on the phone`() {
        assertEquals(PlayerButton.entries.toSet(), PHONE_PLAYER_BUTTONS.toSet())
    }

    @Test
    fun `with nothing hidden the player shows every button it always had`() {
        PHONE_PLAYER_BUTTONS.filterNot { it.optIn }.forEach { assertTrue(showsPlayerButton(emptySet(), it), "$it should show") }
    }

    /** A bar somebody already has does not grow a button overnight: Stop waits to be asked for. */
    @Test
    fun `Stop is offered, not shown, until it is asked for`() {
        assertTrue(PlayerButton.STOP in PHONE_PLAYER_BUTTONS)
        assertFalse(showsPlayerButton(emptySet(), PlayerButton.STOP))
        assertTrue(showsPlayerButton(emptySet(), PlayerButton.STOP, shown = setOf(PlayerButton.STOP)))
    }

    @Test
    fun `the Toolbar bar, built around Stop, has it unless it is put away`() {
        assertTrue(showsPlayerButton(emptySet(), PlayerButton.STOP, partOfLayout = true))
        assertFalse(showsPlayerButton(setOf(PlayerButton.STOP), PlayerButton.STOP, partOfLayout = true))
    }

    @Test
    fun `switching Stop on and off again in Customization leaves the Toolbar's alone`() {
        val on = PhonePreferences().choosingPlayerButton(PlayerButton.STOP, shown = true)
        assertEquals(setOf(PlayerButton.STOP), on.shownPlayerButtons)
        val off = on.choosingPlayerButton(PlayerButton.STOP, shown = false)
        assertTrue(off.shownPlayerButtons.isEmpty())
        assertTrue(off.hiddenPlayerButtons.isEmpty())
        assertTrue(showsPlayerButton(off.hiddenPlayerButtons, PlayerButton.STOP, shown = off.shownPlayerButtons, partOfLayout = true))
        assertFalse(showsPlayerButton(off.hiddenPlayerButtons, PlayerButton.STOP, shown = off.shownPlayerButtons))
    }

    @Test
    fun `an ordinary button is put away and brought back as it always was`() {
        val off = PhonePreferences().choosingPlayerButton(PlayerButton.SHUFFLE, shown = false)
        assertEquals(setOf(PlayerButton.SHUFFLE), off.hiddenPlayerButtons)
        assertTrue(off.choosingPlayerButton(PlayerButton.SHUFFLE, shown = true).hiddenPlayerButtons.isEmpty())
    }

    @Test
    fun `a hidden button is not shown, and the rest are`() {
        val hidden = setOf(PlayerButton.SHUFFLE, PlayerButton.VOLUME)
        assertFalse(showsPlayerButton(hidden, PlayerButton.SHUFFLE))
        assertFalse(showsPlayerButton(hidden, PlayerButton.VOLUME))
        assertTrue(showsPlayerButton(hidden, PlayerButton.REPEAT))
        assertTrue(showsPlayerButton(hidden, PlayerButton.LIKE))
    }

    @Test
    fun `the queue's button on Now playing can be put away like the others`() {
        assertTrue(showsPlayerButton(emptySet(), PlayerButton.QUEUE))
        assertFalse(showsPlayerButton(setOf(PlayerButton.QUEUE), PlayerButton.QUEUE))
    }

    /** A sleep timer about to stop the music, or music sent to another device, can always be reached. */
    @Test
    fun `a hidden button comes back while the thing it controls is in use`() {
        val hidden = setOf(PlayerButton.SLEEP_TIMER, PlayerButton.DEVICES)
        assertFalse(showsPlayerButton(hidden, PlayerButton.SLEEP_TIMER, inUse = false))
        assertTrue(showsPlayerButton(hidden, PlayerButton.SLEEP_TIMER, inUse = true))
        assertTrue(showsPlayerButton(hidden, PlayerButton.DEVICES, inUse = true))
    }

    @Test
    fun `with nothing hidden the bar has the seven tabs it always had and Connect, in their order`() {
        assertEquals(
            listOf(
                Destination.HOME,
                Destination.SEARCH,
                Destination.LINK,
                Destination.LIBRARY,
                Destination.DOWNLOADS,
                Destination.QUEUE,
                Destination.CONNECT,
                Destination.SETTINGS,
            ),
            visibleTabs(emptySet()),
        )
    }

    /** Somebody who put Connect away gets back exactly the bar they had before it came. */
    @Test
    fun `with Connect hidden the bar is the seven tabs it was`() {
        assertEquals(
            listOf(
                Destination.HOME,
                Destination.SEARCH,
                Destination.LINK,
                Destination.LIBRARY,
                Destination.DOWNLOADS,
                Destination.QUEUE,
                Destination.SETTINGS,
            ),
            visibleTabs(setOf(Destination.CONNECT)),
        )
    }

    @Test
    fun `hidden tabs come off and the rest keep their order`() {
        assertEquals(
            listOf(Destination.HOME, Destination.LINK, Destination.DOWNLOADS, Destination.QUEUE, Destination.CONNECT, Destination.SETTINGS),
            visibleTabs(setOf(Destination.SEARCH, Destination.LIBRARY)),
        )
    }

    @Test
    fun `Home, Queue and Settings stay even when a settings file names them`() {
        val tabs = visibleTabs(Destination.entries.toSet())
        assertEquals(listOf(Destination.HOME, Destination.QUEUE, Destination.SETTINGS), tabs)
    }

    /** Every combination there is, so no file can leave a bar of one tab or none. */
    @Test
    fun `whatever is hidden, at least two tabs remain, Home and Settings among them`() {
        val all = Destination.entries
        for (mask in 0 until (1 shl all.size)) {
            val hidden = all.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val tabs = visibleTabs(hidden)
            assertTrue(tabs.size >= 2, "hiding $hidden left $tabs")
            assertTrue(Destination.HOME in tabs && Destination.SETTINGS in tabs, "hiding $hidden left $tabs")
        }
    }

    @Test
    fun `only Search, Link, Library, Downloads and Connect can be hidden`() {
        assertEquals(
            setOf(Destination.SEARCH, Destination.LINK, Destination.LIBRARY, Destination.DOWNLOADS, Destination.CONNECT),
            HIDEABLE_TABS.toSet(),
        )
        HIDEABLE_TABS.forEach { assertTrue(it.stillReachedBy().isNotBlank(), "$it should say where it is still reached from") }
    }

    @Test
    fun `every tab has a name of its own`() {
        val names = PHONE_TAB_ORDER.map(::tabName)
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }
}
