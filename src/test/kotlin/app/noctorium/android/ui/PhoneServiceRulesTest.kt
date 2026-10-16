package app.noctorium.android.ui

import androidx.compose.ui.graphics.Color
import app.noctorium.bandcamp.BandcampGenre
import app.noctorium.bandcamp.BandcampSource
import app.noctorium.core.SearchMode
import app.noctorium.domain.Artist
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.domain.findMusicLink
import app.noctorium.domain.pageUrl
import app.noctorium.settings.AppleMusicConnectionState
import app.noctorium.settings.BandcampConnectionState
import app.noctorium.settings.DEFAULT_HYBRID_SEARCH
import app.noctorium.settings.DeezerConnectionState
import app.noctorium.settings.HYBRID_SEARCH_SERVICES
import app.noctorium.settings.HomePart
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.ScrobbleConnectionStatus
import app.noctorium.settings.ScrobbleServiceState
import app.noctorium.settings.ScrobbleState
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.settings.ThemePreset
import app.noctorium.settings.TidalConnectionState
import app.noctorium.settings.VkConnectionState
import app.noctorium.settings.themeColours
import app.noctorium.spotify.SpotifyDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each service lets the phone do with its music.
 *
 * Most of these are about a newer service not falling into the YouTube half of a "SoundCloud, or else
 * YouTube" decision: not signed in to Google, not written into a YouTube playlist, not offered as a download.
 */
class PhoneServiceRulesTest {

    private fun track(provider: ProviderType, id: String, sourceUrl: String = "https://example.invalid/$id") = Track(
        provider = provider,
        id = id,
        title = "Song $id",
        artists = listOf(Artist("a-$id", "Artist $id", provider)),
        sourceUrl = sourceUrl,
    )

    private fun playlist(provider: ProviderType, id: String, owner: String? = null) =
        Playlist(id = id, title = "List $id", provider = provider, ownerName = owner)

    private val bandcampSong = track(
        ProviderType.BANDCAMP,
        "123",
        BandcampSource.of("https://nightowl.bandcamp.com/track/low-tide", trackId = 123, bandId = 456),
    )

    /** A VK song as core keeps it: its page, with the access key after a `#`. */
    private val vkSong = track(ProviderType.VK, "-2001_9001", "https://vk.ru/audio-2001_9001#vk-access=made-up-key")

    // --- Signing in ---

    @Test
    fun `YouTube, SoundCloud, VK and Apple Music sign in on a page of their own, and nothing else does`() {
        assertTrue(hasSignInPage(ProviderType.YOUTUBE_MUSIC))
        assertTrue(hasSignInPage(ProviderType.YOUTUBE_VIDEO))
        assertTrue(hasSignInPage(ProviderType.SOUNDCLOUD))
        assertTrue(hasSignInPage(ProviderType.VK))
        assertTrue(hasSignInPage(ProviderType.APPLE_MUSIC))
        assertFalse(hasSignInPage(ProviderType.BANDCAMP), "Bandcamp would be signed in to Google as YouTube")
        assertFalse(hasSignInPage(ProviderType.DEEZER), "Deezer is a profile, not a sign-in")
        assertFalse(hasSignInPage(ProviderType.TIDAL), "Tidal signs in in the browser")
        assertFalse(hasSignInPage(ProviderType.SPOTIFY))
        assertFalse(hasSignInPage(ProviderType.LOCAL))
    }

    // --- Writing to an account's playlists ---

    @Test
    fun `a YouTube video goes into a YouTube Music playlist, and a Bandcamp or VK song into none`() {
        assertEquals(ProviderType.YOUTUBE_MUSIC, playlistAccountFor(track(ProviderType.YOUTUBE_MUSIC, "y")))
        assertEquals(ProviderType.YOUTUBE_MUSIC, playlistAccountFor(track(ProviderType.YOUTUBE_VIDEO, "v")))
        assertEquals(ProviderType.SOUNDCLOUD, playlistAccountFor(track(ProviderType.SOUNDCLOUD, "s")))
        assertNull(playlistAccountFor(bandcampSong))
        assertNull(playlistAccountFor(vkSong))
        assertNull(playlistAccountFor(track(ProviderType.SPOTIFY, "sp")))
        assertNull(playlistAccountFor(track(ProviderType.LOCAL, "f")))
        listOf(ProviderType.APPLE_MUSIC, ProviderType.DEEZER, ProviderType.TIDAL).forEach {
            assertNull(playlistAccountFor(track(it, "later")), "$it playlists are read, not written to")
        }
    }

    @Test
    fun `a Bandcamp or VK song is offered none of the playlists, its own service's included`() {
        val library = listOf(
            playlist(ProviderType.BANDCAMP, "album:456:789"),
            playlist(ProviderType.BANDCAMP, "wishlist:1"),
            playlist(ProviderType.VK, "my-music"),
            playlist(ProviderType.YOUTUBE_MUSIC, "PLmine"),
            playlist(ProviderType.SOUNDCLOUD, "42"),
        )
        assertEquals(emptyList(), playlistsToAddTo(bandcampSong, library))
        assertEquals(emptyList(), playlistsToAddTo(vkSong, library))
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
            vkSong,
            track(ProviderType.SPOTIFY, "sp"),
            track(ProviderType.LOCAL, "f"),
            track(ProviderType.APPLE_MUSIC, "am"),
            track(ProviderType.DEEZER, "dz"),
            track(ProviderType.TIDAL, "td"),
        )
        assertEquals(listOf("y", "v"), tracksPlaylistCanHold(ProviderType.YOUTUBE_MUSIC, mixed).map { it.id })
        assertEquals(listOf("s"), tracksPlaylistCanHold(ProviderType.SOUNDCLOUD, mixed).map { it.id })
        assertEquals(emptyList(), tracksPlaylistCanHold(ProviderType.BANDCAMP, mixed))
        assertEquals(emptyList(), tracksPlaylistCanHold(ProviderType.DEEZER, mixed))
    }

    // --- Keeping music on the phone ---

    private val canKeep: (Track) -> Boolean = { it.provider != ProviderType.BANDCAMP && it.provider != ProviderType.VK }

    @Test
    fun `a Bandcamp album or a VK playlist is offered no download at all`() {
        assertNull(downloadsWanted(listOf(bandcampSong, track(ProviderType.BANDCAMP, "124")), canKeep) { false })
        assertNull(downloadsWanted(listOf(vkSong), canKeep) { false })
        assertNull(downloadsWanted(emptyList(), canKeep) { false })
    }

    @Test
    fun `a download counts only the songs it will fetch`() {
        val kept = track(ProviderType.YOUTUBE_MUSIC, "kept")
        val mixed = listOf(kept, track(ProviderType.SOUNDCLOUD, "s"), bandcampSong, vkSong)
        assertEquals(1, downloadsWanted(mixed, canKeep) { it == kept })
        assertEquals(0, downloadsWanted(listOf(kept), canKeep) { true }, "greyed out once everything is kept")
    }

    @Test
    fun `a Bandcamp link is not taken by the Downloads box, and says why`() {
        assertFalse(downloadsFrom(null))
        assertFalse(downloadsFrom(findMusicLink("https://nightowl.bandcamp.com/album/harbour-lights")))
        assertFalse(downloadsFrom(findMusicLink("https://nightowl.bandcamp.com/track/low-tide")))
        assertTrue(downloadsFrom(findMusicLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ")))
        assertTrue(downloadsFrom(findMusicLink("https://soundcloud.com/someone/a-song")))
        assertNotNull(keepRefusal(ProviderType.BANDCAMP))
    }

    @Test
    fun `only Bandcamp's and VK's songs are refused, each with its own reason`() {
        val bandcamp = keepRefusal(ProviderType.BANDCAMP)
        val vk = keepRefusal(ProviderType.VK)
        assertTrue(bandcamp!!.contains("Bandcamp"))
        assertTrue(vk!!.contains("VK"))
        listOf(ProviderType.YOUTUBE_MUSIC, ProviderType.YOUTUBE_VIDEO, ProviderType.SOUNDCLOUD, ProviderType.SPOTIFY)
            .forEach { assertNull(keepRefusal(it), "$it can be kept") }
        // What is kept of theirs is the recording each song was matched to, as a Spotify song's is.
        listOf(ProviderType.APPLE_MUSIC, ProviderType.DEEZER, ProviderType.TIDAL).forEach { assertNull(keepRefusal(it), "$it can be kept") }
    }

    // --- The pages songs open on ---

    @Test
    fun `a Bandcamp song opens on its page, without the ids Noctorium keeps after it`() {
        assertTrue('#' in bandcampSong.sourceUrl)
        assertEquals("https://nightowl.bandcamp.com/track/low-tide", servicePage(bandcampSong.provider, bandcampSong.pageUrl))
        assertEquals("https://nightowl.bandcamp.com/track/low-tide", servicePage(bandcampSong.provider, bandcampSong.sourceUrl))
        assertEquals("Open on Bandcamp", openOnLabel(ProviderType.BANDCAMP))
    }

    @Test
    fun `a VK song opens on VK, without its access key`() {
        assertEquals("https://vk.ru/audio-2001_9001", vkSong.pageUrl)
        assertEquals("https://vk.ru/audio-2001_9001", servicePage(vkSong.provider, vkSong.pageUrl))
        assertEquals("https://vk.ru/audio-2001_9001", servicePage(vkSong.provider, vkSong.sourceUrl))
        assertEquals("Open on VK", openOnLabel(ProviderType.VK))
        assertNull(servicePage(ProviderType.VK, "https://vk.ru/"), "VK's front page is not a page of anything")
    }

    @Test
    fun `only Bandcamp's and VK's music is pointed at its own site`() {
        val song = track(ProviderType.YOUTUBE_MUSIC, "y", "https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNull(servicePage(song.provider, song.pageUrl))
        assertNull(servicePage(ProviderType.SOUNDCLOUD, "https://soundcloud.com/someone/a-song"))
        assertNull(servicePage(ProviderType.SPOTIFY, "https://open.spotify.com/track/made-up"))
    }

    @Test
    fun `a page is opened over https, and nothing that is not a page is opened at all`() {
        assertEquals(
            "https://nightowl.bandcamp.com/album/harbour-lights",
            servicePage(ProviderType.BANDCAMP, "http://nightowl.bandcamp.com/album/harbour-lights"),
        )
        assertEquals("https://bandcamp.com/nightowl/wishlist", servicePage(ProviderType.BANDCAMP, "https://bandcamp.com/nightowl/wishlist"))
        assertNull(servicePage(ProviderType.BANDCAMP, null))
        assertNull(servicePage(ProviderType.BANDCAMP, ""))
        assertNull(servicePage(ProviderType.BANDCAMP, "nightowl.bandcamp.com/album/harbour-lights"))
        assertNull(servicePage(ProviderType.BANDCAMP, "javascript:alert(1)"))
    }

    /** A song listed with no page of its own is given Bandcamp's front page to stand in, which leads nowhere. */
    @Test
    fun `Bandcamp's front page is not offered as a song's page`() {
        val pageless = track(ProviderType.BANDCAMP, "9", BandcampSource.of(null, trackId = 9, bandId = null))
        assertNull(servicePage(pageless.provider, pageless.pageUrl))
        assertNull(servicePage(ProviderType.BANDCAMP, "https://bandcamp.com/"))
    }

    // --- Albums and artists in search ---

    @Test
    fun `Bandcamp's, Spotify's, Apple Music's, Deezer's and Tidal's artists are told from their albums`() {
        assertTrue(isArtistPlaylist(playlist(ProviderType.BANDCAMP, "band:801")))
        assertTrue(isArtistPlaylist(playlist(ProviderType.SPOTIFY, "artist:made-up-id")))
        assertFalse(isArtistPlaylist(playlist(ProviderType.SPOTIFY, "album:made-up-id")))
        assertFalse(isArtistPlaylist(playlist(ProviderType.BANDCAMP, "album:801:9001")))
        assertFalse(isArtistPlaylist(playlist(ProviderType.YOUTUBE_MUSIC, "artist:not-this-one")))
        listOf(ProviderType.APPLE_MUSIC, ProviderType.DEEZER, ProviderType.TIDAL).forEach {
            assertTrue(isArtistPlaylist(playlist(it, "artist:2001")), "$it")
            assertFalse(isArtistPlaylist(playlist(it, "album:2002")), "$it")
        }
        assertFalse(isArtistPlaylist(playlist(ProviderType.VK, "artist:not-this-one")))
    }

    @Test
    fun `an album or an artist among the library's playlists says which it is, and once`() {
        assertEquals("Album", playlistKind(playlist(ProviderType.DEEZER, "album:2002")))
        assertEquals("Artist", playlistKind(playlist(ProviderType.TIDAL, "artist:2001")))
        assertEquals("Album", playlistKind(playlist(ProviderType.APPLE_MUSIC, "album:1440")))
        assertNull(playlistKind(playlist(ProviderType.DEEZER, "908622995")))
        assertNull(playlistKind(playlist(ProviderType.YOUTUBE_MUSIC, "album:not-this-one")))
        assertEquals(
            "Deezer · Album · 12 songs · Harbour Lights Ensemble",
            playlistLine(playlist(ProviderType.DEEZER, "album:2002", owner = "Harbour Lights Ensemble"), "12 songs"),
        )
        assertEquals("Spotify · Artist", playlistLine(playlist(ProviderType.SPOTIFY, "artist:made-up-id", owner = "Artist")), "not Artist twice")
        assertEquals("VK Music", playlistLine(playlist(ProviderType.VK, "my-music", owner = "VK")))
        assertEquals("Tidal · Odile Brandt", playlistLine(playlist(ProviderType.TIDAL, "0b5e7f4e-1d2c-4a3b-9c8d-7e6f5a4b3c2d", owner = "Odile Brandt"), null))
    }

    @Test
    fun `an artist card says it is one, once, and an album card says who it is by`() {
        assertEquals("Artist", cardCaption(playlist(ProviderType.SPOTIFY, "artist:made-up-id", owner = "Artist")))
        assertEquals("Artist · Leith, Scotland", cardCaption(playlist(ProviderType.BANDCAMP, "band:801", owner = "Leith, Scotland")))
        assertEquals("Artist", cardCaption(playlist(ProviderType.BANDCAMP, "band:801")))
        assertEquals("Harbour Lights Ensemble", cardCaption(playlist(ProviderType.SPOTIFY, "album:x", owner = "Harbour Lights Ensemble")))
        assertEquals("Bandcamp", cardCaption(playlist(ProviderType.BANDCAMP, "album:801:9001")))
    }

    @Test
    fun `a playlist's owner is named, unless it is only the service again`() {
        assertNull(ownerWorthNaming(playlist(ProviderType.VK, "my-music", owner = "VK")), "not VK Music · VK")
        assertNull(ownerWorthNaming(playlist(ProviderType.YOUTUBE_MUSIC, "LM", owner = "YouTube Music")))
        assertNull(ownerWorthNaming(playlist(ProviderType.SPOTIFY, "liked-songs")))
        assertEquals("Odile Brandt", ownerWorthNaming(playlist(ProviderType.VK, "playlist:1_2", owner = "Odile Brandt")))
        assertEquals("Sound", ownerWorthNaming(playlist(ProviderType.SOUNDCLOUD, "42", owner = "Sound")), "a name that merely starts the same")
    }

    @Test
    fun `the services in a row are named the way a sentence would name them`() {
        assertEquals("", servicesNamed(emptyList()))
        assertEquals("Bandcamp", servicesNamed(listOf(ProviderType.BANDCAMP, ProviderType.BANDCAMP)))
        assertEquals("Bandcamp and Spotify", servicesNamed(listOf(ProviderType.BANDCAMP, ProviderType.SPOTIFY)))
        assertEquals(
            "Bandcamp, Spotify and VK Music",
            servicesNamed(listOf(ProviderType.BANDCAMP, ProviderType.SPOTIFY, ProviderType.VK, ProviderType.SPOTIFY)),
        )
    }

    @Test
    fun `Spotify and VK say they need signing in to, and only while they do`() {
        assertNotNull(searchSignInHint(SearchMode.SPOTIFY, spotifyConnected = false, vkConnected = true))
        assertNull(searchSignInHint(SearchMode.SPOTIFY, spotifyConnected = true, vkConnected = false))
        assertNotNull(searchSignInHint(SearchMode.VK, spotifyConnected = true, vkConnected = false))
        assertNull(searchSignInHint(SearchMode.VK, spotifyConnected = false, vkConnected = true))
        assertNull(searchSignInHint(SearchMode.HYBRID, spotifyConnected = false, vkConnected = false))
        assertNull(searchSignInHint(SearchMode.BANDCAMP, spotifyConnected = false, vkConnected = false))
    }

    @Test
    fun `Tidal says it has to be set up, and Apple Music and Deezer answer anybody`() {
        assertNotNull(searchSignInHint(SearchMode.TIDAL, spotifyConnected = true, vkConnected = true, tidalReadsCatalogue = false))
        assertNull(searchSignInHint(SearchMode.TIDAL, spotifyConnected = false, vkConnected = false, tidalReadsCatalogue = true))
        assertNull(searchSignInHint(SearchMode.DEEZER, spotifyConnected = false, vkConnected = false, tidalReadsCatalogue = false))
        assertNull(searchSignInHint(SearchMode.APPLE_MUSIC, spotifyConnected = false, vkConnected = false, tidalReadsCatalogue = false))
    }

    // --- Hybrid search ---

    @Test
    fun `the last service Hybrid asks cannot be switched off, and any other can`() {
        val two = setOf(ProviderType.YOUTUBE_MUSIC, ProviderType.BANDCAMP)
        assertTrue(canSwitchHybrid(ProviderType.YOUTUBE_MUSIC, two))
        assertTrue(canSwitchHybrid(ProviderType.VK, two), "one left out can always be brought back")
        val one = setOf(ProviderType.BANDCAMP)
        assertFalse(canSwitchHybrid(ProviderType.BANDCAMP, one))
        assertTrue(canSwitchHybrid(ProviderType.SOUNDCLOUD, one))
    }

    @Test
    fun `the Search tile names who Hybrid asks, in the chips' order`() {
        assertEquals("Hybrid asks SoundCloud and Bandcamp", hybridSummary(setOf(ProviderType.BANDCAMP, ProviderType.SOUNDCLOUD)))
        assertTrue(hybridSummary(DEFAULT_HYBRID_SEARCH).startsWith("Hybrid asks YouTube Music, YouTube, SoundCloud"))
        // The three that are left out to begin with are named once chosen, after the rest, as their chips are.
        assertEquals(
            "Hybrid asks YouTube Music, Apple Music, Deezer and Tidal",
            hybridSummary(setOf(ProviderType.TIDAL, ProviderType.DEEZER, ProviderType.YOUTUBE_MUSIC, ProviderType.APPLE_MUSIC)),
        )
    }

    @Test
    fun `every service Hybrid can ask has a chip, the three later ones included`() {
        assertTrue(HYBRID_SEARCH_SERVICES.containsAll(DEFAULT_HYBRID_SEARCH))
        assertTrue(HYBRID_SEARCH_SERVICES.containsAll(listOf(ProviderType.APPLE_MUSIC, ProviderType.DEEZER, ProviderType.TIDAL)))
    }

    // --- Playback ---

    @Test
    fun `a speed is read out as people say it`() {
        assertEquals("Normal", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("0.5×", speedLabel(0.5f))
        assertEquals("2×", speedLabel(2f))
        assertEquals("1.15×", speedLabel(1.1500001f), "not a float's tail")
        assertTrue(1f in SPEED_STEPS && 0.5f in SPEED_STEPS && 2f in SPEED_STEPS)
    }

    @Test
    fun `the phone's old speed is carried over once, into a speed still at normal`() {
        val old = NoctoriumPreferences(phone = PhonePreferences(playbackSpeed = 1.25f))
        assertEquals(1.25f, speedToCarryOver(old))
        assertNull(speedToCarryOver(NoctoriumPreferences()))
        assertNull(speedToCarryOver(old.copy(playbackSpeed = 1.5f)), "a speed chosen since is not undone")
        assertNull(speedToCarryOver(old.copy(phone = old.phone.copy(playbackSpeed = 1f))), "carried over already")
    }

    @Test
    fun `the Playback tile says the speed and what is switched on`() {
        assertEquals("Normal speed · autoplay · skips non-music", playbackSummary(NoctoriumPreferences()))
        assertEquals(
            "1.5× speed · skips silence",
            playbackSummary(
                NoctoriumPreferences(
                    playbackSpeed = 1.5f,
                    autoplay = false,
                    skipNonMusic = false,
                    phone = PhonePreferences(skipSilence = true),
                ),
            ),
        )
    }

    @Test
    fun `a sleep timer's fade offers the usual lengths and the one in force`() {
        assertEquals(listOf(0, 15, 30, 60), sleepFadeChoices(0))
        assertEquals(listOf(0, 15, 30, 60), sleepFadeChoices(30))
        assertEquals(listOf(0, 15, 30, 45, 60), sleepFadeChoices(45))
    }

    // --- Spotify and VK ---

    private val phone = SpotifyDevice(id = "d-phone", name = "Pocket phone", type = "Smartphone", isActive = false)
    private val speaker = SpotifyDevice(id = "d-speaker", name = "Kitchen speaker", type = "Speaker", isActive = true)

    @Test
    fun `only a Spotify song, with its songs set to play on Spotify, is said to be on Spotify`() {
        val onSpotify = SpotifyConnectionState(connected = true, canPlay = true, playsOnSpotify = true)
        assertTrue(playsOnSpotify(track(ProviderType.SPOTIFY, "sp"), onSpotify))
        assertFalse(playsOnSpotify(track(ProviderType.SPOTIFY, "sp"), onSpotify.copy(playsOnSpotify = false)))
        assertFalse(playsOnSpotify(track(ProviderType.YOUTUBE_MUSIC, "y"), onSpotify))
        assertFalse(playsOnSpotify(null, onSpotify))
    }

    @Test
    fun `Spotify plays on the device chosen, or else the one it has active`() {
        val devices = listOf(phone, speaker)
        assertEquals("Pocket phone", spotifyDeviceName(SpotifyConnectionState(devices = devices, device = "d-phone")))
        assertEquals("Kitchen speaker", spotifyDeviceName(SpotifyConnectionState(devices = devices, device = "")))
        assertEquals("Kitchen speaker", spotifyDeviceName(SpotifyConnectionState(devices = devices, device = "d-gone")))
        assertNull(spotifyDeviceName(SpotifyConnectionState(devices = listOf(phone))))
        assertNull(spotifyDeviceName(SpotifyConnectionState()))
    }

    @Test
    fun `a device says what it is, and whether it is playing or will not take orders`() {
        assertEquals("Speaker · playing now", spotifyDeviceDetail(speaker))
        assertEquals("Smartphone", spotifyDeviceDetail(phone))
        assertEquals("TV · takes no commands from other apps", spotifyDeviceDetail(SpotifyDevice("t", "Lounge", "TV", false, isRestricted = true)))
    }

    @Test
    fun `the Spotify tile says who is signed in and where their songs play`() {
        assertEquals("Waiting for Spotify…", spotifySummary(SpotifyConnectionState(connecting = true)))
        assertTrue(spotifySummary(SpotifyConnectionState()).contains("Premium"))
        assertEquals(
            "Odile · songs play on Spotify",
            spotifySummary(SpotifyConnectionState(connected = true, accountName = "Odile", canPlay = true, playsOnSpotify = true)),
        )
        assertEquals(
            "Connected · songs play matched on YouTube Music",
            spotifySummary(SpotifyConnectionState(connected = true)),
        )
    }

    @Test
    fun `the VK tile says who is signed in, or that nobody is`() {
        assertEquals("Not signed in", vkSummary(VkConnectionState()))
        assertEquals("Checking with VK…", vkSummary(VkConnectionState(checking = true)))
        assertEquals("Signed in as Odile", vkSummary(VkConnectionState(connected = true, accountName = "Odile")))
        assertEquals("Signed in", vkSummary(VkConnectionState(connected = true)))
    }

    // --- Apple Music, Deezer and Tidal ---

    private val night = Color(NoctoriumPreferences().themeColours().background)
    private val day = Color(NoctoriumPreferences(theme = ThemePreset.NOCTORIUM_DAY).themeColours().background)

    @Test
    fun `Tidal's black chip is lined on a dark theme, and every other badge stands out by itself`() {
        assertNotNull(badgeOutline(ProviderType.TIDAL.badgeColour(), night))
        assertNull(badgeOutline(ProviderType.TIDAL.badgeColour(), day))
        ProviderType.entries.filter { it != ProviderType.TIDAL }.forEach { provider ->
            assertNull(badgeOutline(provider.badgeColour(), night), "$provider on night")
            assertNull(badgeOutline(provider.badgeColour(), day), "$provider on day")
        }
    }

    @Test
    fun `a service's colour on the page is its own, but Tidal's black on a dark theme, which is the text's`() {
        val text = Color.White
        assertEquals(text, readableOn(ProviderType.TIDAL.badgeColour(), night, text))
        assertEquals(ProviderType.TIDAL.badgeColour(), readableOn(ProviderType.TIDAL.badgeColour(), day, text))
        assertEquals(ProviderType.DEEZER.badgeColour(), readableOn(ProviderType.DEEZER.badgeColour(), night, text))
        assertEquals(ProviderType.APPLE_MUSIC.badgeColour(), readableOn(ProviderType.APPLE_MUSIC.badgeColour(), night, text))
        assertEquals(1f, contrast(night, night))
        assertTrue(contrast(Color.Black, Color.White) > 20f)
    }

    @Test
    fun `the Apple Music tile says whether anybody is signed in, and in which storefront`() {
        assertEquals("Checking with Apple…", appleMusicSummary(AppleMusicConnectionState(checking = true)))
        assertTrue(appleMusicSummary(AppleMusicConnectionState(storefront = "us")).contains("sign in"))
        assertEquals("Signed in · the GB storefront", appleMusicSummary(AppleMusicConnectionState(connected = true, storefront = "gb")))
        assertEquals("Signed in", appleMusicSummary(AppleMusicConnectionState(connected = true)))
    }

    @Test
    fun `the Deezer tile and page say whose profile is showing, by name once Deezer has said it`() {
        assertTrue(deezerSummary(DeezerConnectionState()).contains("profile"))
        assertEquals("Odile's playlists, loved tracks and albums", deezerSummary(DeezerConnectionState(profileId = "1234567", found = true, name = "Odile")))
        assertEquals("Profile 1234567", deezerSummary(DeezerConnectionState(profileId = "1234567")))
        assertEquals("Asking Deezer about the profile…", deezerSummary(DeezerConnectionState(checking = true)))
        assertNull(deezerStatus(DeezerConnectionState(message = "Deezer profile removed.")))
        assertEquals("Showing Odile, profile 1234567.", deezerStatus(DeezerConnectionState(profileId = "1234567", found = true, name = "Odile")))
        assertEquals("Showing profile 1234567.", deezerStatus(DeezerConnectionState(profileId = "1234567")))
    }

    @Test
    fun `a Deezer profile's number is read from its link as readily as on its own`() {
        assertEquals("1234567", deezerProfileNumber("1234567"))
        assertEquals("1234567", deezerProfileNumber("  https://www.deezer.com/en/profile/1234567  "))
        assertEquals("1234567", deezerProfileNumber("deezer.com/fr/profile/1234567/playlists"))
        assertEquals("1234567", deezerProfileNumber("https://www.deezer.com/pt-br/profile/1234567"))
        // A share link says nothing until Deezer is asked, so Save is left to core.
        assertNull(deezerProfileNumber("https://link.deezer.com/s/30Abc9xYz"))
        assertNull(deezerProfileNumber("https://www.deezer.com/en/album/2002"))
        assertNull(deezerProfileNumber("0"))
        assertNull(deezerProfileNumber(""))
    }

    @Test
    fun `the Tidal tile says first that there is no Tidal app, then who is signed in`() {
        assertEquals("Needs a Tidal app of your own", tidalSummary(TidalConnectionState()))
        assertEquals("Waiting for Tidal…", tidalSummary(TidalConnectionState(configured = true, connecting = true)))
        assertEquals("Signed in as Odile", tidalSummary(TidalConnectionState(configured = true, connected = true, accountName = "Odile")))
        assertEquals("Signed in", tidalSummary(TidalConnectionState(configured = true, connected = true)))
        assertEquals("Search works; connect for your collection", tidalSummary(TidalConnectionState(configured = true, readsCatalogue = true)))
        assertTrue(tidalSummary(TidalConnectionState(configured = true, ownApp = true)).contains("connect"))
    }

    // --- Scrobbling ---

    private fun connected(name: String?) = ScrobbleServiceState(ScrobbleConnectionStatus.CONNECTED, username = name)

    @Test
    fun `the Scrobbling tile names who is connected, each name once, and Libre fm among them`() {
        assertEquals("Connect Last.fm, Libre.fm or ListenBrainz", scrobblingSummary(ScrobbleState()))
        assertFalse(scrobblingAnywhere(ScrobbleState()))
        val libreOnly = ScrobbleState(libreFm = connected("sample-listener"))
        assertEquals("Connected: sample-listener", scrobblingSummary(libreOnly))
        assertTrue(scrobblingAnywhere(libreOnly))
        // The same listener on Last.fm and Libre.fm is said once; somebody else on ListenBrainz is said too.
        assertEquals(
            "Connected: sample-listener, another-listener",
            scrobblingSummary(
                ScrobbleState(lastFm = connected("sample-listener"), libreFm = connected("sample-listener"), listenBrainz = connected("another-listener")),
            ),
        )
    }

    @Test
    fun `the Scrobbling tile says first what needs connecting again, and what waits to be allowed when nothing is connected`() {
        val refused = ScrobbleServiceState(ScrobbleConnectionStatus.ERROR, message = "Libre.fm needs connecting again")
        assertEquals("Libre.fm needs connecting again", scrobblingSummary(ScrobbleState(lastFm = connected("sample-listener"), libreFm = refused)))
        val waiting = ScrobbleServiceState(ScrobbleConnectionStatus.AWAITING_APPROVAL, approvalUrl = "https://www.last.fm/api/auth/?api_key=key&token=t")
        assertEquals("Last.fm sign-in waiting for you", scrobblingSummary(ScrobbleState(lastFm = waiting)))
        assertFalse(scrobblingAnywhere(ScrobbleState(lastFm = waiting)))
        // Connected somewhere counts for more than a sign-in still waiting elsewhere.
        assertEquals("Connected: sample-listener", scrobblingSummary(ScrobbleState(lastFm = waiting, listenBrainz = connected("sample-listener"))))
    }

    // --- Home ---

    @Test
    fun `Home is not all put away while any service's rows are still on it`() {
        val hidden = setOf(HomePart.YOUTUBE_MUSIC, HomePart.SOUNDCLOUD, HomePart.PINNED, HomePart.RECENT)
        assertFalse(serviceRowsAllHidden(hidden))
        assertFalse(serviceRowsAllHidden(hidden + HomePart.BANDCAMP), "Spotify's and VK's rows are still there")
        val older = hidden + HomePart.BANDCAMP + HomePart.SPOTIFY + HomePart.VK
        assertFalse(serviceRowsAllHidden(older), "Apple Music's, Deezer's and Tidal's rows can still be there")
        assertTrue(serviceRowsAllHidden(older + HomePart.APPLE_MUSIC + HomePart.DEEZER + HomePart.TIDAL))
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
