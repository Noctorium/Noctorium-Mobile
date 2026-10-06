package app.noctorium.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading VK's session out of what a WebView says it holds for each host: `p` from the host that signs
 * people in, `remixsid` from the site. Every value here is made up.
 */
class VkSignInCookiesTest {

    private val login = "remixlang=0; p=made-up-p-value; tmr_lvid=abc"
    private val site = "remixstid=12345; remixsid=made-up-session; remixlang=0"

    @Test
    fun `both cookies, from their two hosts, make the session`() {
        assertEquals("p=made-up-p-value; remixsid=made-up-session", vkSessionFrom(login, listOf(site)))
    }

    @Test
    fun `one without the other is no session at all`() {
        assertNull(vkSessionFrom(login, listOf("remixlang=0")))
        assertNull(vkSessionFrom("remixlang=0", listOf(site)))
        assertNull(vkSessionFrom(null, listOf(null)))
        // p on the site's host is not the p that signs people in.
        assertNull(vkSessionFrom(null, listOf("p=wrong-host; remixsid=made-up-session")))
    }

    @Test
    fun `remixsid is taken from the mobile site when the main one has none`() {
        assertEquals(
            "p=made-up-p-value; remixsid=from-mobile",
            vkSessionFrom(login, listOf("remixlang=0", "remixsid=from-mobile")),
        )
    }

    @Test
    fun `a cookie is read by its whole name, and an empty one is no cookie`() {
        assertEquals("made-up-session", cookieValue(site, "remixsid"))
        assertNull(cookieValue("remixsid2=nope", "remixsid"))
        assertNull(cookieValue("remixsid=", "remixsid"))
        assertEquals("a=b", cookieValue("token=a=b", "token"), "only the first = divides a name from its value")
        assertNull(cookieValue(null, "p"))
    }

    @Test
    fun `the names a host holds are listed once each, for clearing them`() {
        assertEquals(listOf("remixstid", "remixsid", "remixlang"), cookieNames(site))
        assertEquals(listOf("a"), cookieNames("a=1; a=2; =orphan"))
        assertEquals(emptyList(), cookieNames(null))
    }
}
