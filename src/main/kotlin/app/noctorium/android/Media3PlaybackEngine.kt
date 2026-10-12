package app.noctorium.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.audiofx.AudioEffect
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.noctorium.domain.Track
import app.noctorium.net.networkFailureMessage
import app.noctorium.playback.AudioAnalysis
import app.noctorium.playback.AudioAnalysisKind
import app.noctorium.playback.MAX_SPEED
import app.noctorium.playback.MIN_SPEED
import app.noctorium.playback.MusicBackend
import app.noctorium.playback.PlaybackEngine
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.playback.Visualiser
import app.noctorium.playback.VolumeBoost
import app.noctorium.settings.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** The logcat tag playback failures are written under. */
private const val PLAYER_LOG_TAG = "NoctoriumPlayer"

/** The quietest volume the meter is made up for while the booster carries the volume: -60 dB, its floor. */
private const val METER_QUIETEST = .001f

/** How many times one track is brought back after its stream fails, before the listener is told. */
internal const val MAX_RECOVERIES = 2

/** The longest a recovery waits for the network to come back before giving up and saying so. */
private const val NETWORK_WAIT_MS = 30_000L

/** How long the player may be told to play and not move before the song is started again. See [stalled]. */
internal const val STALL_MS = 15_000L

/**
 * How long a player told to play with nothing loaded at all is given first: long enough for a prepare that is
 * already on its way -- the lock screen's play does one -- and no longer, since nothing else will happen.
 */
internal const val IDLE_STALL_MS = 3_000L

/** How many times one stall is answered by starting the song again before the listener is told. */
internal const val MAX_STALL_RESTARTS = 2

/**
 * Whether the player is stuck: told to play, with a song to play, not playing, and not at the song's end --
 * which is the queue's business -- while nothing of ours is still finding the song's address.
 */
internal fun stalled(hasTrack: Boolean, finding: Boolean, wantsToPlay: Boolean, playing: Boolean, ended: Boolean): Boolean =
    hasTrack && !finding && wantsToPlay && !playing && !ended

/** How a failed stream is brought back: straight away, or once there is a network to bring it back over. */
internal enum class Recovery { NOW, AFTER_NETWORK }

/**
 * Whether a player error is one a fresh address can fix, and if so when to fetch it.
 *
 * The stream-shaped failures only. A refused or changed stream is fetched again at once. A connection
 * that failed or timed out waits for the network first, since resolving needs it too. Everything else --
 * a format the phone cannot decode, a file that is not there, a clear-text address Android forbids -- is
 * the same the second time, and retrying it would only delay the message by the length of a retry.
 */
internal fun recoveryFor(errorCode: Int): Recovery? = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    -> Recovery.NOW
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> Recovery.AFTER_NETWORK
    else -> null
}

/**
 * Whether the platform lists an equaliser among the effects an app may attach to its own audio.
 *
 * Asked of the list rather than by building one, because building one needs a session and costs a native
 * object, and the answer is wanted in Settings before anything has played. A phone that lists one and then
 * refuses to build it is caught when it is built; one that cannot produce a list at all is given the
 * benefit of the doubt, for the same reason.
 */
private fun platformOffersEqualizer(): Boolean = runCatching {
    AudioEffect.queryEffects()?.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER } ?: true
}.getOrDefault(true)

/**
 * Gives the player an audio session of its own if it has none yet.
 *
 * The effects -- the boost and the equaliser -- attach to a session, and with none they have nothing to
 * attach to until a track starts. Media3 generates one as the player is built on Android 5 and later; this
 * only acts on a player that somehow arrives without one.
 */
@OptIn(UnstableApi::class)
private fun ensureAudioSession(player: ExoPlayer, context: Context) {
    if (player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
        runCatching { player.audioSessionId = Util.generateAudioSessionIdV21(context) }
            .onFailure { android.util.Log.w(PLAYER_LOG_TAG, "Could not give the player an audio session", it) }
    }
}

/**
 * Where the player's requests go out: the way they always have, except that VK's media hosts are asked as the
 * browser their addresses were issued to. VK refuses the audio -- playlist, segments and keys -- to any other,
 * and every other service is left on the agent it already answers.
 */
@OptIn(UnstableApi::class)
private fun mediaRequests(context: Context, vkAgent: () -> String?, opened: TransferListener): DataSource.Factory =
    ResolvingDataSource.Factory(DefaultDataSource.Factory(context).setTransferListener(opened)) { spec ->
        val headers = mediaRequestHeaders(spec.uri.host, vkAgent())
        if (headers.isEmpty()) spec else spec.withAdditionalHeaders(headers)
    }

/**
 * Media3's renderers as they always were, with two things added to the sound's way out: the booster, and after
 * it a tee that shows each buffer to [tee]'s sink and passes it on untouched, for the visualiser -- which so sees
 * the sound boosted, as the desktop's meter does. Everything else about the sink -- its float output, its
 * playback parameters, silence skipping and the speed after the tee -- is Media3's default.
 */
@OptIn(UnstableApi::class)
private class TeedRenderersFactory(
    context: Context,
    private val boost: BoostAudioProcessor,
    private val tee: TeeAudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(boost, tee))
            .build()
}

/** Media3's word for how samples are written, as the meter reads them; null for one it does not read. */
@OptIn(UnstableApi::class)
private fun pcmEncoding(encoding: Int): PcmEncoding? = when (encoding) {
    C.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
    C.ENCODING_PCM_24BIT -> PcmEncoding.PCM_24
    C.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32
    C.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
    else -> null
}


/**
 * Playing on Android, through Media3.
 *
 * The desktop drives mpv as a child process over a pipe, which is why closing the window there had to
 * explicitly kill it — and did not, once. Nothing like that applies here: ExoPlayer runs inside the
 * process and dies with it, and the thing that keeps music going when the screen is off is a foreground
 * service holding this, not a detached program.
 *
 * Media3 insists on being touched from the main thread. Every call below therefore hops there, and the
 * position ticker reads from there too, which is why the state flow is updated on a timer rather than from
 * a listener callback: ExoPlayer reports state changes, but not the passage of time.
 */
class Media3PlaybackEngine(
    context: Context,
    private val backend: MusicBackend,
    /** A track kept on the device plays from there and asks the network for nothing. */
    private val downloadedFile: (Track) -> Path? = { null },
    /** What the phone knows about its connection when a stream fails for a network reason. */
    private val networkProblem: () -> String? = { null },
    /** The browser VK issued its audio addresses to, once a VK song has been looked up; see [mediaRequestHeaders]. */
    private val vkAgent: () -> String? = { null },
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var ticker: Job? = null

    /**
     * How many times the current track has been brought back after its stream failed.
     *
     * A stream address can be refused after it was handed out -- it expired, or the phone moved to a
     * network it was not issued for -- and a connection can drop outright, in a tunnel, a lift, or the
     * moment a phone hands over from Wi-Fi to mobile data. The right answer to all of those is a new
     * address and the music picking up where it stopped, not a message asking the listener to press play.
     *
     * It used to be only the first of those, and only once: a dropped connection, the commonest way a
     * song stops on a phone that moves, went straight to an error. Now any of them, twice per track, and
     * a third failure is reported -- by then it is about the track, not the network.
     */
    private var recoveries = 0

    /**
     * How many starts are finding the song's address, [start] itself or a recovery on its way to one. The
     * player is not the thing being waited on then, so the stall watch leaves it alone. A count rather than a
     * flag: a start called off by the next song finishes after that song's has begun.
     */
    private val finding = java.util.concurrent.atomic.AtomicInteger()

    /** Armed while the player has been told to play and is not playing; see [watchForStall]. */
    private var stallWatch: Job? = null

    /** Times the stall now being watched has been answered by starting the song again. */
    private var stallRestarts = 0

    private val connectivity: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    /**
     * Drops every remembered stream address the moment the phone moves to a different network.
     *
     * YouTube's addresses belong to the network that asked for them, and the phone keeps them for hours
     * now. One fetched on Wi-Fi and played on mobile data is refused. Recovery would still catch that,
     * but only after a failed start and a gap. Forgetting them as the network changes means the next
     * track looks its address up again, and nothing has to fail first.
     *
     * Android reports the network it is already on as soon as this is registered. That first report is
     * where things start, not a change, so it is not treated as one.
     */
    private val networkWatch = object : ConnectivityManager.NetworkCallback() {
        private var current: Network? = null

        override fun onAvailable(network: Network) {
            val previous = current
            current = network
            if (previous != null && previous != network) {
                backend.forgetAllAudio()
                android.util.Log.i(PLAYER_LOG_TAG, "Network changed; stream addresses from the last one dropped")
            }
        }
    }.also { watch -> runCatching { connectivity?.registerDefaultNetworkCallback(watch) } }

    /**
     * Handed to the media session service, and to nothing else.
     *
     * The service is what makes the lock-screen controls, the notification and the Bluetooth buttons work,
     * and Media3 builds those around a Player rather than around an interface of ours. Exposing it is the
     * price of getting the behaviour a phone expects from a music app.
     */
    internal val mediaPlayer: ExoPlayer get() = player

    /** The sound as it goes by, for the visualiser; see [AudioMeter]. Keeps nothing until something watches. */
    private val meter = AudioMeter()

    /** The tee the sound passes through on its way out, showing each buffer to [meter] and changing none of it. */
    @OptIn(UnstableApi::class)
    private val tee = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
        @Volatile private var encoding: PcmEncoding? = null

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this.encoding = pcmEncoding(encoding)
            meter.begin(sampleRateHz, channelCount)
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            encoding?.let { meter.write(buffer, it) }
        }
    })

    /** The volume booster, on the sound on its way out: see [BoostAudioProcessor] and [setVolumeBoostPercent]. */
    internal val boost = BoostAudioProcessor()

    /**
     * How long each file is, by its address, as the server said when it was opened: what a stream's bitrate is
     * worked out from when the stream does not declare one. See [refreshFormat].
     */
    private val streamSizes = ConcurrentHashMap<String, Long>()

    @OptIn(UnstableApi::class)
    private val opened = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
            if (!isNetwork) return
            val total = runCatching { totalBytesFrom(source.responseHeaders, dataSpec.position) }.getOrNull() ?: return
            // A handful at most: one a song, and the next one's, looked up ahead.
            if (streamSizes.size > 32) streamSizes.clear()
            streamSizes[dataSpec.uri.toString()] = total
        }
        override fun onBytesTransferred(source: DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) = Unit
    }

    @OptIn(UnstableApi::class)
    private val player: ExoPlayer = ExoPlayer.Builder(context, TeedRenderersFactory(context, boost, tee))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            // Android's audio focus, so a phone call or a navigation prompt quietens this instead of
            // talking over it, and it resumes afterwards.
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        // Holds the CPU and the wifi radio up while a track is streaming.
        //
        // Without this, playback survives the screen going off only until the device decides to doze,
        // and then stops in the middle of a song with the notification still sitting there. The
        // foreground service keeps the process alive; it does not keep the radio awake. WAKE_LOCK was
        // already asked for in the manifest and, until now, never taken.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        // Starts with a second of audio in hand rather than two and a half. Measured on the phone, the
        // time from handing ExoPlayer an address to hearing anything was 1.3 seconds, and most of it was
        // waiting for the default buffer to fill from a server that delivers far faster than real time.
        // The buffer still grows to its usual size once the music is going; only the start is earlier.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    /* bufferForPlaybackMs = */ 1_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_000,
                )
                .build(),
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(mediaRequests(context, vkAgent, opened)))
        .build()
        .apply {
            // Start where the state says, not where ExoPlayer's own default is.
            //
            // Noctorium opens at 72%, and until something moved the slider nobody told the player
            // that: it began every session at unity while the slider drew 72%, and the first touch
            // of the slider was a jump down rather than the small change it looked like. The desktop
            // never had this because mpv is handed --volume on the command line of every track.
            volume = mutableState.value.volume

            // A session of its own from the start, so the equaliser has something to attach to before
            // the first track rather than only once one is playing. Media3 already generates one when it
            // is built on any Android this runs on; this is the guard against a version that does not.
            ensureAudioSession(this, context)

            // The equaliser hangs off the audio session, and it is not the same session afterwards if the
            // sink is torn down and rebuilt. Re-applying whenever it changes keeps it attached to the thing
            // actually playing. (The booster is in the sound itself, and goes wherever the sound goes.)
            addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                    applyEqualizer(audioSessionId)
                }

                /** What the song turned out to be once its sound began to arrive: the status bar's line. */
                override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    heardFormat = format
                    refreshFormat()
                }
            })
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    publish()
                    watchForStall()
                }

                /**
                 * Play pressed -- here, on the lock screen, in the notification or on headphones -- is a fresh
                 * go at the song, with tries of its own to recover it: whatever an hour-old dropped connection
                 * or last night's stall used up does not leave this one with none.
                 */
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        recoveries = 0
                        stallRestarts = 0
                    }
                    watchForStall()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish()
                    watchForStall()
                    // Re-arm here, not only at play(). The ticker below stops when nothing is playing,
                    // and "nothing is playing" is also true for the second after prepare() while the
                    // first packets arrive — so starting it once left it dead before the track began.
                    if (isPlaying) startTicking()
                }

                /**
                 * The same track starting over, because it was told to loop.
                 *
                 * This is the whole of repeat-one now: ExoPlayer carries on from the top without a gap
                 * and without a request, and says so here. The count is how the rest of Noctorium learns
                 * that a listen finished and another began.
                 */
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        mutableState.update { it.copy(loops = it.loops + 1, positionMs = 0) }
                        android.util.Log.i(PLAYER_LOG_TAG, "Looped ${mediaItem?.mediaId} (round ${mutableState.value.loops})")
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // The whole thing to the log, one line of it to the screen.
                    android.util.Log.w(PLAYER_LOG_TAG, "Playback failed: ${error.errorCodeName}", error)
                    val track = mutableState.value.track
                    val recovery = recoveryFor(error.errorCode)
                    if (recovery != null && track != null && recoveries < MAX_RECOVERIES && downloadedFile(track) == null) {
                        // The address, not the track: fetch a new one and pick up where this stopped.
                        recoveries++
                        val resumeAt = mutableState.value.positionMs
                        android.util.Log.i(
                            PLAYER_LOG_TAG,
                            "Stream failed (${error.errorCodeName}); attempt $recoveries of $MAX_RECOVERIES, " +
                                "fetching a fresh address and resuming at ${resumeAt}ms",
                        )
                        // The spinner rather than an error while it works: from the listener's side this
                        // is buffering, and it usually ends in the music carrying on.
                        mutableState.update { it.copy(status = PlaybackStatus.RESOLVING, errorMessage = null) }
                        // Paused stays paused: a song that failed while it sat paused gets its new address
                        // and waits, rather than starting by itself in somebody's pocket.
                        val playing = player.playWhenReady
                        finding.incrementAndGet()
                        scope.launch {
                            try {
                                if (recovery == Recovery.AFTER_NETWORK) awaitNetwork()
                                backend.forgetAudio(track.sourceUrl)
                                start(track, resumeAt, play = playing)
                            } finally {
                                finding.decrementAndGet()
                            }
                        }
                        return
                    }
                    // Paused as well as failed. Left wanting to play, the player took the next press of play -- on
                    // the lock screen above all -- as nothing new: it prepared the same refused address again and
                    // failed the same way, with no tries left, until another song was played and this one chosen
                    // again. Paused, the next press is a fresh go with fresh tries, and a new address with them.
                    player.playWhenReady = false
                    mutableState.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = describePlayerError(error)) }
                }
            })
        }

    /**
     * Resolves the audio and starts it.
     *
     * The address a service hands back is short-lived, so it is fetched at the moment of playing rather
     * than kept with the track. A downloaded copy short-circuits that entirely.
     */
    override suspend fun play(track: Track) {
        recoveries = 0
        stallRestarts = 0
        start(track, startAtMs = 0)
    }

    /**
     * Waits, briefly, for the phone to be back on a network that actually reaches the internet.
     *
     * A connection that dropped because the phone was between networks is not helped by resolving a new
     * address at once: the resolve itself needs the network, fails the same way, and turns a gap of a few
     * seconds into an error. So it waits for a network Android has validated, and gives up after
     * [NETWORK_WAIT_MS] -- past that, the listener is better told than left looking at a spinner.
     */
    private suspend fun awaitNetwork() {
        val manager = connectivity ?: return
        withTimeoutOrNull(NETWORK_WAIT_MS) {
            while (true) {
                val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
                if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) break
                delay(1_000)
            }
        }
    }

    /**
     * Resolves the audio and starts it at [startAtMs], which is zero for a fresh play and wherever the
     * music stopped when a refused address is being replaced. [play] false leaves it ready and paused.
     */
    private suspend fun start(track: Track, startAtMs: Long, play: Boolean = true) {
        finding.incrementAndGet()
        try {
            startFinding(track, startAtMs, play)
        } finally {
            finding.decrementAndGet()
            // Checked once the address is in hand, on the player's own thread: a launch, not a wait, since this
            // also runs when the start was called off.
            scope.launch { watchForStall() }
        }
    }

    private suspend fun startFinding(track: Track, startAtMs: Long, play: Boolean) {
        // A new song says nothing of its format until its sound arrives, rather than the last one's.
        heardFormat = null
        mutableState.update { it.copy(
            status = PlaybackStatus.RESOLVING,
            track = track,
            errorMessage = null,
            positionMs = startAtMs,
            durationMs = track.durationMs ?: 0,
            loops = 0,
            format = null,
        ) }

        val source = downloadedFile(track)?.toUri()?.toString()
            ?: runCatching { backend.resolveAudio(track.sourceUrl) }.getOrElse { error ->
                // Silence first. Without this the previous track keeps playing while the bar reports that
                // a different one failed, which reads as the error being wrong rather than the track being
                // unplayable.
                withContext(Dispatchers.Main) { runCatching { player.stop() } }
                mutableState.update { it.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.message ?: "Could not find an audio stream for this track.",
                ) }
                return
            }

        // Handing the address to the player can throw, and an exception escaping here kills the process:
        // this runs on the main looper, and nothing above it is catching. It happened — SoundCloud serves
        // HLS, Media3 loads that source factory reflectively by class name, and the artifact was missing,
        // so pressing play on a SoundCloud track took the whole application down. A track that cannot be
        // played has to be a message in the player bar, never an exit.
        val started = withContext(Dispatchers.Main) {
            runCatching {
                player.setMediaItem(mediaItemFor(track, source), startAtMs.coerceAtLeast(0))
                // The speed asked for, again for each track, as the equaliser is: ExoPlayer keeps it, and
                // saying it again costs nothing and leaves nothing to chance.
                player.playbackParameters = PlaybackParameters(speedWanted)
                player.prepare()
                player.playWhenReady = play
            }
        }
        started.onFailure { error ->
            android.util.Log.w(PLAYER_LOG_TAG, "Could not start $source", error)
            mutableState.update { it.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = "This track could not be played: " +
                    (error.message?.take(160) ?: error::class.java.simpleName),
            ) }
            return
        }
        startTicking()
    }

    /**
     * The track as the rest of the phone will see it.
     *
     * Media3 builds the notification, the lock screen and whatever a car or a watch shows from the
     * metadata on the item — not from anything the app draws. Without this it had the address and nothing
     * else, so the notification read "Noctorium is running" while a named song with cover art was playing
     * three centimetres above it.
     */
    private fun mediaItemFor(track: Track, source: String): MediaItem = MediaItem.Builder()
        .setUri(source)
        .setMediaId(track.queueKey)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistLine.takeIf(String::isNotBlank) ?: track.provider.displayName)
                .setAlbumTitle(track.album?.title)
                .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    override suspend fun pause() = withContext(Dispatchers.Main) { player.pause() }

    override suspend fun resume() = withContext(Dispatchers.Main) {
        // A fresh go, with tries of its own to recover: see onPlayWhenReadyChanged.
        recoveries = 0
        stallRestarts = 0
        val track = mutableState.value.track
        if (track != null && player.playbackState == Player.STATE_IDLE && finding.get() == 0) {
            // Nothing loaded to resume -- an error left the player empty -- and play() alone would do nothing at
            // all. The song is started again where it was, from a fresh address.
            android.util.Log.i(PLAYER_LOG_TAG, "Resume with nothing loaded; starting ${track.queueKey} again at ${mutableState.value.positionMs}ms")
            backend.forgetAudio(track.sourceUrl)
            start(track, mutableState.value.positionMs)
            return@withContext
        }
        player.play()
        startTicking()
    }

    /**
     * The song not playing when it was asked to, caught and started again.
     *
     * After hours with the phone locked, a paused song can come back with nothing to play from: the address it
     * was loading from has expired, the connection under it went with the network the phone slept on, or an
     * error left the player empty. When that is an error, [onPlayerError] answers it. When it is only a player
     * that has been told to play and does not -- buffering for ever, or idle with nothing loaded -- the only way
     * out used to be playing another song and coming back to this one. This does that for the listener: once
     * the player has wanted to play for [STALL_MS] without the music moving, the song starts again where it
     * was, from a fresh address. [MAX_STALL_RESTARTS] times; after that it says so, since by then it is not the
     * address.
     *
     * Armed and disarmed by the player's own events rather than a timer running all the time, so a paused
     * phone is not woken to check on music nobody asked for.
     */
    private fun watchForStall() {
        val stuck = stalled(
            hasTrack = mutableState.value.track != null,
            finding = finding.get() > 0,
            wantsToPlay = player.playWhenReady,
            playing = player.isPlaying,
            ended = player.playbackState == Player.STATE_ENDED,
        )
        if (!stuck) {
            stallWatch?.cancel()
            stallWatch = null
            // Music moving again ends the stall, and with it the count of restarts made for it.
            if (player.isPlaying) stallRestarts = 0
            return
        }
        if (stallWatch?.isActive == true) return
        val from = player.currentPosition
        val wait = if (player.playbackState == Player.STATE_IDLE) IDLE_STALL_MS else STALL_MS
        stallWatch = scope.launch {
            delay(wait)
            stallWatch = null
            val track = mutableState.value.track
            if (track == null || finding.get() > 0 || !player.playWhenReady || player.isPlaying) return@launch
            // Moving, only slowly: a phone on a poor connection buffering in fits and starts. Watched again.
            if (player.playbackState == Player.STATE_BUFFERING && player.currentPosition > from + 1_000) {
                watchForStall()
                return@launch
            }
            if (stallRestarts >= MAX_STALL_RESTARTS) {
                android.util.Log.w(PLAYER_LOG_TAG, "${track.queueKey} still stalled after $stallRestarts restarts; giving up")
                player.playWhenReady = false
                mutableState.update { it.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = "This song stopped loading, and starting it again did not help. Press play to try once more.",
                ) }
                return@launch
            }
            stallRestarts++
            val at = player.currentPosition.takeIf { player.playbackState != Player.STATE_IDLE && it > 0 }
                ?: mutableState.value.positionMs
            android.util.Log.i(
                PLAYER_LOG_TAG,
                "${track.queueKey} told to play and not playing (state ${player.playbackState}) for ${wait}ms; " +
                    "starting it again at ${at}ms, restart $stallRestarts of $MAX_STALL_RESTARTS",
            )
            if (!networkUp()) awaitNetwork()
            backend.forgetAudio(track.sourceUrl)
            start(track, at)
        }
    }

    /** Whether the phone is on a network Android has checked reaches the internet. */
    private fun networkUp(): Boolean {
        val manager = connectivity ?: return true
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    override suspend fun setVolume(value: Float) = withContext(Dispatchers.Main) {
        val clamped = value.coerceIn(0f, 1f)
        mutableState.update { it.copy(volume = clamped) }
        applyVolume()
    }

    /**
     * The booster as the switch it was before it had levels: on is the last level used above 100%, 200% the
     * first time. Core now sets a level ([setVolumeBoostPercent]); this is kept for anything still asking the
     * old way.
     */
    override suspend fun setVolumeBoost(enabled: Boolean) =
        setVolumeBoostPercent(if (enabled) lastBoostPercent else VolumeBoost.OFF_PERCENT)

    /**
     * The volume booster, from 100% (off) to 1000%: [boost], on the sound itself on its way to the speaker --
     * the listener's volume first, then a limiter and a clipper, as everywhere (see [VolumeBoost]).
     *
     * It lives in the player's sound rather than on a song or an audio session, so it carries from one song to the
     * next, through a stop and a new start, and through whatever Android does to the session. Every phone has it:
     * it is Noctorium's own arithmetic, not an effect a phone might not offer.
     */
    override suspend fun setVolumeBoostPercent(percent: Int) = withContext(Dispatchers.Main) {
        boostPercent = VolumeBoost.clamp(percent)
        if (VolumeBoost.isOn(boostPercent)) lastBoostPercent = boostPercent
        applyVolume()
        mutableState.update { it.copy(
            volumeBoostEnabled = VolumeBoost.isOn(boostPercent),
            volumeBoostPercent = boostPercent,
            volumeBoostAvailable = true,
        ) }
        // What the booster now does, for whoever is checking a phone over adb.
        android.util.Log.i(
            PLAYER_LOG_TAG,
            if (!VolumeBoost.isOn(boostPercent)) {
                "Volume boost off"
            } else {
                "Volume boost $boostPercent%: gain ${VolumeBoost.gain(boostPercent, mutableState.value.volume)}, " +
                    "drive ${VolumeBoost.drive(boostPercent)}"
            },
        )
        Unit
    }

    /**
     * Puts the volume, the mute and the booster where they should be, between the player and [boost].
     *
     * With the booster off the volume is the player's, as it always was. With it on, the player plays at unity and
     * the volume is in the booster's gain instead, in front of its limiter: a volume below the top is room, and
     * the booster uses it up before it limits anything -- at 72%, 130% is plain gain. The player's own volume
     * comes after everything and can only ever turn the sound down, so the booster used to limit at full scale
     * and then have the sound turned down again. Muting is the player's in either case, so it is immediate.
     *
     * The meter sees the sound after the booster and would read the volume in it while the booster is on; it is
     * told to take it back out, so the bars read the same with the booster on as off.
     */
    private fun applyVolume() {
        val state = mutableState.value
        val boosting = VolumeBoost.isOn(boostPercent)
        boost.setLevel(boostPercent, state.volume)
        meter.gain = if (boosting) 1f / state.volume.coerceAtLeast(METER_QUIETEST) else 1f
        player.volume = when {
            state.isMuted -> 0f
            boosting -> 1f
            else -> state.volume
        }
    }

    /** The level the listener asked for. */
    private var boostPercent = VolumeBoost.OFF_PERCENT

    /** The last level above 100%, for the old switch to go back to. */
    private var lastBoostPercent = VolumeBoost.FIRST_PERCENT

    // --- The visualiser's feed ---

    private val mutableAnalysis = MutableStateFlow<AudioAnalysis?>(null)

    /**
     * What the sound is doing, measured from the sound itself as it leaves for the speaker: the ten bands and
     * each channel's levels, [Visualiser.FRAMES_PER_SECOND] times a second while a song plays, and null while
     * none does. Measured only while something collects this -- a visualiser on screen -- and as much as
     * [setAnalysis] asks for; with neither, the tee keeps nothing and nothing is worked out.
     */
    override val analysis: StateFlow<AudioAnalysis?> = mutableAnalysis.asStateFlow()

    /** Every frame of the phone's own sound can be seen whole, so a spectrum, for every stream it plays. */
    override val analysisKind: AudioAnalysisKind get() = AudioAnalysisKind.SPECTRUM

    /** As much as the listener's visualiser wants, from core: nothing until it says. */
    private var analysisWanted = AudioAnalysisKind.NONE

    /** Whether anything is collecting [analysis] just now. */
    private var analysisWatched = false

    private var meterJob: Job? = null
    private val meterClock = MeterClock()

    override suspend fun setAnalysis(wanted: AudioAnalysisKind) = withContext(Dispatchers.Main) {
        analysisWanted = wanted
        runMeter()
    }

    /** Starts measuring while something watches and something is wanted, and stops the moment either is not. */
    private fun runMeter() {
        val wanted = if (analysisWatched) analysisWanted else AudioAnalysisKind.NONE
        meterJob?.cancel()
        meterJob = null
        meter.listening = wanted != AudioAnalysisKind.NONE
        if (wanted == AudioAnalysisKind.NONE) {
            mutableAnalysis.value = null
            return
        }
        meterClock.restart()
        meterJob = scope.launch {
            while (isActive) {
                mutableAnalysis.value = if (player.isPlaying) measure(wanted) else null
                delay(Visualiser.FRAME_MS)
            }
        }
    }

    /** One frame: the sound at the place the speaker is at, worked out away from the main thread. */
    private suspend fun measure(wanted: AudioAnalysisKind): AudioAnalysis? {
        val heard = meterClock.heard(
            positionMs = player.currentPosition,
            stretches = meter.stretches,
            stretchStart = meter.stretchStart,
            written = meter.written,
            sampleRate = meter.sampleRate,
            ringSize = AudioMeter.RING_FRAMES,
        )
        return withContext(Dispatchers.Default) { meter.analyse(heard, spectrum = wanted == AudioAnalysisKind.SPECTRUM) }
    }

    // --- What is playing, for the status bar ---

    /** The format Media3 found the current song's sound in, once it began to arrive. */
    @Volatile private var heardFormat: Format? = null

    /**
     * The status bar's description of the song, from [heardFormat]: its codec, rate and channels, and its
     * bitrate -- the stream's own where it declares one, or worked out from the file's size and the song's
     * length where a plain file does not (see [streamSizes]). Only ever about the song this state describes.
     */
    @OptIn(UnstableApi::class)
    private fun refreshFormat() {
        val format = heardFormat ?: return
        val held = player.currentMediaItem
        val shown = mutableState.value.track?.queueKey
        if (held?.mediaId != null && shown != null && held.mediaId != shown) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: mutableState.value.durationMs
        val address = held?.localConfiguration?.uri
        val bytes = when (address?.scheme) {
            "file" -> address.path?.let { runCatching { java.io.File(it).length() }.getOrNull() }
            null -> null
            else -> streamSizes[address.toString()]
        }
        val described = streamFormatOf(
            mimeType = format.sampleMimeType,
            codecs = format.codecs,
            sampleRateHz = format.sampleRate,
            channels = format.channelCount,
            bitrate = format.averageBitrate.takeIf { it > 0 } ?: format.bitrate,
            estimatedBitrate = estimatedBitrate(bytes, duration),
        )
        if (described != mutableState.value.format) mutableState.update { it.copy(format = described) }
    }

    init {
        // Watched or not, as the visualisers on screen come and go.
        scope.launch {
            mutableAnalysis.subscriptionCount.map { it > 0 }.distinctUntilChanged().collect { watched ->
                analysisWatched = watched
                runMeter()
            }
        }
    }

    private companion object {
        /**
         * The ordinary priority, not a raised one. Priority only matters when two apps reach for the same
         * session's equaliser, and an app built for that -- a system-wide equaliser somebody installed --
         * should be able to win over this one rather than have its settings quietly undone.
         */
        const val EQUALIZER_PRIORITY = 0
    }

    /**
     * The listener's equaliser, built from Android's own and attached to this player's audio session.
     *
     * The phone's equaliser has bands of its own rather than the ten Noctorium shows, so each of its bands
     * is set to what the ten-band curve is at that band's centre (see [deviceBandLevels]). Like the boost
     * it is best-effort: a phone that refuses to build one is remembered as having none, which Settings
     * then says plainly instead of offering sliders that change nothing.
     *
     * Media3 wants its player touched on the main thread, and the session id is read from it, so the
     * effect is built and changed there too.
     */
    override suspend fun setEqualizer(settings: EqualizerSettings) = withContext(Dispatchers.Main) {
        equalizerWanted = settings
        applyEqualizer()
    }

    /**
     * Whether this phone lets an app shape its sound.
     *
     * Starts from what the platform lists among its effects, so Settings can say so before anything has
     * played, and turns false for good if building one fails when it is actually wanted -- a listed effect
     * is a promise some phones do not keep.
     */
    val equalizerAvailable: StateFlow<Boolean> get() = mutableEqualizerAvailable

    private val mutableEqualizerAvailable = MutableStateFlow(platformOffersEqualizer())

    /** What the listener asked for, as opposed to what the audio session currently has on it. */
    private var equalizerWanted = EqualizerSettings()

    /** Released with the player: an effect outlives its session otherwise and leaks the native object. */
    private var equalizer: android.media.audiofx.Equalizer? = null

    /** The session [equalizer] was built for, so a replacement session is noticed rather than shaped by nothing. */
    private var equalizerSession = C.AUDIO_SESSION_ID_UNSET

    /**
     * Put the wanted curve on whatever session the player has right now.
     *
     * Nothing is built until a curve that actually changes the sound is asked for: an equaliser switched
     * off, or switched on and left flat, should cost the phone nothing. One already built is switched off
     * rather than released, so turning it back on a moment later does not build it all over again.
     */
    private fun applyEqualizer(session: Int = player.audioSessionId) {
        val wanted = equalizerWanted
        if (!wanted.shapesSound) {
            runCatching { equalizer?.enabled = false }
            return
        }
        if (!mutableEqualizerAvailable.value || session == C.AUDIO_SESSION_ID_UNSET) return

        if (equalizer == null || equalizerSession != session) {
            runCatching { equalizer?.release() }
            equalizer = null
            equalizerSession = C.AUDIO_SESSION_ID_UNSET
            val built = runCatching { android.media.audiofx.Equalizer(EQUALIZER_PRIORITY, session) }
            built.onFailure { error ->
                // Not worth retrying with every change: a phone that will not build one now will not
                // build one for the next slider either. Settings says so from here on.
                android.util.Log.w(PLAYER_LOG_TAG, "This phone would not give Noctorium an equaliser", error)
                mutableEqualizerAvailable.value = false
                return
            }
            equalizer = built.getOrNull()
            equalizerSession = session
        }

        val effect = equalizer ?: return
        runCatching {
            val range = effect.bandLevelRange
            val centres = (0 until effect.numberOfBands).map { band -> effect.getCenterFreq(band.toShort()) }
            deviceBandLevels(wanted, centres, range[0].toInt(), range[1].toInt()).forEachIndexed { band, level ->
                effect.setBandLevel(band.toShort(), level)
            }
            effect.enabled = true
        }.onFailure { error ->
            // A band the phone refused is not a reason to give up on the equaliser: the next change, or
            // the next session, sets the whole curve again from the start.
            android.util.Log.w(PLAYER_LOG_TAG, "Could not set the equaliser's bands", error)
        }
    }

    override suspend fun setMuted(muted: Boolean) = withContext(Dispatchers.Main) {
        mutableState.update { it.copy(isMuted = muted) }
        applyVolume()
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) {
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    /**
     * How fast to play, from half to double, the pitch left where it is.
     *
     * Told by core, from Noctorium's own setting, the way the equaliser is -- and kept here and said again as
     * each track starts, so it carries from one song to the next whatever happens to the player in between.
     */
    override suspend fun setSpeed(speed: Float) = withContext(Dispatchers.Main) {
        speedWanted = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        // A speed alone keeps the pitch at one: faster, not higher.
        player.playbackParameters = PlaybackParameters(speedWanted)
    }

    /** What the listener asked for, as opposed to whatever the player happens to hold. */
    private var speedWanted = 1f

    /**
     * Silence-skipping, as the listener set it: a phone's own setting, which nothing in core knows of.
     *
     * Pushed in from outside rather than read here, because the engine is handed no settings and there is no
     * reason for it to grow a dependency on them. Called again whenever it changes. ExoPlayer keeps it across
     * tracks.
     */
    fun setSkipSilence(enabled: Boolean) {
        scope.launch { player.skipSilenceEnabled = enabled }
    }


    /**
     * Why playback stopped, in words for the listener rather than for whoever reads the log.
     *
     * "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: Unable to resolve host" is what ExoPlayer says. It is
     * accurate and it is no use to anybody on a train. The code is mapped where it is unambiguous and
     * the cause chain is read where it is not.
     */
    private fun describePlayerError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            networkProblem() ?: "No internet connection. Check your connection and press play again."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The service refused this track's stream twice. Try it again in a minute."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The audio for this track has gone missing."
        else -> networkFailureMessage(error)
            ?: error.message?.takeIf { it.isNotBlank() }?.take(160)
            ?: "This track could not be played (${error.errorCodeName})."
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        ticker?.cancel()
        stallWatch?.cancel()
        stallWatch = null
        player.stop()
        player.clearMediaItems()
        // Back to no song, and nothing else back to its default: the volume, the mute and the booster are the
        // listener's and outlast the song. Built afresh, the booster read 100% here while the session went on
        // boosting, and the button forgot the boost that was still in force -- and a mute went quiet about itself
        // while the player stayed silent.
        mutableState.update { current ->
            PlaybackState(
                volume = current.volume,
                isMuted = current.isMuted,
                volumeBoostEnabled = current.volumeBoostEnabled,
                volumeBoostPercent = current.volumeBoostPercent,
                volumeBoostAvailable = current.volumeBoostAvailable,
            )
        }
    }

    /**
     * Repeat-one, done by ExoPlayer itself.
     *
     * It goes back to the start of the same item without a gap and without asking the network for the
     * track again, and reports each time round through onMediaItemTransition. Repeat-all stays with the
     * queue, which is why this is a switch and not a mode.
     */
    override suspend fun setLooping(enabled: Boolean) = withContext(Dispatchers.Main) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun close() {
        ticker?.cancel()
        stallWatch?.cancel()
        meterJob?.cancel()
        meter.listening = false
        runCatching { connectivity?.unregisterNetworkCallback(networkWatch) }
        // Release has to happen on the thread the player was built on, and the process may be going away,
        // so this does not wait for a coroutine to be scheduled.
        // The effect first: it holds a native object tied to the session the player is about to drop.
        runCatching { equalizer?.release() }
        equalizer = null
        equalizerSession = C.AUDIO_SESSION_ID_UNSET
        player.release()
        scope.cancel()
    }

    /**
     * A position that moves.
     *
     * ExoPlayer announces state changes but never the passage of time, so the seek bar is fed from a
     * timer. It runs only while something is playing, so a paused app is not waking the CPU twice a second
     * for a number that is not changing.
     */
    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                publish()
                // Buffering counts as still going. Stopping on it is what made the seek bar sit at 0:00
                // for the whole track: the loop was started the instant after prepare(), found the player
                // not yet playing, and ended before a single second had elapsed.
                val moving = player.isPlaying || player.playbackState == Player.STATE_BUFFERING
                if (!moving) break
                delay(500)
            }
        }
    }

    private fun publish() {
        /*
         * A report about a track this state no longer describes is dropped.
         *
         * While the next track resolves, the previous one is still playing in ExoPlayer, and its ticker
         * and its callbacks keep arriving. Letting them through flipped the status from RESOLVING back to
         * PLAYING and wrote the old track's position onto the new one -- so a track that failed to resolve
         * came up at 0:11, which is where the one before it happened to be. Each item carries its track
         * key, so the two can be told apart.
         */
        val held = player.currentMediaItem?.mediaId
        val shown = mutableState.value.track?.queueKey
        if (held != null && shown != null && held != shown) return

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        mutableState.update { it.copy(
            status = when {
                player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.RESOLVING
                player.isPlaying -> PlaybackStatus.PLAYING
                player.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
                player.playbackState == Player.STATE_ENDED -> PlaybackStatus.IDLE
                else -> mutableState.value.status
            },
            // Not read from a stopped player. After stop() ExoPlayer still reports where the last track
            // finished, and this ran on the callback that stop() posts -- so a track that failed to resolve
            // came up showing the previous one's final position, 3:37 of a song it never started.
            positionMs = if (player.playbackState == Player.STATE_IDLE) it.positionMs else player.currentPosition.coerceAtLeast(0),
            // The player's own length once it knows one, since that is what the bar is drawn against.
            durationMs = duration ?: mutableState.value.durationMs,
        ) }
        // The bitrate of a plain file is worked out from its length, which arrives after its format does.
        if (mutableState.value.format?.bitsPerSecond == null) refreshFormat()
    }

}
