package app.noctorium.android.screenshots

import android.app.ActivityManager
import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ACTION_POWER_USAGE_DETAIL
import app.noctorium.android.BackgroundPlay
import app.noctorium.android.BackgroundPlayback
import app.noctorium.android.ForegroundCheck
import app.noctorium.android.Interrupted
import app.noctorium.android.Interruption
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.MakerPageKind
import app.noctorium.android.PhoneMaker
import app.noctorium.android.PlaybackService
import app.noctorium.android.ui.BackgroundPlayNotices
import app.noctorium.android.ui.BackgroundRestrictedBanner
import app.noctorium.android.ui.KeepAliveCard
import app.noctorium.android.ui.NoctoriumTheme
import app.noctorium.android.ui.RESTRICTED_WORDS
import app.noctorium.settings.NoctoriumPreferences
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Playing in the background: the music's foreground taken back after Android took it away while it played, with
 * keep-alive on and off; the warning while the phone restricts Noctorium in the background, gone once it does not, and
 * the page its button opens; and what is noted and said about it afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = StickyRestartApplication::class)
class BackgroundPlayTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmptyActivityDeclared()).around(compose)

    private val app: StickyRestartApplication get() = ApplicationProvider.getApplicationContext()
    private val store get() = KeepAliveStore(app)
    private val services = mutableListOf<ServiceController<*>>()

    @Before
    fun away() {
        // Noctorium off the screen, as when Android takes the foreground away.
        BackgroundPlay.visible = false
    }

    @After
    fun endServices() {
        services.forEach { runCatching { it.destroy() } }
        services.clear()
        BackgroundPlay.visible = false
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    /** Whether the service is in the foreground, as Android would say. */
    private fun inForeground(service: Service): Boolean =
        !shadowOf(service).isForegroundStopped && service.foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE

    /**
     * Android taking the music out of the foreground without a word to it, as a phone restricting Noctorium in the
     * background does the moment it is left. Android forgets the foreground's type with it; Robolectric's service does
     * not by itself, so it is told.
     */
    private fun takeForegroundAway(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        ReflectionHelpers.setField(shadowOf(service), "foregroundServiceType", ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
    }

    /** The media session service, built as the screen's binding builds it, playing [songs]. */
    private fun playing(vararg songs: String): PlaybackService {
        val playback = Robolectric.buildService(PlaybackService::class.java).also { services += it }.create()
        app.player.start(songs.toList(), 0)
        settle()
        assertTrue(inForeground(playback.get()), "in the foreground as it plays")
        return playback.get()
    }

    private fun assertPlayingInForeground(service: PlaybackService, title: String) {
        assertTrue(inForeground(service), "in the foreground again")
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, service.foregroundServiceType)
        assertEquals(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        assertEquals(title, shadowOf(service).lastForegroundNotification.title())
    }

    @Test
    fun `with keep-alive off, the foreground Android took while the music played is taken back at the next check`() {
        app.keepAliveOn = false
        val service = playing("TWENTIES", "worst behaviour")
        takeForegroundAway(service)
        assertFalse(inForeground(service))

        BackgroundPlay.ensure(app, ForegroundCheck.HEARTBEAT)
        assertPlayingInForeground(service, "TWENTIES")
        assertEquals(listOf(Interruption.TAKEN), store.interruptions.map(Interrupted::why), "noted, as taken and given back")
    }

    @Test
    fun `with keep-alive on, it is taken back when the song changes`() {
        app.keepAliveOn = true
        val service = playing("TWENTIES", "worst behaviour")
        takeForegroundAway(service)

        app.player.seekToNextMediaItem()
        settle()
        assertPlayingInForeground(service, "worst behaviour")
    }

    @Test
    fun `and when Noctorium comes to the front`() {
        app.keepAliveOn = false
        val service = playing("TWENTIES")
        takeForegroundAway(service)
        BackgroundPlay.visible = true
        BackgroundPlay.ensure(app, ForegroundCheck.APP)
        assertPlayingInForeground(service, "TWENTIES")
        assertEquals(emptyList(), store.interruptions, "not noted while the listener is looking: nothing was lost")
    }

    @Test
    fun `taken away on a phone that restricts Noctorium, it is put down to the restriction`() {
        val service = playing("TWENTIES")
        shadowOf(app.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(true)
        takeForegroundAway(service)
        BackgroundPlay.ensure(app, ForegroundCheck.HEARTBEAT)
        assertEquals(listOf(Interruption.RESTRICTED), store.interruptions.map(Interrupted::why))
        // Once, however long it goes on: the next check finds it back, and nothing more to note.
        BackgroundPlay.ensure(app, ForegroundCheck.HEARTBEAT)
        assertEquals(1, store.interruptions.size)
    }

    @Test
    fun `stopped by Android while the music plays, it is noted, and the application connects to the session again`() {
        val playback = Robolectric.buildService(PlaybackService::class.java).also { services += it }.create()
        app.player.start(listOf("TWENTIES"), 0)
        settle()
        playback.destroy()
        assertEquals(listOf(Interruption.STOPPED), store.interruptions.map(Interrupted::why))

        val before = shadowOf(app).boundServiceConnections.size
        BackgroundPlay.ensure(app, ForegroundCheck.HEARTBEAT)
        assertTrue(shadowOf(app).boundServiceConnections.size > before, "bound to the media session service again")
    }

    @Test
    fun `nothing is noted while nothing plays`() {
        val playback = Robolectric.buildService(PlaybackService::class.java).also { services += it }.create()
        BackgroundPlay.ensure(app, ForegroundCheck.HEARTBEAT)
        playback.destroy()
        assertEquals(emptyList(), store.interruptions)
    }

    /** The phone's restriction, as both the application and the screen ask Android about it. */
    private fun restrict(restricted: Boolean) {
        shadowOf(app.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(restricted)
        shadowOf(compose.activity.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(restricted)
    }

    /** Noctorium leaving the front and coming back to it, which is when the restriction is asked about again. */
    private fun backToFront() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    @Test
    fun `the warning shows while the phone restricts Noctorium in the background, and goes by itself once it does not`() {
        restrict(true)
        compose.setContent { NoctoriumTheme(NoctoriumPreferences()) { BackgroundRestrictedBanner() } }
        compose.onNodeWithText(RESTRICTED_WORDS).assertExists()

        restrict(false)
        backToFront()
        compose.onNodeWithText(RESTRICTED_WORDS).assertDoesNotExist()

        restrict(true)
        backToFront()
        compose.onNodeWithText(RESTRICTED_WORDS).assertExists()
    }

    @Test
    fun `its button opens Noctorium's battery usage page, or its app info page where there is none`() {
        shadowOf(app).checkActivities(true)
        val packages = shadowOf(app.packageManager)
        fun page(name: String, action: String) = ComponentName("com.android.settings", name).also { page ->
            packages.addActivityIfNotPresent(page)
            packages.addIntentFilterForActivity(page, IntentFilter(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("package")
            })
        }
        page("com.android.settings.applications.InstalledAppDetails", Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertTrue(BackgroundPlayback.openBackgroundActivity(app))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)

        page("com.android.settings.fuelgauge.AdvancedPowerUsageDetailActivity", ACTION_POWER_USAGE_DETAIL)
        assertTrue(BackgroundPlayback.openBackgroundActivity(app))
        val opened = shadowOf(app).nextStartedActivity
        assertEquals(ACTION_POWER_USAGE_DETAIL, opened.action)
        assertEquals("package:${app.packageName}", opened.dataString)

        // And OnePlus's checklist line, the same page.
        assertTrue(BackgroundPlayback.openMakerPage(app, PhoneMaker.ONEPLUS.page(MakerPageKind.BATTERY)))
        assertEquals("package:${app.packageName}", shadowOf(app).nextStartedActivity.dataString)
    }

    @Test
    fun `coming back after the music was stopped in the background, it is explained once, with the switch`() {
        store.interrupted(Interrupted(Interruption.RESTRICTED, System.currentTimeMillis()))
        compose.setContent { NoctoriumTheme(NoctoriumPreferences()) { BackgroundPlayNotices(playing = false) } }
        compose.onNodeWithText("The music was stopped in the background").assertExists()
        compose.onNodeWithText("Allow background activity").assertExists()

        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("The music was stopped in the background").assertDoesNotExist()
        assertTrue(store.interruptionsSeenAt > 0)
        backToFront()
        compose.onNodeWithText("The music was stopped in the background").assertDoesNotExist()
    }

    @Test
    fun `the keep-alive section counts it, and says the phone restricts Noctorium`() {
        val now = System.currentTimeMillis()
        store.interrupted(Interrupted(Interruption.STOPPED, now - 60_000))
        store.interrupted(Interrupted(Interruption.RESTRICTED, now - 30_000))
        store.interrupted(Interrupted(Interruption.TAKEN, now - 10_000))
        restrict(true)
        val state = stillState(NoctoriumPreferences())
        try {
            compose.setContent { NoctoriumTheme(NoctoriumPreferences()) { KeepAliveCard(state.settings.value, state) } }
            compose.onNodeWithText("Android stopped the music in the background twice in the last week.").assertExists()
            compose.onNodeWithText(RESTRICTED_WORDS).assertExists()
        } finally {
            state.close()
        }
    }
}
