package app.noctorium.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import app.noctorium.settings.PhoneNowPlayingLayout
import app.noctorium.settings.PhonePlayerBarStyle

/*
 * The plans the layouts in Customization are chosen by: a small drawing of each, in the theme's own colours.
 *
 * Plans rather than screenshots, so they read on every theme, stay legible at the size of a thumbnail, and
 * cannot fall behind the layouts they stand for by showing last year's buttons.
 */

/** Every player bar, three to a row, each as a plan of the foot of a screen with the bar above the tabs. */
@Composable
internal fun PlayerBarPicker(selected: PhonePlayerBarStyle, choose: (PhonePlayerBarStyle) -> Unit) {
    val colours = planColours()
    PickerGrid(PhonePlayerBarStyle.entries, columns = 3) { layout, modifier ->
        PickerTile(layout.displayName, selected == layout, { choose(layout) }, modifier) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f)) { drawPlayerBarPlan(layout, colours) }
        }
    }
}

/** The theme's colours as the plans use them: the page, a panel on it, writing, faint writing, and the accent. */
@Immutable
private class PlanColours(val page: Color, val panel: Color, val ink: Color, val faint: Color, val accent: Color) {
    /** A cover: the accent, darkening across it, so it reads as artwork rather than as a button. */
    fun cover(topLeft: Offset, size: Size): Brush = Brush.linearGradient(
        listOf(lerp(accent, Color.White, .18f), lerp(accent, Color.Black, .5f)),
        start = topLeft,
        end = Offset(topLeft.x + size.width, topLeft.y + size.height),
    )
}

@Composable
private fun planColours(): PlanColours {
    val scheme = MaterialTheme.colorScheme
    return PlanColours(
        page = scheme.background,
        panel = scheme.surfaceContainerHigh,
        ink = scheme.onSurface.copy(alpha = .78f),
        faint = scheme.onSurface.copy(alpha = .28f),
        accent = scheme.primary,
    )
}

/** The foot of a screen: a little of the page, the bar as [layout] has it, and the tabs under it. */
private fun DrawScope.drawPlayerBarPlan(layout: PhonePlayerBarStyle, c: PlanColours) {
    val u = size.width / 100f
    val h = size.height
    drawRoundRect(c.page, cornerRadius = CornerRadius(4 * u, 4 * u))
    // Two covers from the page above, so the bar is plainly at the foot of something.
    repeat(3) { i -> drawRoundRect(c.faint.copy(alpha = .14f), Offset((6 + i * 24) * u, 6 * u), Size(19 * u, 19 * u), CornerRadius(2.5f * u, 2.5f * u)) }
    // The tabs.
    val tabs = h - 11 * u
    drawRect(c.panel, Offset(0f, tabs), Size(size.width, 11 * u))
    repeat(5) { i -> drawCircle(if (i == 0) c.accent else c.faint, 1.7f * u, Offset((14 + i * 18) * u, tabs + 5.5f * u)) }

    fun bar(height: Float, colour: Color = c.panel): Float {
        drawRect(colour, Offset(0f, tabs - height * u), Size(size.width, height * u))
        return tabs - height * u
    }
    fun line(top: Float, played: Float = .38f, thick: Float = .9f, start: Float = 0f, end: Float = 100f) {
        drawRect(c.faint, Offset(start * u, top), Size((end - start) * u, thick * u))
        drawRect(c.accent, Offset(start * u, top), Size((end - start) * played * u, thick * u))
    }
    when (layout) {
        PhonePlayerBarStyle.CLASSIC -> {
            val top = bar(17f)
            line(top)
            planCover(c, 4 * u, top + 3.5f * u, 10 * u)
            planText(c, 18 * u, top + 5 * u, 34 * u, 22 * u, u)
            planPlay(c, 79 * u, top + 8.5f * u, 4.2f * u)
            planNext(c, 91 * u, top + 8.5f * u, 3.4f * u)
        }
        PhonePlayerBarStyle.SLIM, PhonePlayerBarStyle.SLIM_LEFT -> {
            val top = bar(12f)
            line(top)
            val left = layout == PhonePlayerBarStyle.SLIM_LEFT
            val buttons = if (left) 7f else 59f
            repeat(5) { i ->
                val x = (buttons + i * 8.5f) * u
                if (i == 2) planPlay(c, x, top + 6 * u, 3 * u) else drawCircle(c.faint, 1.3f * u, Offset(x, top + 6 * u))
            }
            planCover(c, (if (left) 88f else 4f) * u, top + 2.5f * u, 7 * u)
            planText(c, (if (left) 49f else 14f) * u, top + 3.2f * u, 24 * u, 16 * u, u, thin = true)
        }
        PhonePlayerBarStyle.CONTROLS -> {
            val top = bar(24f)
            planCover(c, 4 * u, top + 3 * u, 10 * u)
            planText(c, 18 * u, top + 4.5f * u, 30 * u, 20 * u, u)
            planPrevious(c, 69 * u, top + 8 * u, 3 * u)
            planPlay(c, 80 * u, top + 8 * u, 3.8f * u)
            planNext(c, 91 * u, top + 8 * u, 3 * u)
            line(top + 18.5f * u, start = 6f, end = 94f, thick = 1.1f)
            drawCircle(c.accent, 1.8f * u, Offset((6 + 88 * .38f) * u, top + 19 * u))
        }
        PhonePlayerBarStyle.SPOTLIGHT -> {
            val top = bar(24f)
            drawRect(
                Brush.horizontalGradient(listOf(c.accent.copy(alpha = .4f), c.accent.copy(alpha = .08f))),
                Offset(0f, top),
                Size(size.width, 24 * u),
            )
            line(top)
            planCover(c, 4 * u, top + 3.5f * u, 17 * u)
            planText(c, 25 * u, top + 8 * u, 34 * u, 22 * u, u, bold = true)
            drawCircle(c.accent, 5.5f * u, Offset(80 * u, top + 12 * u))
            planPlay(c, 80.6f * u, top + 12 * u, 2.6f * u, colour = c.page)
            planNext(c, 92 * u, top + 12 * u, 3 * u)
        }
        PhonePlayerBarStyle.FLOATING -> {
            val top = tabs - 19 * u
            val card = Size(90 * u, 15 * u)
            drawRoundRect(Color.Black.copy(alpha = .22f), Offset(5 * u, top + 1.2f * u), card, CornerRadius(5 * u, 5 * u))
            drawRoundRect(lerp(c.panel, c.ink, .08f), Offset(5 * u, top), card, CornerRadius(5 * u, 5 * u))
            planCover(c, 8 * u, top + 3 * u, 9 * u)
            planText(c, 20.5f * u, top + 4 * u, 30 * u, 20 * u, u)
            planPlay(c, 76 * u, top + 7.5f * u, 3.8f * u)
            planNext(c, 87 * u, top + 7.5f * u, 3 * u)
            line(top + 13.2f * u, start = 11f, end = 89f, thick = .7f)
        }
        PhonePlayerBarStyle.LINE -> {
            val top = bar(10f)
            drawRoundRect(c.ink, Offset(5 * u, top + 3.4f * u), Size(30 * u, 2.4f * u), CornerRadius(u, u))
            drawCircle(c.faint, .9f * u, Offset(38 * u, top + 4.6f * u))
            drawRoundRect(c.faint, Offset(41 * u, top + 3.6f * u), Size(20 * u, 2 * u), CornerRadius(u, u))
            planPlay(c, 92 * u, top + 4.6f * u, 2.8f * u)
            line(tabs - 1.1f * u, thick = 1.1f)
        }
        PhonePlayerBarStyle.RECORD -> {
            val top = bar(17f)
            line(top)
            planRecord(c, Offset(10.5f * u, top + 8.5f * u), 6.8f * u)
            planText(c, 21 * u, top + 5 * u, 32 * u, 21 * u, u)
            planPlay(c, 79 * u, top + 8.5f * u, 4.2f * u)
            planNext(c, 91 * u, top + 8.5f * u, 3.4f * u)
        }
        PhonePlayerBarStyle.TASKBAR -> {
            val top = bar(13f)
            drawRect(c.ink.copy(alpha = .16f), Offset(0f, top), Size(size.width, .6f * u))
            // The start button, the song as a button held down, and the tray with its clock.
            drawRoundRect(c.accent, Offset(1.5f * u, top + 2 * u), Size(17 * u, 9 * u), CornerRadius(4.5f * u, 4.5f * u))
            drawNoctoriumMark(Offset(5 * u, top + 3.8f * u), 5.4f * u, Color.White)
            drawRoundRect(Color.White.copy(alpha = .85f), Offset(11.5f * u, top + 5.6f * u), Size(5 * u, 1.8f * u), CornerRadius(u, u))
            drawRoundRect(c.ink.copy(alpha = .16f), Offset(21 * u, top + 2 * u), Size(52 * u, 9 * u), CornerRadius(1.5f * u, 1.5f * u))
            drawRoundRect(c.ink.copy(alpha = .3f), Offset(21 * u, top + 2 * u), Size(52 * u, 9 * u), CornerRadius(1.5f * u, 1.5f * u), style = Stroke(.5f * u))
            planCover(c, 23 * u, top + 3.5f * u, 6 * u)
            drawRoundRect(c.ink, Offset(31.5f * u, top + 5.6f * u), Size(30 * u, 1.9f * u), CornerRadius(u, u))
            drawRoundRect(c.faint.copy(alpha = .2f), Offset(76 * u, top + 2 * u), Size(22.5f * u, 9 * u), CornerRadius(1.5f * u, 1.5f * u))
            planPlay(c, 80.5f * u, top + 6.5f * u, 2 * u)
            drawRoundRect(c.ink, Offset(85 * u, top + 5.6f * u), Size(10 * u, 1.9f * u), CornerRadius(u, u))
        }
    }
}

private fun DrawScope.planCover(c: PlanColours, x: Float, y: Float, side: Float, corner: Float = side * .16f) {
    drawRoundRect(c.cover(Offset(x, y), Size(side, side)), Offset(x, y), Size(side, side), CornerRadius(corner, corner))
}

/** A record: black, grooved, with the cover's colour for a label and the hole through the middle. */
private fun DrawScope.planRecord(c: PlanColours, centre: Offset, radius: Float) {
    drawCircle(Color(0xFF141414), radius, centre)
    listOf(.92f, .8f, .68f, .56f).forEach { r ->
        drawCircle(Color.White.copy(alpha = .1f), radius * r, centre, style = Stroke(radius * .02f + .5f))
    }
    drawCircle(c.cover(Offset(centre.x - radius * .4f, centre.y - radius * .4f), Size(radius * .8f, radius * .8f)), radius * .4f, centre)
    drawCircle(c.page, radius * .06f + .5f, centre)
}

private fun DrawScope.planText(c: PlanColours, x: Float, y: Float, first: Float, second: Float, u: Float, thin: Boolean = false, bold: Boolean = false) {
    val tall = when {
        bold -> 2.8f
        thin -> 1.8f
        else -> 2.3f
    } * u
    drawRoundRect(c.ink, Offset(x, y), Size(first, tall), CornerRadius(u, u))
    drawRoundRect(c.faint, Offset(x, y + tall + (if (thin) 1.6f else 2.2f) * u), Size(second, tall * .8f), CornerRadius(u, u))
}

private fun DrawScope.planPlay(c: PlanColours, centreX: Float, centreY: Float, half: Float, colour: Color = c.ink) {
    val path = Path().apply {
        moveTo(centreX - half * .7f, centreY - half); lineTo(centreX + half, centreY); lineTo(centreX - half * .7f, centreY + half); close()
    }
    drawPath(path, colour)
}

private fun DrawScope.planNext(c: PlanColours, centreX: Float, centreY: Float, half: Float, colour: Color = c.ink) {
    planPlay(c, centreX - half * .25f, centreY, half * .85f, colour)
    drawRect(colour, Offset(centreX + half * .7f, centreY - half * .85f), Size(half * .35f, half * 1.7f))
}

private fun DrawScope.planPrevious(c: PlanColours, centreX: Float, centreY: Float, half: Float, colour: Color = c.ink) {
    val path = Path().apply {
        moveTo(centreX + half * .45f, centreY - half * .85f); lineTo(centreX - half * .85f, centreY); lineTo(centreX + half * .45f, centreY + half * .85f); close()
    }
    drawPath(path, colour)
    drawRect(colour, Offset(centreX - half * 1.1f, centreY - half * .85f), Size(half * .35f, half * 1.7f))
}


/** Every now playing layout, three to a row, each as a plan of the whole screen. */
@Composable
internal fun NowPlayingPicker(selected: PhoneNowPlayingLayout, choose: (PhoneNowPlayingLayout) -> Unit) {
    val colours = planColours()
    PickerGrid(PhoneNowPlayingLayout.entries, columns = 3) { layout, modifier ->
        PickerTile(layout.displayName, selected == layout, { choose(layout) }, modifier) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(.62f)) { drawNowPlayingPlan(layout, colours) }
        }
    }
}

/** A whole now playing screen as [layout] arranges it. */
private fun DrawScope.drawNowPlayingPlan(layout: PhoneNowPlayingLayout, c: PlanColours) {
    val u = size.width / 100f
    val corner = CornerRadius(5 * u, 5 * u)
    drawRoundRect(c.page, cornerRadius = corner)
    val ink = c.ink
    if (layout == PhoneNowPlayingLayout.FULL_COVER) {
        drawRoundRect(c.cover(Offset.Zero, size), cornerRadius = corner)
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                .42f to Color.Transparent,
                .7f to c.page.copy(alpha = .85f),
                1f to c.page,
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corner,
        )
    }
    // The bar along the top: the way down, the screen's name, and the buttons at its end.
    val top = 8 * u
    val chevron = Path().apply {
        moveTo(6 * u, top - 1.2f * u); lineTo(8.5f * u, top + 1.2f * u); lineTo(11 * u, top - 1.2f * u)
    }
    drawPath(chevron, ink, style = Stroke(1.1f * u, cap = StrokeCap.Round))
    drawRoundRect(c.faint, Offset(38 * u, top - .9f * u), Size(24 * u, 1.8f * u), CornerRadius(u, u))
    drawCircle(c.faint, 1.4f * u, Offset(84 * u, top))
    drawCircle(c.faint, 1.4f * u, Offset(92 * u, top))

    var titleLines = true
    when (layout) {
        PhoneNowPlayingLayout.CLASSIC -> planCover(c, 19 * u, 26 * u, 62 * u, corner = 4 * u)
        PhoneNowPlayingLayout.FULL_COVER -> Unit
        PhoneNowPlayingLayout.RECORD -> planRecord(c, Offset(50 * u, 57 * u), 33 * u)
        PhoneNowPlayingLayout.COVER_FLOW -> planCoverFlow(c, u)
        PhoneNowPlayingLayout.SING_ALONG -> {
            titleLines = false
            val widths = listOf(46f, 62f, 40f, 70f, 56f, 36f, 50f)
            widths.forEachIndexed { i, w ->
                val sung = i == 3
                val tall = if (sung) 3.6f else 2.6f
                drawRoundRect(
                    if (sung) c.accent else c.faint,
                    Offset((50 - w / 2) * u, (20 + i * 10) * u - tall / 2 * u),
                    Size(w * u, tall * u),
                    CornerRadius(u, u),
                )
            }
            planCover(c, 7 * u, 92 * u, 12 * u, corner = 2 * u)
            planText(c, 23 * u, 94 * u, 40 * u, 26 * u, u)
        }
        PhoneNowPlayingLayout.BIG_TYPE -> {
            titleLines = false
            listOf(80f, 66f, 42f).forEachIndexed { i, w ->
                drawRoundRect(ink, Offset(7 * u, (30 + i * 16) * u), Size(w * u, 11 * u), CornerRadius(2 * u, 2 * u))
            }
            drawRoundRect(c.faint, Offset(7 * u, 83 * u), Size(40 * u, 3 * u), CornerRadius(u, u))
        }
    }
    if (titleLines) {
        // Under a cover in the middle, the track is centred under it too.
        val centred = layout == PhoneNowPlayingLayout.COVER_FLOW
        drawRoundRect(ink, Offset((if (centred) 21f else 7f) * u, 104 * u), Size(58 * u, 3.6f * u), CornerRadius(u, u))
        drawRoundRect(c.faint, Offset((if (centred) 32f else 7f) * u, 111 * u), Size(36 * u, 2.4f * u), CornerRadius(u, u))
    }
    // The seek bar, the controls under it and the row of tools at the foot: the same in every layout.
    drawRect(c.faint, Offset(7 * u, 122 * u), Size(86 * u, 1.1f * u))
    drawRect(c.accent, Offset(7 * u, 122 * u), Size(86 * u * .4f, 1.1f * u))
    drawCircle(c.accent, 2 * u, Offset((7 + 86 * .4f) * u, 122.5f * u))
    drawCircle(c.faint, 1.5f * u, Offset(13 * u, 136 * u))
    planPrevious(c, 31 * u, 136 * u, 3.4f * u, colour = ink)
    planPlay(c, 51 * u, 136 * u, 5.2f * u, colour = ink)
    planNext(c, 69 * u, 136 * u, 3.4f * u, colour = ink)
    drawCircle(c.faint, 1.5f * u, Offset(87 * u, 136 * u))
    listOf(12f, 50f, 88f).forEach { x -> drawCircle(c.faint, 1.4f * u, Offset(x * u, 150 * u)) }
}

/** The queue's covers in a row, the one playing large and the others turned away, each over its reflection. */
private fun DrawScope.planCoverFlow(c: PlanColours, u: Float) {
    fun turned(nearX: Float, farX: Float) {
        val near = 21 * u
        val far = 14 * u
        val middle = 52 * u
        val path = Path().apply {
            moveTo(nearX, middle - near); lineTo(farX, middle - far); lineTo(farX, middle + far); lineTo(nearX, middle + near); close()
        }
        drawPath(path, c.cover(Offset(minOf(nearX, farX), middle - near), Size(kotlin.math.abs(nearX - farX), near * 2)))
        drawPath(path, Color.Black.copy(alpha = .25f))
        val shadow = Path().apply {
            moveTo(nearX, middle + near + u); lineTo(farX, middle + far + u); lineTo(farX, middle + far + 7 * u); lineTo(nearX, middle + near + 9 * u); close()
        }
        drawPath(shadow, Brush.verticalGradient(listOf(c.accent.copy(alpha = .25f), Color.Transparent), startY = middle + far, endY = middle + near + 9 * u))
    }
    turned(25 * u, 4 * u)
    turned(75 * u, 96 * u)
    val side = 46 * u
    val topLeft = Offset(27 * u, 52 * u - side / 2)
    planCover(c, topLeft.x, topLeft.y, side, corner = 2 * u)
    drawRect(
        Brush.verticalGradient(listOf(c.accent.copy(alpha = .3f), Color.Transparent), startY = topLeft.y + side + u, endY = topLeft.y + side + 12 * u),
        Offset(topLeft.x, topLeft.y + side + u),
        Size(side, 11 * u),
    )
}

