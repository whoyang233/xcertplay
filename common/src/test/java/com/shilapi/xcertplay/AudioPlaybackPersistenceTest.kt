package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.media.AudioOutputChannel
import com.shilapi.xcertplay.media.AudioPlaybackSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioPlaybackPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun upgradeDefaultsSeparateChannelsAtOriginalVolume() {
        prefs.edit().putBoolean("advanced_audio_channel_mapping", true).commit()
        assertEquals(AudioPlaybackSettings(), AirPlayPersistence.loadAudioPlaybackSettings(context))
        assertTrue(AirPlayPersistence.loadAdvancedAudioChannelMapping(context))
    }

    @Test fun bindingsAndMutedVolumeSurviveReloadWithoutChangingSystemVolume() {
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        val before = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val settings = AudioPlaybackSettings(AudioOutputChannel.NAVIGATION, AudioOutputChannel.MEDIA, 0)
        AirPlayPersistence.saveAudioPlaybackSettings(context, settings)
        assertEquals(settings, AirPlayPersistence.loadAudioPlaybackSettings(context))
        assertEquals(before, manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    @Test fun badPreferencesRecoverPerFieldAndClampGain() {
        prefs.edit().putString("music_output_channel", "missing")
            .putString("navigation_output_channel", "MEDIA").putInt("navigation_volume_percent", 500).commit()
        assertEquals(AudioPlaybackSettings(AudioOutputChannel.MEDIA, AudioOutputChannel.MEDIA, 200),
            AirPlayPersistence.loadAudioPlaybackSettings(context))
        prefs.edit().putString("navigation_volume_percent", "wrong-type").commit()
        assertEquals(100, AirPlayPersistence.loadAudioPlaybackSettings(context).navigationVolumePercent)
    }

    @Test fun numberedStreamsSurviveReloadAndRecoverInvalidPreferences() {
        val settings = AudioPlaybackSettings(navigationVolumePercent = 50, musicStream = 3, navigationStream = 14)
        AirPlayPersistence.saveAudioPlaybackSettings(context, settings)
        assertEquals(settings, AirPlayPersistence.loadAudioPlaybackSettings(context))
        prefs.edit().putInt("music_stream_binding", 21)
            .putString("navigation_stream_binding", "bad").commit()
        assertEquals(settings.copy(musicStream = 0, navigationStream = 0),
            AirPlayPersistence.loadAudioPlaybackSettings(context))
        AirPlayPersistence.saveAudioPlaybackSettings(context, settings.copy(musicStream = -1, navigationStream = 100))
        assertEquals(0, AirPlayPersistence.loadAudioPlaybackSettings(context).musicStream)
        assertEquals(0, AirPlayPersistence.loadAudioPlaybackSettings(context).navigationStream)
    }
}
