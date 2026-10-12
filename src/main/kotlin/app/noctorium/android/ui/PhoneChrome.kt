package app.noctorium.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.settings.BadgePolicy
import app.noctorium.settings.CardSize
import app.noctorium.settings.ServiceBadgeStyle
import kotlin.math.roundToInt

/**
 * How wide a card is, translated for a phone.
 *
 * The desktop's numbers — 140, 172, 208 — are sized against a window a metre from your face. On a screen
 * held at arm's length the same widths leave one and a half cards visible, so the choice is kept and the
 * scale is not.
 */
internal fun CardSize.phoneWidth(): Dp = when (this) {
    CardSize.COMPACT -> 112.dp
    CardSize.COMFORTABLE -> 134.dp
    CardSize.LARGE -> 160.dp
}

/**
 * Whether a row should name the services in it.
 *
 * "Only when mixed" is the interesting one and the default: a shelf of nothing but YouTube Music says
 * nothing useful by stamping YTM on all six, whereas a shelf with one SoundCloud track among them very
 * much does.
 */
internal fun BadgePolicy.showsFor(tracks: List<Track>): Boolean = when (this) {
    BadgePolicy.ALWAYS -> true
    BadgePolicy.NEVER -> false
    BadgePolicy.AUTO -> tracks.map { it.provider }.distinct().size > 1
}

/** How the badges are drawn, provided by [NoctoriumTheme] from the settings. Marks wherever nothing provides it. */
internal val LocalServiceBadgeStyle = staticCompositionLocalOf { ServiceBadgeStyle.MARKS }

/** How much of the letters' line a badge's mark takes up, the rest being the chip round it. */
private const val MARK_SHARE = .66f

/** The small badge in the corner of a card saying where a track came from. */
@Composable
internal fun ProviderBadge(track: Track, modifier: Modifier = Modifier) = ProviderBadge(track.provider, modifier)

/**
 * The badge for [provider]: its own mark, or the letters -- YTM, SC, SP -- it had before there were marks, as the
 * listener chooses under Look › Icons ([ServiceBadgeStyle]).
 *
 * Either way it is the same chip, in the same colour and at the same height, so changing the setting moves nothing
 * on the card. A mark says less than the letters to somebody who does not know it, so it carries the service's name
 * for TalkBack.
 */
@Composable
internal fun ProviderBadge(provider: ProviderType, modifier: Modifier = Modifier) {
    val marks = LocalServiceBadgeStyle.current == ServiceBadgeStyle.MARKS
    Surface(
        color = provider.badgeColour().copy(alpha = .92f),
        shape = RoundedCornerShape(5.dp),
        modifier = if (marks) modifier.semantics(mergeDescendants = true) { contentDescription = provider.displayName } else modifier,
    ) {
        if (marks) {
            BadgeMark(provider, Modifier.padding(1.dp))
        } else {
            BadgeLetters(provider.badgeLabel(), Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}

/**
 * [provider]'s mark, in a square as tall as its badge's letters would be. The letters are measured and never drawn,
 * so the chip is exactly the height it is with them, at whatever size the text is.
 */
@Composable
private fun BadgeMark(provider: ProviderType, modifier: Modifier) {
    Layout(
        content = {
            BadgeLetters(provider.badgeLabel())
            Icon(provider.markVector(), null, tint = Color.White)
        },
        modifier = modifier,
    ) { (line, mark), _ ->
        val side = line.measure(Constraints()).height
        // As much smaller than the square as leaves the same whole pixels each side of it, so it is not half a pixel
        // off its middle.
        val size = (side * MARK_SHARE).roundToInt().let { it - (side - it) % 2 }
        val drawn = mark.measure(Constraints.fixed(size, size))
        layout(side, side) { drawn.place((side - size) / 2, (side - size) / 2) }
    }
}

@Composable
private fun BadgeLetters(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

/**
 * The same colours the desktop badges use, so a card reads identically on either screen. Bandcamp's Settings
 * tile is tinted with its own, the way Spotify's is with Spotify's green.
 */
internal fun ProviderType.badgeColour(): Color = when (this) {
    ProviderType.YOUTUBE_MUSIC -> Color(0xFF8B5CF6)
    ProviderType.YOUTUBE_VIDEO -> Color(0xFFCC0000)
    ProviderType.SOUNDCLOUD -> Color(0xFFFF7300)
    ProviderType.SPOTIFY -> Color(0xFF1DB954)
    ProviderType.BANDCAMP -> Color(0xFF408294)
    ProviderType.VK -> Color(0xFF0077FF)
    ProviderType.LOCAL -> Color(0xFF5A5A6E)
}

private fun ProviderType.badgeLabel(): String = when (this) {
    ProviderType.YOUTUBE_MUSIC -> "YTM"
    ProviderType.YOUTUBE_VIDEO -> "YT"
    ProviderType.SOUNDCLOUD -> "SC"
    ProviderType.SPOTIFY -> "SP"
    ProviderType.BANDCAMP -> "BC"
    ProviderType.VK -> "VK"
    ProviderType.LOCAL -> "FILE"
}
