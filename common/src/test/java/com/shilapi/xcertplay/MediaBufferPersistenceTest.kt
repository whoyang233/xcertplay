package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaBufferPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun anUpgradePreservesEveryPreviouslySavedValue() {
        assertEquals(100, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
        for (old in 100..1000 step 100) {
            prefs.edit().putInt("main_media_audio_buffer_duration_ms", old).commit()
            assertEquals(old, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
        }
    }
    @Test fun lowLatencyAndIntermediatePresetsSurviveReloadExactly() {
        for (value in listOf(20, 50, 100, 150, 200, 250, 300, 350, 1000)) {
            AirPlayPersistence.saveMainMediaAudioBufferDurationMs(context, value)
            assertEquals(value, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
        }
    }
    @Test fun invalidPreferenceTypesRecoverToTheOldDefault() {
        prefs.edit().putString("main_media_audio_buffer_duration_ms", "invalid").commit()
        assertEquals(100, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
    }
}
