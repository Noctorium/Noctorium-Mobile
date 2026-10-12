package app.noctorium.android

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import app.noctorium.playback.RepeatMode

/**
 * The player as the lock screen, the notification and a headset see it.
 *
 * ExoPlayer is only ever given one track at a time. The queue lives in `core`, because it is the same
 * queue on the desktop, it survives a track failing to resolve, and filling ExoPlayer's own playlist
 * would mean resolving fifty stream URLs up front that expire before most of them are reached.
 *
 * The cost of that, until now, was silent: with a single item in its timeline ExoPlayer correctly reports
 * that there is nothing to skip to, Media3 believes it, and the skip button on the lock screen, in the
 * notification and on a pair of headphones does nothing at all. It is not disabled or missing -- it is
 * there, and pressing it has no effect, which is worse. The session advertised SKIP_TO_PREVIOUS and not
 * SKIP_TO_NEXT, which is exactly what a one-item timeline looks like from the outside.
 *
 * So the session is handed this instead: the same player, telling the truth about the real queue and
 * passing skips to the thing that owns it.
 *
 * While this phone is another device's remote control the music is not here, and nothing that reaches the session
 * -- a headset's button, a car's, a notification left from before -- may start it here: the queue would be playing
 * in two rooms at once. The player here is idle then (the music went with the queue), so the notification and the
 * lock screen have nothing to show; and play, pause and the skips are passed to the application, whose buttons
 * drive the device the music is on, as the player on screen does. See [remote].
 */
class QueueAwarePlayer(
    player: Player,
    private val goNext: () -> Unit,
    private val goPrevious: () -> Unit,
    private val canGoNext: () -> Boolean,
    private val canGoPrevious: () -> Boolean,
    /** The queue's repeat mode, which is the truth; the bare player only knows whether it is looping. */
    private val repeatMode: () -> RepeatMode = { RepeatMode.OFF },
    private val setRepeat: (RepeatMode) -> Unit = {},
    /**
     * Whether the music is on another device with this phone as its remote control, and if so whether it is playing
     * there: null while it is here. Read at each press.
     */
    private val remote: () -> RemotePlaying? = { null },
    /** The application's play button: while [remote], the other device's. */
    private val togglePlayback: () -> Unit = {},
) : ForwardingPlayer(player) {

    /** What a press of play or pause does while the music is on another device. */
    enum class RemotePlaying { PLAYING, PAUSED }

    /** Play: the other device's while the music is there, which starts nothing here. */
    override fun play() {
        when (remote()) {
            null -> super.play()
            RemotePlaying.PAUSED -> togglePlayback()
            RemotePlaying.PLAYING -> Unit
        }
    }

    override fun pause() {
        when (remote()) {
            null -> super.pause()
            RemotePlaying.PLAYING -> togglePlayback()
            RemotePlaying.PAUSED -> Unit
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (remote() == null) super.setPlayWhenReady(playWhenReady) else if (playWhenReady) play() else pause()
    }

    /** Loading the song here to play it, which while the music is elsewhere is exactly what must not happen. */
    override fun prepare() {
        if (remote() == null) super.prepare()
    }

    /**
     * Repeat as the queue has it, not as ExoPlayer has it.
     *
     * ExoPlayer is told to loop only for repeat-one; repeat-all is the queue's business, and reporting
     * the player's own setting would tell a car's head unit that repeat was off while the queue went
     * round. Setting it from outside goes to the queue for the same reason.
     */
    override fun getRepeatMode(): Int = when (repeatMode()) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    override fun setRepeatMode(repeatMode: Int) = setRepeat(
        when (repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
    )

    /**
     * What Android is allowed to ask for.
     *
     * Media3 reads this once per state change and builds the notification's buttons from it, so a command
     * left out here is a button that does nothing rather than a button that is missing.
     */
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> canGoNext()
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> canGoPrevious()
        else -> super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = canGoNext()

    override fun hasPreviousMediaItem(): Boolean = canGoPrevious()

    /**
     * All four go to the queue, through the application's own next and previous.
     *
     * Previous starts the song again once it is five seconds in, and goes to the song before only sooner than
     * that -- the rule the buttons in the application keep (see AppState.previous). Media3's own seekToPrevious
     * keeps a rule like it with three seconds; it is the application's that applies, so a lock screen and the
     * app never disagree about what the same press does.
     */
    override fun seekToNext() = goNext()

    override fun seekToNextMediaItem() = goNext()

    override fun seekToPrevious() = goPrevious()

    override fun seekToPreviousMediaItem() = goPrevious()
}
