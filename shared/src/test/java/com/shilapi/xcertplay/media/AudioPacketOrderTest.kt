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

    @Test fun aNewRtpSourceMayRestartItsSequenceButLateOldSourcePacketsStayRetired() {
        val order = AudioPacketOrder()
        fun source(sequence: Int, ssrc: Int) = packet(sequence).also { it[11] = ssrc.toByte() }
        assertTrue(order.accept(source(1200, 1)))
        assertTrue(order.accept(source(0, 2)))
        assertFalse(order.accept(source(1201, 1)))
        assertTrue(order.accept(source(1, 2)))
    }

    private fun packet(sequence: Int): ByteArray = ByteArray(14).also {
        it[2] = (sequence ushr 8).toByte(); it[3] = sequence.toByte()
    }
}
