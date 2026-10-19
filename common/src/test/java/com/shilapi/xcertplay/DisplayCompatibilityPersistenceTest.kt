package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.airplay.CarPlayDisplayScale
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class DisplayCompatibilityPersistenceTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    @Before fun clear() { context.getSharedPreferences("xcertplay_airplay",Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun existingSafeAreasAndBadHistoricalMaximaDoNotEnableFastAreas() {
        AirPlayPersistence.saveMaximumDetectedDisplay(context,1920,1920)
        AirPlayPersistence.saveSafeAreaRect(context,2560,1440,
            com.shilapi.xcertplay.airplay.SafeAreaRect(0,0,2560,1440))
        assertFalse(AirPlayPersistence.loadFastViewAreaSwitching(context))
        assertEquals(CarPlayDisplayScale.DEFAULT_TENTHS,AirPlayPersistence.loadDisplayScaleTenths(context))
    }
    @Test fun fastAreaSettingIsAnIndependentOptInAndCanBeRestored() {
        AirPlayPersistence.saveDisplayScaleTenths(context,6)
        AirPlayPersistence.saveFastViewAreaSwitching(context,true)
        assertTrue(AirPlayPersistence.loadFastViewAreaSwitching(context))
        assertEquals(6,AirPlayPersistence.loadDisplayScaleTenths(context))
        AirPlayPersistence.saveFastViewAreaSwitching(context,false)
        assertFalse(AirPlayPersistence.loadFastViewAreaSwitching(context))
    }
}
