package app.noctorium.android.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.android.ui.ServicePlaylistControls
import app.noctorium.android.ui.YouTubePlaylistEditDialog
import app.noctorium.domain.Playlist
import app.noctorium.domain.PlaylistVisibility
import app.noctorium.domain.ProviderType
import app.noctorium.domain.withVisibility
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.ThemePreset
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The listener's own YouTube playlist, opened: who can see it as three chips, with what the lit one means, Edit and
 * Delete -- for each privacy, for one YouTube has not said, and SoundCloud's two beside them for comparison; and the
 * Edit dialog, at an ordinary size, a narrow one and under Windows 98. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.YouTubePlaylistScreenshots"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class YouTubePlaylistScreenshots {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    private val own = Playlist(
        id = "PLmadeupnightbus",
        title = "Night Bus Home",
        provider = ProviderType.YOUTUBE_MUSIC,
        ownerName = "Sample Listener",
        trackCount = 24,
        description = "Songs for the last bus home, window seat, rain on the glass.",
        ownedByListener = true,
    )

    private fun controls(name: String, vararg playlists: Pair<String, Playlist>, preferences: NoctoriumPreferences = NoctoriumPreferences()) =
        compose.shootPhone(screenshotFolder, name, preferences) { _, state ->
            Column(Modifier.padding(vertical = 16.dp)) {
                playlists.forEach { (heading, playlist) ->
                    Text(heading, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp))
                    ServicePlaylistControls(playlist, notice = null, state)
                }
            }
        }

    private fun dialog(name: String, playlist: Playlist = own.withVisibility(PlaylistVisibility.UNLISTED), preferences: NoctoriumPreferences = NoctoriumPreferences()) =
        // The dialog is a window of its own, so the whole screen is taken rather than the page under it.
        compose.shootPhone(screenshotFolder, name, preferences, then = {}) { _, _ ->
            YouTubePlaylistEditDialog(playlist) {}
        }

    @Test
    fun controls() = controls(
        "youtube-playlist-controls",
        "Public" to own.withVisibility(PlaylistVisibility.PUBLIC),
        "Unlisted" to own.withVisibility(PlaylistVisibility.UNLISTED),
        "Private" to own.withVisibility(PlaylistVisibility.PRIVATE),
        "Not said yet" to own,
        "SoundCloud, as it was" to Playlist("1234567", "Tape Hiss Sessions", ProviderType.SOUNDCLOUD, isPublic = false),
    )

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun controlsNarrow() = controls("youtube-playlist-controls-narrow", "Unlisted" to own.withVisibility(PlaylistVisibility.UNLISTED))

    @Test
    fun controls98() = controls(
        "youtube-playlist-controls-98",
        "Private" to own.withVisibility(PlaylistVisibility.PRIVATE),
        preferences = NoctoriumPreferences(theme = ThemePreset.WINDOWS_98),
    )

    /*
     * Material's dialog is taken on the narrow phone only. With a text field in it, at 360dp and wider, Robolectric
     * never lets it settle -- the Rename dialog that has been on phones for months does the same there -- while on a
     * phone it opens as any dialog does.
     */

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun editDialog() = dialog("youtube-playlist-edit")

    @Test
    @Config(qualifiers = NARROW_PHONE)
    fun editDialogNotSaid() = dialog("youtube-playlist-edit-not-said", own)

    @Test
    fun editDialog98() = dialog("youtube-playlist-edit-98", preferences = NoctoriumPreferences(theme = ThemePreset.WINDOWS_98))
}
