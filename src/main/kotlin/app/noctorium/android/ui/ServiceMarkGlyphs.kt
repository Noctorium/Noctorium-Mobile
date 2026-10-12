package app.noctorium.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.noctorium.domain.ProviderType
import app.noctorium.domain.ServiceMarks
import java.util.EnumMap

/*
 * The services' marks as vectors, for the badges on the cards that say where a song comes from. The shapes are
 * Base's [ServiceMarks] -- Simple Icons' (CC0), and Material's folder for music on the phone -- and the desktop
 * builds the same vectors from the same text.
 *
 * They are no [Glyph], and so in no icon set: a service's mark is the service's, whatever style the buttons are
 * drawn in.
 */

private val built = EnumMap<ProviderType, ImageVector>(ProviderType::class.java)

/** [this] service's mark, filled in one colour on a 24-point square, built the first time it is asked for. */
internal fun ProviderType.markVector(): ImageVector = synchronized(built) {
    built.getOrPut(this) {
        ImageVector.Builder(
            name = "mark.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = ServiceMarks.VIEWPORT,
            viewportHeight = ServiceMarks.VIEWPORT,
        ).apply {
            addPath(pathData = addPathNodes(ServiceMarks.path(this@markVector)), fill = SolidColor(Color.Black))
        }.build()
    }
}
