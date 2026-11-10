package com.shilapi.xcertplay.media

/** Frame-based attack envelope; navigation silence must not consume the next speech attack. */
internal class PcmPlaybackEnvelope(
    sampleRate: Int,
    private val channels: Int,
    private val detectSpeechOnsets: Boolean = false,
) {
    private val fadeFrames = maxOf(1, sampleRate * (if (detectSpeechOnsets) 20 else 10) / 1000)
    private val quietFramesToRearm = maxOf(1, sampleRate / 4)
    private var framesProcessed = 0
    private var quietFrames = 0
    private var waitingForSpeech = detectSpeechOnsets
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
    }

    fun apply(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= data.size - length)
        val bytesPerFrame = channels * 2
        require(length % bytesPerFrame == 0) { "PCM must contain complete sample frames" }
        inputPeak = 0
        outputPeak = 0
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
                if (peak <= QUIET_PEAK) {
                    quietFrames = minOf(quietFrames + 1, quietFramesToRearm)
                    if (quietFrames >= quietFramesToRearm) waitingForSpeech = true
                } else {
                    if (waitingForSpeech) {
                        framesProcessed = 0
                        waitingForSpeech = false
                        onsetCount++
                        firstSpeechInputPeak = peak
                        newOnset = true
                    }
                    quietFrames = 0
                }
            }
            val numerator = if (waitingForSpeech) 0 else minOf(framesProcessed + 1, fadeFrames)
            var frameOutputPeak = 0
            for (channel in 0 until channels) {
                val position = frameOffset + channel * 2
                val sample = (data[position].toInt() and 0xff) or (data[position + 1].toInt() shl 8)
                val scaled = (sample.toLong() * numerator / fadeFrames).toInt()
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
        // About -60 dBFS: below audible speech, well below a plosive. Ignore a quiet noise floor.
        const val QUIET_PEAK = 32
    }
}
