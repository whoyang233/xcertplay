package com.shilapi.xcertplay.media

import kotlin.math.exp

/** Causal, stereo-linked peak protection. Never raises gain or buffers/drops speech. */
internal class NavigationPeakLimiter(sampleRate: Int) {
    private val holdFrames = maxOf(1, sampleRate * 10 / 1000)
    private val recovery = 1.0 - exp(-1.0 / (sampleRate * 0.080))
    private var gain = 1.0
    private var holdRemaining = 0

    init { require(sampleRate > 0) }

    fun reset() { gain = 1.0; holdRemaining = 0 }

    fun gainForPeak(peak: Int): Double {
        require(peak in 0..32768)
        val ceilingGain = if (peak > CEILING) CEILING.toDouble() / peak else 1.0
        if (ceilingGain < gain) {
            gain = ceilingGain
            holdRemaining = holdFrames
        } else if (holdRemaining > 0) {
            holdRemaining--
        } else {
            gain = minOf(ceilingGain, gain + (1.0 - gain) * recovery)
        }
        return gain
    }

    companion object {
        // -9 dBFS before the user's navigation gain: headroom for plosives, including at 200%.
        const val CEILING = 11626
    }
}
