package app.noctorium.android.screenshots

import app.noctorium.connect.DeviceKind
import app.noctorium.core.AppState
import app.noctorium.domain.Artist
import app.noctorium.domain.HomeSection
import app.noctorium.domain.PlaybackContext
import app.noctorium.domain.Playlist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.SearchResults
import app.noctorium.domain.Track
import app.noctorium.platform.SystemBridge
import app.noctorium.playback.MusicBackend
import app.noctorium.playback.PlaybackEngine
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playlists.PinnedTracksRepository
import app.noctorium.playlists.RecentTracksRepository
import app.noctorium.providers.MusicProvider
import app.noctorium.settings.AppDirectories
import app.noctorium.settings.CookieSource
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.SecretStore
import app.noctorium.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path

/*
 * A still life for the screenshots: the real application state, built from parts that answer from memory.
 *
 * The screens are drawn from an AppState, and a real one reaches out as it starts -- Home asks the services
 * for their rows, Connect looks for devices, the updater asks GitHub. Here every one of those either has a
 * stand-in that answers with the made-up music below, or is switched off in the settings it starts from,
 * so a screenshot is the same every time and nothing leaves the machine. Covers are left out for the same
 * reason; the screens draw their own placeholder where a cover would be.
 */

/** Music that does not exist, by people who do not exist, at addresses that cannot resolve. */
internal object Still {
    private fun track(provider: ProviderType, id: String, title: String, artist: String, seconds: Long) = Track(
        provider = provider,
        id = id,
        title = title,
        artists = listOf(Artist("artist-$id", artist, provider)),
        durationMs = seconds * 1_000,
        artworkUrl = null,
        sourceUrl = "https://example.invalid/$id",
    )

    val nowPlaying = track(ProviderType.YOUTUBE_MUSIC, "np", "Lanterns Over the Harbour", "The Quiet Hours", 247)

    val pinned = listOf(
        track(ProviderType.YOUTUBE_MUSIC, "p1", "Paper Satellites", "Marrow & Fern", 212),
        track(ProviderType.SOUNDCLOUD, "p2", "Low Tide Static", "Odile Brandt", 305),
        track(ProviderType.YOUTUBE_MUSIC, "p3", "Glasshouse", "Northern Lights Committee", 198),
    )

    val recent = listOf(
        nowPlaying,
        track(ProviderType.YOUTUBE_MUSIC, "r1", "Halfway to Morning", "Juniper Kaye", 233),
        track(ProviderType.SOUNDCLOUD, "r2", "Tape Hiss Lullaby", "Saltmarsh", 181),
        track(ProviderType.YOUTUBE_MUSIC, "r3", "Signal Fires", "The Quiet Hours", 264),
    )

    val youTubeShelf = HomeSection(
        id = "ytm-mixes",
        title = "Your mixes",
        provider = ProviderType.YOUTUBE_MUSIC,
        subtitle = "Made from what you play",
        tracks = listOf(
            track(ProviderType.YOUTUBE_MUSIC, "y1", "Copper Skies", "Ilse Varga", 221),
            track(ProviderType.YOUTUBE_MUSIC, "y2", "The Long Way Round", "Marrow & Fern", 256),
            track(ProviderType.YOUTUBE_MUSIC, "y3", "Weathervane", "Juniper Kaye", 199),
        ),
    )

    val soundCloudShelf = HomeSection(
        id = "sc-stream",
        title = "From your stream",
        provider = ProviderType.SOUNDCLOUD,
        subtitle = "New from people you follow",
        tracks = listOf(
            track(ProviderType.SOUNDCLOUD, "s1", "Night Bus (Demo)", "Saltmarsh", 174),
            track(ProviderType.SOUNDCLOUD, "s2", "Fog Machine", "Odile Brandt", 288),
            track(ProviderType.SOUNDCLOUD, "s3", "Second Draft", "Pell Mell", 203),
        ),
    )

    val shelves = listOf(youTubeShelf, soundCloudShelf)
}

/**
 * The application state for one screenshot, settled before it is drawn.
 *
 * Settings, pins and the recently played list are written to a folder of their own first, as a phone would
 * have them on disk, so the state reads them in the ordinary way.
 */
internal fun stillState(
    preferences: NoctoriumPreferences,
    playing: Track? = null,
    pinned: List<Track> = Still.pinned,
    recent: List<Track> = Still.recent,
    shelves: List<HomeSection> = Still.shelves,
): AppState {
    val base: Path = Files.createTempDirectory("noctorium-screenshots")
    AppDirectories.useBase(base)
    val settings = SettingsRepository(base.resolve("settings.json"))
    settings.save(
        preferences.copy(
            // Nothing that reaches out: no update check, no Connect, and the one-time question about
            // playing in the background already answered so it does not cover the screen.
            updates = preferences.updates.copy(checkOnLaunch = false),
            connect = preferences.connect.copy(enabled = false),
            phone = preferences.phone.copy(askedAboutBackground = true),
        ),
    )
    val state = AppState(
        ytDlp = StillBackend,
        credentials = StillSecrets(),
        system = StillSystem,
        playbackEngine = StillEngine(playing),
        injectedProviders = listOf(
            StillProvider(ProviderType.YOUTUBE_MUSIC, shelves),
            StillProvider(ProviderType.SOUNDCLOUD, shelves),
        ),
        settingsRepository = settings,
        pinnedRepository = PinnedTracksRepository(base.resolve("pinned.json")).also { it.save(pinned) },
        recentRepository = RecentTracksRepository(base.resolve("recent.json")).also { it.save(recent) },
        deviceName = { "Screenshot phone" },
        deviceKind = DeviceKind.PHONE,
    )
    // Home's rows arrive in the background. Waited for, so the picture is of the screen once it has
    // settled rather than of a spinner.
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline) {
        val ui = state.ui.value
        if (!ui.homeLoading && ui.homeSections.size == shelves.size) break
        Thread.sleep(20)
    }
    return state
}

/** A player holding one track, playing and a minute and a half into it, that never moves on. */
private class StillEngine(track: Track?) : PlaybackEngine {
    override val state: StateFlow<PlaybackState> = MutableStateFlow(
        if (track == null) {
            PlaybackState()
        } else {
            PlaybackState(status = PlaybackStatus.PLAYING, track = track, positionMs = 83_000, durationMs = track.durationMs ?: 0)
        },
    )

    override suspend fun play(track: Track) = Unit
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun setVolume(value: Float) = Unit
    override suspend fun setVolumeBoost(enabled: Boolean) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override suspend fun stop() = Unit
    override fun close() = Unit
}

/** A service whose Home is whichever of the made-up rows are its own, and which finds nothing else. */
private class StillProvider(override val type: ProviderType, private val shelves: List<HomeSection>) : MusicProvider {
    override suspend fun getHome(): List<HomeSection> = shelves.filter { it.provider == type }
    override suspend fun search(query: String): SearchResults = SearchResults()
    override suspend fun getTrack(id: String): Track? = null
    override suspend fun getRecommendations(context: PlaybackContext): List<Track> = emptyList()
}

/** A backend that knows nothing and is never asked for anything that matters. */
private object StillBackend : MusicBackend {
    override fun useSession(provider: ProviderType, source: CookieSource) = Unit
    override fun useSoundCloudProfile(username: String) = Unit
    override val soundCloudProfile: String = ""
    override suspend fun search(provider: ProviderType, query: String, limit: Int): List<Track> = emptyList()
    override suspend fun listPlaylists(provider: ProviderType, url: String, limit: Int): List<Playlist> = emptyList()
    override suspend fun listTracks(provider: ProviderType, url: String, limit: Int): List<Track> = emptyList()
    override suspend fun resolveTracks(provider: ProviderType, url: String, from: Int, to: Int): List<Track> = emptyList()
    override suspend fun enrichMetadata(track: Track): Track = track
    override suspend fun resolveAudio(sourceUrl: String): String = sourceUrl
    override suspend fun resolveSoundCloudPermalink(userId: String): String? = null
    override suspend fun downloadAudio(sourceUrl: String, outputTemplate: String, onProgress: (Float) -> Unit) = Unit
    override suspend fun exportAudio(sourceUrl: String, outputTemplate: String, onProgress: (Float) -> Unit) = Unit
    override suspend fun describe(): String = "Screenshots"
}

private class StillSecrets : SecretStore {
    private val secrets = mutableMapOf<String, String>()
    override fun put(key: String, secret: String) {
        secrets[key] = secret
    }

    override fun get(key: String): String? = secrets[key]
    override fun remove(key: String) {
        secrets.remove(key)
    }
}

private object StillSystem : SystemBridge {
    override fun openUrl(url: String) = Unit
    override fun copyToClipboard(text: String) = Unit
    override fun defaultExportFolder(): Path? = null
}
