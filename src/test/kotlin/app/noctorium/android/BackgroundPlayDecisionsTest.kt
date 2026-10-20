package app.noctorium.android

import app.noctorium.android.ui.interruptionWords
import app.noctorium.android.ui.worthSaying
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a loss of the music's foreground in the background is put down to, what is said about it, and the maker's page for it. */
class BackgroundPlayDecisionsTest {

    @Test
    fun `a loss is put down to the restriction whenever there is one`() {
        for (refused in listOf(true, false)) {
            for (regained in listOf(true, false)) {
                assertEquals(Interruption.RESTRICTED, interruptionFor(restricted = true, refused = refused, regained = regained))
            }
        }
        assertEquals(Interruption.RESTRICTED, interruptionFor(restricted = true, refused = false, regained = false, stopped = true))
    }

    @Test
    fun `otherwise to Android stopping the service, refusing, or taking it and giving it back`() {
        assertEquals(Interruption.STOPPED, interruptionFor(restricted = false, refused = false, regained = false, stopped = true))
        assertEquals(Interruption.REFUSED, interruptionFor(restricted = false, refused = true, regained = false))
        assertEquals(Interruption.REFUSED, interruptionFor(restricted = false, refused = false, regained = false), "not had back, for whatever reason")
        assertEquals(Interruption.TAKEN, interruptionFor(restricted = false, refused = false, regained = true))
    }

    @Test
    fun `taken and given back is counted but not explained, since the music played on`() {
        val taken = Interrupted(Interruption.TAKEN, 1)
        val stopped = Interrupted(Interruption.STOPPED, 2)
        assertEquals(listOf(stopped), worthSaying(listOf(taken, stopped)))
        assertTrue(worthSaying(listOf(taken)).isEmpty())
    }

    @Test
    fun `the explanation names the restriction and its switch when it was behind any of it`() {
        val restricted = interruptionWords(listOf(Interrupted(Interruption.REFUSED, 1), Interrupted(Interruption.RESTRICTED, 2)))
        assertTrue("restricts it there" in restricted, restricted)
        assertTrue("Allow background activity" in restricted, restricted)
        assertTrue(" twice" in restricted, restricted)
        val refused = interruptionWords(listOf(Interrupted(Interruption.REFUSED, 1)))
        assertTrue("out of the foreground" in refused, refused)
        assertFalse("restricts" in refused, refused)
        assertFalse("once" in refused, "a single time is not counted out loud: $refused")
    }

    @Test
    fun `on OnePlus, Oppo and realme the checklist has Allow background activity, on Noctorium's battery usage page`() {
        for (maker in listOf(PhoneMaker.ONEPLUS, PhoneMaker.OPPO, PhoneMaker.REALME)) {
            val page = maker.pages.single { it.title.startsWith("Allow background activity") }
            assertEquals(MakerPageKind.BATTERY, page.kind, maker.displayName)
            val target = page.targets.single()
            assertEquals(ACTION_POWER_USAGE_DETAIL, target.action)
            assertTrue(target.packageData, "opened on Noctorium's own page")
            assertEquals(null, target.packageName, "whichever settings app answers it")
            assertEquals(page, maker.page(MakerPageKind.BATTERY), "and it is the maker's battery page")
        }
        assertTrue(PhoneMaker.XIAOMI.pages.none { it.title.startsWith("Allow background activity") })
    }
}
