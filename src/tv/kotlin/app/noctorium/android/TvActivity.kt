package app.noctorium.android

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.noctorium.tv.AppStateTvMusic
import app.noctorium.tv.NoctoriumTv
import com.google.common.util.concurrent.ListenableFuture

/**
 * Noctorium on a television: what Android TV's home screen opens, with the TV's screens from Noctorium-Android-TV.
 *
 * The same application as on a phone -- the same state, services, queue and player -- so only the screens differ.
 * Signing in and settings are [MainActivity]'s, which the TV's Settings page opens.
 */
class TvActivity : ComponentActivity() {

    private var controller: ListenableFuture<MediaController>? = null

    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        // A TV is watched, not held: no screensaver over the song playing while this is on the screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Connecting to the session is what starts the playback service, as on a phone (see MainActivity).
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()

        val music = AppStateTvMusic((application as NoctoriumApplication).state)
        setContent {
            NoctoriumTv(music, openPhoneScreens = { startActivity(Intent(this, MainActivity::class.java)) })
        }
    }

    override fun onStart() {
        super.onStart()
        BackgroundPlay.visible = true
        KeepAlive.start(applicationContext, KeepAliveStart.APP)
        BackgroundPlay.ensure(applicationContext, ForegroundCheck.APP)
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
