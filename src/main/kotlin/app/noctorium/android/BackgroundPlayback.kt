package app.noctorium.android

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

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
        get() = PhoneMaker.thisPhone == PhoneMaker.XIAOMI

    /**
     * Whether the phone restricts Noctorium in the background: Android's "Restricted" battery choice for it, which
     * OnePlus calls "Allow background activity" switched off. Restricted, Android takes the music out of the foreground
     * the moment Noctorium is left and stops it soon after; nothing Noctorium does gets round that, only the switch.
     * Android 9 and later; nothing before.
     */
    fun restrictedInBackground(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val activities = context.getSystemService(ActivityManager::class.java) ?: return false
        return runCatching { activities.isBackgroundRestricted }.getOrDefault(false)
    }

    /**
     * Noctorium's battery usage page, where the restriction is lifted: "Unrestricted" on Android's own, "Allow background
     * activity" on OnePlus's. Its app info page where the phone has no such page to open straight onto.
     */
    fun openBackgroundActivity(context: Context): Boolean =
        open(context, Intent(ACTION_POWER_USAGE_DETAIL, Uri.parse("package:${context.packageName}"))) || openAppInfo(context)

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
    fun openBatteryPage(context: Context): Boolean = openMakerPage(context, PhoneMaker.thisPhone?.page(MakerPageKind.BATTERY))

    /**
     * One of the phone maker's pages for Noctorium -- its autostart, its battery saver -- at the first of the places
     * it has been that this phone opens; Noctorium's app info page where none does, or where there is no such page.
     *
     * Tried by opening rather than looked up first. Android 11 and later hide other apps' activities from a lookup
     * unless each is named in the manifest, so a lookup would miss pages that are there; opening one that is not
     * there, or not open to other apps, is an exception, and the next place is tried.
     */
    internal fun openMakerPage(context: Context, page: MakerPage?): Boolean =
        openFirst(page?.targets.orEmpty(), { open(context, intentFor(context, it)) }) { openAppInfo(context) }

    /** Noctorium's app info page in Android's settings. */
    fun openAppInfo(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    /** Whether Noctorium may show notifications at all: the keep-alive's, and the music's. */
    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Noctorium's notification settings, where they are allowed, and where the keep-alive's own can be hidden. */
    fun openNotificationSettings(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) ||
            openAppInfo(context)

    /** A page in the browser: dontkillmyapp.com's how-to for this phone. */
    fun openWebPage(context: Context, address: String): Boolean = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(address)))

    /** The intent that opens [target], told it is Noctorium's page that is wanted where the page takes that. */
    internal fun intentFor(context: Context, target: MakerTarget): Intent = Intent().apply {
        target.action?.let(::setAction)
        when {
            target.packageName != null && target.className != null -> component = ComponentName(target.packageName, target.className)
            target.packageName != null -> setPackage(target.packageName)
        }
        target.data?.let { data = Uri.parse(it) }
        if (target.packageData) data = Uri.parse("package:${context.packageName}")
        target.packageExtras.forEach { putExtra(it, context.packageName) }
        target.labelExtra?.let { putExtra(it, context.applicationInfo.loadLabel(context.packageManager).toString()) }
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

/**
 * Android's page for one app's battery usage, by the action its settings answer to; not in the SDK's constants, though every
 * phone checked has it, and nothing breaks where one does not: the app info page is opened instead.
 */
internal const val ACTION_POWER_USAGE_DETAIL = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL"

/** The first of [places] that [open] opens, or [otherwise] where none does. */
internal fun <T> openFirst(places: List<T>, open: (T) -> Boolean, otherwise: () -> Boolean): Boolean {
    for (place in places) if (open(place)) return true
    return otherwise()
}
