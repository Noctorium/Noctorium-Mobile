package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** How long the Shadow code's takeover lasts before Noctorium closes. */
internal const val SHADOW_TAKEOVER_MILLIS = 5_000L

/** The takeover's words, and the room each takes in its grid. */
internal const val SHADOW_WORD = "Sh@dow"
private val CELL_WIDTH = 76.dp
private val CELL_HEIGHT = 26.dp

/**
 * What the code "sh@d0w" does before anything else: the whole screen black, and "Sh@dow" in small white letters
 * appearing in straight rows and columns, one place after another in no order, until they fill it -- for
 * [SHADOW_TAKEOVER_MILLIS], and then [done], which closes Noctorium. Over the whole screen, and Back does nothing.
 */
@Composable
internal fun ShadowTakeover(done: () -> Unit) {
    // How many thousandths of the grid's places are filled: all of them a little before the end.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            if (elapsed >= SHADOW_TAKEOVER_MILLIS) break
            shown = (elapsed * 1000 / (SHADOW_TAKEOVER_MILLIS * 9 / 10)).toInt().coerceAtMost(1000)
            delay(40)
        }
        done()
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val columns = maxOf(1, (maxWidth / CELL_WIDTH).toInt())
            val rows = maxOf(1, (maxHeight / CELL_HEIGHT).toInt())
            val order = remember(columns, rows) { (0 until columns * rows).shuffled(Random(7)) }
            // Centred, so the margins either side are the same.
            val left = (maxWidth - CELL_WIDTH * columns) / 2
            val top = (maxHeight - CELL_HEIGHT * rows) / 2
            order.take(order.size * shown / 1000).forEach { place ->
                Text(
                    SHADOW_WORD,
                    Modifier.offset(left + CELL_WIDTH * (place % columns), top + CELL_HEIGHT * (place / columns)),
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
