package app.noctorium.android

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import app.noctorium.domain.artworkAt
import app.noctorium.domain.artworkBucket
import app.noctorium.net.Http
import app.noctorium.settings.ArtworkColour
import coil.ImageLoader
import coil.imageLoader
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The image loader for the whole application.
 *
 * Two things over Coil's own. Every cover is asked for at the size it will be drawn -- see [artworkAt]: a
 * YouTube Music listing names 120 pixels, and the now playing cover was that, stretched across most of the
 * screen. And images go over the application's one HTTP client, so a screen of covers shares the
 * connections the rest of Noctorium already has open rather than paying for its own on a device where
 * opening TLS costs battery.
 */
fun artworkImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .okHttpClient(Http.shared)
    .components { add(ArtworkSizing) }
    .crossfade(true)
    .build()

/**
 * Rewrites a cover's address for the size Coil has worked out it will be drawn at.
 *
 * An interceptor rather than a change at each image, because Coil knows the final pixel size here and
 * the call sites do not. It runs before Coil looks in its memory cache, so the sized address is what gets
 * cached: a thumbnail and the full cover are two entries and never stand in for each other.
 */
private object ArtworkSizing : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val address = chain.request.data as? String ?: return chain.proceed(chain.request)
        val drawn = maxOf(chain.size.width.pxOrElse { 0 }, chain.size.height.pxOrElse { 0 })
        val sized = artworkAt(address, artworkBucket(drawn))
        if (sized == address) return chain.proceed(chain.request)
        return chain.proceed(chain.request.newBuilder().data(sized).build())
    }
}

/**
 * The colour the cover at [url] is known by, as core's [ArtworkColour] finds it: what "Match the artwork" takes for
 * the accent and Afterglow for its light. Null for a cover with no colour in it, and for one that could not be had.
 *
 * Counted from a small copy, asked for at the size the player bar draws the cover so that it is the copy already in
 * the image loader's caches rather than another download, and counted off the main thread. Once a cover: its colour
 * never changes, and it is asked for again every time the song comes round.
 */
suspend fun artworkColour(context: Context, url: String): Long? {
    ArtworkColours.known(url)?.let { return it.colour }
    val side = (ArtworkColours.SAMPLED_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val request = ImageRequest.Builder(context).data(url).size(side).allowHardware(false).build()
    // Not kept when it fails: a cover that did not load in a tunnel has its colour once the signal is back.
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
    val colour = withContext(Dispatchers.Default) {
        val bitmap = drawable.toBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        ArtworkColour.of(pixels, bitmap.width, bitmap.height)
    }
    ArtworkColours.keep(url, colour)
    return colour
}

/** The covers' colours worked out so far, by address: see [artworkColour]. */
object ArtworkColours {
    /** The player bar's cover: the smallest copy of it that a screen with the song on it has already loaded. */
    const val SAMPLED_DP = 44

    /** A cover's colour, or that it has none, which is as much worth remembering as a colour is. */
    class Known(val colour: Long?)

    // A few hundred covers is a long evening's listening, and each is a few dozen bytes.
    private val known = LruCache<String, Known>(400)

    /** What [url]'s cover was found to be, or null while it has not been looked at. */
    fun known(url: String?): Known? = url?.let { synchronized(known) { known.get(it) } }

    fun keep(url: String, colour: Long?) {
        synchronized(known) { known.put(url, Known(colour)) }
    }
}
