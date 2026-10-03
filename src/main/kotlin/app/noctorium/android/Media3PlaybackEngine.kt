package app.noctorium.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import app.noctorium.domain.Track
import app.noctorium.net.networkFailureMessage
import app.noctorium.playback.MusicBackend
import app.noctorium.playback.PlaybackEngine
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import app.noctorium.settings.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path

/** The logcat tag playback failures are written under. */
private const val PLAYER_LOG_TAG = "NoctoriumPlayer"

/** The range the rate is clamped to, past which speech stops being speech. */
private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 2f

/** How many times one track is brought back after its stream fails, before the listener is told. */
internal const val MAX_RECOVERIES = 2

/** The longest a recovery waits for the network to come back before giving up and saying so. */
private const val NETWORK_WAIT_MS = 30_000L

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

    private val player: ExoPlayer = ExoPlayer.Builder(context)
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

            // The volume boost and the equaliser hang off the audio session, and it is not the same
            // session afterwards if the sink is torn down and rebuilt. Re-applying whenever it changes
            // keeps both attached to the thing actually playing.
            addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                    applyBoost(audioSessionId)
                    applyEqualizer(audioSessionId)
                }
            })
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = publish()

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish()
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
                        scope.launch {
                            if (recovery == Recovery.AFTER_NETWORK) awaitNetwork()
                            backend.forgetAudio(track.sourceUrl)
                            start(track, resumeAt)
                        }
                        return
                    }
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
     * music stopped when a refused address is being replaced.
     */
    private suspend fun start(track: Track, startAtMs: Long) {
        mutableState.update { it.copy(
            status = PlaybackStatus.RESOLVING,
            track = track,
            errorMessage = null,
            positionMs = startAtMs,
            durationMs = track.durationMs ?: 0,
            loops = 0,
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
                player.prepare()
                player.play()
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
        player.play()
        startTicking()
    }

    override suspend fun setVolume(value: Float) = withContext(Dispatchers.Main) {
        val clamped = value.coerceIn(0f, 1f)
        player.volume = clamped
        mutableState.update { it.copy(volume = clamped) }
    }

    /**
     * Android's own loudness enhancer, attached to this player's audio session.
     *
     * ExoPlayer's `volume` stops at unity and multiplying past it would only clip, which is why this used
     * to say no. `LoudnessEnhancer` is the part of the platform meant for exactly this: it sits in the
     * effect chain on the session and applies a target gain in millibels, compressing rather than simply
     * scaling, so a quiet track gets louder instead of squarer. The same trade the desktop's compressor
     * makes, done by the phone's own audio framework.
     *
     * Best-effort throughout. The effect needs a real audio session, some devices refuse to allocate one,
     * and a phone with nothing playing has no session at all -- so a failure leaves the toggle off rather
     * than pretending.
     */
    override suspend fun setVolumeBoost(enabled: Boolean) = withContext(Dispatchers.Main) {
        boostWanted = enabled
        applyBoost()
    }

    /**
     * Put the wanted boost on whatever session the player has right now.
     *
     * Kept apart from the toggle because the answer changes underneath it. A player that has not opened
     * its audio sink yet has no session to attach an effect to, and one that reopens it -- a headset
     * arriving, a phone call ending -- gets a different one. Asking again every time the session changes
     * is what stops a boost switched on during a buffering track from quietly doing nothing.
     */
    private fun applyBoost(session: Int = player.audioSessionId) {
        val applied = runCatching {
            if (!boostWanted || session == C.AUDIO_SESSION_ID_UNSET) {
                loudness?.enabled = false
                return@runCatching false
            }
            if (loudnessSession != session) {
                loudness?.release()
                loudness = LoudnessEnhancer(session)
                loudnessSession = session
            }
            loudness?.setTargetGain(BOOST_GAIN_MILLIBELS)
            loudness?.enabled = true
            true
        }.getOrDefault(false)
        mutableState.update { it.copy(volumeBoostEnabled = applied) }
    }

    /** What the listener asked for, as opposed to what the audio session currently has on it. */
    private var boostWanted = false

    /** Released with the player; an effect outlives its session otherwise and leaks the native object. */
    private var loudness: LoudnessEnhancer? = null

    /** The session [loudness] was built for, so a replacement is noticed instead of boosting nothing. */
    private var loudnessSession = C.AUDIO_SESSION_ID_UNSET

    private companion object {
        /**
         * Nine decibels, in the hundredths Android counts them in.
         *
         * Enough to be plainly louder on a phone speaker and short of where a loudness enhancer starts
         * sounding pumped and flat. It is a fixed amount rather than a second slider because the one
         * question somebody has here is whether this is loud enough, not by how many decibels.
         */
        const val BOOST_GAIN_MILLIBELS = 900

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

    /** Released with the player, for the same reason as [loudness]: it holds a native object. */
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
        player.volume = if (muted) 0f else mutableState.value.volume
        mutableState.update { it.copy(isMuted = muted) }
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) {
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    /**
     * Rate and silence-skipping, as the listener set them.
     *
     * Pushed in from outside rather than read here, because the engine is handed no settings and there
     * is no reason for it to grow a dependency on them. Called again whenever either changes.
     */
    fun applyAudioOptions(speed: Float, skipSilence: Boolean) {
        scope.launch {
            player.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
            player.skipSilenceEnabled = skipSilence
        }
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
        player.stop()
        player.clearMediaItems()
        mutableState.value = PlaybackState(volume = mutableState.value.volume)
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
        runCatching { connectivity?.unregisterNetworkCallback(networkWatch) }
        // Release has to happen on the thread the player was built on, and the process may be going away,
        // so this does not wait for a coroutine to be scheduled.
        // The effect first: it holds a native object tied to the session the player is about to drop.
        runCatching { loudness?.release() }
        loudness = null
        loudnessSession = C.AUDIO_SESSION_ID_UNSET
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
    }

}
