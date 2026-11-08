package com.shilapi.xcertplay.media

/** Frame-based attack envelope; packet boundaries must not shorten or restart the fade. */
internal class PcmPlaybackEnvelope(sampleRate: Int, private val channels: Int) {
    private val fadeFrames = maxOf(1, sampleRate / 100) // 10 ms, without buffering or delaying audio.
    private var framesProcessed = 0

    init {
        require(sampleRate > 0 && channels > 0)
    }

    fun restart() {
        framesProcessed = 0
    }

    fun apply(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= data.size - length)
        val bytesPerFrame = channels * 2
        require(length % bytesPerFrame == 0) { "PCM must contain complete sample frames" }
        val frames = minOf(length / bytesPerFrame, fadeFrames - framesProcessed)
        for (frame in 0 until frames) {
            val numerator = framesProcessed + frame + 1
            for (channel in 0 until channels) {
                val position = offset + frame * bytesPerFrame + channel * 2
                val sample = (data[position].toInt() and 0xff) or (data[position + 1].toInt() shl 8)
                val scaled = (sample.toLong() * numerator / fadeFrames).toInt()
                data[position] = scaled.toByte()
                data[position + 1] = (scaled shr 8).toByte()
            }
        }
        framesProcessed += frames
    }
}
