package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class PcmPlaybackEnvelopeTest {
    @Test fun navigationLeadingSilenceDoesNotSpendTheAttackBeforeAPlosive() {
        for (rate in listOf(8000, 16000, 24000, 44100, 48000)) {
            for (channels in 1..2) {
                val envelope = PcmPlaybackEnvelope(rate, channels, detectSpeechOnsets = true)
                val silence = ByteArray(rate * channels * 2) // A second of packets before speaking.
                envelope.apply(silence, 0, silence.size)
                val burst = pcm(IntArray(rate / 20 * channels) { if (it % 2 == 0) 30000 else -30000 })
                envelope.apply(burst, 0, burst.size)
                val output = samples(burst)
                assertEquals("rate=$rate channels=$channels", 30000 / (rate * 20 / 1000), output.first())
                assertEquals(1L, envelope.onsetCount)
                assertEquals(30000, envelope.firstSpeechInputPeak)
                assertEquals(output.first(), envelope.firstSpeechOutputPeak)
                assertEquals(30000, kotlin.math.abs(output.last()))
            }
        }
    }

    @Test fun otherAudioKeepsTheOriginalStartupAndDoesNotRearmOnSilentPcm() {
        val envelope = PcmPlaybackEnvelope(48000, 1)
        val silence = ByteArray(48000)
        envelope.apply(silence, 0, silence.size)
        val music = pcm(intArrayOf(24000, -24000))
        envelope.apply(music, 0, music.size)
        assertEquals(listOf(24000, -24000), samples(music))
        assertEquals(0L, envelope.onsetCount)
    }

    @Test fun everyPromptInAContinuouslySilentNavigationStreamGetsANewAttack() {
        val envelope = PcmPlaybackEnvelope(48000, 2, detectSpeechOnsets = true)
        repeat(3) { prompt ->
            // Deliberately nonzero noise floor, provided as one-millisecond PCM packets.
            repeat(300) {
                val quiet = pcm(IntArray(96) { if (it % 2 == 0) 8 else -8 })
                envelope.apply(quiet, 0, quiet.size)
            }
            val speech = pcm(IntArray(1920) { 24000 })
            envelope.apply(speech, 0, speech.size)
            assertEquals(25, samples(speech).first())
            assertEquals(24000, samples(speech).last())
            assertEquals(prompt + 1L, envelope.onsetCount)
        }
    }

    @Test fun navigationOnsetIsIndependentOfPacketSplitsAndLinkedAcrossStereo() {
        val input = pcm(IntArray(24000) { index ->
            val frame = index / 2
            if (frame < 2000 || frame in 4000..4999) 0 else if (index % 2 == 0) 28000 else -14000
        })
        val whole = input.copyOf()
        PcmPlaybackEnvelope(48000, 2, true).apply(whole, 0, whole.size)
        val split = input.copyOf()
        val envelope = PcmPlaybackEnvelope(48000, 2, true)
        var offset = 0
        var packet = 1
        while (offset < split.size) {
            val length = minOf(split.size - offset, (packet % 137 + 1) * 4)
            envelope.apply(split, offset, length)
            offset += length
            packet++
        }
        assertArrayEquals(whole, split)
        assertEquals(1L, envelope.onsetCount) // A 20 ms word gap must not restart the fade.
    }

    @Test fun restartAndShortZerosNeverBypassTheSavedGainOrInvertASample() {
        val nav = AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        for (gain in listOf(0, 40, 60, 100, 200)) {
            val envelope = PcmPlaybackEnvelope(48000, 1, true)
            repeat(2) {
                envelope.restart()
                val silence = ByteArray(800)
                envelope.apply(silence, 0, silence.size)
                val block = pcm(IntArray(2000) { if (it % 2 == 0) 32767 else -32768 })
                NavigationVolume.applyPcm16InPlace(block, 0, block.size, nav, gain)
                val before = samples(block)
                envelope.apply(block, 0, block.size)
                val after = samples(block)
                assertTrue(after.indices.all { i -> kotlin.math.abs(after[i]) <= kotlin.math.abs(before[i]) &&
                    (after[i] == 0 || (after[i] > 0) == (before[i] > 0)) })
                assertEquals(before.last(), after.last())
            }
        }
    }

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
