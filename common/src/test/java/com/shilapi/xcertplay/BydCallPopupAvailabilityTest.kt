package com.shilapi.xcertplay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 32])
class BydCallPopupAvailabilityTest {
    private fun installByd() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.bluetoothcall"
            applicationInfo = ApplicationInfo().apply { packageName = "com.byd.bluetoothcall" }
        })
    }
    @Test fun requiresTheBydCallComponentAndOptIn() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(BydCallPopupAvailability.supported(context))
        installByd()
        assertTrue(BydCallPopupAvailability.supported(context))
        assertFalse(AirPlayPersistence.loadHideBydCallPopup(context))
        AirPlayPersistence.saveHideBydCallPopup(context, true)
        assertTrue(AirPlayPersistence.loadHideBydCallPopup(context))
        AirPlayPersistence.saveHideBydCallPopup(context, false)
        assertFalse(AirPlayPersistence.loadHideBydCallPopup(context))
    }
    @Test @Config(sdk = [28]) fun android9DoesNotExposeTheControl() {
        installByd()
        assertFalse(BydCallPopupAvailability.supported(RuntimeEnvironment.getApplication()))
    }
}
