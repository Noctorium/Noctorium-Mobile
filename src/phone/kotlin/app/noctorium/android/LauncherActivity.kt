package app.noctorium.android

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.android.ui.PlayerModeArtwork
import app.noctorium.android.ui.playerPlayback
import app.noctorium.android.ui.setPlayerMode
import app.noctorium.core.AppState
import app.noctorium.launcher.PlayerHome
import app.noctorium.launcher.PlayerHomeMusic
import app.noctorium.launcher.PlayerHomeNow
import app.noctorium.launcher.PlayerSection
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Player mode: Noctorium as the phone's home screen, from Noctorium-Mp3-Like-Player-Android-Launcher-'s screen.
 *
 * Switched off in the manifest, and on only from Settings › On this phone › Player mode, after its warning. Home from
 * anywhere comes back here; Noctorium's own screens are [MainActivity]'s, opened at the place a big button names.
 */
class LauncherActivity : ComponentActivity() {

    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Connecting to the session is what starts the playback service, as on Noctorium's own screens.
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        val state = (application as NoctoriumApplication).state
        val music = AppStatePlayerMusic(state)
        setContent {
            val settings by state.settings.collectAsState()
            val equalizer by (application as NoctoriumApplication).player.equalizerAvailable.collectAsState()
            NoctoriumTheme(settings.preferences, equalizer, artworkUrl = state.playerPlayback().track?.artworkUrl) {
                PlayerHome(
                    music,
                    artwork = { url, modifier -> PlayerModeArtwork(url, modifier) },
                    open = { section ->
                        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_FROM_PLAYER_MODE, section.name))
                    },
                    leave = {
                        setPlayerMode(false)
                        finish()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BackgroundPlay.visible = true
    }

    override fun onStop() {
        BackgroundPlay.visible = false
        super.onStop()
    }

    override fun onDestroy() {
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onDestroy()
    }
}

/** The phone's player, as Player mode's screen asks for it. */
private class AppStatePlayerMusic(private val state: AppState) : PlayerHomeMusic {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val now: StateFlow<PlayerHomeNow?> = state.playback.map { playback ->
        playback.track?.let { PlayerHomeNow(it.title, it.artistLine, it.artworkUrl, playback.isPlaying, playback.positionMs, playback.durationMs) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun playPause() = state.togglePlayback()
    override fun next() = state.next()
    override fun previous() = state.previous()
}
