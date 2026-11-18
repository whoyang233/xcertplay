package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
@LooperMode(LooperMode.Mode.PAUSED)
class StartupForegroundTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val handler get() = Handler(Looper.getMainLooper())
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun resetPreferences() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun launchTargetsThisPackageAndReusesTheExistingHostLikeTheLauncherIcon() {
        val intent = StartupForeground.launchIntent(context)
        assertEquals(context.packageName, intent.component!!.packageName)
        assertEquals(CarPlayHostActivity::class.java.name, intent.component!!.className)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        for (flag in listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue(intent.flags and flag != 0)
        val command = StartupForeground.adbCommand("com.whoyang233.xcertplay")
        assertTrue(command.contains("'com.whoyang233.xcertplay/com.shilapi.xcertplay.CarPlayHostActivity'"))
        assertTrue(command.contains("-f 0x" + Integer.toHexString(intent.flags)))
        assertThrows(IllegalArgumentException::class.java) { StartupForeground.adbCommand("x'; reboot #") }
        assertFalse(StartupForeground.isLaunchIntent(intent)) // A late internal launch must not rearm another retry window.
        assertTrue(command.contains("--ez com.shilapi.xcertplay.STARTUP_FOREGROUND_REQUEST true"))
        assertTrue(StartupForeground.isLaunchIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertTrue(StartupForeground.isLaunchIntent(Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)))
        assertFalse(StartupForeground.isLaunchIntent(Intent(Intent.ACTION_VIEW)))
    }

    @Test fun compatibilityDefaultDoesNotEnableBootStartupAndTheOptOutSurvivesReload() {
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertTrue(AirPlayPersistence.loadStartupForegroundAdb(context))
        AirPlayPersistence.saveAutoStartOnBoot(context, true)
        AirPlayPersistence.saveStartupForegroundAdb(context, false)
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertFalse(AirPlayPersistence.loadStartupForegroundAdb(context))
    }

    @Test fun bootReceiverOnlyLaunchesForEnabledBootAndUsesTheSameForegroundEntry() {
        val app = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(app.nextStartedActivity)
        AirPlayPersistence.saveAutoStartOnBoot(context, true)
        AirPlayPersistence.saveStartupForegroundAdb(context, false)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_USER_PRESENT))
        assertNull(app.nextStartedActivity)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val launch = app.nextStartedActivity
        assertNotNull(launch)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertTrue(launch.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(CarPlayHostActivity::class.java.name, launch.component!!.className)
        advance(8000)
        assertEquals("not_visible", StartupForeground.status(context))
    }

    @Test fun visibleHostFinishesOnceAndNeverStartsAdbOrRepeatsTheLaunch() {
        var launches = 0; var adb = 0; val finished = mutableListOf<String>()
        val attempt = StartupForegroundAttempt(handler, { launches++ }, { adb++; Closeable {} }, { finished += it })
        attempt.start(); attempt.onResumed()
        advance(1000)
        assertEquals(listOf("foreground"), finished)
        advance(10000)
        assertEquals(1, launches)
        assertEquals(0, adb)
        assertEquals(1, finished.size)
    }

    @Test fun silentBackgroundLaunchTriesAdbOnceAndCancelsItAtTheDeadline() {
        var launches = 0; var adb = 0; var cancelled = 0; val finished = mutableListOf<String>()
        val attempt = StartupForegroundAttempt(handler, { launches++ }, { adb++; Closeable { cancelled++ } }, { finished += it })
        attempt.start()
        advance(2000)
        assertEquals(1, launches)
        assertEquals(1, adb)
        advance(6000)
        assertEquals(listOf("not_visible"), finished)
        assertEquals(1, cancelled)
        advance(60000)
        assertEquals(1, adb)
        assertEquals(1, finished.size)
    }

    @Test fun userSwitchingAppsStopsAllForegroundAttemptsImmediately() {
        var adb = 0; val finished = mutableListOf<String>()
        val attempt = StartupForegroundAttempt(handler, {}, { adb++; Closeable {} }, { finished += it })
        attempt.start(); attempt.onResumed(); attempt.onPaused(); attempt.onUserLeft()
        advance(10000)
        assertEquals(0, adb)
        assertEquals(listOf("user_left"), finished)
    }

    @Test fun launcherCoveringTheHostBeforeItSettlesStillAllowsOneFallback() {
        var adb = 0; var cancelled = 0; val finished = mutableListOf<String>()
        val attempt = StartupForegroundAttempt(handler, {}, { adb++; Closeable { cancelled++ } }, { finished += it })
        attempt.start(); attempt.onResumed(); advance(500); attempt.onPaused()
        advance(1500)
        assertEquals(1, adb)
        attempt.onResumed(); advance(1000)
        assertEquals(listOf("foreground"), finished)
        assertEquals(1, cancelled)
    }

    @Test fun disabledCompatibilityAndRejectedNativeLaunchStillReleaseTheReceiver() {
        val finished = mutableListOf<String>()
        val attempt = StartupForegroundAttempt(handler, { throw SecurityException("Background start denied") }, null, { finished += it })
        attempt.start(); advance(8000)
        assertEquals(listOf("not_visible"), finished)
    }
}
