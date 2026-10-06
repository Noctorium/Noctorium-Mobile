package app.noctorium.android.ui

import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.bandcamp.BandcampSource
import app.noctorium.domain.Artist
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.domain.findMusicLink
import app.noctorium.domain.pageUrl
import app.noctorium.settings.BandcampConnectionState
import app.noctorium.settings.HomePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each service lets the phone do with its music.
 *
 * Most of these are about Bandcamp not falling into the YouTube half of a "SoundCloud, or else YouTube"
 * decision: not signed in to Google, not written into a YouTube playlist, not offered as a download.
 */
class PhoneServiceRulesTest {

    private fun track(provider: ProviderType, id: String, sourceUrl: String = "https://example.invalid/$id") = Track(
        provider = provider,
        id = id,
        title = "Song $id",
        artists = listOf(Artist("a-$id", "Artist $id", provider)),
        sourceUrl = sourceUrl,
    )

    private fun playlist(provider: ProviderType, id: String) = Playlist(id = id, title = "List $id", provider = provider)

    private val bandcampSong = track(
        ProviderType.BANDCAMP,
        "123",
        BandcampSource.of("https://nightowl.bandcamp.com/track/low-tide", trackId = 123, bandId = 456),
    )

    // --- Signing in ---

    @Test
    fun `only YouTube and SoundCloud sign in on a page of their own`() {
        assertTrue(hasSignInPage(ProviderType.YOUTUBE_MUSIC))
        assertTrue(hasSignInPage(ProviderType.YOUTUBE_VIDEO))
        assertTrue(hasSignInPage(ProviderType.SOUNDCLOUD))
        assertFalse(hasSignInPage(ProviderType.BANDCAMP), "Bandcamp would be signed in to Google as YouTube")
        assertFalse(hasSignInPage(ProviderType.SPOTIFY))
        assertFalse(hasSignInPage(ProviderType.VK))
        assertFalse(hasSignInPage(ProviderType.LOCAL))
    }

    // --- Writing to an account's playlists ---

    @Test
    fun `a YouTube video goes into a YouTube Music playlist, and a Bandcamp song into none`() {
        assertEquals(ProviderType.YOUTUBE_MUSIC, playlistAccountFor(track(ProviderType.YOUTUBE_MUSIC, "y")))
        assertEquals(ProviderType.YOUTUBE_MUSIC, playlistAccountFor(track(ProviderType.YOUTUBE_VIDEO, "v")))
        assertEquals(ProviderType.SOUNDCLOUD, playlistAccountFor(track(ProviderType.SOUNDCLOUD, "s")))
        assertNull(playlistAccountFor(bandcampSong))
        assertNull(playlistAccountFor(track(ProviderType.SPOTIFY, "sp")))
        assertNull(playlistAccountFor(track(ProviderType.LOCAL, "f")))
    }

    @Test
    fun `a Bandcamp song is offered none of the playlists, its own collection included`() {
        val library = listOf(
            playlist(ProviderType.BANDCAMP, "album:456:789"),
            playlist(ProviderType.BANDCAMP, "wishlist:1"),
            playlist(ProviderType.YOUTUBE_MUSIC, "PLmine"),
            playlist(ProviderType.SOUNDCLOUD, "42"),
        )
        assertEquals(emptyList(), playlistsToAddTo(bandcampSong, library))
    }

    @Test
    fun `a song is offered its own service's playlists, without the service's own views`() {
        val library = listOf(
            playlist(ProviderType.YOUTUBE_MUSIC, "LM"),
            playlist(ProviderType.YOUTUBE_MUSIC, "PLmine"),
            playlist(ProviderType.SOUNDCLOUD, "likes"),
            playlist(ProviderType.SOUNDCLOUD, "42"),
            playlist(ProviderType.BANDCAMP, "album:456:789"),
        )
        assertEquals(listOf("PLmine"), playlistsToAddTo(track(ProviderType.YOUTUBE_MUSIC, "y"), library).map { it.id })
        assertEquals(listOf("42"), playlistsToAddTo(track(ProviderType.SOUNDCLOUD, "s"), library).map { it.id })
    }

    @Test
    fun `copying a playlist up sends only the songs that live on that account`() {
        val mixed = listOf(
            track(ProviderType.YOUTUBE_MUSIC, "y"),
            track(ProviderType.YOUTUBE_VIDEO, "v"),
            track(ProviderType.SOUNDCLOUD, "s"),
            bandcampSong,
            track(ProviderType.SPOTIFY, "sp"),
            track(ProviderType.LOCAL, "f"),
        )
        assertEquals(listOf("y", "v"), tracksPlaylistCanHold(ProviderType.YOUTUBE_MUSIC, mixed).map { it.id })
        assertEquals(listOf("s"), tracksPlaylistCanHold(ProviderType.SOUNDCLOUD, mixed).map { it.id })
        assertEquals(emptyList(), tracksPlaylistCanHold(ProviderType.BANDCAMP, mixed))
    }

    // --- Keeping music on the phone ---

    private val canKeep: (Track) -> Boolean = { it.provider != ProviderType.BANDCAMP }

    @Test
    fun `a Bandcamp album is offered no download at all`() {
        val album = listOf(bandcampSong, track(ProviderType.BANDCAMP, "124"))
        assertNull(downloadsWanted(album, canKeep) { false })
        assertNull(downloadsWanted(emptyList(), canKeep) { false })
    }

    @Test
    fun `a download counts only the songs it will fetch`() {
        val kept = track(ProviderType.YOUTUBE_MUSIC, "kept")
        val mixed = listOf(kept, track(ProviderType.SOUNDCLOUD, "s"), bandcampSong)
        assertEquals(1, downloadsWanted(mixed, canKeep) { it == kept })
        assertEquals(0, downloadsWanted(listOf(kept), canKeep) { true }, "greyed out once everything is kept")
    }

    @Test
    fun `a Bandcamp link is not taken by the Downloads box`() {
        assertFalse(downloadsFrom(null))
        assertFalse(downloadsFrom(findMusicLink("https://nightowl.bandcamp.com/album/harbour-lights")))
        assertFalse(downloadsFrom(findMusicLink("https://nightowl.bandcamp.com/track/low-tide")))
        assertTrue(downloadsFrom(findMusicLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ")))
        assertTrue(downloadsFrom(findMusicLink("https://soundcloud.com/someone/a-song")))
    }

    // --- Bandcamp's own pages ---

    @Test
    fun `a Bandcamp song opens on its page, without the ids Noctorium keeps after it`() {
        assertTrue('#' in bandcampSong.sourceUrl)
        assertEquals("https://nightowl.bandcamp.com/track/low-tide", bandcampPage(bandcampSong.provider, bandcampSong.pageUrl))
        assertEquals("https://nightowl.bandcamp.com/track/low-tide", bandcampPage(bandcampSong.provider, bandcampSong.sourceUrl))
    }

    @Test
    fun `only Bandcamp's music is pointed at Bandcamp`() {
        val song = track(ProviderType.YOUTUBE_MUSIC, "y", "https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNull(bandcampPage(song.provider, song.pageUrl))
        assertNull(bandcampPage(ProviderType.SOUNDCLOUD, "https://soundcloud.com/someone/a-song"))
    }

    @Test
    fun `a page is opened over https, and nothing that is not a page is opened at all`() {
        assertEquals(
            "https://nightowl.bandcamp.com/album/harbour-lights",
            bandcampPage(ProviderType.BANDCAMP, "http://nightowl.bandcamp.com/album/harbour-lights"),
        )
        assertEquals("https://bandcamp.com/nightowl/wishlist", bandcampPage(ProviderType.BANDCAMP, "https://bandcamp.com/nightowl/wishlist"))
        assertNull(bandcampPage(ProviderType.BANDCAMP, null))
        assertNull(bandcampPage(ProviderType.BANDCAMP, ""))
        assertNull(bandcampPage(ProviderType.BANDCAMP, "nightowl.bandcamp.com/album/harbour-lights"))
        assertNull(bandcampPage(ProviderType.BANDCAMP, "javascript:alert(1)"))
    }

    /** A song listed with no page of its own is given Bandcamp's front page to stand in, which leads nowhere. */
    @Test
    fun `Bandcamp's front page is not offered as a song's page`() {
        val pageless = track(ProviderType.BANDCAMP, "9", BandcampSource.of(null, trackId = 9, bandId = null))
        assertNull(bandcampPage(pageless.provider, pageless.pageUrl))
        assertNull(bandcampPage(ProviderType.BANDCAMP, "https://bandcamp.com/"))
    }

    // --- Home ---

    @Test
    fun `Home is not all put away while Bandcamp's rows are still on it`() {
        val hidden = setOf(HomePart.YOUTUBE_MUSIC, HomePart.SOUNDCLOUD, HomePart.PINNED, HomePart.RECENT)
        assertFalse(serviceRowsAllHidden(hidden))
        assertTrue(serviceRowsAllHidden(hidden + HomePart.BANDCAMP))
    }

    @Test
    fun `the listener's own parts of Home are not a service's rows`() {
        assertFalse(serviceRowsAllHidden(emptySet()))
        assertFalse(serviceRowsAllHidden(setOf(HomePart.GREETING, HomePart.PINNED, HomePart.RECENT)))
        assertTrue(serviceRowsAllHidden(HomePart.entries.toSet()))
    }

    @Test
    fun `a genre picked goes at the end, and one picked again comes out`() {
        val start = listOf(BandcampGenre.ELECTRONIC, BandcampGenre.AMBIENT)
        assertEquals(listOf(BandcampGenre.ELECTRONIC, BandcampGenre.AMBIENT, BandcampGenre.JAZZ), toggledGenre(start, BandcampGenre.JAZZ))
        assertEquals(listOf(BandcampGenre.AMBIENT), toggledGenre(start, BandcampGenre.ELECTRONIC))
        assertEquals(listOf(BandcampGenre.ROCK), toggledGenre(emptyList(), BandcampGenre.ROCK))
    }

    // --- Bandcamp in Settings ---

    @Test
    fun `the name is found in a whole address as readily as on its own`() {
        assertEquals("nightowl", bandcampName("nightowl"))
        assertEquals("nightowl", bandcampName("  https://bandcamp.com/nightowl/  "))
        assertEquals("nightowl", bandcampName("bandcamp.com/nightowl?from=fanpub_fnb"))
        assertEquals("nightowl", bandcampName("@nightowl"))
        assertEquals("", bandcampName("   "))
    }

    @Test
    fun `the tile says the name in use, or that there is none`() {
        assertEquals("Not set", bandcampSummary("", BandcampConnectionState()))
        assertEquals("nightowl's collection and wishlist", bandcampSummary("nightowl", BandcampConnectionState()))
        assertEquals("Odile's collection and wishlist", bandcampSummary("nightowl", BandcampConnectionState(fanName = "Odile")))
        assertEquals("Checking the name with Bandcamp…", bandcampSummary("", BandcampConnectionState(checking = true)))
    }

    @Test
    fun `the page says whose collection is showing, by name once Bandcamp has said it`() {
        assertNull(bandcampStatus("", BandcampConnectionState()))
        assertNull(bandcampStatus("", BandcampConnectionState(message = "Bandcamp collection removed.")))
        assertEquals("Showing the collection of Odile.", bandcampStatus("nightowl", BandcampConnectionState(fanName = "Odile")))
        assertEquals("Showing the collection of bandcamp.com/nightowl.", bandcampStatus("nightowl", BandcampConnectionState()))
        assertEquals("Checking the name with Bandcamp…", bandcampStatus("nightowl", BandcampConnectionState(checking = true)))
    }
}
