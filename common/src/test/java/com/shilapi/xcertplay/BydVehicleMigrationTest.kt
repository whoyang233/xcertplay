package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.vehicle.VehicleDataConfig
import com.shilapi.xcertplay.vehicle.VehicleDataFeature
import com.shilapi.xcertplay.vehicle.source.VehicleDataEntries
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class BydVehicleMigrationTest {
    private val context: Context=ApplicationProvider.getApplicationContext()
    private val prefs get()=context.getSharedPreferences("xcertplay_airplay",Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun oldJsonSettingsCannotSilentlyEnableAdbButGpsChoicesSurvive() {
        prefs.edit().putString("vehicle_data_source","json-profile")
            .putBoolean("vehicle_data_WHEEL_SPEED",true).putBoolean("vehicle_data_EV_STATUS",true)
            .putBoolean("vehicle_data_GPS_SPEED",false).putBoolean("vehicle_data_ALTITUDE",true)
            .putString("vehicle_profile_selection","imported").commit()
        val config=AirPlayPersistence.loadVehicleDataConfig(context)
        assertEquals("byd-adb",config.sourceId)
        assertFalse(config.isEnabled(VehicleDataFeature.WHEEL_SPEED));assertFalse(config.isEnabled(VehicleDataFeature.EV_STATUS))
        assertFalse(config.isEnabled(VehicleDataFeature.GPS_SPEED));assertTrue(config.isEnabled(VehicleDataFeature.ALTITUDE))
    }
    @Test fun explicitAdbChoicesAreSavedAndRestoredIndependently() {
        val config=VehicleDataConfig().with(VehicleDataFeature.EV_STATUS,true)
        AirPlayPersistence.saveVehicleDataConfig(context,config)
        assertEquals(config,AirPlayPersistence.loadVehicleDataConfig(context))
        assertFalse(AirPlayPersistence.loadVehicleDataConfig(context).isEnabled(VehicleDataFeature.WHEEL_SPEED))
    }
    @Test fun onlyBydEntryIsInstalledAndOldSourceIdsResolveToIt() {
        val entries=InstalledVehicleDataEntries.all()
        assertEquals(1,entries.size);assertEquals("byd-adb",entries.single().id)
        assertEquals(entries.single(),VehicleDataEntries.select(entries,"json-profile"))
        assertTrue(entries.single().canProvide(VehicleDataFeature.EV_STATUS))
        assertTrue(entries.single().canProvide(VehicleDataFeature.WHEEL_SPEED))
        for (name in listOf("com.shilapi.xcertplay.VehicleProfileRepository","com.shilapi.xcertplay.JsonProfileVehicleDataEntry",
            "com.shilapi.xcertplay.vehicle.AndroidCarPropertyBackend"))
            assertThrows(ClassNotFoundException::class.java) { Class.forName(name) }
    }
}
