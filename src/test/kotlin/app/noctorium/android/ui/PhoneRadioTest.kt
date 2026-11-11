package app.noctorium.android.ui

import app.noctorium.android.PlainHttpDataSource
import app.noctorium.core.AppUiState
import app.noctorium.radio.RadioCountry
import app.noctorium.settings.TimeDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneRadioTest {
    private val countries = listOf(
        RadioCountry("DE", "Germany", 7000),
        RadioCountry("GB", "The United Kingdom Of Great Britain And Northern Ireland", 2500),
        RadioCountry("RO", "Romania", 900),
    )

    @Test
    fun `countries are found by part of a name or by their code`() {
        assertEquals(listOf("RO"), filterRadioCountries(countries, "roman").map { it.code })
        assertEquals(listOf("GB"), filterRadioCountries(countries, "gb").map { it.code })
        assertEquals(countries, filterRadioCountries(countries, "  "))
        assertEquals(emptyList(), filterRadioCountries(countries, "atlantis"))
    }

    @Test
    fun `no country is everywhere, and an unknown code is shown as it is`() {
        assertEquals("Everywhere", radioCountryName(null, countries))
        assertEquals("Germany", radioCountryName("DE", countries))
        assertEquals("XK", radioCountryName("XK", countries))
    }

    @Test
    fun `the station list says what it is`() {
        assertEquals("Most listened-to around the world", radioHeading(AppUiState()))
        assertEquals("Most listened-to in Romania", radioHeading(AppUiState(radioCountry = "RO", radioCountries = countries)))
        assertEquals("Stations for “jazz”", radioHeading(AppUiState(searchQuery = " jazz ")))
    }

    @Test
    fun `a station shows LIVE and how long it has been on, not a length`() {
        assertEquals("LIVE · 1:05", toolbarTime(65_000, 0, TimeDisplay.TOTAL, live = true))
        assertEquals("1:05 / --:--", toolbarTime(65_000, 0, TimeDisplay.TOTAL))
    }

    @Test
    fun `a station's header lines are read without their line endings`() {
        val answer = "ICY 200 OK\r\nicy-name: Test\nContent-Type: audio/mpeg\r\n\r\nbody".byteInputStream()
        assertEquals("ICY 200 OK", PlainHttpDataSource.readLine(answer))
        assertEquals("icy-name: Test", PlainHttpDataSource.readLine(answer))
        assertEquals("Content-Type: audio/mpeg", PlainHttpDataSource.readLine(answer))
        assertEquals("", PlainHttpDataSource.readLine(answer))
        assertEquals("body", PlainHttpDataSource.readLine(answer))
        assertNull(PlainHttpDataSource.readLine(answer))
    }
}
