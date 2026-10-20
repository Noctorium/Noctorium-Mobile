package app.noctorium.android

import android.app.Application
import app.noctorium.android.ui.speedToCarryOver
import coil.ImageLoader
import coil.ImageLoaderFactory
import app.noctorium.account.NoctoriumAccountClient
import app.noctorium.connect.DeviceKind
import app.noctorium.core.AppState
import app.noctorium.discord.NoPresenceReporter
import app.noctorium.downloads.DownloadManager
import app.noctorium.net.Http
import app.noctorium.playback.MusicBackend
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.UncheckedSession
import app.noctorium.settings.AppDirectories
import app.noctorium.settings.SecretStore
import app.noctorium.settings.SettingsRepository
import app.noctorium.social.SoundCloudLikeClient
import app.noctorium.social.SoundCloudPlaylistClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe

/**
 * Everything Noctorium needs on a phone, built once.
 *
 * This is the whole of the wiring. `core` holds the application — the library, likes, playlists, the
 * queue, downloads, settings, scrobbling, the Noctorium account and the Spotify matching — written against
 * interfaces, and this is where the Android answers to them are chosen. There is no dependency-injection
 * framework because there is nothing here one would help with: a handful of objects, in a fixed order,
 * none of them optional.
 */
class NoctoriumApplication : Application(), ImageLoaderFactory, KeepAliveHost, PlaybackHost {

    /** Every cover in the application comes through here: see [artworkImageLoader]. */
    override fun newImageLoader(): ImageLoader = artworkImageLoader(this)

    lateinit var backend: MusicBackend
        private set
    lateinit var player: Media3PlaybackEngine
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var state: AppState
        private set

    /** One browser for every SoundCloud write, shared so there is one page rather than one each. */
    private lateinit var soundCloudBrowser: WebViewRequester

    /** The application's own work over time: keep-alive following its setting, Connect, and what plays. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        /**
         * Named before anything reads a path.
         *
         * `core` keeps every settings file, download index and cookie jar as a `java.nio.file.Path`, and
         * the search it does on the desktop — LOCALAPPDATA, then the home directory — finds nothing
         * writable on Android. An app is handed one private directory and has no business writing anywhere
         * else, so it is simply told.
         */
        AppDirectories.useBase(filesDir.toPath().resolve("noctorium"))

        // NewPipe requires a downloader before any extraction, and sharing the application's OkHttp client
        // means one connection pool rather than two on a device where opening TLS costs battery.
        NewPipe.init(OkHttpNewPipeDownloader(Http.shared))

        val settingsRepository = SettingsRepository()
        secrets = KeystoreSecretStore(this)
        backend = NewPipeBackend(
            http = Http.shared,
            downloadDirectory = filesDir.toPath().resolve("noctorium").resolve("downloads"),
            refuseDownload = {
                // Read at the moment of downloading, not once at startup: both the setting and the
                // connection change while the application is running.
                val wifiOnly = settingsRepository.load().phone.downloadOnWifiOnly
                if (wifiOnly && isOnMeteredConnection()) {
                    "Downloads are set to Wi-Fi only, and this is mobile data. " +
                        "Change it under Settings, on this phone."
                } else {
                    null
                }
            },
            // What the phone knows about its connection when a lookup fails: more than the resolver does.
            networkProblem = { describeNetworkProblem() },
        )
        val downloads = DownloadManager(backend)

        soundCloudBrowser = WebViewRequester(this)
        player = Media3PlaybackEngine(
            this,
            backend,
            downloadedFile = downloads::localFile,
            networkProblem = { describeNetworkProblem() },
            vkAgent = { (backend as? NewPipeBackend)?.vkAgent },
        )

        state = AppState(
            ytDlp = backend,
            settingsRepository = settingsRepository,
            credentials = secrets,
            system = AndroidBridge(this),
            downloads = downloads,
            playbackEngine = player,
            // A phone has no other browser to borrow a session from and no yt-dlp to probe with, so a
            // saved session is taken at its word rather than reported as broken.
            accountProbe = UncheckedSession,
            // Discord's presence arrives over a named pipe to its desktop app. There is neither here.
            discordPresence = NoPresenceReporter(),
            // What the desktop shows in its device list. The marketing name is what somebody recognises;
            // MODEL alone reads as a part number on a lot of phones.
            deviceName = {
                listOfNotNull(
                    android.os.Build.MANUFACTURER?.replaceFirstChar(Char::uppercase),
                    android.os.Build.MODEL,
                ).distinct().joinToString(" ").ifBlank { "Phone" }
            },
            deviceKind = DeviceKind.PHONE,
            // Likes and playlists are both written from the phone's own browser, through one WebView
            // between them. SoundCloud's bot protection answers an ordinary request with a captcha
            // however it is dressed; see [WebViewRequester].
            likeClient = SoundCloudLikeClient(soundCloudBrowser),
            playlistClient = SoundCloudPlaylistClient(soundCloudBrowser),
            networkPresence = WifiPresence(this),
            // The real service, except in a debug build pointed at one on the computer through adb; see DebugHooks.
            accountClient = DebugHooks.serviceUrl(this)?.let { NoctoriumAccountClient(baseUrl = it) } ?: NoctoriumAccountClient(),
            updateInstaller = AndroidUpdateInstaller(this),
        )
        (backend as? NewPipeBackend)?.let { pipe ->
            // Private SoundCloud tracks are found through the state's SoundCloud session, which exists only now.
            pipe.privateSoundCloudStream = state::soundCloudPrivateStream
            // The data saver, read when a song is looked up rather than once: both the setting and the
            // connection change while Noctorium is running, and the settings are the state's from here on.
            pipe.savingData = { savesData(state.settings.value.preferences.phone.dataSaver) { isOnMeteredConnection() } }
        }
        // The phone kept a speed of its own before speed was one of Noctorium's settings. Carried over once,
        // so a speed somebody chose is not quietly lost, and set back to normal where it was kept before.
        speedToCarryOver(state.settings.value.preferences)?.let { speed ->
            state.setPlaybackSpeed(speed)
            state.updatePhone { copy(playbackSpeed = 1f) }
        }
        keepAlive()
    }

    /**
     * "Keep Noctorium alive", wired to the state: the heartbeat left by the process before this one read before
     * anything can clear it, the heartbeat kept from here on, and the setting and Connect followed as they change.
     *
     * Starting it is not done here. This runs for every start of the process -- the phone starting up, the watchdog,
     * Android restarting a service -- and whichever of those it was starts keep-alive itself, saying so; opening
     * Noctorium is one of them (see MainActivity).
     */
    private fun keepAlive() {
        val on = state.settings.map { it.preferences.phone.keepAlive }.distinctUntilChanged()
        KeepAlive.processStarted(this, keepAliveOn)
        KeepAlive.follow(this, state.playback, on, scope)
        scope.launch { on.drop(1).collect { KeepAlive.switched(this@NoctoriumApplication, it) } }
        // The quiet notification says whether the listener's other devices can reach this phone; said again when that changes.
        scope.launch {
            state.connect.map { it.available }.distinctUntilChanged().drop(1).collect { KeepAliveNotification.refresh(this@NoctoriumApplication) }
        }
        // And, keep-alive or not, the music kept in the foreground while it plays here: see BackgroundPlay.
        BackgroundPlay.follow(this, state.playback, scope)
    }

    /** Whether the player is playing, or loading to play: what the music needs the foreground for. */
    override fun playingHere(): Boolean = player.mediaPlayer.let {
        it.playWhenReady && (it.playbackState == androidx.media3.common.Player.STATE_READY || it.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
    }

    /**
     * The player the media session is built around, for the media session service.
     *
     * Wrapped, so that skipping from the lock screen reaches the queue that actually holds the tracks. See
     * QueueAwarePlayer: the bare ExoPlayer only ever has one item and reports, correctly and uselessly, that there is
     * nothing to skip to.
     */
    override fun sessionPlayer(): androidx.media3.common.Player = QueueAwarePlayer(
        player = player.mediaPlayer,
        goNext = state::next,
        goPrevious = state::previous,
        // The queue the player shows: this phone's, or the one on the device it is the remote control of.
        canGoNext = { state.shownQueue.value.hasNext },
        // A song playing can always go back to its start, so the first in the queue keeps the button too.
        canGoPrevious = { state.canGoPrevious || state.connect.value.target != null },
        repeatMode = { state.shownQueue.value.repeatMode },
        setRepeat = state.queue::setRepeat,
        remote = {
            when {
                state.connect.value.target == null -> null
                state.shownPlayback.value.isPlaying -> QueueAwarePlayer.RemotePlaying.PLAYING
                else -> QueueAwarePlayer.RemotePlaying.PAUSED
            }
        },
        togglePlayback = state::togglePlayback,
    )

    override val keepAliveOn: Boolean
        get() = ::state.isInitialized && state.settings.value.preferences.phone.keepAlive

    override fun turnOffKeepAlive() {
        if (::state.isInitialized) state.updatePhone { copy(keepAlive = false) }
    }

    override val reachableOverConnect: Boolean
        get() = ::state.isInitialized && state.connect.value.available

    /**
     * The kept queue's song, played from [positionMs] -- what keep-alive does when it brings Noctorium back mid-song.
     *
     * Only if it is the song keep-alive noted, and nothing has played here since this process started, nor has another
     * device been taken over: whatever the listener did since is what stands. The place goes to the player before play
     * is pressed, which is where play picks a song up from (see [Media3PlaybackEngine.holdPlace]) -- once the state
     * has seen it there, since the state follows the player a moment behind and decides play from what it has seen.
     */
    override fun pickUp(queueKey: String, positionMs: Long): Boolean {
        if (!::state.isInitialized) return false
        val song = state.queue.state.value.current?.takeIf { it.queueKey == queueKey } ?: return false
        if (state.playback.value.track != null || state.playback.value.status != PlaybackStatus.IDLE) return false
        if (state.connect.value.target != null) return false
        player.holdPlace(song, positionMs)
        scope.launch {
            val held = withTimeoutOrNull(PICK_UP_WAIT_MS) {
                state.playback.first { it.track?.queueKey == queueKey && it.positionMs == positionMs }
            }
            // Played anyway if the state never caught up: from where the queue was last written down, then.
            if (held == null) android.util.Log.w("NoctoriumKeepAlive", "The place was not seen in time; playing from the queue's")
            state.togglePlayback()
        }
        return true
    }

    override fun onTerminate() {
        scope.cancel()
        soundCloudBrowser.close()
        state.close()
        super.onTerminate()
    }
}

/** How long a song keep-alive picks up waits for the state to see its place before it plays anyway. */
private const val PICK_UP_WAIT_MS = 2_000L
