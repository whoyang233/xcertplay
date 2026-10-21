package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayIcon
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarPlayBrandIconTest {
    @Test fun packagedBydIconIsSentAndPreviewedWithoutThePlaceholder() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        AirPlayPersistence.clearCustomAirPlayIcon(host)
        val expected = host.resources.openRawResource(R.raw.ic_car_home).use { it.readBytes() }
        val icon = ReflectionHelpers.callInstanceMethod<AirPlayIcon>(host, "loadAirPlayIcon")
        assertEquals(192, icon.widthPixels)
        assertEquals(192, icon.heightPixels)
        assertArrayEquals(expected, icon.data)
        ReflectionHelpers.callInstanceMethod<Unit>(host, "updateAirPlayIconPreview")
        assertFalse(ReflectionHelpers.getField<androidx.compose.runtime.MutableState<Boolean>>(host, "airPlayIconCustom\$delegate").value)
    }

    @Test fun blankOrMissingNameUsesBydAndExistingCustomNameIsPreserved() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
        prefs.edit().remove("oem_label").commit()
        assertEquals("BYD", AirPlayPersistence.loadOemLabel(context))
        AirPlayPersistence.saveOemLabel(context, "  ")
        assertEquals("BYD", AirPlayPersistence.loadOemLabel(context))
        AirPlayPersistence.saveOemLabel(context, "我的车")
        assertEquals("我的车", AirPlayPersistence.loadOemLabel(context))
        prefs.edit().remove("oem_label").commit()
    }
}
