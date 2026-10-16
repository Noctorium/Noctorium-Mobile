package app.noctorium.android.ui

import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.core.SearchMode
import app.noctorium.domain.MusicLink
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.domain.isAlbumPage
import app.noctorium.domain.isArtistPage
import app.noctorium.domain.pluralTracks
import app.noctorium.playlists.LocalPlaylist
import app.noctorium.playlists.PlaylistImport
import app.noctorium.settings.AppleMusicConnectionState
import app.noctorium.settings.BandcampConnectionState
import app.noctorium.settings.DeezerConnectionState
import app.noctorium.settings.HYBRID_SEARCH_SERVICES
import app.noctorium.settings.HomePart
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ScrobbleConnectionStatus
import app.noctorium.settings.ScrobbleServiceState
import app.noctorium.settings.ScrobbleState
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.settings.TidalConnectionState
import app.noctorium.settings.VkConnectionState
import app.noctorium.spotify.SPOTIFY_CUT_NOTE
import app.noctorium.spotify.SPOTIFY_PUBLIC_NOTE
import app.noctorium.spotify.SpotifyDevice
import app.noctorium.spotify.SpotifyPublicPlaylistState
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
 * SoundCloud's, VK's and Apple Music's.
 *
 * The sign-in screen knows those four by name. One it does not know would fall through to Google's page
 * and be saved as YouTube's session, so nothing else is let in: Bandcamp needs a name and Deezer a profile rather
 * than a sign-in, and Spotify and Tidal sign in through the browser.
 */
internal fun hasSignInPage(provider: ProviderType): Boolean = when (provider) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO, ProviderType.SOUNDCLOUD, ProviderType.VK, ProviderType.APPLE_MUSIC -> true
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.DEEZER, ProviderType.TIDAL, ProviderType.LOCAL -> false
}

/**
 * The account a playlist holding [track] would live on, or null where no account here can take it.
 *
 * A YouTube video goes into a YouTube Music playlist, which is the one YouTube account there is. Spotify,
 * VK, Apple Music, Deezer and Tidal playlists are read here and not written to, a file belongs to no account,
 * and Bandcamp's lists are a fan's collection and wishlist, which are bought into rather than added to.
 */
internal fun playlistAccountFor(track: Track): ProviderType? = when (track.provider) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO -> ProviderType.YOUTUBE_MUSIC
    ProviderType.SOUNDCLOUD -> ProviderType.SOUNDCLOUD
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.APPLE_MUSIC, ProviderType.DEEZER,
    ProviderType.TIDAL, ProviderType.LOCAL -> null
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
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.APPLE_MUSIC, ProviderType.DEEZER,
    ProviderType.TIDAL, ProviderType.LOCAL -> emptyList()
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
 * Bandcamp's, Spotify's, Apple Music's, Deezer's and Tidal's searches answer with both, and an artist is drawn
 * round so it is not taken for an album of theirs. Core tells the two apart, the same way for every service and
 * on every player.
 */
internal fun isArtistPlaylist(playlist: Playlist): Boolean = playlist.isArtistPage()

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
 * What a "playlist" from a service really is, where it is not a playlist: "Album" or "Artist", for the line under
 * its name in the library and on its page, among playlists that look just like it. Null for a playlist.
 */
internal fun playlistKind(playlist: Playlist): String? = when {
    playlist.isArtistPage() -> "Artist"
    playlist.isAlbumPage() -> "Album"
    else -> null
}

/**
 * The line under a service's playlist in the library and on its page: the service, what it is when it is an album
 * or an artist, and who it is by when that says something -- once, so a Spotify artist, whose owner is "Artist",
 * does not say it twice.
 */
internal fun playlistLine(playlist: Playlist, vararg between: String?): String {
    val kind = playlistKind(playlist)
    val owner = ownerWorthNaming(playlist)?.takeUnless { it.equals(kind, ignoreCase = true) }
    return (listOf(playlist.provider.displayName, kind) + between + owner).filterNotNull().joinToString(" · ")
}

/**
 * What Search says when its chosen service cannot answer until somebody signs in, or null when it can.
 *
 * Spotify and VK search only for an account, and Tidal only through a Tidal app; the rest answer anybody, Apple
 * Music and Deezer included. Hybrid leaves out whichever cannot answer, so it is never the one waiting.
 */
internal fun searchSignInHint(
    mode: SearchMode,
    spotifyConnected: Boolean,
    vkConnected: Boolean,
    tidalReadsCatalogue: Boolean = true,
): String? = when {
    mode == SearchMode.SPOTIFY && !spotifyConnected -> "Spotify answers once it is connected, under Settings."
    mode == SearchMode.VK && !vkConnected -> "VK answers once you are signed in to it, under Settings."
    mode == SearchMode.TIDAL && !tidalReadsCatalogue -> "Tidal answers once it is set up, under Settings."
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
    "Hybrid asks " + servicesNamed(HYBRID_SEARCH_SERVICES.filter { it in included })

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

/**
 * Where the Spotify account stands, in the few words a Settings tile has room for.
 *
 * Signed out, the Spotify music that is in the library all the same is said instead, where there is any: the
 * public playlists followed by their links, and the [imported] playlists made from Spotify's data download -- so a
 * listener whose sign-in Spotify refuses sees that the way round it worked, rather than an offer to sign in.
 */
internal fun spotifySummary(spotify: SpotifyConnectionState, imported: Int = 0): String {
    val who = spotify.accountName.ifBlank { "Connected" }
    val followed = spotify.publicPlaylists.size
    return when {
        spotify.connecting -> "Waiting for Spotify…"
        !spotify.connected && followed > 0 && imported > 0 ->
            "${counted(followed, "public playlist")} followed · $imported imported"
        !spotify.connected && followed > 0 -> "${counted(followed, "public playlist")} followed, no sign-in needed"
        !spotify.connected && imported > 0 -> "${counted(imported, "playlist")} imported from Spotify's data download"
        !spotify.connected -> "Your library, search and likes; with Premium, songs play on Spotify"
        spotify.playsOnSpotify -> "$who · songs play on Spotify"
        else -> "$who · songs play matched on YouTube Music"
    }
}

/** "1 playlist", "3 playlists": [count] of [thing], as a sentence would say it. */
private fun counted(count: Int, thing: String): String = "$count ${if (count == 1) thing else "${thing}s"}"

/** Whether Spotify's data download made this playlist, rather than the listener. */
internal val LocalPlaylist.fromSpotifyImport: Boolean get() = importedFrom?.source == PlaylistImport.SPOTIFY_DATA

/** How many of the playlists made here came from Spotify's data download, Liked Songs among them. */
internal fun importedFromSpotify(playlists: List<LocalPlaylist>): Int = playlists.count { it.fromSpotifyImport }

/**
 * The line under a followed public Spotify playlist on Spotify's page in Settings: whose it is, how many songs the
 * last good read found, and -- as the library says it -- that those are only its first hundred when the page showed
 * as many as it ever shows. "Not read yet" until it has been read once; what went wrong is said apart, in red.
 */
internal fun spotifyPublicLine(playlist: SpotifyPublicPlaylistState): String = listOfNotNull(
    playlist.owner.takeIf(String::isNotBlank),
    playlist.songCount?.let(::pluralTracks) ?: "Not read yet".takeIf { playlist.lastError == null },
    SPOTIFY_CUT_NOTE.takeIf { playlist.mayBeLonger && playlist.songCount != null },
).joinToString(" · ")

/**
 * The library's entry for a followed public playlist, to open it from Settings: the one the library lists, with its
 * cover and its count, or -- before the library has been read -- one made from what Settings knows of it, which
 * opens all the same.
 */
internal fun publicPlaylistToOpen(playlist: SpotifyPublicPlaylistState, listed: List<Playlist>): Playlist =
    listed.firstOrNull { it.provider == ProviderType.SPOTIFY && it.id == playlist.id } ?: Playlist(
        id = playlist.id,
        title = playlist.title.ifBlank { playlist.id },
        provider = ProviderType.SPOTIFY,
        // As the library writes it: whose it is, that it is public, and that it is cut short when it may be.
        ownerName = listOfNotNull(
            playlist.owner.takeIf(String::isNotBlank),
            SPOTIFY_PUBLIC_NOTE,
            SPOTIFY_CUT_NOTE.takeIf { playlist.mayBeLonger },
        ).joinToString(" · "),
        artworkUrl = playlist.artworkUrl,
        sourceUrl = playlist.url,
        trackCount = playlist.songCount,
        isPublic = true,
        ownedByListener = false,
    )

/**
 * What a playlist made here says of where it came from, after its count: "made here" in the library and "made in
 * Noctorium" on its own page -- or, for one Spotify's data download made, "imported from Spotify" in both. That one
 * is Noctorium's own all the same, listed with the rest.
 */
internal fun localPlaylistOrigin(playlist: LocalPlaylist, short: Boolean = true): String = when {
    playlist.fromSpotifyImport -> "imported from Spotify"
    short -> "made here"
    else -> "made in Noctorium"
}

/**
 * What the file chosen for the import is called, for what the import says while it runs and when it is done: the
 * name the file picker gives it, or else the last part of its address, or else "the file", which reads as well in
 * "Opening the file…" and "Could not read the file".
 */
internal fun importFileName(displayName: String?, lastPathSegment: String?): String =
    displayName?.trim()?.takeIf(String::isNotBlank)
        ?: lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.trim()?.takeIf(String::isNotBlank)
        ?: "the file"

/**
 * What the file picker offers for Spotify's data download: the .zip Spotify sends, by either of the names a phone
 * gives the kind, a .json from inside it -- and every other file, since some phones call a .zip nothing more
 * particular than a file, and the import goes by what is in what it is given rather than by its name.
 */
internal val SPOTIFY_EXPORT_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/json", "*/*")

/** Where the VK account stands, in the few words a Settings tile has room for. */
internal fun vkSummary(vk: VkConnectionState): String = when {
    vk.checking -> "Checking with VK…"
    vk.connected && vk.accountName.isNotBlank() -> "Signed in as ${vk.accountName}"
    vk.connected -> "Signed in"
    else -> "Not signed in"
}

/** Where the Apple Music sign-in stands, in the few words a Settings tile has room for. */
internal fun appleMusicSummary(apple: AppleMusicConnectionState): String = when {
    apple.checking -> "Checking with Apple…"
    apple.connected && apple.storefront.isNotBlank() -> "Signed in · the ${apple.storefront.uppercase()} storefront"
    apple.connected -> "Signed in"
    else -> "Search and links; sign in for your library"
}

/** Where the Deezer profile stands, in the few words a Settings tile has room for. */
internal fun deezerSummary(deezer: DeezerConnectionState): String = when {
    deezer.checking -> "Asking Deezer about the profile…"
    deezer.profileId.isBlank() -> "Search and links; give a profile for your library"
    deezer.name.isNotBlank() -> "${deezer.name}'s playlists, loved tracks and albums"
    else -> "Profile ${deezer.profileId}"
}

/**
 * Where Tidal stands, in the few words a Settings tile has room for: first whether there is a Tidal app to go
 * through at all, since nothing works without one.
 */
internal fun tidalSummary(tidal: TidalConnectionState): String = when {
    tidal.connecting -> "Waiting for Tidal…"
    !tidal.configured -> "Needs a Tidal app of your own"
    tidal.connected && tidal.accountName.isNotBlank() -> "Signed in as ${tidal.accountName}"
    tidal.connected -> "Signed in"
    tidal.readsCatalogue -> "Search works; connect for your collection"
    else -> "Your Tidal app is set; connect to sign in"
}

/**
 * The line under the field on Deezer's page in Settings, or null while there is no profile to speak of: whose it
 * is, with its number, since the number is all Settings is given back after a restart until Deezer has said more.
 */
internal fun deezerStatus(deezer: DeezerConnectionState): String? = when {
    deezer.checking -> "Asking Deezer about the profile…"
    deezer.profileId.isBlank() -> null
    deezer.name.isNotBlank() -> "Showing ${deezer.name}, profile ${deezer.profileId}."
    else -> "Showing profile ${deezer.profileId}."
}

/**
 * The number of the Deezer profile in what was typed: the number itself, or the one in a `deezer.com/profile/<number>`
 * address, with or without the language Deezer puts in front. Null for anything else, a share link included, which
 * only Deezer can say the number of.
 *
 * Read the way core reads it, so Save is offered only for a profile that is not already the one kept. Core still
 * decides, and follows a share link to see where it leads.
 */
internal fun deezerProfileNumber(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isNotEmpty() && trimmed.all(Char::isDigit)) return trimmed.trimStart('0').takeIf(String::isNotEmpty)
    return DEEZER_PROFILE.find(trimmed)?.groupValues?.get(1)?.trimStart('0')?.takeIf(String::isNotEmpty)
}

private val DEEZER_PROFILE = Regex("""deezer\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?profile/(\d+)""", RegexOption.IGNORE_CASE)

/** The scrobblers by name, in the order the Scrobbling page has them. */
private fun scrobblers(scrobbling: ScrobbleState): List<Pair<String, ScrobbleServiceState>> =
    listOf("Last.fm" to scrobbling.lastFm, "Libre.fm" to scrobbling.libreFm, "ListenBrainz" to scrobbling.listenBrainz)

/** Whether anything is being scrobbled to, which is when the Scrobbling tile is lit. */
internal fun scrobblingAnywhere(scrobbling: ScrobbleState): Boolean =
    scrobblers(scrobbling).any { it.second.status == ScrobbleConnectionStatus.CONNECTED }

/**
 * Where scrobbling stands, in the few words a Settings tile has room for: a service that needs connecting again
 * first, since what is played is being kept back for it, then who is connected, then a sign-in still waiting to be
 * allowed. Each name once, though a listener is often the same name on Last.fm and on Libre.fm.
 */
internal fun scrobblingSummary(scrobbling: ScrobbleState): String {
    val services = scrobblers(scrobbling)
    val refused = services.firstOrNull { it.second.status == ScrobbleConnectionStatus.ERROR }?.first
    val connected = services.map { it.second }
        .filter { it.status == ScrobbleConnectionStatus.CONNECTED }
        .mapNotNull { it.username }
        .distinct()
    val waiting = services.firstOrNull { it.second.status == ScrobbleConnectionStatus.AWAITING_APPROVAL }?.first
    return when {
        refused != null -> "$refused needs connecting again"
        connected.isNotEmpty() -> "Connected: ${connected.joinToString()}"
        waiting != null -> "$waiting sign-in waiting for you"
        else -> "Connect Last.fm, Libre.fm or ListenBrainz"
    }
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
