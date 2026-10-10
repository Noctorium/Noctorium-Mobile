package app.noctorium.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.twotone.PlayArrow
import app.noctorium.settings.IconSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The one mapping every icon goes through: Standard as it always was, and every glyph in every set. */
class GlyphsTest {

    @Test
    fun `Standard is exactly the icons the phone has always drawn`() {
        assertSame(Icons.Filled.PlayArrow, Glyph.PLAY_ARROW.vector(IconSet.STANDARD))
        assertSame(Icons.Filled.Settings, Glyph.SETTINGS.vector(IconSet.STANDARD))
        assertSame(Icons.AutoMirrored.Filled.QueueMusic, Glyph.QUEUE_MUSIC.vector(IconSet.STANDARD))
        // The one icon that was drawn outlined is still outlined.
        assertSame(Icons.Outlined.PushPin, Glyph.PUSH_PIN_OUTLINE.vector(IconSet.STANDARD))
    }

    @Test
    fun `Filled is Standard on the phone, and is not offered twice`() {
        Glyph.entries.forEach { assertSame(it.vector(IconSet.STANDARD), it.vector(IconSet.FILLED)) }
        assertTrue(IconSet.FILLED !in PHONE_ICON_SETS)
        assertEquals(IconSet.STANDARD, shownIconSet(IconSet.FILLED))
    }

    @Test
    fun `the other sets are Material's own styles of the same icons`() {
        assertSame(Icons.Rounded.PlayArrow, Glyph.PLAY_ARROW.vector(IconSet.ROUNDED))
        assertSame(Icons.TwoTone.PlayArrow, Glyph.PLAY_ARROW.vector(IconSet.TWO_TONE))
    }

    @Test
    fun `every glyph is drawn in every set the phone offers`() {
        PHONE_ICON_SETS.forEach { set ->
            Glyph.entries.forEach { glyph ->
                val vector = glyph.vector(set)
                assertEquals(24f, vector.viewportWidth, "$glyph in $set")
            }
        }
    }

    @Test
    fun `Line is Lucide's, built once and kept`() {
        val play = Glyph.PLAY_ARROW.vector(IconSet.LINE)
        assertEquals("line.play", play.name)
        assertSame(play, Glyph.PLAY_ARROW.vector(IconSet.LINE))
        assertNotSame(Glyph.FAVORITE.vector(IconSet.LINE), Glyph.FAVORITE_BORDER.vector(IconSet.LINE))
    }

    @Test
    fun `liked and not liked, pinned and not pinned, look different in every set`() {
        PHONE_ICON_SETS.forEach { set ->
            assertNotSame(Glyph.FAVORITE.vector(set), Glyph.FAVORITE_BORDER.vector(set), "hearts in $set")
            assertNotSame(Glyph.PUSH_PIN.vector(set), Glyph.PUSH_PIN_OUTLINE.vector(set), "pins in $set")
        }
    }
}
