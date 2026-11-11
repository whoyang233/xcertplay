package com.shilapi.xcertplay.media

/** Frame-based attack envelope; navigation silence must not consume the next speech attack. */
internal class PcmPlaybackEnvelope(
    sampleRate: Int,
    private val channels: Int,
    private val detectSpeechOnsets: Boolean = false,
    protectNavigationPeaks: Boolean = false,
) {
    private val fadeFrames = maxOf(1, sampleRate * (if (detectSpeechOnsets) 20 else 10) / 1000)
    private val quietFramesToRearm = maxOf(1, sampleRate / 4)
    private var framesProcessed = 0
    private var quietFrames = 0
    private var waitingForSpeech = detectSpeechOnsets
    private val quietWindowFrames = maxOf(1, sampleRate * 5 / 1000)
    private var windowFrames = 0
    private var windowEnergy = 0L
    private val limiter = if (detectSpeechOnsets && protectNavigationPeaks) NavigationPeakLimiter(sampleRate) else null
    var minimumGainPercent = 100
        private set
    var limitedFrames = 0L
        private set
    var firstSpeechFrameOffset: Int? = null
        private set
    var onsetCount = 0L
        private set
    var inputPeak = 0
        private set
    var outputPeak = 0
        private set
    var firstSpeechInputPeak = 0
        private set
    var firstSpeechOutputPeak = 0
        private set

    init {
        require(sampleRate > 0 && channels > 0)
    }

    fun restart() {
        framesProcessed = 0
        quietFrames = 0
        waitingForSpeech = detectSpeechOnsets
        windowFrames = 0
        windowEnergy = 0L
        limiter?.reset()
    }

    fun apply(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= data.size - length)
        val bytesPerFrame = channels * 2
        require(length % bytesPerFrame == 0) { "PCM must contain complete sample frames" }
        inputPeak = 0
        outputPeak = 0
        minimumGainPercent = 100
        firstSpeechFrameOffset = null
        val frames = if (detectSpeechOnsets) length / bytesPerFrame
            else minOf(length / bytesPerFrame, fadeFrames - framesProcessed)
        for (frame in 0 until frames) {
            val frameOffset = offset + frame * bytesPerFrame
            var peak = 0
            for (channel in 0 until channels) {
                val position = frameOffset + channel * 2
                val sample = (data[position].toInt() and 0xff) or (data[position + 1].toInt() shl 8)
                peak = maxOf(peak, kotlin.math.abs(sample))
            }
            inputPeak = maxOf(inputPeak, peak)
            var newOnset = false
            if (detectSpeechOnsets) {
                if (peak > SPEECH_PEAK && waitingForSpeech) {
                    framesProcessed = 0
                    waitingForSpeech = false
                    onsetCount++
                    firstSpeechInputPeak = peak
                    firstSpeechFrameOffset = frameOffset
                    newOnset = true
                }
                // Short-window energy tolerates dither and isolated noise samples; requiring
                // 250 ms of individually quiet samples never rearms on some decoded streams.
                windowEnergy += peak.toLong() * peak
                windowFrames++
                if (windowFrames == quietWindowFrames) {
                    if (windowEnergy <= windowFrames.toLong() * QUIET_RMS * QUIET_RMS) {
                        quietFrames = minOf(quietFrames + windowFrames, quietFramesToRearm)
                        if (quietFrames >= quietFramesToRearm && !newOnset) waitingForSpeech = true
                    } else quietFrames = 0
                    windowFrames = 0
                    windowEnergy = 0L
                }
            }
            val peakGain = limiter?.gainForPeak(peak) ?: 1.0
            if (peakGain < 1.0) {
                limitedFrames++
                minimumGainPercent = minOf(minimumGainPercent, (peakGain * 100).toInt())
            }
            val numerator = if (waitingForSpeech) 0 else minOf(framesProcessed + 1, fadeFrames)
            var frameOutputPeak = 0
            for (channel in 0 until channels) {
                val position = frameOffset + channel * 2
                val sample = (data[position].toInt() and 0xff) or (data[position + 1].toInt() shl 8)
                val limited = if (limiter == null) sample else (sample * peakGain).toInt()
                val scaled = (limited.toLong() * numerator / fadeFrames).toInt()
                data[position] = scaled.toByte()
                data[position + 1] = (scaled shr 8).toByte()
                outputPeak = maxOf(outputPeak, kotlin.math.abs(scaled))
                frameOutputPeak = maxOf(frameOutputPeak, kotlin.math.abs(scaled))
            }
            if (newOnset) firstSpeechOutputPeak = frameOutputPeak
            if (!waitingForSpeech) framesProcessed = minOf(framesProcessed + 1, fadeFrames)
        }
    }

    private companion object {
        // Detection runs BEFORE the user's volume adjustment, including at 0% or 5%.
        const val QUIET_RMS = 128
        const val SPEECH_PEAK = 256
    }
}
