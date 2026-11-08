package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioPacketOrderTest {
    @Test fun duplicatesAndLatePacketsAreDroppedWhileLossAndWrapKeepMoving() {
        val order = AudioPacketOrder()
        for ((sequence, accepted) in listOf(65534 to true, 65534 to false, 65533 to false,
            65535 to true, 0 to true, 2 to true, 1 to false, 2 to false, 3 to true)) {
            assertEquals("sequence=$sequence", accepted, order.accept(packet(sequence)))
        }
    }

    @Test fun invalidPacketsDoNotConsumeTheNextSequence() {
        val order = AudioPacketOrder()
        for (length in 0..12) assertFalse(order.accept(ByteArray(length)))
        assertTrue(order.accept(packet(0)))
    }

    private fun packet(sequence: Int): ByteArray = ByteArray(14).also {
        it[2] = (sequence ushr 8).toByte(); it[3] = sequence.toByte()
    }
}
