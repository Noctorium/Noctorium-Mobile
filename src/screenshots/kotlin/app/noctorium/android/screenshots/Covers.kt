package app.noctorium.android.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import app.noctorium.domain.Track
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers

/**
 * Covers for the still life, painted rather than downloaded.
 *
 * The layouts that are about the cover -- the full cover, the record, the cover flow -- say nothing with the
 * grey placeholder the other pictures make do with. These are a few made-up sleeves, each a colour field with
 * a low sun over a hill, painted on demand by an image loader that knows nothing else and answers at once, so
 * a picture never waits on a cover and nothing leaves the machine. The same song always gets the same sleeve.
 */
internal object Covers {
    private const val SCHEME = "still-cover"

    private val palettes = listOf(
        Triple(0xFF1D3B72, 0xFFE8875B, 0xFFF6D6A8),
        Triple(0xFF0F5257, 0xFF9BD1C3, 0xFFF4FFF8),
        Triple(0xFF3A1C4A, 0xFFD9487A, 0xFFFFC2D1),
        Triple(0xFF283618, 0xFFDDA15E, 0xFFFEFAE0),
        Triple(0xFF15171C, 0xFF3FA7D6, 0xFFF2F2F2),
        Triple(0xFF6B2737, 0xFFE9B44C, 0xFFFFF3E2),
    )

    /** [track], with a sleeve of its own. */
    fun on(track: Track): Track = track.copy(artworkUrl = "$SCHEME://${track.id}")

    fun on(tracks: List<Track>): List<Track> = tracks.map(::on)

    /** Makes the painted sleeves what every image in the application loads, for this run. */
    fun install(context: Context) {
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components { add(Painter.Factory) }
                // Answered on the spot rather than on a pool of threads the picture would have to wait for.
                .dispatcher(Dispatchers.Main.immediate)
                .crossfade(false)
                .build(),
        )
    }

    private class Painter(private val id: String, private val options: Options) : Fetcher {
        override suspend fun fetch(): FetchResult =
            DrawableResult(BitmapDrawable(options.context.resources, paint(id)), isSampled = false, dataSource = DataSource.MEMORY)

        object Factory : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
                if (data.scheme == SCHEME) Painter(data.host.orEmpty(), options) else null
        }
    }

    /** A sky of two colours, a sun low in it, and two hills across the foot. */
    private fun paint(id: String): Bitmap {
        val (deep, warm, pale) = palettes[Math.floorMod(id.hashCode(), palettes.size)].let {
            Triple(it.first.toInt(), it.second.toInt(), it.third.toInt())
        }
        val size = 480f
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, size, deep, warm, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, paint)
        val sunX = size * (.3f + .4f * ((id.hashCode() ushr 3) % 10) / 10f)
        paint.shader = RadialGradient(sunX, size * .48f, size * .2f, pale, warm, Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, size * .48f, size * .17f, paint)
        paint.shader = null
        paint.color = deep
        paint.alpha = 210
        canvas.drawPath(hill(size, .62f, .7f), paint)
        paint.alpha = 255
        canvas.drawPath(hill(size, .74f, .3f), paint)
        return bitmap
    }

    private fun hill(size: Float, height: Float, crest: Float): Path = Path().apply {
        moveTo(0f, size)
        lineTo(0f, size * (height + .08f))
        quadTo(size * crest, size * (height - .14f), size, size * (height + .04f))
        lineTo(size, size)
        close()
    }
}
