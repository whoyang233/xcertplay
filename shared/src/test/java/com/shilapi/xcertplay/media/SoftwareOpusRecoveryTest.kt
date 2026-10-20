package com.shilapi.xcertplay.media

import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SoftwareOpusRecoveryTest {
    @Test fun assertionAndRuntimeFailureDropOnlyTheirFrameAndResetEncoder() {
        for (failure in listOf(AssertionError("Concentus check"), IllegalStateException("codec failure"))) {
            var resets = 0
            val backend = SoftwareOpusBackend(48_000, 48_000) { rate ->
                object : org.concentus.OpusEncoder(rate, 1, OpusApplication.OPUS_APPLICATION_VOIP) {
                    var frames = 0
                    override fun encode(pcm: ShortArray, pcmOffset: Int, frameSize: Int, output: ByteArray,
                        outputOffset: Int, maxBytes: Int): Int {
                        if (frames++ == 1) throw failure
                        return super.encode(pcm, pcmOffset, frameSize, output, outputOffset, maxBytes)
                    }
                    override fun resetState() { resets++; super.resetState() }
                }
            }
            val decoder = OpusDecoder(48_000, 1)
            val frame = ByteArray(1_920)
            assertEquals(1, backend.encode(frame).size)
            val before = resets
            assertTrue(backend.encode(frame).isEmpty())
            assertEquals(before + 1, resets)
            repeat(4) {
                val packet = backend.encode(frame).single()
                assertEquals(960, decoder.decode(packet, 0, packet.size, ShortArray(960), 0, 960, false))
            }
            backend.close()
            assertTrue(backend.encode(frame).isEmpty())
        }
    }

    @Test fun fallbackStillDrainsPendingFramesWhenAnEncoderFrameFails() {
        val backend = SoftwareOpusBackend(48_000, 48_000) { rate ->
            object : org.concentus.OpusEncoder(rate, 1, OpusApplication.OPUS_APPLICATION_VOIP) {
                var first = true
                override fun encode(pcm: ShortArray, pcmOffset: Int, frameSize: Int, output: ByteArray,
                    outputOffset: Int, maxBytes: Int): Int {
                    if (first) { first = false; throw AssertionError() }
                    return super.encode(pcm, pcmOffset, frameSize, output, outputOffset, maxBytes)
                }
            }
        }
        val encoder = OpusEncoder(48_000, 48_000, systemFactory = { _, _ ->
            object : OpusBackend {
                override val name = "system"
                override fun encode(pcm: ByteArray): List<ByteArray> = error("system codec stopped")
                override fun close() = Unit
            }
        }, softwareFactory = { _, _ -> backend })
        assertTrue(encoder.encode(ByteArray(1_920)).isEmpty())
        assertEquals("software", encoder.backendName)
        assertEquals(1, encoder.encode(ByteArray(1_920)).size)
        encoder.close()
    }
}
