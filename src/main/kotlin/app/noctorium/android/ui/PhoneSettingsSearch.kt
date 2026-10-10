package app.noctorium.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.settings.SettingMatch
import app.noctorium.settings.SettingMatchKind
import app.noctorium.settings.ThemeSkin
import kotlinx.coroutines.delay

/** The setting a search result led to, which its page brings into view and lights for a moment. */
internal val LocalSettingHighlight = staticCompositionLocalOf<String?> { null }

/**
 * Where one setting is on its page: what the settings search jumps to, and lights.
 *
 * Arriving from a search, the page scrolls to the setting's card, this brings the setting itself into view --
 * a card can be taller than the screen -- and it glows in the accent for a couple of seconds, so the eye lands on
 * it rather than hunting. Under 98 the glow is that desktop's dotted focus rectangle, and under XP its orange
 * hover edge. Otherwise it is nothing at all: it takes no room and draws nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingAnchor(id: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val highlighted = LocalSettingHighlight.current == id
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (!highlighted) return@LaunchedEffect
        // Once the page has slid in and scrolled to the card.
        delay(HIGHLIGHT_SETTLE_MS)
        runCatching { requester.bringIntoView() }
        glow.animateTo(1f, tween(220))
        delay(HIGHLIGHT_HOLD_MS)
        glow.animateTo(0f, tween(900))
    }
    val accent = MaterialTheme.colorScheme.primary
    val skin = LocalSkin.current
    val win98 = Win98
    Column(
        modifier
            .fillMaxWidth()
            .testTag(ANCHOR_TAG + id)
            .bringIntoViewRequester(requester)
            .drawBehind {
                val strength = glow.value
                if (strength <= 0f) return@drawBehind
                val outX = 6.dp.toPx()
                val outY = 3.dp.toPx()
                val topLeft = Offset(-outX, -outY)
                val area = Size(size.width + outX * 2, size.height + outY * 2)
                when (skin) {
                    ThemeSkin.STANDARD -> {
                        val corner = CornerRadius(10.dp.toPx())
                        drawRoundRect(accent.copy(alpha = .14f * strength), topLeft, area, corner)
                        drawRoundRect(accent.copy(alpha = .7f * strength), topLeft, area, corner, style = Stroke(1.5.dp.toPx()))
                    }
                    ThemeSkin.WINDOWS_98 -> {
                        drawRect(win98.Selection.copy(alpha = .12f * strength), topLeft, area)
                        drawRect(
                            win98.Text.copy(alpha = strength),
                            topLeft,
                            area,
                            style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 1f))),
                        )
                    }
                    ThemeSkin.WINDOWS_XP -> {
                        val corner = CornerRadius(3.dp.toPx())
                        drawRoundRect(Luna.Hot.copy(alpha = .14f * strength), topLeft, area, corner)
                        drawRoundRect(Luna.Hot.copy(alpha = strength), topLeft, area, corner, style = Stroke(2.dp.toPx()))
                    }
                }
            },
        content = content,
    )
}

/** What each setting's place on its page is called to a test: the anchor's tag, then the setting's id. */
internal const val ANCHOR_TAG = "setting:"

/** How long a page is given to arrive before its setting is lit, and how long the light stays. */
private const val HIGHLIGHT_SETTLE_MS = 380L
private const val HIGHLIGHT_HOLD_MS = 1_800L

/** The search box at the head of Settings. */
@Composable
internal fun SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val skinned = LocalSkin.current != ThemeSkin.STANDARD
    OutlinedTextField(
        value = query,
        onValueChange = { onQuery(it.take(60)) },
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Glyph.SEARCH.image, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton({ onQuery("") }) { Icon(Glyph.CLOSE.image, "Clear the search") }
            }
        },
        singleLine = true,
        shape = if (skinned) RectangleShape else RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag(SEARCH_TAG),
    )
}

/** The settings search box, to a test. */
internal const val SEARCH_TAG = "settings-search"

/**
 * What a result says under its name: where the setting is, and why it was found when that is not plain from its
 * name -- the other word it goes by ("dark mode"), or that it only shows once something is so.
 */
internal fun resultDetail(setting: PhoneSetting, match: SettingMatch): String = listOfNotNull(
    setting.page.path,
    match.keyword?.takeIf { match.kind == SettingMatchKind.KEYWORD }?.let { "also “$it”" },
    setting.whenShown,
).joinToString(" · ")

/** The results, or a note when there are none. */
@Composable
internal fun NoSettingsFound(query: String) {
    Text(
        "Nothing in Settings matches “${query.trim()}”. Try another word for it.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
    )
}
