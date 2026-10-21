package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.media.VideoBufferPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoBufferPersistenceTest {
    @Test fun existingInstallRetainsCompatibilityAndPresetsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(VideoBufferPolicy.COMPATIBILITY, AirPlayPersistence.loadVideoBufferDurationMs(context))
        for (value in VideoBufferPolicy.presetsMs) {
            AirPlayPersistence.saveVideoBufferDurationMs(context, value)
            assertEquals(value, AirPlayPersistence.loadVideoBufferDurationMs(context))
        }
    }
    @Test fun corruptValuesRecoverWithoutChangingIndependentAudioPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
        AirPlayPersistence.saveMainMediaAudioBufferDurationMs(context, 20)
        prefs.edit().putString("video_buffer_duration_ms", "bad").commit()
        assertEquals(VideoBufferPolicy.COMPATIBILITY, AirPlayPersistence.loadVideoBufferDurationMs(context))
        prefs.edit().putInt("video_buffer_duration_ms", 999).commit()
        assertEquals(VideoBufferPolicy.COMPATIBILITY, AirPlayPersistence.loadVideoBufferDurationMs(context))
        assertEquals(20, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
    }
    @Test fun removedWebClassesAndDependenciesAreAbsent() {
        for (name in listOf("fi.iki.elonen.NanoHTTPD", "fi.iki.elonen.NanoWSD",
            "com.shilapi.xcertplay.WebDisplayServer", "com.shilapi.xcertplay.WebDisplayCertificate",
            "com.shilapi.xcertplay.CarPlayWebDisplayFactory", "com.shilapi.xcertplay.CarPlayWebDisplayConfig",
            "com.shilapi.xcertplay.media.MediaTap")) {
            assertThrows(name, ClassNotFoundException::class.java) { Class.forName(name) }
        }
    }
}
