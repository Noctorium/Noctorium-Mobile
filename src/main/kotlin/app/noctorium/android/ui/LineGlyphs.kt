package app.noctorium.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * The Line icon set: Lucide's icons (https://lucide.dev), thin single strokes on a 24-point square, the shapes
 * taken from lucide-react 1.50.0 and drawn here as Compose vectors. Lucide is published under the ISC License:
 *
 * ISC License
 *
 * Copyright (c) 2026 Lucide Icons and Contributors
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * ---
 *
 * The following Lucide icons are derived from the Feather project:
 *
 * airplay, alert-circle, alert-octagon, alert-triangle, aperture, arrow-down-circle, arrow-down-left, arrow-down-right, arrow-down, arrow-left-circle, arrow-left, arrow-right-circle, arrow-right, arrow-up-circle, arrow-up-left, arrow-up-right, arrow-up, at-sign, calendar, cast, check, chevron-down, chevron-left, chevron-right, chevron-up, chevrons-down, chevrons-left, chevrons-right, chevrons-up, circle, clipboard, clock, code, columns, command, compass, corner-down-left, corner-down-right, corner-left-down, corner-left-up, corner-right-down, corner-right-up, corner-up-left, corner-up-right, crosshair, database, divide-circle, divide-square, dollar-sign, download, external-link, feather, frown, hash, headphones, help-circle, info, italic, key, layout, life-buoy, link-2, link, loader, lock, log-in, log-out, maximize, meh, minimize, minimize-2, minus-circle, minus-square, minus, monitor, moon, more-horizontal, more-vertical, move, music, navigation-2, navigation, octagon, pause-circle, percent, plus-circle, plus-square, plus, power, radio, rss, search, server, share, shopping-bag, sidebar, smartphone, smile, square, table-2, tablet, target, terminal, trash-2, trash, triangle, tv, type, upload, x-circle, x-octagon, x-square, x, zoom-in, zoom-out
 *
 * The MIT License (MIT) (for the icons listed above)
 *
 * Copyright (c) 2013-present Cole Bemis
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/** How heavy the lines are: a little under Lucide's own two points, nearer a desktop icon theme's hairlines. */
private const val LINE_WEIGHT = 1.75f

/** Each glyph's vector, built the first time it is drawn and kept. Only ever touched from composition. */
private val built = arrayOfNulls<ImageVector>(Glyph.entries.size)

/** [glyph] in the Line set. */
internal fun lineGlyph(glyph: Glyph): ImageVector =
    built[glyph.ordinal] ?: lineVector(glyph).also { built[glyph.ordinal] = it }

private fun lineVector(glyph: Glyph): ImageVector = when (glyph) {
    Glyph.ACCOUNT_CIRCLE -> line("circle-user-round")
    Glyph.ADD -> line("plus")
    Glyph.ALBUM -> line("disc-3")
    Glyph.ALL_INCLUSIVE -> line("infinity")
    Glyph.ARROW_BACK -> line("arrow-left")
    Glyph.AUDIOTRACK -> line("music-2")
    Glyph.BATTERY_ALERT -> line("battery-warning")
    Glyph.BATTERY_FULL -> line("battery-full")
    Glyph.BEDTIME -> line("moon")
    Glyph.BOLT -> line("zap")
    Glyph.CHECK -> line("check")
    Glyph.CHECK_CIRCLE -> line("circle-check")
    Glyph.CHEVRON_RIGHT -> line("chevron-right")
    Glyph.CLEAR_ALL -> line("list-x")
    Glyph.CLEAR_ALL_BUT_THIS -> line("list-minus")
    Glyph.CLOSE -> line("x")
    Glyph.CLOUD -> line("cloud")
    Glyph.CLOUD_UPLOAD -> line("cloud-upload")
    Glyph.COLOR_LENS -> line("palette")
    Glyph.COMPUTER -> line("monitor")
    Glyph.CONTENT_COPY -> line("copy")
    Glyph.CONTENT_PASTE -> line("clipboard-paste")
    Glyph.DELETE -> line("trash-2")
    Glyph.DEVICES -> line("monitor-smartphone")
    Glyph.DIRECTIONS_CAR -> line("car")
    Glyph.DOWNLOAD -> line("download")
    Glyph.DOWNLOAD_DONE -> line("circle-check-big")
    Glyph.DOWNLOAD_FOR_OFFLINE -> line("circle-arrow-down")
    Glyph.DOWNLOADING -> line("loader-circle")
    Glyph.EDIT -> line("pencil")
    Glyph.EQUALIZER -> line("sliders-vertical")
    Glyph.ERROR_OUTLINE -> line("circle-alert")
    Glyph.EXPAND_LESS -> line("chevron-up")
    Glyph.EXPAND_MORE -> line("chevron-down")
    Glyph.FAVORITE -> line("heart", filled = true)
    Glyph.FAVORITE_BORDER -> line("heart")
    Glyph.FORMAT_SIZE -> line("a-large-small")
    Glyph.GRAPHIC_EQ -> line("audio-lines")
    Glyph.HEADPHONES -> line("headphones")
    Glyph.HISTORY -> line("history")
    Glyph.HOME -> line("house")
    Glyph.ICONS -> line("shapes")
    Glyph.INSIGHTS -> line("chart-spline")
    Glyph.KEY -> line("key-round")
    Glyph.KEYBOARD_ARROW_DOWN -> line("chevron-down")
    Glyph.KEYBOARD_ARROW_UP -> line("chevron-up")
    Glyph.LIBRARY_MUSIC -> line("library-big")
    Glyph.LINK -> line("link")
    Glyph.LOCK -> line("lock")
    Glyph.LYRICS -> line("mic-vocal")
    Glyph.MONITOR_HEART -> line("heart-pulse")
    Glyph.MORE_VERT -> line("ellipsis-vertical")
    Glyph.MUSIC_NOTE -> line("music")
    Glyph.OPEN_IN_NEW -> line("external-link")
    Glyph.PALETTE -> line("palette")
    Glyph.PAUSE -> line("pause")
    Glyph.PERSON_ADD -> line("user-plus")
    Glyph.PHONE_ANDROID -> line("smartphone")
    Glyph.PLAY_ARROW -> line("play")
    Glyph.PLAY_CIRCLE -> line("circle-play")
    Glyph.PLAYLIST_ADD -> line("list-plus")
    Glyph.PLAYLIST_PLAY -> line("list-video")
    Glyph.PUBLIC -> line("globe")
    Glyph.PUSH_PIN -> line("pin", filled = true)
    Glyph.PUSH_PIN_OUTLINE -> line("pin")
    Glyph.QUEUE_MUSIC -> line("list-music")
    Glyph.REFRESH -> line("refresh-cw")
    Glyph.REMOVE_CIRCLE_OUTLINE -> line("circle-minus")
    Glyph.REPEAT -> line("repeat")
    Glyph.REPEAT_ONE -> line("repeat-1")
    Glyph.SAVE -> line("save")
    Glyph.SAVE_ALT -> line("folder-down")
    Glyph.SEARCH -> line("search")
    Glyph.SETTINGS -> line("settings")
    Glyph.SHARE -> line("share-2")
    Glyph.SHUFFLE -> line("shuffle")
    Glyph.SKIP_NEXT -> line("skip-forward")
    Glyph.SKIP_PREVIOUS -> line("skip-back")
    Glyph.SMART_DISPLAY -> line("monitor-play")
    Glyph.SPEAKER -> line("speaker")
    Glyph.SPEED -> line("gauge")
    Glyph.STATUS_BAR -> line("panel-bottom-dashed")
    Glyph.STOP -> line("square")
    Glyph.STOP_AFTER -> line("circle-stop")
    Glyph.SYSTEM_UPDATE_ALT -> line("monitor-down")
    Glyph.TAB -> line("panel-bottom")
    Glyph.TABLET -> line("tablet")
    Glyph.TERMINAL -> line("terminal")
    Glyph.TEXT_FIELDS -> line("type")
    Glyph.TUNE -> line("sliders-horizontal")
    Glyph.TV -> line("tv")
    Glyph.VIEW_COLUMN -> line("columns-3")
    Glyph.VOLUME_DOWN -> line("volume-1")
    Glyph.VOLUME_OFF -> line("volume-x")
    Glyph.VOLUME_UP -> line("volume-2")
    Glyph.WARNING -> line("triangle-alert")
    Glyph.WAVES -> line("waves")
    Glyph.WIFI -> line("wifi")
}

/** Lucide's [name] drawn as a vector, its lines filled in for a glyph that is a state switched on. */
private fun line(name: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name = "line.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            LUCIDE.getValue(name).forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = LINE_WEIGHT,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

/** Lucide's paths, by Lucide's name for each icon. */
private val LUCIDE: Map<String, List<String>> = mapOf(
    "circle-user-round" to listOf(
        "M17.925 20.056a6 6 0 0 0-11.851.001",
        "M8 11a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
    ),
    "plus" to listOf(
        "M5 12h14",
        "M12 5v14",
    ),
    "disc-3" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M6 12c0-1.7.7-3.2 1.8-4.2",
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "M18 12c0 1.7-.7 3.2-1.8 4.2",
    ),
    "infinity" to listOf(
        "M6 16c5 0 7-8 12-8a4 4 0 0 1 0 8c-5 0-7-8-12-8a4 4 0 1 0 0 8",
    ),
    "arrow-left" to listOf(
        "m12 19-7-7 7-7",
        "M19 12H5",
    ),
    "music-2" to listOf(
        "M4 18a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
        "M12 18V2l7 4",
    ),
    "battery-warning" to listOf(
        "M10 17h.01",
        "M10 7v6",
        "M14 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2",
        "M22 14v-4",
        "M6 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2",
    ),
    "battery-full" to listOf(
        "M10 10v4",
        "M14 10v4",
        "M22 14v-4",
        "M6 10v4",
        "M4 6h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
    ),
    "moon" to listOf(
        "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
    ),
    "zap" to listOf(
        "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
    ),
    "check" to listOf(
        "M20 6 9 17l-5-5",
    ),
    "circle-check" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "m16 9-5.5 5.5L8 12",
    ),
    "chevron-right" to listOf(
        "m9 18 6-6-6-6",
    ),
    "list-x" to listOf(
        "M16 5H3",
        "M11 12H3",
        "M16 19H3",
        "m15.5 9.5 5 5",
        "m20.5 9.5-5 5",
    ),
    "list-minus" to listOf(
        "M16 5H3",
        "M11 12H3",
        "M16 19H3",
        "M21 12h-6",
    ),
    "x" to listOf(
        "M18 6 6 18",
        "m6 6 12 12",
    ),
    "cloud" to listOf(
        "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
    ),
    "cloud-upload" to listOf(
        "M12 13v8",
        "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242",
        "m8 17 4-4 4 4",
    ),
    "palette" to listOf(
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
        "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
        "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
        "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
    ),
    "monitor" to listOf(
        "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        "M8 21L16 21",
        "M12 17L12 21",
    ),
    "copy" to listOf(
        "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    ),
    "clipboard-paste" to listOf(
        "M11 14h10",
        "M16 4h2a2 2 0 0 1 2 2v1.344",
        "m17 18 4-4-4-4",
        "M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 1.793-1.113",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z",
    ),
    "trash-2" to listOf(
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    ),
    "monitor-smartphone" to listOf(
        "M18 8V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h8",
        "M10 19v-3.96 3.15",
        "M7 19h5",
        "M18 12h2a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z",
    ),
    "car" to listOf(
        "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
        "M5 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        "M9 17h6",
        "M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
    ),
    "download" to listOf(
        "M12 15V3",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
    ),
    "circle-check-big" to listOf(
        "M21.801 10A10 10 0 1 1 17 3.335",
        "m9 11 3 3L22 4",
    ),
    "circle-arrow-down" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 8v8",
        "m8 12 4 4 4-4",
    ),
    "loader-circle" to listOf(
        "M21 12a9 9 0 1 1-6.219-8.56",
    ),
    "pencil" to listOf(
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4",
    ),
    "sliders-vertical" to listOf(
        "M10 8h4",
        "M12 21v-9",
        "M12 8V3",
        "M17 16h4",
        "M19 12V3",
        "M19 21v-5",
        "M3 14h4",
        "M5 10V3",
        "M5 21v-7",
    ),
    "circle-alert" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 8L12 12",
        "M12 16L12.01 16",
    ),
    "chevron-up" to listOf(
        "m18 15-6-6-6 6",
    ),
    "chevron-down" to listOf(
        "m6 9 6 6 6-6",
    ),
    "heart" to listOf(
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
    ),
    "a-large-small" to listOf(
        "m15 16 2.536-7.328a1.02 1.02 1 0 1 1.928 0L22 16",
        "M15.697 14h5.606",
        "m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16",
        "M3.304 13h6.392",
    ),
    "audio-lines" to listOf(
        "M2 10v3",
        "M6 6v11",
        "M10 3v18",
        "M14 8v7",
        "M18 5v13",
        "M22 10v3",
    ),
    "history" to listOf(
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    ),
    "house" to listOf(
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    ),
    "shapes" to listOf(
        "M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z",
        "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
        "M14 17.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z",
    ),
    "chart-spline" to listOf(
        "M3 3v16a2 2 0 0 0 2 2h16",
        "M7 16c.5-2 1.5-7 4-7 2 0 2 3 4 3 2.5 0 4.5-5 5-7",
    ),
    "key-round" to listOf(
        "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z",
        "M16 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0z",
    ),
    "library-big" to listOf(
        "M4 3h6a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
        "M7 3v18",
        "M20.4 18.9c.2.5-.1 1.1-.6 1.3l-1.9.7c-.5.2-1.1-.1-1.3-.6L11.1 5.1c-.2-.5.1-1.1.6-1.3l1.9-.7c.5-.2 1.1.1 1.3.6Z",
    ),
    "link" to listOf(
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    ),
    "lock" to listOf(
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    ),
    "mic-vocal" to listOf(
        "m11 7.601-5.994 8.19a1 1 0 0 0 .1 1.298l.817.818a1 1 0 0 0 1.314.087L15.09 12",
        "M16.5 21.174C15.5 20.5 14.372 20 13 20c-2.058 0-3.928 2.356-6 2-2.072-.356-2.775-3.369-1.5-4.5",
        "M11 7a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z",
    ),
    "heart-pulse" to listOf(
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
        "M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
    ),
    "ellipsis-vertical" to listOf(
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
    ),
    "music" to listOf(
        "M9 18V5l12-2v13",
        "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    ),
    "external-link" to listOf(
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    ),
    "pause" to listOf(
        "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
        "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
    ),
    "user-plus" to listOf(
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
        "M19 8L19 14",
        "M22 11L16 11",
    ),
    "smartphone" to listOf(
        "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
        "M12 18h.01",
    ),
    "play" to listOf(
        "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
    ),
    "circle-play" to listOf(
        "M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
    ),
    "list-plus" to listOf(
        "M16 5H3",
        "M11 12H3",
        "M16 19H3",
        "M18 9v6",
        "M21 12h-6",
    ),
    "list-video" to listOf(
        "M21 5H3",
        "M10 12H3",
        "M10 19H3",
        "M15 12.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997a1 1 0 0 1-1.517-.86z",
    ),
    "globe" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
    ),
    "pin" to listOf(
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    ),
    "list-music" to listOf(
        "M16 5H3",
        "M11 12H3",
        "M11 19H3",
        "M21 16V5",
        "M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    ),
    "refresh-cw" to listOf(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    ),
    "circle-minus" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M8 12h8",
    ),
    "repeat" to listOf(
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
    ),
    "repeat-1" to listOf(
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
        "M11 10h1v4",
    ),
    "save" to listOf(
        "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
        "M7 3v4a1 1 0 0 0 1 1h7",
    ),
    "folder-down" to listOf(
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        "M12 10v6",
        "m15 13-3 3-3-3",
    ),
    "search" to listOf(
        "m21 21-4.34-4.34",
        "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z",
    ),
    "settings" to listOf(
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
    ),
    "share-2" to listOf(
        "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        "M8.59 13.51L15.42 17.49",
        "M15.41 6.51L8.59 10.49",
    ),
    "shuffle" to listOf(
        "m18 14 4 4-4 4",
        "m18 2 4 4-4 4",
        "M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22",
        "M2 6h1.972a4 4 0 0 1 3.6 2.2",
        "M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.45",
    ),
    "skip-forward" to listOf(
        "M21 4v16",
        "M6.029 4.285A2 2 0 0 0 3 6v12a2 2 0 0 0 3.029 1.715l9.997-5.998a2 2 0 0 0 .003-3.432z",
    ),
    "skip-back" to listOf(
        "M17.971 4.285A2 2 0 0 1 21 6v12a2 2 0 0 1-3.029 1.715l-9.997-5.998a2 2 0 0 1-.003-3.432z",
        "M3 20V4",
    ),
    "monitor-play" to listOf(
        "M15.033 9.44a.647.647 0 0 1 0 1.12l-4.065 2.352a.645.645 0 0 1-.968-.56V7.648a.645.645 0 0 1 .967-.56z",
        "M12 17v4",
        "M8 21h8",
        "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
    ),
    "speaker" to listOf(
        "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
        "M12 6h.01",
        "M8 14a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
        "M12 14h.01",
    ),
    "gauge" to listOf(
        "m12 14 4-4",
        "M3.34 19a10 10 0 1 1 17.32 0",
    ),
    "panel-bottom-dashed" to listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M14 15h1",
        "M19 15h2",
        "M3 15h2",
        "M9 15h1",
    ),
    "square" to listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
    ),
    "circle-stop" to listOf(
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
        "M10 9h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z",
    ),
    "monitor-down" to listOf(
        "M12 13V7",
        "m15 10-3 3-3-3",
        "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        "M12 17v4",
        "M8 21h8",
    ),
    "panel-bottom" to listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M3 15h18",
    ),
    "tablet" to listOf(
        "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
        "M12 18L12.01 18",
    ),
    "terminal" to listOf(
        "M12 19h8",
        "m4 17 6-6-6-6",
    ),
    "type" to listOf(
        "M12 4v16",
        "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
        "M9 20h6",
    ),
    "sliders-horizontal" to listOf(
        "M10 5H3",
        "M12 19H3",
        "M14 3v4",
        "M16 17v4",
        "M21 12h-9",
        "M21 19h-5",
        "M21 5h-7",
        "M8 10v4",
        "M8 12H3",
    ),
    "tv" to listOf(
        "m17 2-5 5-5-5",
        "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
    ),
    "columns-3" to listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M9 3v18",
        "M15 3v18",
    ),
    "volume-1" to listOf(
        "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
        "M16 9a5 5 0 0 1 0 6",
    ),
    "volume-x" to listOf(
        "M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z",
        "m16.5 14.5 5-5",
        "m16.5 9.5 5 5",
    ),
    "wifi" to listOf(
        "M12 20h.01",
        "M2 8.82a15 15 0 0 1 20 0",
        "M5 12.859a10 10 0 0 1 14 0",
        "M8.5 16.429a5 5 0 0 1 7 0",
    ),
    "volume-2" to listOf(
        "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
        "M16 9a5 5 0 0 1 0 6",
        "M19.364 18.364a9 9 0 0 0 0-12.728",
    ),
    "headphones" to listOf(
        "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3",
    ),
    "triangle-alert" to listOf(
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    ),
    "waves" to listOf(
        "M2 6c.6.5 1.2 1 2.5 1C7 7 7 5 9.5 5c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
        "M2 12c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
        "M2 18c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
    ),
)
