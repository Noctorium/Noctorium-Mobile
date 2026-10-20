package app.noctorium.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Keeps Noctorium in the foreground while keep-alive is on and nothing is loaded to play, with the quiet notification
 * (see [KeepAliveNotification]), so a power manager that closes background apps finds an app it is meant to leave.
 *
 * It does nothing but be there. The music, the session and Connect are the application's and go on as they would; this
 * only holds the process up. The moment a song is loaded the media session service holds the foreground with the
 * music's own notification, and this stands down, so the two are never shown together. It is also how keep-alive comes
 * back: the receiver starts it after the phone restarts and when the watchdog finds it gone, and Android starts it
 * again by itself after the process was killed, since it asks to be (START_STICKY).
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No intent is Android starting it again by itself, after the process was killed.
        val start = if (intent == null) {
            KeepAliveStart.STICKY
        } else {
            intent.getStringExtra(EXTRA_START)?.let { runCatching { KeepAliveStart.valueOf(it) }.getOrNull() } ?: KeepAliveStart.APP
        }
        // Into the foreground first, whatever happens next: a service started as a foreground one that stops before
        // saying so is, to Android, an app that broke its word, and it ends the whole of Noctorium for it.
        try {
            ServiceCompat.startForeground(this, KeepAliveNotification.ID, KeepAliveNotification.build(this), readyForegroundType(Build.VERSION.SDK_INT))
        } catch (refusal: IllegalStateException) {
            // Android 12 and later refuse it from the background now and then, a restart by Android itself included.
            KeepAlive.refused(this, start, refusal)
            stopSelf()
            return START_NOT_STICKY
        } catch (refusal: SecurityException) {
            KeepAlive.refused(this, start, refusal)
            stopSelf()
            return START_NOT_STICKY
        }
        PlaybackService.resyncHeld()
        if (!KeepAlive.on(this) || KeepAlive.heldByPlayback) {
            stopEverything()
            return START_NOT_STICKY
        }
        KeepAlive.cameBack(this, start)
        return START_STICKY
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_START = "start"

        /** Whether it is running in this process. */
        @Volatile
        var running = false
            private set

        internal fun intent(context: Context, start: KeepAliveStart): Intent =
            Intent(context, KeepAliveService::class.java).putExtra(EXTRA_START, start.name)

        /** Stops it, with its notification: keep-alive turned off, or the music's own notification holding it now. */
        fun standDown(context: Context) {
            if (running) context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
