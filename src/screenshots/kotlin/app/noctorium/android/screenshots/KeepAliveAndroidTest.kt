package app.noctorium.android.screenshots

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.noctorium.android.ACTION_TURN_OFF
import app.noctorium.android.ACTION_WATCHDOG
import app.noctorium.android.BackgroundPlayback
import app.noctorium.android.Clocks
import app.noctorium.android.Heartbeat
import app.noctorium.android.KeepAlive
import app.noctorium.android.KeepAliveHost
import app.noctorium.android.KeepAliveNotification
import app.noctorium.android.KeepAliveReceiver
import app.noctorium.android.KeepAliveService
import app.noctorium.android.KeepAliveStart
import app.noctorium.android.KeepAliveStore
import app.noctorium.android.KeepAliveWatchdog
import app.noctorium.android.MakerPageKind
import app.noctorium.android.PhoneMaker
import app.noctorium.android.Refusal
import app.noctorium.android.recentExits
import app.noctorium.android.summarise
import app.noctorium.domain.Artist
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track
import app.noctorium.playback.PlaybackState
import app.noctorium.playback.PlaybackStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An application that is only what keep-alive asks of one: the setting, Connect, and a song to pick up. */
class KeepAliveTestApplication : Application(), KeepAliveHost {
    override var keepAliveOn = false
    override var reachableOverConnect = true
    var turnedOff = false
    val pickedUp = mutableListOf<Pair<String, Long>>()

    override fun turnOffKeepAlive() {
        turnedOff = true
        keepAliveOn = false
    }

    override fun pickUp(queueKey: String, positionMs: Long): Boolean {
        pickedUp += queueKey to positionMs
        return true
    }
}

/**
 * Keep-alive against Android itself, in Robolectric: the receiver, the service and its notification, the watchdog's
 * alarm, the heartbeat and the comeback, the phone makers' pages, and Android's record of why Noctorium stopped.
 * Opt-in with the screenshots, which bring Robolectric:
 *
 *     ./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.KeepAliveAndroidTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KeepAliveTestApplication::class)
class KeepAliveAndroidTest {
    private val app: KeepAliveTestApplication get() = ApplicationProvider.getApplicationContext()
    private val store get() = KeepAliveStore(app)
    private val song = Track(ProviderType.YOUTUBE_MUSIC, "np", "Lanterns Over the Harbour", listOf(Artist("a", "The Quiet Hours", ProviderType.YOUTUBE_MUSIC)), durationMs = 247_000, sourceUrl = "https://example.invalid/np")

    @Before
    fun clean() {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 41)
        store.heartbeat = null
        KeepAlive.heldByPlayback = false
    }

    // Every service a test builds is ended with it: one left running would read, to the next test, as keep-alive already up.
    private val built = mutableListOf<org.robolectric.android.controller.ServiceController<KeepAliveService>>()

    @After
    fun stopServices() {
        built.forEach { it.destroy() }
        built.clear()
    }

    private fun service(intent: Intent? = null): KeepAliveService {
        val controller = Robolectric.buildService(KeepAliveService::class.java, intent).create()
        built += controller
        if (intent != null) controller.startCommand(0, 1) else controller.get().onStartCommand(null, 0, 1)
        return controller.get()
    }

    private fun startedServices(): List<Intent> = generateSequence { shadowOf(app).nextStartedService }.toList()

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Test
    fun `the receiver starts keep-alive after a restart, an update and the watchdog, and only while it is on`() {
        val receiver = KeepAliveReceiver()
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_WATCHDOG)) {
            app.keepAliveOn = false
            receiver.onReceive(app, Intent(action))
            assertEquals(emptyList(), startedServices(), "$action while off")

            app.keepAliveOn = true
            receiver.onReceive(app, Intent(action))
            val started = startedServices().single()
            assertEquals(KeepAliveService::class.java.name, started.component?.className, action)
        }
        assertTrue(alarms().isNotEmpty(), "the watchdog's next look is set")
    }

    @Test
    fun `the notification's Turn off turns it off`() {
        app.keepAliveOn = true
        KeepAliveReceiver().onReceive(app, Intent(ACTION_TURN_OFF))
        assertTrue(app.turnedOff)
        assertFalse(app.keepAliveOn)
    }

    @Test
    fun `started, the service is in the foreground as special use, with the quiet notification`() {
        app.keepAliveOn = true
        val service = service(KeepAliveService.intent(app, KeepAliveStart.SETTING))
        val shadow = shadowOf(service)
        assertEquals(KeepAliveNotification.ID, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
        assertFalse(shadow.isStoppedBySelf)
        assertTrue(KeepAliveService.running)

        val notification = shadow.lastForegroundNotification
        assertEquals(KeepAliveNotification.CHANNEL, notification.channelId)
        assertEquals("Keep Noctorium alive", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Ready to play, and reachable over Noctorium Connect", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Turn off"), notification.actions.map { it.title.toString() })
        assertNotNull(notification.contentIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

        // On a channel of its own, quiet, named so it can be found and hidden.
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(KeepAliveNotification.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Keep Noctorium alive", channel.name.toString())
    }

    @Test
    fun `with Connect out of reach it says only that it is ready`() {
        app.keepAliveOn = true
        app.reachableOverConnect = false
        val service = service(KeepAliveService.intent(app, KeepAliveStart.APP))
        assertEquals("Ready to play", shadowOf(service).lastForegroundNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `started while off, it goes into the foreground as it promised Android, and straight out again`() {
        app.keepAliveOn = false
        val service = service(KeepAliveService.intent(app, KeepAliveStart.BOOT))
        val shadow = shadowOf(service)
        assertEquals(KeepAliveNotification.ID, shadow.lastForegroundNotificationId, "into the foreground first")
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun `while the music's own notification holds it, the quiet one stands down`() {
        app.keepAliveOn = true
        KeepAlive.heldByPlayback = true
        val service = service(KeepAliveService.intent(app, KeepAliveStart.APP))
        assertTrue(shadowOf(service).isStoppedBySelf)
        // Nor is it started at all from elsewhere while the music holds it.
        assertTrue(KeepAlive.start(app, KeepAliveStart.WATCHDOG))
        assertEquals(emptyList(), startedServices())
    }

    @Test
    fun `turned on, it starts and the watchdog is set, and turned off, both go and the heartbeat with them`() {
        app.keepAliveOn = true
        KeepAlive.switched(app, on = true)
        assertEquals(KeepAliveService::class.java.name, startedServices().single().component?.className)
        val alarm = alarms().single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(KeepAliveWatchdog.RESTING_EVERY_MS, alarm.interval)
        assertTrue(store.hintDismissed, "the hint suggesting it is not wanted once it has been turned on")

        store.heartbeat = Heartbeat(song.queueKey, 83_000, SystemClock.elapsedRealtime(), 41, 0)
        app.keepAliveOn = false
        KeepAlive.switched(app, on = false)
        assertEquals(emptyList(), alarms())
        assertNull(store.heartbeat)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `while a song plays it is noted every ten seconds, and the note goes the moment it pauses`() = runTest {
        val playback = MutableStateFlow(PlaybackState())
        val on = MutableStateFlow(true)
        KeepAlive.follow(app, playback, on, backgroundScope)
        runCurrent()
        assertNull(store.heartbeat)

        playback.value = PlaybackState(status = PlaybackStatus.PLAYING, track = song, positionMs = 83_000, durationMs = 247_000)
        runCurrent()
        assertEquals(song.queueKey to 83_000L, store.heartbeat?.let { it.queueKey to it.positionMs })
        assertEquals(41, store.heartbeat?.bootCount)
        // The watchdog half a minute off, allowed while the phone dozes, pushed back by every note.
        val soon = alarms().single()
        assertTrue(soon.allowWhileIdle)
        assertEquals(0L, soon.interval)

        playback.value = playback.value.copy(positionMs = 93_000)
        advanceTimeBy(10_001)
        assertEquals(93_000L, store.heartbeat?.positionMs)

        // Finding a song's address again: still meant to be playing.
        playback.value = playback.value.copy(status = PlaybackStatus.RESOLVING)
        runCurrent()
        assertEquals(93_000L, store.heartbeat?.positionMs)

        playback.value = playback.value.copy(status = PlaybackStatus.PAUSED)
        runCurrent()
        assertNull(store.heartbeat, "paused: nothing to carry on with")
        assertEquals(KeepAliveWatchdog.RESTING_EVERY_MS, alarms().single().interval)
    }

    /**
     * A heartbeat from [ago] milliseconds back in this boot of the phone, read off the phone's own clocks now: what
     * one process would have written just before Android stopped it and the next one started.
     */
    private fun beatFrom(ago: Long): Heartbeat {
        val now = Clocks.now(app)
        return Heartbeat(song.queueKey, 171_000, now.elapsedMs - ago, now.bootCount, now.bootedAtMs)
    }

    @Test
    fun `brought back by Android a moment after being stopped mid-song, the song is picked up where it was`() {
        app.keepAliveOn = true
        // A phone that has been on a while.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        store.heartbeat = beatFrom(ago = 20_000)
        KeepAlive.processStarted(app, on = true)

        // No intent: Android starting it again by itself.
        service(intent = null)
        assertEquals(listOf(song.queueKey to 171_000L), app.pickedUp)
    }

    @Test
    fun `opened by the listener, after a restart, or too late, it waits to be asked`() {
        app.keepAliveOn = true
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        fun comeBack(start: KeepAliveStart, beat: Heartbeat) {
            store.heartbeat = beat
            KeepAlive.processStarted(app, on = true)
            service(KeepAliveService.intent(app, start))
        }
        comeBack(KeepAliveStart.APP, beatFrom(ago = 10_000))
        comeBack(KeepAliveStart.BOOT, beatFrom(ago = 10_000))
        comeBack(KeepAliveStart.WATCHDOG, beatFrom(ago = 10_000).copy(bootCount = 40))
        comeBack(KeepAliveStart.WATCHDOG, beatFrom(ago = 91_000))
        assertEquals(emptyList(), app.pickedUp)
        // The same note, a moment old, brought back by the watchdog, is picked up.
        comeBack(KeepAliveStart.WATCHDOG, beatFrom(ago = 10_000))
        assertEquals(listOf(song.queueKey to 171_000L), app.pickedUp)
    }

    @Test
    fun `a refusal is noted, and the last few kept`() {
        repeat(25) { store.refused(Refusal(KeepAliveStart.WATCHDOG, 1_000L + it)) }
        KeepAlive.refused(app, KeepAliveStart.STICKY)
        val refusals = store.refusals
        assertEquals(20, refusals.size)
        assertEquals(KeepAliveStart.STICKY, refusals.last().start)
    }

    @Test
    fun `a maker's page opens at the first place this phone has, and at Noctorium's app info page otherwise`() {
        shadowOf(app).checkActivities(true)
        val packages = shadowOf(app.packageManager)
        val appInfo = ComponentName("com.android.settings", "com.android.settings.applications.InstalledAppDetails")
        packages.addActivityIfNotPresent(appInfo)
        packages.addIntentFilterForActivity(
            appInfo,
            IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("package")
            },
        )
        val autostart = PhoneMaker.XIAOMI.page(MakerPageKind.AUTOSTART)

        // A phone with none of Xiaomi's pages.
        assertTrue(BackgroundPlayback.openMakerPage(app, autostart))
        val fallback = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fallback.action)
        assertEquals("package:${app.packageName}", fallback.dataString)

        // One with the second of them only: Noctorium's own permissions page, told which app.
        val permissions = ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        packages.addActivityIfNotPresent(permissions)
        // Declared the way Xiaomi's security centre declares it, by the action the page is asked for with.
        packages.addIntentFilterForActivity(
            permissions,
            IntentFilter("miui.intent.action.APP_PERM_EDITOR").apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
        assertTrue(BackgroundPlayback.openMakerPage(app, autostart))
        val opened = shadowOf(app).nextStartedActivity
        assertEquals(permissions, opened.component)
        assertEquals(app.packageName, opened.getStringExtra("extra_pkgname"))

        // And the power keeper's battery saver, told Noctorium's name too.
        val keeper = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        packages.addActivityIfNotPresent(keeper)
        assertTrue(BackgroundPlayback.openMakerPage(app, PhoneMaker.XIAOMI.page(MakerPageKind.BATTERY)))
        val battery = shadowOf(app).nextStartedActivity
        assertEquals(keeper, battery.component)
        assertEquals(app.packageName, battery.getStringExtra("package_name"))
        assertNotNull(battery.getStringExtra("package_label"))
    }

    @Test
    fun `Android's record of why Noctorium stopped is read and counted`() {
        val activities = shadowOf(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        val now = System.currentTimeMillis()
        fun add(reason: Int, importance: Int, ago: Long, note: String? = null) = activities.addApplicationExitInfo(
            ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setProcessName(app.packageName)
                .setReason(reason)
                .setImportance(importance)
                .setTimestamp(now - ago)
                .setDescription(note)
                .build(),
        )
        add(ApplicationExitInfo.REASON_SIGNALED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE, 60_000)
        add(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE, 3_600_000)
        add(ApplicationExitInfo.REASON_USER_REQUESTED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, 7_200_000)
        add(ApplicationExitInfo.REASON_CRASH, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND, 9_000_000)

        val summary = summarise(recentExits(app), now)
        assertEquals(2, summary.stopped)
        assertEquals(ApplicationExitInfo.REASON_SIGNALED, summary.lastStopped?.reason)
        assertEquals(1, summary.crashed)
    }
}
