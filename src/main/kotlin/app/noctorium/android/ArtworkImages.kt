package app.noctorium.android

import android.content.Context
import app.noctorium.domain.artworkAt
import app.noctorium.domain.artworkBucket
import app.noctorium.net.Http
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.size.pxOrElse

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
