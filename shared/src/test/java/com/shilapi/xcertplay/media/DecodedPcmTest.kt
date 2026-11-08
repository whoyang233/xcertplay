package com.shilapi.xcertplay.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class DecodedPcmTest {
    @Test fun floatOutputIsConvertedRatherThanPlayedAsPcm16Noise() {
        val values = listOf(Float.NEGATIVE_INFINITY, -1f, -0.5f, 0f, 0.5f, 1f, Float.POSITIVE_INFINITY, Float.NaN)
        val input = ByteBuffer.allocate(4 + values.size * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        input.putInt(0x12345678)
        values.forEach { input.putFloat(it) }
        input.putInt(0x12345678)
        input.limit(4 + values.size * 4)
        input.position(4)
        val out = ByteArray(values.size * 2)
        assertEquals(out.size, DecodedPcm.copyToPcm16(input, DecodedPcm.PCM_FLOAT, 2, out))
        val shorts = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals(listOf(-32768, -32768, -16384, 0, 16384, 32767, 32767, 0),
            List(values.size) { shorts.get().toInt() })
    }

    @Test fun pcm16HonorsDecoderOffsetAndLengthWithoutCopyingStaleBufferContents() {
        val input = ByteBuffer.wrap(byteArrayOf(91, 92, 1, 2, 3, 4, 93, 94))
        input.limit(6)
        input.position(2)
        val out = ByteArray(8) { 99 }
        assertEquals(4, DecodedPcm.copyToPcm16(input, DecodedPcm.PCM_16BIT, 2, out))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 99, 99, 99, 99), out)
    }

    @Test fun malformedFramesAndUnsupportedEncodingsCannotReachTheTrack() {
        for ((length, encoding, channels) in listOf(Triple(3, 2, 1), Triple(2, 2, 2),
            Triple(4, 4, 2), Triple(8, 99, 1))) {
            assertThrows(IllegalArgumentException::class.java) { DecodedPcm.pcm16Length(length, encoding, channels) }
        }
    }
}
