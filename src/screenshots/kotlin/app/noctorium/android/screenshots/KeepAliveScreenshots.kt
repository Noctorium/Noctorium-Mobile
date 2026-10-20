package app.noctorium.android.screenshots

import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.Exit
import app.noctorium.android.ExitSummary
import app.noctorium.android.KeepAliveNotification
import app.noctorium.android.PhoneMaker
import app.noctorium.android.ui.KeepAliveCard
import app.noctorium.android.ui.KeepAliveFacts
import app.noctorium.android.ui.KeepAliveHint
import app.noctorium.android.ui.LocalKeepAlivePicture
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.ThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * "Keep Noctorium alive" in Settings: the section off and on, on a made-up Xiaomi that stopped Noctorium three times
 * this week, with the checklist part done; the one-time hint; and the quiet notification itself. In the night theme,
 * 98 and XP, at an ordinary size and the narrowest. Opt-in, as the others:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.*KeepAlive*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class KeepAliveScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = keepAliveLooks("").map { arrayOf(it) }
    }
}

/** The same on the narrowest phone, where the checklist's buttons are closest to its words. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = NARROW_PHONE, application = Application::class)
class NarrowKeepAliveScreenshots(private val look: Look) {
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    @Test
    fun picture() = look.shoot(compose)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = keepAliveLooks("-narrow").map { arrayOf(it) }
    }
}

/** The made-up phone the pictures are of. */
internal object KeepAliveStill {
    /** The moment the pictures are taken at. */
    const val NOW = 1_760_000_000_000L
    private const val HOUR = 3_600_000L

    /** A Xiaomi that has stopped Noctorium three times this week, the last four hours ago; allowed to run in the background, notifications not yet. */
    val stopped = KeepAliveFacts(
        exits = ExitSummary(
            stopped = 3,
            lastStopped = Exit(android.app.ApplicationExitInfo.REASON_SIGNALED, android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE, NOW - 4 * HOUR),
            crashed = 0,
        ),
        refusals = 0,
        unrestricted = true,
        notificationsAllowed = false,
        asksForNotifications = true,
        maker = PhoneMaker.XIAOMI,
        manufacturer = "Xiaomi",
        nowMs = NOW,
    )
}

private val THEMES = listOf(
    "night" to NoctoriumPreferences(),
    "98" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_98, taskbarClock = false),
    "xp" to NoctoriumPreferences(theme = ThemePreset.WINDOWS_XP, taskbarClock = false),
)

private fun keepAliveLooks(size: String): List<Look> = THEMES.flatMap { (theme, preferences) ->
    val on = preferences.copy(phone = PhonePreferences(keepAlive = true))
    listOf(
        Look("keep-alive-off-$theme$size", preferences, playing = { null }) { settings, state ->
            CompositionLocalProvider(LocalKeepAlivePicture provides KeepAliveStill.stopped) { Card { KeepAliveCard(settings, state) } }
        },
        Look("keep-alive-on-$theme$size", on, playing = { null }) { settings, state ->
            CompositionLocalProvider(LocalKeepAlivePicture provides KeepAliveStill.stopped) { Card { KeepAliveCard(settings, state) } }
        },
        Look("keep-alive-hint-$theme$size", preferences, playing = { null }) { settings, state ->
            CompositionLocalProvider(LocalKeepAlivePicture provides KeepAliveStill.stopped) { Card { KeepAliveHint(settings, state) {} } }
        },
    )
}

/**
 * The quiet notification, drawn by Android's own notification layout: the content view the notification shade would
 * show, inflated and painted here. Not the shade itself, which is the system's, but the same words in the same places.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class KeepAliveNotificationPicture {
    private fun draw(name: String, reachable: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = KeepAliveNotification.build(context, reachable)
        val builder = Notification.Builder.recoverBuilder(context, notification)
        val frame = FrameLayout(context)
        // Expanded, as the shade shows the top notification: the words whole, and Turn off under them.
        val view = builder.createBigContentView().apply(context, frame)
        frame.addView(view)
        val width = (context.resources.displayMetrics.density * 411).toInt()
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((context.resources.displayMetrics.density * 400).toInt(), View.MeasureSpec.AT_MOST))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
        val bitmap = Bitmap.createBitmap(frame.measuredWidth, frame.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF2F2F2.toInt())
        frame.draw(canvas)
        screenshotFolder.mkdirs()
        FileOutputStream(File(screenshotFolder, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun reachable() = draw("keep-alive-notification", reachable = true)

    @Test
    fun alone() = draw("keep-alive-notification-no-connect", reachable = false)
}
