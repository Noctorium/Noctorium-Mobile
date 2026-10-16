package app.noctorium.android.ui

import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.playlists.LocalPlaylist
import app.noctorium.playlists.PlaylistImport
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.spotify.SPOTIFY_LIKED_SONGS_TITLE
import app.noctorium.spotify.SpotifyPublicLibrary
import app.noctorium.spotify.SpotifyPublicList
import app.noctorium.spotify.SpotifyPublicPlaylistState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spotify without a sign-in, as the phone says it: the tile, each followed public playlist's line on Spotify's page,
 * the playlists an import made among the listener's own, and the file the import is given.
 */
class PhoneSpotifyWithoutSignInTest {
    private val followed = listOf(
        SpotifyPublicPlaylistState("MadeUpHarbourMornings1", "Harbour Mornings", "Night Owl", songCount = 42),
        SpotifyPublicPlaylistState("MadeUpFerrySongs000001", "Ferry Songs", "Odile", songCount = 100, mayBeLonger = true),
        SpotifyPublicPlaylistState("MadeUpPrivateList00001", lastError = "This playlist is private or does not exist."),
    )

    private fun imported(title: String, key: String) = LocalPlaylist.create(title, 0).copy(importedFrom = PlaylistImport(PlaylistImport.SPOTIFY_DATA, key))

    @Test
    fun `signed out, the Spotify tile says what is in the library without a sign-in`() {
        assertEquals("3 public playlists followed, no sign-in needed", spotifySummary(SpotifyConnectionState(publicPlaylists = followed)))
        assertEquals("1 public playlist followed, no sign-in needed", spotifySummary(SpotifyConnectionState(publicPlaylists = followed.take(1))))
        assertEquals("12 playlists imported from Spotify's data download", spotifySummary(SpotifyConnectionState(), imported = 12))
        assertEquals("1 playlist imported from Spotify's data download", spotifySummary(SpotifyConnectionState(), imported = 1))
        assertEquals("3 public playlists followed · 12 imported", spotifySummary(SpotifyConnectionState(publicPlaylists = followed), imported = 12))
        // With neither, the sign-in is offered as before.
        assertTrue(spotifySummary(SpotifyConnectionState()).contains("Premium"))
    }

    @Test
    fun `signed in, or signing in, the Spotify tile says what it always said`() {
        val signedIn = SpotifyConnectionState(connected = true, accountName = "Odile", publicPlaylists = followed)
        assertEquals("Odile · songs play matched on YouTube Music", spotifySummary(signedIn, imported = 12))
        assertEquals("Waiting for Spotify…", spotifySummary(SpotifyConnectionState(connecting = true, publicPlaylists = followed), imported = 3))
    }

    @Test
    fun `only the playlists Spotify's data download made are counted as imported`() {
        val own = LocalPlaylist.create("Road trip", 0)
        val other = LocalPlaylist.create("From elsewhere", 0).copy(importedFrom = PlaylistImport("somewhere-else", "x"))
        val playlists = listOf(own, imported(SPOTIFY_LIKED_SONGS_TITLE, "liked-songs"), imported("Ferry Songs", "playlist:Ferry Songs"), other)
        assertEquals(2, importedFromSpotify(playlists))
        assertEquals(0, importedFromSpotify(listOf(own)))
    }

    @Test
    fun `a followed playlist's line says whose it is, how many songs, and when it is only the first hundred`() {
        assertEquals("Night Owl · 42 tracks", spotifyPublicLine(followed[0]))
        // As the library says it.
        assertEquals("Odile · 100 tracks · first 100 songs", spotifyPublicLine(followed[1]))
        // Never read, and the reason in red apart: nothing to say on this line.
        assertEquals("", spotifyPublicLine(followed[2]))
        assertEquals("Not read yet", spotifyPublicLine(SpotifyPublicPlaylistState("MadeUpPrivateList00001")))
        // A copy from before still says what it was, though the last read failed.
        assertEquals("Night Owl · 42 tracks", spotifyPublicLine(followed[0].copy(lastError = "Could not reach Spotify's public page.")))
    }

    @Test
    fun `a followed playlist opens as the library lists it, or as Settings knows it before the library is read`() {
        val listed = SpotifyPublicLibrary.playlistOf(
            SpotifyPublicList(id = followed[1].id, title = "Ferry Songs", owner = "Odile", artworkUrl = "https://example.invalid/cover.jpg", mayBeLonger = true),
        )
        val other = Playlist(id = followed[1].id, title = "Same id, other service", provider = ProviderType.SOUNDCLOUD)
        assertEquals(listed, publicPlaylistToOpen(followed[1], listOf(other, listed)))
        val made = publicPlaylistToOpen(followed[1], listOf(other))
        assertEquals(ProviderType.SPOTIFY, made.provider)
        assertEquals(followed[1].id, made.id)
        assertEquals("Ferry Songs", made.title)
        assertEquals(listed.ownerName, made.ownerName)
        assertEquals("https://open.spotify.com/playlist/${followed[1].id}", made.sourceUrl)
        // One never read is opened by its id, which is all there is to call it.
        assertEquals(followed[2].id, publicPlaylistToOpen(followed[2], emptyList()).title)
    }

    @Test
    fun `a playlist the import made says so, where one made here says it was made here`() {
        val own = LocalPlaylist.create("Road trip", 0)
        assertEquals("made here", localPlaylistOrigin(own))
        assertEquals("made in Noctorium", localPlaylistOrigin(own, short = false))
        val liked = imported(SPOTIFY_LIKED_SONGS_TITLE, "liked-songs")
        assertEquals("imported from Spotify", localPlaylistOrigin(liked))
        assertEquals("imported from Spotify", localPlaylistOrigin(liked, short = false))
    }

    @Test
    fun `the file chosen is called what the picker calls it, or the end of its address, or the file`() {
        assertEquals("my_spotify_data.zip", importFileName("my_spotify_data.zip", "primary:Download/other.zip"))
        assertEquals("my_spotify_data.zip", importFileName("  my_spotify_data.zip ", null))
        // A document's address ends in the volume, a colon and the path.
        assertEquals("my_spotify_data.zip", importFileName(null, "primary:Download/my_spotify_data.zip"))
        assertEquals("Playlist1.json", importFileName("", "msf:1000001234/Playlist1.json"))
        assertEquals("1000001234", importFileName(" ", "msf:1000001234"))
        assertEquals("the file", importFileName(null, null))
        assertEquals("the file", importFileName(null, "primary:"))
    }

    @Test
    fun `the picker offers the zip and the json, and every other file`() {
        assertTrue("application/zip" in SPOTIFY_EXPORT_TYPES)
        assertTrue("application/json" in SPOTIFY_EXPORT_TYPES)
        assertTrue("*/*" in SPOTIFY_EXPORT_TYPES)
    }
}
