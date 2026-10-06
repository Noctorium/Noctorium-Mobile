package app.noctorium.android

import app.noctorium.settings.DataSaver
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/*
 * The data saver, worked out apart from the backend that obeys it, so what it chooses can be checked
 * without a network or a phone.
 */

/**
 * Whether the data saver wants the smallest audio right now: never, always, or while the connection is one
 * paid for by the megabyte. [metered] is asked only when the answer depends on it.
 */
internal fun savesData(saver: DataSaver, metered: () -> Boolean): Boolean = when (saver) {
    DataSaver.OFF -> false
    DataSaver.ON_MOBILE_DATA -> metered()
    DataSaver.ALWAYS -> true
}

/**
 * The audio to play out of what a track's page offers: the best, or with [smallest] the one that spends the
 * least data while still being the same thing.
 *
 * "The same thing" is what keeps the small choice safe to make blind. Only streams fetched the way the best
 * one is are considered, so a phone that plays the best plays this; and never SoundCloud's Opus, which comes
 * as HLS segments in an Ogg container, a kind Media3's HLS player has no reader for -- the best stream is
 * never that one, being the smallest SoundCloud offers. Only the same audio track, too, so a video with
 * dubbed tracks does not trade its language for a few kilobits. A stream that does not say its bitrate is
 * not taken for a small one. If nothing qualifies, the best is played.
 */
internal fun chooseAudioStream(streams: List<AudioStream>, smallest: Boolean): AudioStream? {
    val playable = streams.filter { !it.content.isNullOrBlank() }
    val best = playable.maxByOrNull(AudioStream::getAverageBitrate) ?: return null
    if (!smallest) return best
    return playable
        .filter {
            it.averageBitrate > 0 &&
                it.deliveryMethod == best.deliveryMethod &&
                it.audioTrackId == best.audioTrackId &&
                !(it.deliveryMethod == DeliveryMethod.HLS && it.format == MediaFormat.OPUS)
        }
        .minByOrNull(AudioStream::getAverageBitrate)
        ?: best
}
