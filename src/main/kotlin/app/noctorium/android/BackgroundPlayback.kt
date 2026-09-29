package app.noctorium.android

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether the phone lets Noctorium go on playing once the screen is locked, and the way to let it.
 *
 * Noctorium plays from a foreground service and holds a wake lock while it streams, which is what Android
 * asks of a music app. Some phones stop it anyway: Xiaomi's power manager, and a few others, close
 * background apps when the screen locks unless the app has been left out of battery optimisation. That is
 * a choice only the listener can make, so this never makes it -- it opens the phone's own dialog or page,
 * and the listener answers there.
 */
object BackgroundPlayback {

    /** True when Android has left Noctorium out of battery optimisation, or has no such thing. */
    fun unrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Phones whose own power manager is known to stop music at the lock screen without this. */
    val strictManufacturer: Boolean
        get() = Build.MANUFACTURER.orEmpty().lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Android's own "let this app always run in the background?" dialog, for Noctorium.
     *
     * The one-tap way, where the phone offers it. Falls back to the list of apps that battery optimisation
     * covers, where Noctorium can be picked by hand.
     */
    @SuppressLint("BatteryLife")
    fun ask(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) ||
            open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /**
     * The phone maker's own battery page for Noctorium, where "No restrictions" is chosen.
     *
     * On Xiaomi's phones that page is the power keeper's, and it is separate from Android's setting above:
     * both may need saying yes to. Where it cannot be opened, Noctorium's app info page is, which is where
     * the same choice sits one tap further in.
     */
    fun openBatteryPage(context: Context): Boolean {
        if (strictManufacturer) {
            val xiaomi = Intent().apply {
                component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            }
            if (open(context, xiaomi)) return true
        }
        return open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun open(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
