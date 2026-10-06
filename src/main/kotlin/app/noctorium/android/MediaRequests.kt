package app.noctorium.android

import java.net.URI

/**
 * Whether [host] is one of VK's media hosts -- `*.vkuseraudio.ru`, `.net` or `.com` -- which serve a song's
 * audio only to the browser its address was issued to.
 */
internal fun isVkMediaHost(host: String?): Boolean {
    val name = host?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty) ?: return false
    return VK_MEDIA_DOMAINS.any { name == it || name.endsWith(".$it") }
}

private val VK_MEDIA_DOMAINS = listOf("vkuseraudio.ru", "vkuseraudio.net", "vkuseraudio.com")

/**
 * What a request the player makes to [host] adds to its own headers: VK's browser agent, for VK's media hosts
 * -- the playlist, its segments and its keys alike -- and nothing anywhere else, so every other service is
 * asked exactly as it always has been.
 */
internal fun mediaRequestHeaders(host: String?, vkAgent: String?): Map<String, String> =
    if (!vkAgent.isNullOrBlank() && isVkMediaHost(host)) mapOf("User-Agent" to vkAgent) else emptyMap()

/** The host of an address, or null for something that is not one. */
internal fun hostOf(address: String): String? = runCatching { URI(address).host }.getOrNull()
