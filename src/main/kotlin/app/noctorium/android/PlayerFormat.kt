package app.noctorium.android

import app.noctorium.playback.StreamFormat

/*
 * What is playing, as the status bar says it -- "Opus, stereo, 48 kHz, 128 kbit/s" -- from what Media3 knows of
 * the stream. Kept apart from the engine, and from Media3's own types, so the sums can be checked on their own.
 */

/**
 * The stream's format from Media3's numbers for it: the sample MIME type ("audio/opus", "audio/mp4a-latm"), or the
 * codecs string where there is no MIME type, the rate, the channels and the bitrate. Media3 says -1 for anything
 * it does not know, and so does anything else here at zero or below; [estimatedBitrate] stands in for a bitrate a
 * plain file does not declare. Null when nothing at all is known.
 */
internal fun streamFormatOf(
    mimeType: String?,
    codecs: String?,
    sampleRateHz: Int,
    channels: Int,
    bitrate: Int,
    estimatedBitrate: Int? = null,
): StreamFormat? {
    val format = StreamFormat(
        codec = mimeType?.takeIf { it.isNotBlank() } ?: codecs?.takeIf { it.isNotBlank() },
        sampleRateHz = sampleRateHz.takeIf { it > 0 },
        channels = channels.takeIf { it > 0 },
        bitsPerSecond = bitrate.takeIf { it > 0 } ?: estimatedBitrate?.takeIf { it > 0 },
    )
    return format.takeIf { it.describe() != null }
}

/**
 * How long the whole file is, in bytes, from the answer to a request for it: the total after the slash of a
 * `Content-Range` ("bytes 0-1048575/3456789"), or the `Content-Length` of an answer that began at the start of the
 * file ([requestedFrom] zero) and is not itself a range. Null where the answer does not say -- a live stream, a
 * server that hides it. Header names are compared without regard to case, as HTTP has them.
 */
internal fun totalBytesFrom(headers: Map<String, List<String>>, requestedFrom: Long): Long? {
    fun header(name: String): String? =
        // The status line comes back from Java under a null name, which equals() below takes in its stride.
        headers.entries.firstOrNull { (key, _) -> (key as String?).equals(name, ignoreCase = true) }?.value?.firstOrNull()?.trim()
    header("Content-Range")?.let { range ->
        return range.substringAfterLast('/', "").trim().toLongOrNull()?.takeIf { it > 0 }
    }
    if (requestedFrom != 0L) return null
    return header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
}

/**
 * The average bitrate of a file of [bytes] that plays for [durationMs], for a stream that does not declare its
 * own: near enough for a status bar, the container's own few bytes counted in with the sound. Null where either
 * is unknown, or the answer would be no bitrate any music is sent at.
 */
internal fun estimatedBitrate(bytes: Long?, durationMs: Long): Int? {
    if (bytes == null || bytes <= 0 || durationMs < 1_000) return null
    val bits = bytes * 8_000.0 / durationMs
    return bits.takeIf { it in 8_000.0..20_000_000.0 }?.toInt()
}
