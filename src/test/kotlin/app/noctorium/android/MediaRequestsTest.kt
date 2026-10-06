package app.noctorium.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which of the player's requests are made as VK's browser: VK's media hosts, and nothing else. */
class MediaRequestsTest {

    private val agent = "Made-up Browser/1.0"

    @Test
    fun `VK's media hosts are matched on all three domains, at any depth`() {
        listOf(
            "cs1-23v4.vkuseraudio.net",
            "psv4.vkuseraudio.ru",
            "a.b.vkuseraudio.com",
            "vkuseraudio.net",
            // Hosts are not case-sensitive, and a fully qualified one ends in a dot.
            "CS9-1V4.VKUSERAUDIO.NET",
            "cs1.vkuseraudio.ru.",
        ).forEach { assertTrue(isVkMediaHost(it), it) }
    }

    @Test
    fun `nothing that only looks like one is matched`() {
        listOf(
            "vkuseraudio.net.example.invalid",
            "notvkuseraudio.net",
            "vkuseraudio.org",
            "vk.ru",
            "rr3---sn-made-up.googlevideo.com",
            "cf-media.sndcdn.com",
            "t4.bcbits.com",
            "",
            null,
        ).forEach { assertFalse(isVkMediaHost(it), it.toString()) }
    }

    @Test
    fun `VK's agent goes to VK's media hosts and nowhere else`() {
        assertEquals(mapOf("User-Agent" to agent), mediaRequestHeaders("cs1-23v4.vkuseraudio.net", agent))
        assertEquals(emptyMap(), mediaRequestHeaders("rr3---sn-made-up.googlevideo.com", agent))
        assertEquals(emptyMap(), mediaRequestHeaders("cf-media.sndcdn.com", agent))
        assertEquals(emptyMap(), mediaRequestHeaders(null, agent))
    }

    @Test
    fun `without an agent yet, nothing is added`() {
        assertEquals(emptyMap(), mediaRequestHeaders("cs1-23v4.vkuseraudio.net", null))
        assertEquals(emptyMap(), mediaRequestHeaders("cs1-23v4.vkuseraudio.net", " "))
    }

    @Test
    fun `an address's host is read, and a broken one has none`() {
        assertEquals(
            "cs1-23v4.vkuseraudio.net",
            hostOf("https://cs1-23v4.vkuseraudio.net/s/v1/ac/made-up/index.m3u8?siren=1"),
        )
        assertEquals(null, hostOf("not an address"))
    }
}
