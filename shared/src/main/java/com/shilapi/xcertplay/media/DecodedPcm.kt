package com.shilapi.xcertplay.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Normalizes decoder output to the signed little-endian PCM16 consumed by AudioTrack. */
internal object DecodedPcm {
    // Android AudioFormat encoding values; keep conversion independently testable on the JVM.
    const val PCM_16BIT = 2
    const val PCM_FLOAT = 4

    fun supports(encoding: Int): Boolean = encoding == PCM_16BIT || encoding == PCM_FLOAT

    fun pcm16Length(inputLength: Int, encoding: Int, channels: Int): Int {
        require(inputLength >= 0 && channels in 1..2 && supports(encoding))
        val bytesPerSample = if (encoding == PCM_FLOAT) 4 else 2
        require(inputLength % (bytesPerSample * channels) == 0) { "Incomplete decoded PCM frame" }
        return inputLength / bytesPerSample * 2
    }

    fun copyToPcm16(input: ByteBuffer, encoding: Int, channels: Int, output: ByteArray): Int {
        val length = pcm16Length(input.remaining(), encoding, channels)
        require(output.size >= length)
        if (encoding == PCM_16BIT) {
            input.get(output, 0, length)
        } else {
            val floats = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until length step 2) {
                val value = floats.float
                val sample = when {
                    value.isNaN() -> 0
                    value <= -1f -> Short.MIN_VALUE.toInt()
                    value >= 1f -> Short.MAX_VALUE.toInt()
                    else -> (value * 32768f).toInt()
                }
                output[offset] = sample.toByte()
                output[offset + 1] = (sample shr 8).toByte()
            }
        }
        return length
    }
}
