package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainMediaAudioBufferTest {
    @Test
    fun newPresetsKeepEveryOldValueAndTheOriginalDefault() {
        assertEquals(100, MainMediaAudioBuffer.DEFAULT_DURATION_MS)
        for (old in 100..1000 step 100) assertEquals(old, MainMediaAudioBuffer.sanitizeDurationMs(old))
        for (preset in listOf(0, 20, 50, 100, 150, 200, 250, 300)) {
            assertTrue(preset in MainMediaAudioBuffer.presetsMs)
            assertEquals(preset, MainMediaAudioBuffer.sanitizeDurationMs(preset))
        }
        assertEquals(0, MainMediaAudioBuffer.sanitizeDurationMs(Int.MIN_VALUE))
        assertEquals(50, MainMediaAudioBuffer.sanitizeDurationMs(35))
        assertEquals(150, MainMediaAudioBuffer.sanitizeDurationMs(149))
        assertEquals(1000, MainMediaAudioBuffer.sanitizeDurationMs(Int.MAX_VALUE))
        assertEquals(1000, MainMediaAudioBuffer.sanitizeDurationMs(1500))
    }

    @Test
    fun durationConvertsToPcm16TrackCapacity() {
        assertEquals(96_000, MainMediaAudioBuffer.bufferSizeBytes(500, 48_000, 2, 16_384))
        assertEquals(3_840, MainMediaAudioBuffer.bufferSizeBytes(20, 48_000, 2, 1_024))
        assertEquals(9_600, MainMediaAudioBuffer.bufferSizeBytes(50, 48_000, 2, 1_024))
        assertEquals(440, MainMediaAudioBuffer.bufferSizeBytes(20, 11_025, 1, 128))
    }

    @Test
    fun systemMinimumRemainsTheLowerBound() {
        assertEquals(24_000, MainMediaAudioBuffer.bufferSizeBytes(100, 8_000, 1, 24_000))
    }

    @Test
    fun musicClassificationAlsoCoversDefaultHighQualityStreamsAndProtectsVoiceRoles() {
        assertTrue(MainMediaAudioBuffer.isMainMedia("media", 100))
        assertTrue(MainMediaAudioBuffer.isMainMedia("MEDIA", 102))
        assertFalse(MainMediaAudioBuffer.isMainMedia("media", 101))
        for (audioType in listOf("default", "compatibility", "alert", "telephony", "speechrecognition")) {
            assertFalse(MainMediaAudioBuffer.isMainMedia(audioType, 100))
        }
        for (mode in AudioChannelMappingMode.entries) {
            assertTrue(MainMediaAudioBuffer.isMainMedia("default", 102, mode))
            assertTrue(MainMediaAudioBuffer.isMainMedia("compatibility", 102, mode))
            for (audioType in listOf("alert", "navigation", "telephony", "speechrecognition"))
                assertFalse(MainMediaAudioBuffer.isMainMedia(audioType, 102, mode))
        }
        assertTrue(MainMediaAudioBuffer.isMainMedia("default", 100, AudioChannelMappingMode.AUTOMOTIVE_BUS))
    }

    @Test fun a20MsBufferCanStartWithoutBlockingOnAnOverCapacityWrite() {
        val capacity = MainMediaAudioBuffer.bufferSizeBytes(20, 48_000, 2, 1_024)
        val threshold = MainMediaAudioBuffer.startThresholdBytes(capacity, 1_024)
        assertEquals(3_840, threshold)
        var queued = 0
        val writes = mutableListOf<Int>()
        while (queued < threshold) {
            val count = MainMediaAudioBuffer.prebufferWriteLength(8_192, queued, threshold)
            assertTrue("A blocking write cannot exceed the free space before play", count <= capacity - queued)
            assertTrue(count > 0)
            writes += count
            queued += count
        }
        assertEquals(listOf(2_048, 1_792), writes)
    }

    @Test fun everyPresetStartsWithinCapacityEvenWhenAndroidEnforcesALargerMinimum() {
        for (duration in MainMediaAudioBuffer.presetsMs) for (rate in listOf(8_000, 11_025, 44_100, 48_000))
            for (channels in 1..2) for (minimum in listOf(128, 1_024, 16_384)) {
                val capacity = MainMediaAudioBuffer.bufferSizeBytes(duration, rate, channels, minimum)
                val threshold = MainMediaAudioBuffer.startThresholdBytes(capacity, minimum)
                assertTrue(capacity >= minimum)
                assertTrue(threshold in 1..capacity)
                var queued = 0
                while (queued < threshold) {
                    val write = MainMediaAudioBuffer.prebufferWriteLength(16_384, queued, threshold)
                    assertTrue(write in 1..(capacity - queued))
                    queued += write
                }
            }
    }

    @Test fun legacyDefaultStillUsesTheSameCapacityAndStartupThreshold() {
        val capacity = MainMediaAudioBuffer.bufferSizeBytes(100, 48_000, 2, 1_024)
        assertEquals(19_200, capacity)
        assertEquals(4_096, MainMediaAudioBuffer.startThresholdBytes(capacity, 1_024))
    }
    @Test fun zeroModeRequestsOnlySystemMinimumAndStartsAfterTheFirstPcmFrame() {
        val capacity = MainMediaAudioBuffer.bufferSizeBytes(0, 48_000, 2, 1_024)
        assertEquals(1_024, capacity)
        val threshold = MainMediaAudioBuffer.startThresholdBytes(capacity, 1_024, 0)
        assertEquals(0, threshold)
        val firstWrite = MainMediaAudioBuffer.prebufferWriteLength(8_192, 0, threshold, 4)
        assertEquals(4, firstWrite)
        assertTrue(firstWrite <= capacity)
        assertTrue(firstWrite >= threshold)
    }

}
