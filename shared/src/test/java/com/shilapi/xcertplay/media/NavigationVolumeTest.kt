package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class NavigationVolumeTest {
    private val navigation = AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)

    @Test fun muteAttenuationUnityAndBoostPreservePcmSignAndClipSafely() {
        val input = pcm(-32768, -20000, -1000, 0, 1000, 20000, 32767)
        val expected = mapOf(
            0 to pcm(0, 0, 0, 0, 0, 0, 0),
            50 to pcm(-16384, -10000, -500, 0, 500, 10000, 16383),
            100 to input,
            200 to pcm(-32768, -32768, -2000, 0, 2000, 32767, 32767),
        )
        for ((gain, output) in expected) {
            val actual = input.copyOf()
            NavigationVolume.applyPcm16InPlace(actual, 0, actual.size, navigation, gain)
            assertArrayEquals("gain=$gain", output, actual)
        }
    }

    @Test fun attenuatesOnlyNavigationEvenWhenOutputBindingsAreReversed() {
        val settings = AudioPlaybackSettings(AudioOutputChannel.NAVIGATION, AudioOutputChannel.MEDIA, 25)
        for ((type, payload) in listOf("media" to 102, "default" to 101, "telephony" to 100, "speechRecognition" to 100)) {
            val role = AudioChannelMapper.map(type, payload, AudioChannelMappingMode.MOBILE_COMPATIBLE)
            val input = pcm(-10000, 10000)
            NavigationVolume.applyPcm16InPlace(input, 0, input.size, role, settings.navigationVolumePercent)
            assertArrayEquals(if (payload == 101) pcm(-2500, 2500) else pcm(-10000, 10000), input)
        }
    }

    @Test fun respectsOffsetLengthAndTrailingPartialSampleInReusedStereoBuffer() {
        val input = byteArrayOf(91, 92) + pcm(1000, -1000, 2000, -2000) + byteArrayOf(93, 94, 95)
        NavigationVolume.applyPcm16InPlace(input, 2, 9, navigation, 50)
        assertArrayEquals(byteArrayOf(91, 92) + pcm(500, -500, 1000, -1000) + byteArrayOf(93, 94, 95), input)
    }

    @Test fun invalidBufferRangesFailBeforeMutation() {
        val input = pcm(1234)
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 1 to 2, Int.MAX_VALUE to 2)) {
            assertThrows(IllegalArgumentException::class.java) {
                NavigationVolume.applyPcm16InPlace(input, offset, length, navigation, 0)
            }
        }
        assertArrayEquals(pcm(1234), input)
    }

    @Test fun staleValuesClampAndAllSliderStepsRoundTrip() {
        assertEquals(0, NavigationVolume.sanitize(Int.MIN_VALUE))
        assertEquals(200, NavigationVolume.sanitize(Int.MAX_VALUE))
        assertEquals(100, NavigationVolume.percentForStep(NavigationVolume.stepForPercent(100)))
        for (step in 0 until NavigationVolume.STEP_COUNT) {
            assertEquals(step, NavigationVolume.stepForPercent(NavigationVolume.percentForStep(step)))
        }
    }

    private fun pcm(vararg samples: Int): ByteArray = ByteArray(samples.size * 2).also { data ->
        samples.forEachIndexed { i, sample -> data[i * 2] = sample.toByte(); data[i * 2 + 1] = (sample ushr 8).toByte() }
    }
}
