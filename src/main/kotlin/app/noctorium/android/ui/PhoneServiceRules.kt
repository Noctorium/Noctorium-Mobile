package app.noctorium.android.ui

import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.bandcamp.BandcampMusicProvider
import app.noctorium.core.SearchMode
import app.noctorium.domain.MusicLink
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.settings.BandcampConnectionState
import app.noctorium.settings.DEFAULT_HYBRID_SEARCH
import app.noctorium.settings.HomePart
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.settings.VkConnectionState
import app.noctorium.spotify.SpotifyDevice
import java.util.Locale
import kotlin.math.abs

/*
 * What each service lets the phone do with its music, worked out apart from the screens that offer it, so
 * the rules -- which account a playlist can be written to, what may be kept, which page a song opens on --
 * are written once and can be checked without a screen.
 *
 * Most of it is about not sending one service's music to another. Several of the screens below were
 * written when there were two services and decided "SoundCloud, or else YouTube"; Bandcamp, arriving as a
 * third, would have fallen into the YouTube half of every one of them.
 */

/** The providers whose tracks a YouTube Music playlist will accept. */
internal val YOUTUBE_PROVIDERS = setOf(ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO)

/**
 * YouTube's own lists, which cannot be added to.
 *
 * Liked Music fills itself from the heart, Episodes for Later from podcasts. Offering them here would be
 * offering something that quietly does nothing.
 */
internal val YOUTUBE_SYSTEM_PLAYLISTS = setOf("LM", "SE", "HL", "WL", "LL")

/**
 * Whether [provider] signs in on a page of its own inside Noctorium: YouTube's, which plain YouTube shares,
 * SoundCloud's and VK's.
 *
 * The sign-in screen knows those three by name. One it does not know would fall through to Google's page
 * and be saved as YouTube's session, so nothing else is let in: Bandcamp needs a name rather than a
 * sign-in, and Spotify signs in through the browser.
 */
internal fun hasSignInPage(provider: ProviderType): Boolean = when (provider) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO, ProviderType.SOUNDCLOUD, ProviderType.VK -> true
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.LOCAL -> false
}

/**
 * The account a playlist holding [track] would live on, or null where no account here can take it.
 *
 * A YouTube video goes into a YouTube Music playlist, which is the one YouTube account there is. Spotify
 * and VK playlists are read here and not written to, a file belongs to no account, and Bandcamp's lists are
 * a fan's collection and wishlist, which are bought into rather than added to.
 */
internal fun playlistAccountFor(track: Track): ProviderType? = when (track.provider) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO -> ProviderType.YOUTUBE_MUSIC
    ProviderType.SOUNDCLOUD -> ProviderType.SOUNDCLOUD
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.LOCAL -> null
}

/**
 * The listener's playlists [track] can be added to: the ones on its own service that are lists of their own
 * rather than the service's views of something -- YouTube's Liked Music, SoundCloud's likes.
 *
 * None at all for a track no account here can take. A Bandcamp song would otherwise be offered its own
 * collection, which only ever answers that it is read-only.
 */
internal fun playlistsToAddTo(track: Track, playlists: List<Playlist>): List<Playlist> {
    if (playlistAccountFor(track) == null) return emptyList()
    return playlists.filter {
        it.provider == track.provider &&
            (track.provider != ProviderType.YOUTUBE_MUSIC || it.id !in YOUTUBE_SYSTEM_PLAYLISTS) &&
            (track.provider != ProviderType.SOUNDCLOUD || it.id != "likes")
    }
}

/**
 * Out of [tracks], the ones a playlist on [service] can hold: its own service's, and nothing else.
 *
 * Copying a playlist made here up to an account sends only these. Left to itself, the copy to YouTube
 * keeps everything that is not SoundCloud's -- which would hand YouTube a Bandcamp or Spotify song's id as
 * if it were a video's.
 */
internal fun tracksPlaylistCanHold(service: ProviderType, tracks: List<Track>): List<Track> = when (service) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO -> tracks.filter { it.provider in YOUTUBE_PROVIDERS }
    ProviderType.SOUNDCLOUD -> tracks.filter { it.provider == ProviderType.SOUNDCLOUD }
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.LOCAL -> emptyList()
}

/**
 * How many of [tracks] a "download all" would fetch, or null when it should not be offered at all.
 *
 * Null when not one of them may be kept, which is a Bandcamp album or a VK playlist: a button that can only
 * explain why it does nothing is worse than no button. Otherwise the ones that may be kept and are not yet,
 * so a list with such a song in it never counts a song the download will not fetch. [canKeep] is core's
 * word on that, and [kept] whether a track is on the phone already.
 */
internal fun downloadsWanted(tracks: List<Track>, canKeep: (Track) -> Boolean, kept: (Track) -> Boolean): Int? {
    val keepable = tracks.filter(canKeep)
    if (keepable.isEmpty()) return null
    return keepable.count { !kept(it) }
}

/**
 * Why a song from [provider] is not downloaded, or null where it can be.
 *
 * Core keeps neither Bandcamp's songs nor VK's: what Bandcamp streams is for listening and the file is the
 * artist's to sell, and VK licenses its music for playing, not keeping. Said in the place a download was
 * asked for, rather than the download being taken and nothing coming of it.
 */
internal fun keepRefusal(provider: ProviderType): String? = when (provider) {
    ProviderType.BANDCAMP ->
        "Bandcamp streams are for listening, so its songs are not downloaded. Buy one on its Bandcamp page and the file is yours."
    ProviderType.VK -> "VK songs play in Noctorium but are not downloaded: VK licenses its music for playing, not keeping."
    else -> null
}

/** Whether a pasted link can be downloaded: one that is a link at all, to a service whose songs may be kept. */
internal fun downloadsFrom(link: MusicLink?): Boolean = link != null && keepRefusal(link.provider) == null

/**
 * The page a song or a playlist opens on, or null where the phone has no reason to send anybody there.
 *
 * Bandcamp and VK are the services whose music is played here and kept nowhere, so the phone points back at
 * them instead: Bandcamp's page is where a song is bought, VK's is where it lives. [address] is the page as
 * Noctorium has it -- a track's `pageUrl`, a playlist's `sourceUrl` -- and anything after a `#` is
 * Noctorium's own note of ids, which no browser should be handed. Only https is opened on a phone, and both
 * serve everything over it, so a plain http address is asked for over https. A service's front page is not
 * a page of anything and is not offered.
 */
internal fun servicePage(provider: ProviderType, address: String?): String? {
    val frontPages = FRONT_PAGES[provider] ?: return null
    val page = address?.trim()?.substringBefore('#') ?: return null
    val rest = when {
        page.startsWith("https://", ignoreCase = true) -> page.substring("https://".length)
        page.startsWith("http://", ignoreCase = true) -> page.substring("http://".length)
        else -> return null
    }
    if (rest.isBlank() || rest.trimEnd('/').lowercase() in frontPages) return null
    return "https://$rest"
}

/** The services whose pages the phone opens, each with the addresses of its front page. */
private val FRONT_PAGES = mapOf(
    ProviderType.BANDCAMP to setOf("bandcamp.com", "www.bandcamp.com"),
    ProviderType.VK to setOf("vk.ru", "vk.com", "m.vk.ru", "m.vk.com"),
)

/** What the action that opens a song or a playlist on [provider]'s own site is called. */
internal fun openOnLabel(provider: ProviderType): String = when (provider) {
    // "VK Music" is the service; the site the page opens on is VK.
    ProviderType.VK -> "Open on VK"
    else -> "Open on ${provider.displayName}"
}

/**
 * Whether a playlist is a whole artist rather than one record or list.
 *
 * Bandcamp's and Spotify's searches answer with both, and an artist is drawn round so it is not taken for an
 * album of theirs. Spotify's mark is its own `artist:` prefix, which core keeps to itself.
 */
internal fun isArtistPlaylist(playlist: Playlist): Boolean = when (playlist.provider) {
    ProviderType.BANDCAMP -> BandcampMusicProvider.isArtist(playlist)
    ProviderType.SPOTIFY -> playlist.id.startsWith(SPOTIFY_ARTIST)
    else -> false
}

private const val SPOTIFY_ARTIST = "artist:"

/**
 * The line under a playlist card's title: who it is by, or, for an artist, that it is one -- with where
 * they are from when Bandcamp says. Spotify's artists already say "Artist" there, which is not said twice.
 */
internal fun cardCaption(playlist: Playlist): String {
    val owner = playlist.ownerName?.takeIf(String::isNotBlank)
    if (!isArtistPlaylist(playlist)) return owner ?: playlist.provider.displayName
    return listOfNotNull("Artist", owner?.takeUnless { it.equals("Artist", ignoreCase = true) }).joinToString(" · ")
}

/**
 * What Search says when its chosen service cannot answer until somebody signs in, or null when it can.
 *
 * Spotify and VK search only for an account; the rest answer anybody. Hybrid leaves out whichever cannot
 * answer, so it is never the one waiting.
 */
internal fun searchSignInHint(mode: SearchMode, spotifyConnected: Boolean, vkConnected: Boolean): String? = when {
    mode == SearchMode.SPOTIFY && !spotifyConnected -> "Spotify answers once it is connected, under Settings."
    mode == SearchMode.VK && !vkConnected -> "VK answers once you are signed in to it, under Settings."
    else -> null
}

/**
 * Who a playlist is by, when that says something: not when it only repeats the service, as VK's "My music"
 * does, being VK's own -- "VK Music · VK" says the same thing twice.
 */
internal fun ownerWorthNaming(playlist: Playlist): String? {
    val owner = playlist.ownerName?.takeIf(String::isNotBlank) ?: return null
    val service = playlist.provider.displayName
    return owner.takeUnless { it.equals(service, ignoreCase = true) || it.equals(service.substringBefore(' '), ignoreCase = true) }
}

/** "Bandcamp", "Bandcamp and Spotify", "Bandcamp, Spotify and VK Music": services named as a sentence would. */
internal fun servicesNamed(services: List<ProviderType>): String {
    val names = services.distinct().map(ProviderType::displayName)
    return when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

/**
 * Whether a service's chip under Hybrid search can be switched: any one can, except the last one still
 * asked, since a Hybrid search that asks nobody is not a search.
 */
internal fun canSwitchHybrid(provider: ProviderType, included: Set<ProviderType>): Boolean =
    provider !in included || included.size > 1

/** What the Search tile says: who Hybrid asks, in the order the chips are in. */
internal fun hybridSummary(included: Set<ProviderType>): String =
    "Hybrid asks " + servicesNamed(DEFAULT_HYBRID_SEARCH.filter { it in included })

/** The speeds offered in a menu, where a slider would be too fine for a thumb; Settings has the slider. */
internal val SPEED_STEPS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** "Normal", "1.25×", "2×". Two decimal places at most: core keeps a speed to the nearest twentieth. */
internal fun speedLabel(speed: Float): String {
    if (abs(speed - 1f) < 0.001f) return "Normal"
    return String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"
}

/**
 * The speed from the phone's own setting, which came before speed was one of Noctorium's settings on every
 * platform, when it is worth carrying over: once, and only into a speed still at normal, so a speed chosen
 * since is never undone.
 */
internal fun speedToCarryOver(preferences: NoctoriumPreferences): Float? =
    preferences.phone.playbackSpeed.takeIf { it != 1f && preferences.playbackSpeed == 1f }

/** What the Playback tile says, in the few words it has room for. */
internal fun playbackSummary(preferences: NoctoriumPreferences): String = listOfNotNull(
    if (preferences.playbackSpeed == 1f) "Normal speed" else "${speedLabel(preferences.playbackSpeed)} speed",
    "autoplay".takeIf { preferences.autoplay },
    "skips silence".takeIf { preferences.phone.skipSilence },
    "skips non-music".takeIf { preferences.skipNonMusic },
).joinToString(" · ")

/**
 * The fades a sleep timer can end with, in seconds: none, a quarter, a half or a whole minute, and whatever
 * else is set already -- chosen on the desktop, say -- so the one in force is always among them.
 */
internal fun sleepFadeChoices(current: Int): List<Int> =
    (SLEEP_FADES + listOfNotNull(current.takeIf { it > 0 })).distinct().sorted()

private val SLEEP_FADES = listOf(0, 15, 30, 60)

/** Whether [track] is playing in the account's own Spotify app rather than on this phone. */
internal fun playsOnSpotify(track: Track?, spotify: SpotifyConnectionState): Boolean =
    track?.provider == ProviderType.SPOTIFY && spotify.playsOnSpotify

/**
 * Where Spotify plays, by the name Spotify gives it: the device chosen, or else the one it has active, which
 * is where Spotify goes when the chosen one is not there. Null until Spotify has been asked.
 */
internal fun spotifyDeviceName(spotify: SpotifyConnectionState): String? =
    spotify.devices.firstOrNull { spotify.device.isNotBlank() && it.id == spotify.device }?.name
        ?: spotify.devices.firstOrNull { it.isActive }?.name

/** What a Spotify device is, in Spotify's word, and whether it is the one playing or one that takes no orders. */
internal fun spotifyDeviceDetail(device: SpotifyDevice): String = listOfNotNull(
    device.type.takeIf(String::isNotBlank),
    "playing now".takeIf { device.isActive },
    "takes no commands from other apps".takeIf { device.isRestricted },
).joinToString(" · ")

/** Where the Spotify account stands, in the few words a Settings tile has room for. */
internal fun spotifySummary(spotify: SpotifyConnectionState): String {
    val who = spotify.accountName.ifBlank { "Connected" }
    return when {
        spotify.connecting -> "Waiting for Spotify…"
        !spotify.connected -> "Your library, search and likes; with Premium, songs play on Spotify"
        spotify.playsOnSpotify -> "$who · songs play on Spotify"
        else -> "$who · songs play matched on YouTube Music"
    }
}

/** Where the VK account stands, in the few words a Settings tile has room for. */
internal fun vkSummary(vk: VkConnectionState): String = when {
    vk.checking -> "Checking with VK…"
    vk.connected && vk.accountName.isNotBlank() -> "Signed in as ${vk.accountName}"
    vk.connected -> "Signed in"
    else -> "Not signed in"
}

/** The parts of Home that are the listener's own rather than a service's rows. */
private val OWN_HOME_PARTS = setOf(HomePart.GREETING, HomePart.PINNED, HomePart.RECENT)

/**
 * Whether every service's rows on Home are put away, so there is nothing to wait for from any of them.
 *
 * Every part that is not the listener's own counts, so a service that joins Home counts without being named
 * here. With YouTube Music and SoundCloud named and nothing else, putting those two away left Home saying it
 * was all put away underneath a row of Bandcamp's.
 */
internal fun serviceRowsAllHidden(hidden: Set<HomePart>): Boolean =
    HomePart.entries.all { it in OWN_HOME_PARTS || it in hidden }

/**
 * [genres] with [genre] taken out when it is there, or put at the end when it is not, so Home's rows keep
 * the order they were picked in.
 */
internal fun toggledGenre(genres: List<BandcampGenre>, genre: BandcampGenre): List<BandcampGenre> =
    if (genre in genres) genres - genre else genres + genre

/**
 * The Bandcamp name in what was typed: the name itself, or the end of a whole `bandcamp.com/<name>` address
 * pasted in.
 *
 * Read the way core reads it when the name is saved, so Save is offered only for a name that is not already
 * the one kept. Core still decides; this only stops a button that would ask Bandcamp the same question again.
 */
internal fun bandcampName(input: String): String =
    input.trim().substringBefore('?').trimEnd('/').substringAfterLast('/').removePrefix("@").take(64)

/** Where the Bandcamp collection stands, in the few words a Settings tile has room for. */
internal fun bandcampSummary(username: String, connection: BandcampConnectionState): String = when {
    connection.checking -> "Checking the name with Bandcamp…"
    username.isBlank() -> "Not set"
    else -> "${connection.fanName.ifBlank { username }}'s collection and wishlist"
}

/**
 * The line under the name on Bandcamp's page in Settings, or null while there is no name to speak of.
 *
 * The fan's own name once Bandcamp has said it, and the address otherwise: Bandcamp is asked for the name
 * when it is saved, not every time Noctorium opens, so after a restart the address is all there is.
 */
internal fun bandcampStatus(username: String, connection: BandcampConnectionState): String? = when {
    connection.checking -> "Checking the name with Bandcamp…"
    username.isBlank() -> null
    connection.fanName.isNotBlank() -> "Showing the collection of ${connection.fanName}."
    else -> "Showing the collection of bandcamp.com/$username."
}
