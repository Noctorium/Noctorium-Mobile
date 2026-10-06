package app.noctorium.android.ui

import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.domain.MusicLink
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.settings.BandcampConnectionState
import app.noctorium.settings.HomePart

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
 * and SoundCloud's.
 *
 * The sign-in screen knows those two and treats everything that is not SoundCloud as YouTube, so a service
 * without a page of its own, sent there, would sign in to Google and be saved as YouTube's session.
 * Bandcamp needs a name rather than a sign-in, and Spotify signs in through the browser.
 */
internal fun hasSignInPage(provider: ProviderType): Boolean = when (provider) {
    ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO, ProviderType.SOUNDCLOUD -> true
    ProviderType.SPOTIFY, ProviderType.BANDCAMP, ProviderType.VK, ProviderType.LOCAL -> false
}

/**
 * The account a playlist holding [track] would live on, or null where no account here can take it.
 *
 * A YouTube video goes into a YouTube Music playlist, which is the one YouTube account there is. Spotify
 * is read and never written to, a file belongs to no account, and Bandcamp's lists are a fan's collection
 * and wishlist, which are bought into rather than added to.
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
 * Null when not one of them may be kept, which is a Bandcamp album: a button that can only explain why it
 * does nothing is worse than no button. Otherwise the ones that may be kept and are not yet, so a list
 * with a Bandcamp song in it never counts a song the download will not fetch. [canKeep] is core's word on
 * that, and [kept] whether a track is on the phone already.
 */
internal fun downloadsWanted(tracks: List<Track>, canKeep: (Track) -> Boolean, kept: (Track) -> Boolean): Int? {
    val keepable = tracks.filter(canKeep)
    if (keepable.isEmpty()) return null
    return keepable.count { !kept(it) }
}

/**
 * Whether a pasted link can be downloaded.
 *
 * Not a Bandcamp one. Core will not keep Bandcamp's songs -- what Bandcamp streams is for listening, and the
 * file is the artist's to sell -- so the Downloads box says so instead of taking the link and doing nothing.
 */
internal fun downloadsFrom(link: MusicLink?): Boolean = link != null && link.provider != ProviderType.BANDCAMP

/**
 * The Bandcamp page a song or a playlist opens on, or null when it is not Bandcamp's or has no page.
 *
 * Bandcamp is the one service the phone points back at instead of keeping a copy, since its page is where a
 * song is bought. [address] is the page as Noctorium has it -- a track's `pageUrl`, a playlist's
 * `sourceUrl` -- and anything after a `#` is Noctorium's own note of ids, which no browser should be handed.
 * Only https is opened on a phone, and Bandcamp serves everything over it, so a plain http address is asked
 * for over https; Bandcamp's front page is not a page of anything and is not offered.
 */
internal fun bandcampPage(provider: ProviderType, address: String?): String? {
    if (provider != ProviderType.BANDCAMP) return null
    val page = address?.trim()?.substringBefore('#') ?: return null
    val rest = when {
        page.startsWith("https://", ignoreCase = true) -> page.substring("https://".length)
        page.startsWith("http://", ignoreCase = true) -> page.substring("http://".length)
        else -> return null
    }
    if (rest.isBlank() || rest.trimEnd('/').equals("bandcamp.com", ignoreCase = true)) return null
    return "https://$rest"
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
