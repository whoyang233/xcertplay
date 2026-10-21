package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChannelMappingTest {
    @Test fun numberedBindingsFollowOriginalRolesWithoutCapturingCallsOrSiri() {
        for (mode in AudioChannelMappingMode.entries) {
            val settings = AudioPlaybackSettings(navigationVolumePercent = 50, musicStream = 3, navigationStream = 14)
            for ((type, payload, expected) in listOf(Triple("default", 102, 3), Triple("default", 101, 14),
                Triple("telephony", 102, 0), Triple("speechRecognition", 101, 0))) {
                assertEquals(expected, AudioChannelMapper.streamOverride(AudioChannelMapper.map(type, payload, mode), settings))
            }
            val nav = AudioChannelMapper.map("navigation", 100, mode)
            assertEquals(0, AudioChannelMapper.streamOverride(nav, settings.copy(navigationStream = -1)))
            assertEquals(20, AudioChannelMapper.streamOverride(nav, settings.copy(navigationStream = 20)))
        }
    }

    @Test
    fun dedicatedStreamsSeparateMusicFromNavigationInBothModes() {
        for (mode in AudioChannelMappingMode.entries) {
            for (type in listOf("default", "compatibility", "unknown", "")) {
                assertMapped(mode, type, 102, AudioChannel.MEDIA, AudioContentType.MUSIC)
                assertMapped(mode, type, 101, AudioChannel.NAVIGATION, AudioContentType.SPEECH)
            }
            assertMapped(mode, " MEDIA ", 100, AudioChannel.MEDIA, AudioContentType.MUSIC)
            assertMapped(mode, "Navigation", 100, AudioChannel.NAVIGATION, AudioContentType.SPEECH)
            assertMapped(mode, "navigationGuidance", 100, AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        }
    }

    @Test
    fun numberedOverridesKeepDefaultUsagesAndDoNotCaptureCallsOrSiri() {
        val settings = AudioPlaybackSettings(navigationVolumePercent = 0, musicStream = 3, navigationStream = 14)
        for (mode in AudioChannelMappingMode.entries) {
            for ((type, payload, channel, stream) in listOf(
                listOf("media", 102, AudioChannel.MEDIA, 3),
                listOf("navigation", 101, AudioChannel.NAVIGATION, 14),
                listOf("telephony", 101, AudioChannel.PHONE, 0),
                listOf("speechRecognition", 102, AudioChannel.ASSISTANT, 0),
            )) {
                val selection = AudioChannelMapper.map(type as String, payload as Int, mode)
                assertEquals(channel, selection.channel)
                assertEquals(stream, AudioChannelMapper.streamOverride(selection, settings))
            }
        }
    }

    @Test
    fun mobileCompatibleMappingMatchesTheOriginalRouting() {
        assertMapped(
            mode = AudioChannelMappingMode.MOBILE_COMPATIBLE,
            audioType = "telephony",
            payloadType = 100,
            channel = AudioChannel.PHONE,
            contentType = AudioContentType.SPEECH,
        )
        assertMapped(
            mode = AudioChannelMappingMode.MOBILE_COMPATIBLE,
            audioType = "speechRecognition",
            payloadType = 100,
            channel = AudioChannel.ASSISTANT,
            contentType = AudioContentType.SPEECH,
        )
        assertMapped(
            mode = AudioChannelMappingMode.MOBILE_COMPATIBLE,
            audioType = "media",
            payloadType = 100,
            channel = AudioChannel.MEDIA,
            contentType = AudioContentType.MUSIC,
        )
        listOf("default", "alert", "compatibility").forEach { audioType ->
            assertMapped(
                mode = AudioChannelMappingMode.MOBILE_COMPATIBLE,
                audioType = audioType,
                payloadType = 100,
                channel = AudioChannel.NAVIGATION,
                contentType = AudioContentType.SPEECH,
            )
        }
    }

    @Test
    fun automotiveMappingUsesTheBusSpecificCarPlayTypes() {
        listOf("media", "default", "compatibility").forEach { audioType ->
            assertMapped(
                mode = AudioChannelMappingMode.AUTOMOTIVE_BUS,
                audioType = audioType,
                payloadType = 100,
                channel = AudioChannel.MEDIA,
                contentType = AudioContentType.MUSIC,
            )
        }
        assertMapped(
            mode = AudioChannelMappingMode.AUTOMOTIVE_BUS,
            audioType = "telephony",
            payloadType = 100,
            channel = AudioChannel.PHONE,
            contentType = AudioContentType.SPEECH,
        )
        assertMapped(
            mode = AudioChannelMappingMode.AUTOMOTIVE_BUS,
            audioType = "speechRecognition",
            payloadType = 100,
            channel = AudioChannel.ASSISTANT,
            contentType = AudioContentType.SPEECH,
        )
        assertMapped(
            mode = AudioChannelMappingMode.AUTOMOTIVE_BUS,
            audioType = "alert",
            payloadType = 100,
            channel = AudioChannel.NAVIGATION,
            contentType = AudioContentType.SPEECH,
        )
    }

    @Test
    fun unknownTypesKeepTheMainHighAudioFallback() {
        assertMapped(
            mode = AudioChannelMappingMode.MOBILE_COMPATIBLE,
            audioType = "unknown",
            payloadType = AudioChannelMapper.STREAM_TYPE_MAIN_HIGH_AUDIO,
            channel = AudioChannel.MEDIA,
            contentType = AudioContentType.MUSIC,
        )
        assertMapped(
            mode = AudioChannelMappingMode.AUTOMOTIVE_BUS,
            audioType = "unknown",
            payloadType = 100,
            channel = AudioChannel.NAVIGATION,
            contentType = AudioContentType.SPEECH,
        )
    }

    private fun assertMapped(
        mode: AudioChannelMappingMode,
        audioType: String,
        payloadType: Int,
        channel: AudioChannel,
        contentType: AudioContentType,
    ) {
        assertEquals(
            AudioChannelSelection(channel, contentType),
            AudioChannelMapper.map(audioType, payloadType, mode),
        )
    }
}
