package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebDisplayDisabledTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun oldEnabledPreferencesCannotRedirectEitherDisplayAfterUpgrade() {
        for (type in listOf(110,111)) {
            prefs.edit().putBoolean("web_display_${type}_enabled",true)
                .putBoolean("web_display_${type}_preview",false)
                .putInt("web_display_${type}_width",1920).commit()
            val loaded=AirPlayPersistence.loadWebDisplay(context,type)
            assertFalse(loaded.enabled)
            assertTrue(loaded.preview)
            val original=AirPlayDisplayConfig(800,480,widthPhysicalMm=200)
            assertSame(original,loaded.override(original))
            assertSame(original,loaded.copy(enabled=true).override(original))
        }
    }

    @Test fun savingUnrelatedSettingsKeepsOptionalPreferencesForAnExplicitFutureBuild() {
        prefs.edit().putBoolean("web_display_110_enabled",true).putInt("web_display_110_width",1920).commit()
        AirPlayPersistence.saveWebDisplay(context,110,CarPlayWebDisplayConfig())
        assertTrue(prefs.getBoolean("web_display_110_enabled",false))
        assertEquals(1920,prefs.getInt("web_display_110_width",0))
        assertFalse(AirPlayPersistence.loadWebDisplay(context,110).enabled)
    }

    @Test fun evenExplicitRemoteConfigCannotStartASessionOrAdvertiseAnAddress() {
        assertFalse(CarPlayWebDisplayFactory.ENABLED)
        assertNull(CarPlayWebDisplayFactory.create(context,mapOf(110 to CarPlayWebDisplayConfig(enabled=true))) {
            error("Remote touch must be unavailable")
        })
        assertNull(CarPlayWebDisplayFactory.find(null))
        assertTrue(CarPlayWebDisplayFactory.urls(110).isEmpty())
        assertTrue(CarPlayWebDisplayFactory.urls(111).isEmpty())
    }

    @Test fun serverAndCertificateImplementationsAreAbsentFromTheDefaultRuntime() {
        for (name in listOf("fi.iki.elonen.NanoHTTPD","fi.iki.elonen.NanoWSD",
            "com.shilapi.xcertplay.WebDisplayCertificate","com.shilapi.xcertplay.WebDisplayServer")) {
            assertThrows(ClassNotFoundException::class.java) { Class.forName(name) }
        }
    }
}
