package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.settings.IconSet
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.QueueColumn
import app.noctorium.settings.SettingsState
import app.noctorium.settings.VisualiserStyle

/** The icon set as the phone offers it: Filled, which is what Standard already is here, reads as Standard. */
internal fun shownIconSet(set: IconSet): IconSet = if (set == IconSet.FILLED) IconSet.STANDARD else set

/**
 * The icons every button is drawn with, each set shown by a few of its own icons -- play, pause, next, shuffle,
 * the heart and Settings' cog -- so the choice is made by looking.
 */
@Composable
internal fun IconSetCard(settings: SettingsState, state: AppState) {
    val chosen = shownIconSet(settings.preferences.iconSet)
    SettingsCardShell {
        CardHeading(Glyph.ICONS.image, "Icons")
        Text(
            "Every icon in Noctorium, in one style. Standard is the icons it has always had.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        SettingAnchor(SettingIds.ICON_SET) {
            PickerGrid(PHONE_ICON_SETS, columns = 2) { set, modifier ->
                PickerTile(set.displayName, chosen == set, { state.setIconSet(set) }, modifier) { IconSample(set) }
            }
            Text(chosen.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text(
            "Rounded, Outlined, Sharp and Two-tone are Google's Material icons (Apache License 2.0); Line is " +
                "Lucide's (ISC License).",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** What an icon set is shown by in its tile: the same six glyphs every set draws. */
private val SAMPLE_GLYPHS = listOf(Glyph.PLAY_ARROW, Glyph.PAUSE, Glyph.SKIP_NEXT, Glyph.SHUFFLE, Glyph.FAVORITE_BORDER, Glyph.SETTINGS)

@Composable
private fun IconSample(set: IconSet) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        SAMPLE_GLYPHS.forEach { glyph -> Icon(glyph.vector(set), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
    }
}

/**
 * The Playlist layout's own choices: its visualiser, the columns of its queue and their order, and its status
 * bar. Offered whichever layout is chosen -- they wait for it -- and the card says when they do.
 */
@Composable
internal fun PlaylistLayoutCard(settings: SettingsState, state: AppState) {
    val preferences = settings.preferences
    SettingsCardShell {
        CardHeading(Glyph.VIEW_COLUMN.image, "Playlist layout")
        Text(
            if (preferences.phone.nowPlayingLayout == PhoneNowPlayingLayout.PLAYLIST) {
                "The queue as a table, with a visualiser beside the song and a status bar along the foot."
            } else {
                "These are for the Playlist layout, chosen above: the queue as a table, with a visualiser beside " +
                    "the song and a status bar along the foot."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        SettingAnchor(SettingIds.VISUALISER) {
            Text("Visualiser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            PickerGrid(VisualiserStyle.entries, columns = 3) { style, modifier ->
                PickerTile(style.displayName, preferences.visualiser == style, { state.setVisualiser(style) }, modifier) {
                    VisualiserPreview(style)
                }
            }
            Text(
                preferences.visualiser.description + " Drawn from the sound itself, so it rests while nothing plays, " +
                    "and has nothing to draw while a song plays in Spotify's own app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        SettingAnchor(SettingIds.QUEUE_COLUMNS) {
            Text("Columns", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Which the table has, in their order. The title is always there.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val columns = QueueColumn.normalised(preferences.queueColumns)
            columnsInOrder(columns).forEach { column ->
                ColumnRow(column, columns, state)
            }
            Spacer(Modifier.height(8.dp))
        }

        SettingAnchor(SettingIds.STATUS_BAR) {
            Toggle(
                "Status bar",
                "What is playing -- \"Opus, stereo, 48 kHz, 128 kbit/s\" -- and how far into the queue, along the foot.",
                preferences.statusBar,
                onChange = state::setStatusBar,
            )
        }
    }
}

/** One column: whether the table has it, and where it stands among the ones it has. */
@Composable
private fun ColumnRow(column: QueueColumn, columns: List<QueueColumn>, state: AppState) {
    val shown = column in columns
    val at = columns.indexOf(column)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (column.heading != column.displayName) "${column.displayName} (${column.heading})" else column.displayName,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).alpha(if (shown) 1f else DISABLED_ALPHA),
        )
        IconButton({ state.setQueueColumns(columnsMoved(columns, column, -1)) }, Modifier.size(40.dp), enabled = shown && at > 0) {
            Icon(Glyph.KEYBOARD_ARROW_UP.image, "Move ${column.displayName} earlier", Modifier.size(20.dp))
        }
        IconButton({ state.setQueueColumns(columnsMoved(columns, column, 1)) }, Modifier.size(40.dp), enabled = shown && at < columns.lastIndex) {
            Icon(Glyph.KEYBOARD_ARROW_DOWN.image, "Move ${column.displayName} later", Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        if (column == QueueColumn.TITLE) {
            // Always there, which a switch that cannot be moved said less plainly than this.
            Text("Always", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            SkinnedSwitch(shown, { state.setQueueColumns(columnsWith(columns, column, it)) })
        }
    }
}

/** The columns the table has, in their order, then the ones it has not, in their usual order. */
internal fun columnsInOrder(columns: List<QueueColumn>): List<QueueColumn> =
    columns + QueueColumn.entries.filter { it !in columns }

/** [columns] with [column] added at the end, or taken out; the title stays whatever is asked. */
internal fun columnsWith(columns: List<QueueColumn>, column: QueueColumn, shown: Boolean): List<QueueColumn> =
    QueueColumn.normalised(if (shown) (columns - column) + column else columns - column)

/** [columns] with [column] moved [by] places, earlier for a negative, kept within the list. */
internal fun columnsMoved(columns: List<QueueColumn>, column: QueueColumn, by: Int): List<QueueColumn> {
    val from = columns.indexOf(column)
    if (from < 0) return columns
    val to = (from + by).coerceIn(0, columns.lastIndex)
    return columns.toMutableList().apply { add(to, removeAt(from)) }
}

/** A visualiser style in its picker: drawn from a still frame of music, never from what is playing. */
@Composable
private fun VisualiserPreview(style: VisualiserStyle) {
    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        if (style == VisualiserStyle.OFF) {
            val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = .25f)
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(faint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), size.height)
            }
        } else {
            VisualiserDrawing(style, VisualiserLevels.of(SAMPLE_ANALYSIS), Modifier.fillMaxWidth().height(32.dp))
        }
    }
}
