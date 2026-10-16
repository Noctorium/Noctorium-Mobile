package app.noctorium.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.WindowsXpColours

/**
 * The beta warning at the top of a service's page in Settings: Base's words ([app.noctorium.domain.BETA_WARNING]),
 * where the listener reads them before signing in or setting the service up, and drawn as a warning whichever
 * theme draws it.
 *
 * On the ordinary themes it is a panel in the error colours with a warning sign, as VK's word on its terms is. Under
 * the Windows themes it is what Windows put a warning in: a tooltip's pale yellow with a line round it -- square in
 * 98, rounded in XP -- and a message box's yellow triangle. Not a dialog: it is there whenever the page is, and asks
 * nothing of anybody.
 */
@Composable
internal fun BetaNotice(text: String) {
    val skin = LocalSkin.current
    val words = leadInBold(text)
    // Read out as one notice, by its words: the sign beside them says nothing they do not.
    val described = Modifier.semantics(mergeDescendants = true) {}
    if (skin == ThemeSkin.STANDARD) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .6f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .5f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).then(described),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Glyph.WARNING.image, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Text(words, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        return
    }
    val xp = skin == ThemeSkin.WINDOWS_XP
    val win98 = Win98
    // A tooltip's face and its line, as each drew them: Noctorium 98's is its own night, lined in its pale text.
    val face = if (xp) Color(WindowsXpColours.TOOLTIP) else win98.Tooltip
    val ink = if (xp) Luna.Text else win98.Text
    Surface(
        color = face,
        contentColor = ink,
        shape = RoundedCornerShape(if (xp) 6.dp else 0.dp),
        border = BorderStroke(1.dp, ink),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).then(described),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            WarningTriangle(xp, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(words, color = ink, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * A warning about what one way in cannot do, inside the card that offers it: following Spotify's public playlists,
 * which reaches only the public ones and only their first hundred songs, and importing its data download, which is
 * a copy as of the day Spotify made it. Base's words, as the beta warning's are, and drawn as a warning too -- but
 * not as that one is, since both are on one page and say different things: the beta warning is about the whole
 * service, this about one way into it, and the eye should not take one for the other.
 *
 * On the ordinary themes it is a panel in amber rather than in the error colours, with the same sign, a little
 * smaller, as fits inside a card. Under the Windows themes it is the message box's yellow triangle on a field's
 * white -- sunk into the face in 98, lined in a field's blue in XP -- where the beta warning is a tooltip's yellow.
 */
@Composable
internal fun LimitsNotice(text: String) {
    val skin = LocalSkin.current
    val words = leadInBold(text)
    val described = Modifier.semantics(mergeDescendants = true) {}
    if (skin == ThemeSkin.STANDARD) {
        // Fixed for a light page and for a dark one, as the error colours are, so it reads the same in every theme.
        val pale = MaterialTheme.colorScheme.background.luminance() > .5f
        val amber = if (pale) Color(0xFF8A5A00) else Color(0xFFFFC857)
        Surface(
            color = if (pale) Color(0xFFFFF1CC) else Color(0xFF3A2C08),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, amber.copy(alpha = .6f)),
            modifier = Modifier.fillMaxWidth().then(described),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Glyph.WARNING.image, null, Modifier.size(18.dp), tint = amber)
                Spacer(Modifier.width(10.dp))
                Text(words, color = if (pale) Color(0xFF3D2800) else Color(0xFFFFE8B5), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        return
    }
    val xp = skin == ThemeSkin.WINDOWS_XP
    val win98 = Win98
    val ink = if (xp) Luna.Text else win98.Text
    val edged = if (xp) {
        Modifier.background(Luna.Window).border(1.dp, Luna.FieldEdge)
    } else {
        Modifier.background(win98.Window).drawWithContent {
            drawContent()
            drawEdge98(Edge98.SUNKEN, win98)
        }
    }
    Row(Modifier.fillMaxWidth().then(described).then(edged).padding(horizontal = 10.dp, vertical = 9.dp)) {
        WarningTriangle(xp, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(words, color = ink, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/**
 * [text] with the words before its first colon in bold -- "In beta:" -- so the warning says what it is before it
 * says why. As it is, where there is no colon near the start.
 */
internal fun leadInBold(text: String): AnnotatedString {
    val colon = text.indexOf(':').takeIf { it in 1..LEAD_AT_MOST } ?: return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, colon + 1)) }
        append(text.substring(colon + 1))
    }
}

/** How far into the warning its lead can end: a heading's length, not a sentence's. */
private const val LEAD_AT_MOST = 24

/**
 * The warning sign of a Windows message box: a yellow triangle with a black edge and a black exclamation mark in it,
 * flat in 98 and shaded from a paler yellow at its peak in XP, whose edge was a dark amber rather than black.
 */
@Composable
private fun WarningTriangle(xp: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(w / 2f, h * .05f)
            lineTo(w * .97f, h * .93f)
            lineTo(w * .03f, h * .93f)
            close()
        }
        if (xp) {
            drawPath(triangle, Brush.verticalGradient(listOf(Color(0xFFFFEE8A), Color(0xFFF5BE00))))
        } else {
            drawPath(triangle, Color(0xFFFFFF00))
        }
        drawPath(triangle, if (xp) Color(0xFF8A6400) else Color.Black, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
        val bar = w * .12f
        drawRoundRect(Color.Black, Offset(w / 2f - bar / 2f, h * .33f), Size(bar, h * .33f), CornerRadius(bar / 2f, bar / 2f))
        drawCircle(Color.Black, radius = bar * .62f, center = Offset(w / 2f, h * .78f))
    }
}
