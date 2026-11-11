package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class NavigationPeakProtectionTest {
    @Test fun plosiveAfterTheTwentyMillisecondFadeIsLimitedWithoutClippingTheFollowingSpeech() {
        val input = IntArray(48000) { frame -> when (frame) {
            in 0 until 1920 -> 1000 // 40 ms: the old startup fade has already finished.
            in 1920 until 3840 -> if (frame % 2 == 0) 30000 else -30000
            else -> if (frame % 2 == 0) 7000 else -7000
        } }
        val protected = pcm(input)
        val original = protected.copyOf()
        PcmPlaybackEnvelope(48000, 1, true, true).apply(protected, 0, protected.size)
        PcmPlaybackEnvelope(48000, 1, true, false).apply(original, 0, original.size)
        assertEquals(30000, samples(original).maxOf { kotlin.math.abs(it) })
        assertTrue(samples(protected).all { kotlin.math.abs(it) <= 11626 })
        // The body recovers smoothly; this is attenuation, not an AGC that raises quiet words.
        assertTrue(samples(protected).takeLast(4800).all { kotlin.math.abs(it) in 6990..7000 })
        assertTrue(samples(protected).drop(3840).all { kotlin.math.abs(it) <= 7000 })
    }

    @Test fun decodedNoiseAndSparseClicksStillRearmProtectionForTheNextPrompt() {
        val envelope = PcmPlaybackEnvelope(48000, 1, true)
        val first = pcm(IntArray(4800) { 10000 })
        envelope.apply(first, 0, first.size)
        val quiet = pcm(IntArray(24000) { frame -> if (frame % 2400 == 0) 500 else if (frame % 2 == 0) 80 else -80 })
        envelope.apply(quiet, 0, quiet.size)
        val previous = envelope.onsetCount
        val next = pcm(IntArray(2400) { 30000 })
        envelope.apply(next, 0, next.size)
        assertEquals(previous + 1, envelope.onsetCount)
        assertEquals(31, samples(next).first()) // 80-LSB dither kept the old sample gate open forever.
        assertEquals(30000, samples(next).last())
    }

    @Test fun sourceLevelDetectionDoesNotMistakeQuietSavedVolumeForSilence() {
        val nav = AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        val source = pcm(IntArray(2400) { 300 })
        PcmPlaybackEnvelope(48000, 1, true, true).apply(source, 0, source.size)
        NavigationVolume.applyPcm16InPlace(source, 0, source.size, nav, 5)
        assertEquals(15, samples(source).last())
        assertTrue(samples(source).any { it > 0 })
    }

    @Test fun limitingAndSavedGainStayBoundedAcrossRatesStereoAndExtremeVolumes() {
        val nav = AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        for (rate in listOf(8000, 16000, 24000, 44100, 48000)) for (channels in 1..2) {
            for (volume in listOf(0, 5, 60, 100, 200)) {
                val input = pcm(IntArray(rate / 10 * channels) { if (it % 2 == 0) 32767 else -32768 })
                PcmPlaybackEnvelope(rate, channels, true, true).apply(input, 0, input.size)
                NavigationVolume.applyPcm16InPlace(input, 0, input.size, nav, volume)
                val maximum = 11626 * volume / 100
                assertTrue("$rate/$channels/$volume", samples(input).all { kotlin.math.abs(it) <= maximum })
                if (volume == 0) assertTrue(input.all { it == 0.toByte() })
            }
        }
    }

    @Test fun stereoUsesOneGainAndPacketSplitsOrOffsetsCannotExposeAPeak() {
        val input = pcm(IntArray(12000) { if (it % 2 == 0) 30000 else -15000 })
        val all = input.copyOf()
        PcmPlaybackEnvelope(48000, 2, true, true).apply(all, 0, all.size)
        val split = byteArrayOf(11, 22, 33, 44) + input + byteArrayOf(55, 66, 77, 88)
        val envelope = PcmPlaybackEnvelope(48000, 2, true, true)
        var offset = 4
        var step = 1
        while (offset < split.size - 4) {
            val length = minOf(split.size - 4 - offset, (step++ % 101 + 1) * 4)
            envelope.apply(split, offset, length)
            offset += length
        }
        assertArrayEquals(all, split.copyOfRange(4, split.size - 4))
        assertArrayEquals(byteArrayOf(11, 22, 33, 44), split.take(4).toByteArray())
        assertArrayEquals(byteArrayOf(55, 66, 77, 88), split.takeLast(4).toByteArray())
        val output = samples(all)
        for (i in output.indices step 2) assertTrue(kotlin.math.abs(output[i] + output[i + 1] * 2) <= 2)
    }

    @Test fun musicAndOtherRolesKeepTheirExistingEnvelopeEvenIfProtectionIsRequested() {
        val a = pcm(IntArray(4800) { 30000 })
        val b = a.copyOf()
        PcmPlaybackEnvelope(48000, 1, false, true).apply(a, 0, a.size)
        PcmPlaybackEnvelope(48000, 1, false, false).apply(b, 0, b.size)
        assertArrayEquals(b, a)
        assertEquals(30000, samples(a).last())
    }

    @Test fun speechBelowTheCeilingIsBitIdenticalToTheUnprotectedNavigationEnvelope() {
        val a = pcm(IntArray(4800) { if (it % 2 == 0) 7000 else -7000 })
        val b = a.copyOf()
        PcmPlaybackEnvelope(48000, 1, true, true).apply(a, 0, a.size)
        PcmPlaybackEnvelope(48000, 1, true, false).apply(b, 0, b.size)
        assertArrayEquals(b, a)
    }

    private fun pcm(values: IntArray) = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { i, value -> bytes[i * 2] = value.toByte(); bytes[i * 2 + 1] = (value shr 8).toByte() }
    }
    private fun samples(bytes: ByteArray) = bytes.indices.step(2).map {
        (bytes[it].toInt() and 255) or (bytes[it + 1].toInt() shl 8)
    }
}
