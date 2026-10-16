package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToKey
import app.noctorium.android.ui.LibraryScreen
import app.noctorium.android.ui.LinkScreen
import app.noctorium.android.ui.SettingsPage
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.core.AppState
import app.noctorium.core.LibraryState
import app.noctorium.core.LinkState
import app.noctorium.core.LinkStatus
import app.noctorium.domain.Artist
import app.noctorium.domain.LinkKind
import app.noctorium.domain.MusicLink
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playlists.LocalPlaylist
import app.noctorium.playlists.PlaylistImport
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.SettingsState
import app.noctorium.settings.SpotifyConnectionState
import app.noctorium.settings.SpotifyDataImportState
import app.noctorium.settings.ThemePreset
import app.noctorium.spotify.SPOTIFY_LIKED_SONGS_TITLE
import app.noctorium.spotify.SPOTIFY_WITHOUT_SIGN_IN
import app.noctorium.spotify.SpotifyExportSkips
import app.noctorium.spotify.SpotifyImportSummary
import app.noctorium.spotify.SpotifyPublicLibrary
import app.noctorium.spotify.SpotifyPublicList
import app.noctorium.spotify.SpotifyPublicPlaylistState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spotify without a sign-in on the phone: the public playlists followed by their links and the import of Spotify's
 * data download on Spotify's page in Settings -- nothing followed, three followed (one cut at a hundred songs, one
 * Spotify would not show), an import under way, done and failed -- signed out and signed in; Keep in library under a
 * pasted link; and what the two put in the library. In the night theme, 98, Noctorium 98 and XP, on an ordinary
 * phone and the narrowest.
 *
 * Nothing here asks Spotify for anything. Every playlist, song, owner and id is made up, and what core would publish
 * once Spotify's public page or the file had been read is handed to the state as it is: no link is followed, no
 * page read, no file opened. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.SpotifyWithoutSignIn*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class SpotifyWithoutSignInScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (pageLooks("") + libraryLooks("")).only().map { arrayOf(it) }
    }
}

/** The same on the narrowest phone still sold, where the warnings and the playlists' lines have the least room. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class SpotifyWithoutSignInNarrowScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = (pageLooks("-narrow") + libraryLooks("-narrow")).only().map { arrayOf(it) }
    }
}

/** Settings' list on a screen tall enough to reach Spotify's tile, signed out with playlists followed and imported. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi", application = Application::class)
class SpotifyWithoutSignInTileScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = KINDS.filter { (kind, _) -> kind == "night" || kind == "98" }.map { (kind, preferences) ->
            Look(
                "spotify-free-tile-$kind",
                preferences,
                playing = { null },
                prepare = { state ->
                    state.libraryAs { copy(localPlaylists = IMPORTED) }
                    state.spotifyAs { copy(publicPlaylists = FOLLOWED) }
                },
            ) { _, state -> SettingsScreen(state) {} }
        }.only().map { arrayOf(it) }
    }
}

/** The night theme, 98, Noctorium 98 and XP, with the Windows taskbars' clock put away so two runs agree. */
private val KINDS = listOf(
    "night" to NoctoriumPreferences(),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    "noctorium-98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98_NOCTORIUM),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP),
).map { (kind, preferences) -> kind to preferences.copy(taskbarClock = false) }

/** Twenty-two letters and digits, as a Spotify id is, and none of them Spotify's. */
private const val HARBOUR = "MadeUpHarbourMornings1"
private const val FERRY = "MadeUpFerrySongs000001"
private const val PRIVATE = "MadeUpPrivateList00001"

/** Three followed: one as it was read, one the page cut at a hundred songs, and one Spotify would not show. */
private val FOLLOWED = listOf(
    SpotifyPublicPlaylistState(HARBOUR, "Harbour Mornings", "Night Owl", songCount = 42, readAtEpochSeconds = 1),
    SpotifyPublicPlaylistState(FERRY, "Every Ferry Song Ever Recorded, Probably", "Odile Brandt", songCount = 100, mayBeLonger = true, readAtEpochSeconds = 1),
    SpotifyPublicPlaylistState(PRIVATE, lastError = "This playlist is private or does not exist. Only public playlists can be read without signing in."),
)

/** A Spotify song, by an id that is not Spotify's. */
private fun song(id: String, title: String, artist: String, seconds: Long) = Track(
    provider = ProviderType.SPOTIFY,
    id = id,
    title = title,
    artists = listOf(Artist(artist, artist, ProviderType.SPOTIFY)),
    durationMs = seconds * 1_000,
    artworkUrl = null,
    sourceUrl = "https://example.invalid/spotify/$id",
)

private val SONGS = listOf(
    song("named:0001", "Shipping Forecast", "Harbour Lights Ensemble", 251),
    song("named:0002", "Low Tide at Leith", "Harbour Lights Ensemble", 198),
    song("named:0003", "Paper Lanterns", "Marrow & Fern", 189),
    song("named:0004", "Night Bus", "Saltmarsh", 174),
    song("named:0005", "Weathervane", "Juniper Kaye", 199),
)

/** The playlists the library lists for the two that could be read, as core makes them from their public pages. */
private val LISTED = listOf(
    SpotifyPublicLibrary.playlistOf(SpotifyPublicList(HARBOUR, "Harbour Mornings", "Night Owl", tracks = SONGS.take(3))),
    SpotifyPublicLibrary.playlistOf(SpotifyPublicList(FERRY, "Every Ferry Song Ever Recorded, Probably", "Odile Brandt", tracks = SONGS, mayBeLonger = true)),
).let { (harbour, ferry) -> listOf(harbour.copy(trackCount = 42), ferry.copy(trackCount = 100)) }

/** A playlist of the listener's own, and what an import of Spotify's file made beside it. */
private val IMPORTED: List<LocalPlaylist> = listOf(
    LocalPlaylist.create("Road trip", 0).copy(id = "local-road-trip", tracks = SONGS.take(2)),
    LocalPlaylist.create(SPOTIFY_LIKED_SONGS_TITLE, 0).copy(
        id = "local-liked",
        tracks = SONGS,
        importedFrom = PlaylistImport(PlaylistImport.SPOTIFY_DATA, "liked-songs"),
    ),
    LocalPlaylist.create("Kitchen Radio", 0).copy(
        id = "local-kitchen",
        tracks = SONGS.drop(1),
        importedFrom = PlaylistImport(PlaylistImport.SPOTIFY_DATA, "playlist:Kitchen Radio"),
    ),
)

/** What Spotify says of a sign-in its app's owner has no Premium for, and the two ways round it, as core says them. */
private val REFUSED = "Spotify signed you in, but it reads nothing through Noctorium's Spotify app until the account that owns " +
    "that app has Spotify Premium. If yours has Premium, a Spotify app of your own works instead, under Use your own " +
    "Spotify app in Settings. $SPOTIFY_WITHOUT_SIGN_IN"

private val IMPORT_DONE = SpotifyImportSummary(
    playlists = 12,
    songs = 840,
    likedSongs = 312,
    replaced = 0,
    emptyPlaylists = 1,
    skipped = SpotifyExportSkips(episodes = 3),
    savedAlbums = 4,
)

private fun page(page: SettingsPage): @androidx.compose.runtime.Composable (SettingsState, AppState) -> Unit = { _, state ->
    SettingsPageScreen(page, state, signIn = {}, back = {})
}

/** Scrolls Spotify's page until the card by that name is at its top. */
private fun scrollTo(card: String): ComposeContentTestRule.() -> Unit = {
    onNode(hasScrollToKeyAction()).performScrollToKey(card)
    mainClock.advanceTimeBy(500)
    waitForIdle()
}

/** Spotify's page in each state the two new cards can be in, each scrolled to the card it is about. */
private fun pageLooks(size: String): List<Look> = KINDS.flatMap { (kind, preferences) ->
    val none = { null }
    val public = scrollTo("spotify-public")
    val import = scrollTo("spotify-import")
    listOf(
        // The top of the page, signed out: the sign-in and its beta warning, as they always were.
        Look("spotify-free-top-$kind$size", preferences, playing = none, screen = page(SettingsPage.SPOTIFY)),
        Look("spotify-free-none-$kind$size", preferences, playing = none, act = public, screen = page(SettingsPage.SPOTIFY)),
        Look(
            "spotify-free-three-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.spotifyAs {
                    copy(publicPlaylists = FOLLOWED, publicMessage = "Added “Harbour Mornings”: 42 songs.")
                }
            },
            act = public,
            screen = page(SettingsPage.SPOTIFY),
        ),
        Look(
            "spotify-free-adding-$kind$size",
            preferences,
            playing = none,
            prepare = { state -> state.spotifyAs { copy(publicPlaylists = FOLLOWED.take(1), addingPublicPlaylist = true) } },
            act = public,
            screen = page(SettingsPage.SPOTIFY),
        ),
        Look(
            "spotify-free-importing-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.spotifyAs {
                    copy(dataImport = SpotifyDataImportState(importing = true, progress = "Reading the playlists in Playlist1.json…"))
                }
            },
            act = import,
            screen = page(SettingsPage.SPOTIFY),
        ),
        Look(
            "spotify-free-imported-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.spotifyAs { copy(dataImport = SpotifyDataImportState(message = IMPORT_DONE.message, summary = IMPORT_DONE)) }
            },
            act = import,
            screen = page(SettingsPage.SPOTIFY),
        ),
        Look(
            "spotify-free-import-failed-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.spotifyAs {
                    copy(
                        dataImport = SpotifyDataImportState(
                            // As core says it of a .zip with nothing of Spotify's in it.
                            message = "That is not Spotify's data download. Choose the my_spotify_data.zip Spotify " +
                                "emails, or the Playlist1.json and YourLibrary.json inside it.",
                            failed = true,
                        ),
                    )
                }
            },
            act = import,
            screen = page(SettingsPage.SPOTIFY),
        ),
        // Signed in, with the sign-in Spotify refuses for want of the app owner's Premium, and the three followed.
        Look(
            "spotify-free-signed-in-top-$kind$size",
            preferences.copy(spotifyAccountName = "Night Owl"),
            playing = none,
            prepare = { state ->
                state.spotifyAs { copy(connected = true, accountName = "Night Owl", message = REFUSED, publicPlaylists = FOLLOWED) }
            },
            screen = page(SettingsPage.SPOTIFY),
        ),
        Look(
            "spotify-free-signed-in-three-$kind$size",
            preferences.copy(spotifyAccountName = "Night Owl"),
            playing = none,
            prepare = { state ->
                state.spotifyAs { copy(connected = true, accountName = "Night Owl", message = REFUSED, publicPlaylists = FOLLOWED) }
            },
            act = public,
            screen = page(SettingsPage.SPOTIFY),
        ),
    )
}

/** What the two put in the library, and Keep in library under a pasted link, in the night theme, 98 and XP. */
private fun libraryLooks(size: String): List<Look> = KINDS.filter { (kind, _) -> kind != "noctorium-98" }.flatMap { (kind, preferences) ->
    val none = { null }
    val ferry = LISTED[1].copy(tracks = SONGS)
    listOf(
        Look(
            "spotify-free-library-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.libraryAs {
                    copy(
                        playlists = LISTED,
                        localPlaylists = IMPORTED,
                        errorMessage = "“Pier Lights at Four” is private now, or gone from Spotify, so it is shown as it was last read.",
                    )
                }
            },
        ) { _, state -> LibraryScreen(state) },
        Look(
            "spotify-free-library-public-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.libraryAs { copy(playlists = LISTED, localPlaylists = IMPORTED, openPlaylist = ferry, openPlaylistLoading = false) }
            },
        ) { _, state -> LibraryScreen(state) },
        Look(
            "spotify-free-library-liked-$kind$size",
            preferences,
            playing = none,
            prepare = { state -> state.libraryAs { copy(playlists = LISTED, localPlaylists = IMPORTED, openLocalPlaylist = IMPORTED[1]) } },
        ) { _, state -> LibraryScreen(state) },
        Look(
            "spotify-free-link-keep-$kind$size",
            preferences,
            playing = none,
            prepare = { state ->
                state.linkAs(
                    LinkState(
                        LinkStatus.DONE,
                        MusicLink(ProviderType.SPOTIFY, LinkKind.PLAYLIST, FERRY, "https://open.spotify.com/playlist/$FERRY"),
                        tracks = SONGS,
                        message = "Playing 5 songs. Spotify's public page shows only a playlist's first 100 songs.",
                        canKeepInLibrary = true,
                    ),
                )
            },
        ) { _, state -> LinkScreen(state) },
    )
}

/** One of the state's own flows, by its name, to hand it what core would have published. */
@Suppress("UNCHECKED_CAST")
private fun <T> AppState.flow(name: String): MutableStateFlow<T> =
    AppState::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this) as MutableStateFlow<T>

/**
 * Hands Spotify's settings [change], as core would publish it once a page or a file had been read -- and again for as
 * long as anything starting up still writes over it, as Spotify's sign-in and its followed playlists both are as the
 * state starts.
 */
private fun AppState.spotifyAs(change: SpotifyConnectionState.() -> SpotifyConnectionState) {
    val shown = flow<SettingsState>("mutableSettings")
    val deadline = System.currentTimeMillis() + 2_000
    while (System.currentTimeMillis() < deadline) {
        if (shown.value.spotify != shown.value.spotify.change()) shown.update { it.copy(spotify = it.spotify.change()) }
        Thread.sleep(50)
    }
}

/** The library as [change] makes it, listed just now, so opening the library does not read it again over this. */
private fun AppState.libraryAs(change: LibraryState.() -> LibraryState) {
    val shown = flow<LibraryState>("mutableLibrary")
    waitUntil { !library.value.loading }
    shown.update { it.change().copy(loading = false, loaded = true, loadedAtMillis = System.currentTimeMillis()) }
}

/** A pasted link, as core leaves it once it has opened: nothing is followed or read for it. */
private fun AppState.linkAs(link: LinkState) {
    flow<LinkState>("mutableLink").value = link
}
