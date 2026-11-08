package com.shilapi.xcertplay.media

/** Keeps duplicate/late UDP datagrams out of the decoder without adding a jitter buffer. */
internal class AudioPacketOrder {
    private var lastSequence: Int? = null
    private var source: Int? = null
    private val retiredSources = ArrayDeque<Int>()

    fun accept(rtp: ByteArray): Boolean {
        if (rtp.size <= 12) return false
        val incomingSource = ((rtp[8].toInt() and 0xff) shl 24) or
            ((rtp[9].toInt() and 0xff) shl 16) or
            ((rtp[10].toInt() and 0xff) shl 8) or (rtp[11].toInt() and 0xff)
        if (source != incomingSource) {
            if (incomingSource in retiredSources) return false
            source?.let {
                if (retiredSources.size == 4) retiredSources.removeFirst()
                retiredSources.addLast(it)
            }
            source = incomingSource
            lastSequence = null
        }
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
