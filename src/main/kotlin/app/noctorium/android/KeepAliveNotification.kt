package app.noctorium.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The quiet notification keep-alive shows while nothing is loaded to play: what Android asks of an app that stays
 * running in the background, said as plainly as it can be.
 *
 * On a channel of its own, at low importance -- no sound, no buzz, nothing on the lock screen to read -- and named
 * after the setting, so somebody who would rather not see it finds it by name in Android's notification settings and
 * turns that channel off; Noctorium is kept alive all the same. Tapping it opens Noctorium, and Turn off turns
 * keep-alive off. Whichever service is holding the foreground shows it, under the one id, so there is only ever one.
 */
internal object KeepAliveNotification {
    const val ID = 7_301
    const val CHANNEL = "keep-alive"

    /** Whether it is showing, from either service: it is redrawn when Connect comes or goes, and only then. */
    val showing: Boolean get() = KeepAliveService.running || PlaybackService.showingReady

    fun channel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Keep Noctorium alive")
                .setDescription(
                    "The quiet notification shown while Noctorium is kept alive with nothing playing. Turning this off " +
                        "hides it; Noctorium is still kept alive.",
                )
                .setShowBadge(false)
                .setVibrationEnabled(false)
                .setLightsEnabled(false)
                .build(),
        )
    }

    /** What it says: ready to play, and reachable over Connect when the listener's other devices can find this phone. */
    fun text(reachable: Boolean): String =
        if (reachable) "Ready to play, and reachable over Noctorium Connect" else "Ready to play"

    fun build(context: Context, reachable: Boolean = KeepAlive.host(context)?.reachableOverConnect == true): Notification {
        channel(context)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Keep Noctorium alive")
            .setContentText(text(reachable))
            .setContentIntent(openNoctorium(context))
            .addAction(0, "Turn off", turnOff(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Says it again, when what it says has changed, if it is showing and Noctorium may show notifications at all. */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context) {
        if (!showing || !BackgroundPlayback.notificationsAllowed(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(ID, build(context)) }
    }

    private fun openNoctorium(context: Context): PendingIntent {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun turnOff(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_TURN_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
