package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class PcmPlaybackEnvelopeTest {
    @Test fun shortPacketsHaveTheSameTenMillisecondAttackAsOneBuffer() {
        for (channels in 1..2) {
            val frames = 960
            val whole = pcm(IntArray(frames * channels) { 10000 })
            PcmPlaybackEnvelope(48000, channels).apply(whole, 0, whole.size)
            val splitEnvelope = PcmPlaybackEnvelope(48000, channels)
            val split = pcm(IntArray(frames * channels) { 10000 })
            val packetBytes = 48 * channels * 2 // 1 ms packets.
            for (offset in split.indices step packetBytes) splitEnvelope.apply(split, offset, packetBytes)
            assertArrayEquals("channels=$channels", whole, split)
            val samples = samples(split)
            assertEquals(20, samples[0])
            assertEquals(10000, samples[479 * channels])
            if (channels == 2) {
                for (frame in 0 until frames) assertEquals(samples[frame * 2], samples[frame * 2 + 1])
            }
        }
    }

    @Test fun firstAndFollowingNavigationSamplesNeverExceedTheSavedSixtyPercentGain() {
        val nav = AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        for (gain in listOf(0, 60, 100, 200)) {
            val envelope = PcmPlaybackEnvelope(48000, 1)
            repeat(2) { burst ->
                if (burst > 0) envelope.restart()
                repeat(20) {
                    val data = pcm(IntArray(48) { if (it % 2 == 0) 10000 else -10000 })
                    NavigationVolume.applyPcm16InPlace(data, 0, data.size, nav, gain)
                    envelope.apply(data, 0, data.size)
                    assertTrue(samples(data).all { kotlin.math.abs(it) <= 10000 * gain / 100 })
                    if (it == 19) assertEquals(10000 * gain / 100, samples(data)[0])
                }
            }
        }
    }

    @Test fun emptyBuffersDoNotConsumeFadeAndIncompleteStereoFramesAreRejected() {
        val envelope = PcmPlaybackEnvelope(48000, 2)
        envelope.apply(ByteArray(0), 0, 0)
        val data = pcm(intArrayOf(10000, 10000))
        envelope.apply(data, 0, data.size)
        assertEquals(20, samples(data)[0])
        assertThrows(IllegalArgumentException::class.java) { envelope.apply(data, 0, 2) }
    }

    private fun pcm(samples: IntArray): ByteArray = ByteArray(samples.size * 2).also { data ->
        samples.forEachIndexed { i, sample -> data[i * 2] = sample.toByte(); data[i * 2 + 1] = (sample shr 8).toByte() }
    }

    private fun samples(data: ByteArray): List<Int> = data.indices.step(2).map {
        (data[it].toInt() and 0xff) or (data[it + 1].toInt() shl 8)
    }
}
