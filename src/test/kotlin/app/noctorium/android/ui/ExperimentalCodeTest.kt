package app.noctorium.android.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExperimentalCodeTest {
    @Test
    fun `a code is found however it is typed, and nothing else is one`() {
        assertEquals(ExperimentalCode.CRASH, ExperimentalCode.find(" Crash "))
        assertEquals(ExperimentalCode.SHADOW, ExperimentalCode.find("sh@d0w"))
        assertEquals(ExperimentalCode.SHADOW, ExperimentalCode.find("SH@D0W "))
        assertNull(ExperimentalCode.find("shadow"))
        assertNull(ExperimentalCode.find(""))
    }
}
