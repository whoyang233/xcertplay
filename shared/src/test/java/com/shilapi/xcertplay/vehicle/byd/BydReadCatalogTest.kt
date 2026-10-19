package com.shilapi.xcertplay.vehicle.byd

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE)
class BydReadCatalogTest {
    private val context get()=object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getPackageCodePath()="/data/app/xcertplay/base.apk"
    }
    @Before fun clear() { context.getSharedPreferences("byd_read_catalog",Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun detectionCachesOnlyKnownSymbolsAndUsesFirmwareDeviceIds() {
        val link=FakeBydLink().apply { fallback={ "${BydCatalogProbeMain.HEADER}\nSpeed.SPEED_AUTO_SPEED=99\nBYDAutoConstants.BYDAUTO_DEVICE_SPEED=400\nUnknown.WRITE_THING=500\n" } }
        val addresses=BydReadCatalog.loadOrDetect(context,link,true)
        assertEquals(mapOf(BydField.SPEED to BydAddress(400,99,7)),addresses)
        link.reads.clear()
        assertEquals(addresses,BydReadCatalog.loadOrDetect(context,link,false))
        assertTrue(link.reads.isEmpty())
        assertEquals(addresses,BydReadCatalog.loadOrDetect(context,FakeBydLink().apply { fallback={"failed"} },true))
    }
    @Test fun anotherFirmwareDoesNotReuseSavedAddresses() {
        val prefs=context.getSharedPreferences("byd_read_catalog",Context.MODE_PRIVATE)
        prefs.edit().putString("firmware","another-car").putInt("SPEED",99).commit()
        assertTrue(BydReadCatalog.loadOrDetect(context,FakeBydLink(),false).isEmpty())
    }
    @Test fun missingApkPathSkipsProcessLaunchWithoutDiscardingCachedAddresses() {
        val link=FakeBydLink().apply { fallback={ "${BydCatalogProbeMain.HEADER}\nSpeed.SPEED_AUTO_SPEED=99\n" } }
        val cached=BydReadCatalog.loadOrDetect(context,link,true)
        link.reads.clear()
        assertEquals(cached,BydReadCatalog.loadOrDetect(RuntimeEnvironment.getApplication(),link,true))
        assertTrue(link.reads.isEmpty())
    }
    @Test fun incompleteCatalogPreservesPreviouslyKnownFieldsOnTheSameFirmware() {
        val link=FakeBydLink().apply { fallback={ "${BydCatalogProbeMain.HEADER}\nSpeed.SPEED_AUTO_SPEED=99\n" } }
        BydReadCatalog.loadOrDetect(context,link,true)
        link.fallback={ "${BydCatalogProbeMain.HEADER}\nGearbox.GEARBOX_AUTO_MODE_TYPE=100\n" }
        val updated=BydReadCatalog.loadOrDetect(context,link,true)
        assertEquals(setOf(BydField.SPEED,BydField.GEAR),updated.keys)
    }
}
