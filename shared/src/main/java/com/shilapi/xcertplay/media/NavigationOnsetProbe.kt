package com.shilapi.xcertplay.media

import kotlin.math.sqrt

/** Numeric levels of the first 200 ms actually sent to AudioTrack; never stores voice data. */
internal class NavigationOnsetProbe(private val sampleRate: Int, private val channels: Int) {
    data class Levels(val onset: Long, val frames: Int, val peak: Int, val rms: Int)
    private val targetFrames = maxOf(1, sampleRate / 5)
    private var onset = 0L
    private var frames = 0
    private var peak = 0
    private var energy = 0L
    private var collecting = false

    fun begin(id: Long) {
        onset = id; frames = 0; peak = 0; energy = 0; collecting = true
    }

    fun append(data: ByteArray, offset: Int, length: Int): Levels? {
        if (!collecting) return null
        val bytesPerFrame = channels * 2
        require(offset >= 0 && length >= 0 && offset <= data.size - length && length % bytesPerFrame == 0)
        val count = minOf(length / bytesPerFrame, targetFrames - frames)
        for (position in offset until offset + count * bytesPerFrame step 2) {
            val sample = (data[position].toInt() and 255) or (data[position + 1].toInt() shl 8)
            peak = maxOf(peak, kotlin.math.abs(sample))
            energy += sample.toLong() * sample
        }
        frames += count
        return if (frames == targetFrames) finish() else null
    }

    fun finish(): Levels? {
        if (!collecting || frames == 0) return null
        collecting = false
        return Levels(onset, frames, peak, sqrt(energy.toDouble() / (frames * channels)).toInt())
    }
}
