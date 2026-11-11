package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class NavigationOnsetProbeTest {
    @Test fun capturesAnEntireFirstWordInsteadOfJustItsSmallFadedFirstSample() {
        val probe = NavigationOnsetProbe(48000, 1)
        val data = pcm(IntArray(12000) { if (it in 1920..2400) 30000 else 1000 })
        probe.begin(7)
        val result = probe.append(data, 0, data.size)!!
        assertEquals(7L, result.onset)
        assertEquals(9600, result.frames)
        assertEquals(30000, result.peak)
        assertTrue(result.rms in 6700..6900)
        assertNull(probe.finish())
    }

    @Test fun shortPromptsFlushOnceAndTheNextPromptDoesNotInheritTheirPeaks() {
        val probe = NavigationOnsetProbe(48000, 2)
        probe.begin(1)
        val short = pcm(intArrayOf(30000, -15000, 1000, -500))
        assertNull(probe.append(short, 0, short.size))
        assertEquals(30000, probe.finish()!!.peak)
        assertNull(probe.finish())
        probe.begin(2)
        probe.append(pcm(intArrayOf(100, -100)), 0, 4)
        val result = probe.finish()!!
        assertEquals(2L, result.onset)
        assertEquals(100, result.peak)
        assertEquals(100, result.rms)
    }

    @Test fun packetSplitsAndOffsetsPreserveStereoEnergyAndIgnoreUnwrittenTrailingSamples() {
        val data = pcm(IntArray(24000) { if (it < 19200) if (it % 2 == 0) 6000 else -3000 else 32767 })
        val whole = NavigationOnsetProbe(48000, 2).apply { begin(1) }.append(data, 0, data.size)!!
        val split = NavigationOnsetProbe(48000, 2).apply { begin(1) }
        var result: NavigationOnsetProbe.Levels? = null
        for (offset in data.indices step 400) split.append(data, offset, minOf(400, data.size - offset))?.let { result = it }
        assertEquals(whole, result)
        assertEquals(6000, whole.peak)
        assertEquals(4743, whole.rms)
    }

    private fun pcm(values: IntArray) = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { i, value -> bytes[i * 2] = value.toByte(); bytes[i * 2 + 1] = (value shr 8).toByte() }
    }
}
