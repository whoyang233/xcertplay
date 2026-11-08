package com.shilapi.xcertplay.media

/** Keeps duplicate/late UDP datagrams out of the decoder without adding a jitter buffer. */
internal class AudioPacketOrder {
    private var lastSequence: Int? = null

    fun accept(rtp: ByteArray): Boolean {
        if (rtp.size <= 12) return false
        val sequence = ((rtp[2].toInt() and 0xff) shl 8) or (rtp[3].toInt() and 0xff)
        val previous = lastSequence
        if (previous != null) {
            val advance = (sequence - previous) and 0xffff
            if (advance == 0 || advance >= 0x8000) return false
        }
        lastSequence = sequence
        return true
    }
}
