package app.noctorium.android

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.noctorium.settings.ThemeSkin
import app.noctorium.settings.themeColours
import app.noctorium.settings.themeSkin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.noctorium.android.ui.NoctoriumPhone
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import com.google.common.util.concurrent.ListenableFuture

/**
 * The one screen Android starts, holding the one application.
 *
 * `AppState` belongs to the Application rather than to this, deliberately: it owns the queue, the player
 * and every in-flight request, and rotating the phone must not restart a download or silence what is
 * playing. This binds a view to it and gets out of the way.
 */
class MainActivity : ComponentActivity() {

    private var controller: ListenableFuture<MediaController>? = null

    /**
     * Asked for the moment the app opens, because the answer decides whether music can play at all.
     *
     * A foreground service has to show a notification, and on Android 13 and later a notification needs
     * permission. Refused, the service cannot start, and playback then stops whenever the system decides
     * to reclaim a backgrounded process. It is requested here rather than at the moment of first play so
     * the dialog does not land on top of a track somebody just chose.
     */
    private val askForNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Either answer is survivable; playback in the background is what is at stake. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, which is where the library insists on being asked. It draws the icon
        // named in Theme.Noctorium.Starting and hands over to the real theme once Compose has a frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        /**
         * Connecting to the session is what starts the service.
         *
         * Declaring it in the manifest is not enough — nothing had ever bound to it, so no session was
         * registered, and the consequences were all invisible until looked for: no notification, no
         * lock-screen controls, no headset buttons, and nothing keeping the process alive once it went to
         * the background. The controller is not used to control anything; the screen holds the player
         * directly. It exists so the service does.
         */
        controller = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()

        val state = (application as NoctoriumApplication).state
        // Only on a real start: a rotation recreates the activity with the same intent, and replaying the
        // link would start the song again from the top.
        if (savedInstanceState == null) openShared(intent)
        setContent {
            // Read live, so changing the accent or the background in Settings repaints at once rather
            // than at the next launch.
            val settings by state.settings.collectAsState()
            val playback by state.playback.collectAsState()

            /*
             * Holding the screen awake, but only while something is actually playing.
             *
             * Tying it to the setting alone would keep a phone lit in somebody pocket all afternoon. The
             * flag is cleared again the moment playback stops, so the worst case is a bright screen for as
             * long as the music lasts, which is what was asked for.
             */
            LaunchedEffect(settings.preferences.phone.keepScreenOn, playback.isPlaying) {
                if (settings.preferences.phone.keepScreenOn && playback.isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            /*
             * Silence-skipping, applied when it changes and once at startup.
             *
             * ExoPlayer keeps it across tracks, so this does not need to run per song; it does need to run on
             * the first composition, or a choice made last week would sit in the settings file doing nothing
             * until it was touched again. The speed is not here any more: it is Noctorium's own setting now,
             * and core tells the player of it the way it tells it of the equaliser.
             */
            LaunchedEffect(settings.preferences.phone.skipSilence) {
                (application as NoctoriumApplication).player.setSkipSilence(settings.preferences.phone.skipSilence)
            }
            // The status bar's icons have to be told which way round the theme is, or a light theme gets
            // white icons on a white bar. XP's taskbar runs on under the navigation in its blue, which wants white.
            val theme = settings.preferences.themeColours()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = theme.light
                    isAppearanceLightNavigationBars = theme.light && settings.preferences.themeSkin != ThemeSkin.WINDOWS_XP
                }
            }
            val equalizerAvailable by (application as NoctoriumApplication).player.equalizerAvailable.collectAsState()
            NoctoriumTheme(settings.preferences, equalizerAvailable) { NoctoriumPhone(state) }
        }
    }

    /** A link shared while Noctorium was already open: singleTask brings this one forward rather than a new one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openShared(intent)
    }

    /**
     * Plays what another app shared, when it was shared here.
     *
     * YouTube shares a line of text with the link at the end of it, SoundCloud a short `on.soundcloud.com`
     * link; [AppState.openLink] reads both. The link screen is opened so what happened is on the screen --
     * the song, or why not -- rather than the music simply starting behind whatever was showing.
     */
    private fun openShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotBlank) ?: return
        val state = (application as NoctoriumApplication).state
        state.openLink(text)
        state.navigate(Destination.LINK)
    }

    override fun onDestroy() {
        // The controller is released; the player, the service and the application state are not. Music is
        // expected to carry on when this screen goes away, which is the entire reason the service exists.
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onDestroy()
    }
}
