package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChannelMappingTest {
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
    fun outputBindingsDoNotChangeLogicalRolesOrCallAndSiriRouting() {
        for (music in AudioOutputChannel.entries) for (navigation in AudioOutputChannel.entries) {
            val settings = AudioPlaybackSettings(music, navigation, 0)
            val media = AudioChannelMapper.map("media", 102, AudioChannelMappingMode.MOBILE_COMPATIBLE)
            val nav = AudioChannelMapper.map("default", 101, AudioChannelMappingMode.AUTOMOTIVE_BUS)
            assertEquals(AudioChannel.valueOf(music.name), AudioChannelMapper.outputChannel(media, settings))
            assertEquals(AudioChannel.valueOf(navigation.name), AudioChannelMapper.outputChannel(nav, settings))
            assertEquals(AudioChannel.MEDIA, media.channel)
            assertEquals(AudioChannel.NAVIGATION, nav.channel)
            for ((type, channel) in listOf("telephony" to AudioChannel.PHONE, "speechRecognition" to AudioChannel.ASSISTANT)) {
                for (payload in listOf(100, 101, 102)) {
                    val selection = AudioChannelMapper.map(type, payload, AudioChannelMappingMode.MOBILE_COMPATIBLE)
                    assertEquals(channel, AudioChannelMapper.outputChannel(selection, settings))
                }
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
