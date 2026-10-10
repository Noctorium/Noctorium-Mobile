package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.noctorium.android.ui.ANCHOR_TAG
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.android.ui.PHONE_SETTINGS
import app.noctorium.android.ui.SEARCH_TAG
import app.noctorium.android.ui.SettingIds
import app.noctorium.android.ui.SettingsPageScreen
import app.noctorium.android.ui.SettingsScreen
import app.noctorium.settings.NoctoriumPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every setting the search can find is where its result leads: its page opens at its card and the setting itself
 * is brought onto the screen. One test for each setting, so a card that moves, or a setting whose mark is left out
 * when a card is rewritten, fails by name.
 *
 * A setting that is only on its page some of the time -- once signed in -- is only checked for its page opening.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class SettingsSearchLandsTest(private val id: String) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun `its result opens its page with the setting in view`() {
        val setting = PHONE_SETTINGS.first { it.id == id }
        val state = stillState(NoctoriumPreferences())
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val settings by state.settings.collectAsState()
                NoctoriumTheme(settings.preferences) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        SettingsPageScreen(setting.page, state, signIn = {}, highlight = id, back = {})
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1_500)
            if (setting.whenShown == null) compose.onNodeWithTag(ANCHOR_TAG + id, useUnmergedTree = true).assertIsDisplayed()
        } finally {
            state.close()
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun settings(): List<Array<Any>> = PHONE_SETTINGS.map { arrayOf(it.id) }
    }
}

/** The search box itself: what is typed finds the setting, and choosing it opens its page with it in view. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class SettingsSearchBoxTest {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun `typing louder and choosing the result lands on the volume booster`() {
        val state = stillState(NoctoriumPreferences())
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val settings by state.settings.collectAsState()
                NoctoriumTheme(settings.preferences) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        SettingsScreen(state) {}
                    }
                }
            }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag(SEARCH_TAG).performTextInput("louder")
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Volume booster").performClick()
            compose.mainClock.advanceTimeBy(1_500)
            compose.onNodeWithTag(ANCHOR_TAG + SettingIds.VOLUME_BOOSTER, useUnmergedTree = true).assertIsDisplayed()
        } finally {
            state.close()
        }
    }
}
