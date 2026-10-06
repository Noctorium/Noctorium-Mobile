package app.noctorium.android

import app.noctorium.auth.SOUNDCLOUD_SESSION_URLS
import app.noctorium.auth.YOUTUBE_SESSION_URLS
import app.noctorium.domain.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which cookies each sign-in clears: its own service's, so that signing in to one never signs the listener
 * out of another.
 */
class SignInCookieHostsTest {

    private val signedInHere = listOf(ProviderType.YOUTUBE_MUSIC, ProviderType.SOUNDCLOUD, ProviderType.VK)

    @Test
    fun `each service clears every host its session is read from`() {
        YOUTUBE_SESSION_URLS.forEach { url ->
            assertTrue(url.substringAfter("://").trimEnd('/') in cookieHostsFor(ProviderType.YOUTUBE_MUSIC), url)
        }
        SOUNDCLOUD_SESSION_URLS.forEach { url ->
            assertTrue(url.substringAfter("://").trimEnd('/') in cookieHostsFor(ProviderType.SOUNDCLOUD), url)
        }
        assertTrue("login.vk.ru" in cookieHostsFor(ProviderType.VK))
        assertTrue("m.vk.ru" in cookieHostsFor(ProviderType.VK))
        assertEquals(cookieHostsFor(ProviderType.YOUTUBE_MUSIC), cookieHostsFor(ProviderType.YOUTUBE_VIDEO))
    }

    @Test
    fun `no two services share a site, so clearing one leaves the others alone`() {
        val sites = signedInHere.associateWith { provider -> cookieHostsFor(provider).map(::registrableDomain).toSet() }
        signedInHere.forEach { one ->
            signedInHere.filter { it != one }.forEach { other ->
                assertEquals(emptySet(), sites.getValue(one) intersect sites.getValue(other), "$one and $other")
            }
        }
        assertEquals(setOf("google.com", "youtube.com"), sites[ProviderType.YOUTUBE_MUSIC])
        assertEquals(setOf("soundcloud.com"), sites[ProviderType.SOUNDCLOUD])
        assertEquals(setOf("vk.ru", "vk.com"), sites[ProviderType.VK])
    }

    @Test
    fun `services without a sign-in page clear nothing`() {
        listOf(ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.LOCAL).forEach {
            assertEquals(emptyList(), cookieHostsFor(it), it.name)
        }
    }

    @Test
    fun `a host's site is its last two labels`() {
        assertEquals("soundcloud.com", registrableDomain("api-v2.soundcloud.com"))
        assertEquals("google.com", registrableDomain("accounts.google.com"))
        assertEquals("vk.ru", registrableDomain("vk.ru"))
    }

    @Test
    fun `a cookie is ended in every place it might be held, and securely`() {
        assertEquals(
            listOf(
                "SID=; Max-Age=0; Path=/; Secure",
                "SID=; Max-Age=0; Path=/; Secure; Domain=accounts.google.com",
                "SID=; Max-Age=0; Path=/; Secure; Domain=google.com",
            ),
            expiredCookies("SID", "accounts.google.com"),
        )
        // On the site itself the host and the site are one place, and it is said once.
        assertEquals(2, expiredCookies("remixsid", "vk.ru").size)
        // A __Secure- cookie is ended like any other, as long as the line is Secure.
        assertTrue(expiredCookies("__Secure-3PAPISID", "google.com").all { it.contains("; Secure") })
    }

    @Test
    fun `a __Host- cookie is ended only as the host's own, the one way Chromium accepts`() {
        assertEquals(listOf("__Host-GAPS=; Max-Age=0; Path=/; Secure"), expiredCookies("__Host-GAPS", "accounts.google.com"))
    }
}
