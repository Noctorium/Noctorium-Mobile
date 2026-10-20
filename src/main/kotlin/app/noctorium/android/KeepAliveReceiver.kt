package app.noctorium.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Where keep-alive is brought back from: the phone having started up, Noctorium having been updated, and the
 * watchdog's alarm. And the notification's Turn off.
 *
 * Each does nothing while keep-alive is off. The application is already built by the time this runs -- Android starts
 * Noctorium's process to deliver it -- so the setting is read from Noctorium's settings as everything else reads it.
 */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TURN_OFF) {
            KeepAlive.host(context)?.turnOffKeepAlive()
            return
        }
        val start = receiverStart(intent.action, KeepAlive.on(context)) ?: return
        // The next look in a quarter of an hour, the ordinary way; a song playing again puts it closer.
        if (start == KeepAliveStart.WATCHDOG) KeepAliveWatchdog.resting(context)
        KeepAlive.start(context, start)
    }
}
