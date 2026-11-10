package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioManager
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioTrack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigationAudioPipelineTest {
    @Test fun everyShortStereoPacketIsAttenuatedFromTheFirstWriteAndUsesOneEnvelope() {
        val received = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
        val bytesWritten = AtomicInteger()
        val done = CountDownLatch(1)
        val listener = ShadowAudioTrack.OnAudioDataWrittenListener { _, data, _ ->
            received += data.copyOf()
            if (bytesWritten.addAndGet(data.size) >= 3840) done.countDown()
        }
        ShadowAudioTrack.addAudioDataListener(listener)
        val sink = AndroidMediaSink(RuntimeEnvironment.getApplication(),
            audioPlaybackSettings = AudioPlaybackSettings(navigationVolumePercent = 60))
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 2, 101, "default")
        try {
            sink.onAudioStarted(101, format, 0)
            repeat(20) { sequence ->
                val packet = ByteArray(12 + 192)
                packet[3] = sequence.toByte()
                for (i in 12 until packet.size step 2) { packet[i] = 0x27; packet[i + 1] = 0x10 }
                sink.onAudioRtp(101, format, packet, sequence * 48)
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            val output = synchronized(received) { received.flatMap { it.toList() }.toByteArray() }
            val samples = output.indices.step(2).map { (output[it].toInt() and 0xff) or (output[it + 1].toInt() shl 8) }
            assertTrue(samples.all { it in 0..6000 })
            assertEquals(6, samples.first())
            assertEquals(6000, samples.last())
            for (i in samples.indices step 2) assertEquals(samples[i], samples[i + 1])
        } finally {
            sink.close()
            ShadowAudioTrack.removeAudioDataListener(listener)
        }
    }

    @Test fun leadingSilentRtpDoesNotExposeAnUnprotectedNavigationPlosive() {
        val diagnostics = java.util.Collections.synchronizedList(mutableListOf<String>())
        val received = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
        val done = CountDownLatch(1)
        val bytesWritten = AtomicInteger()
        val silenceFrames = 4800
        val speechFrames = 1440
        val expectedBytes = (silenceFrames + speechFrames) * 2
        val listener = ShadowAudioTrack.OnAudioDataWrittenListener { _, data, _ ->
            received += data.copyOf()
            if (bytesWritten.addAndGet(data.size) >= expectedBytes) done.countDown()
        }
        ShadowAudioTrack.addAudioDataListener(listener)
        val sink = AndroidMediaSink(RuntimeEnvironment.getApplication(),
            audioPlaybackSettings = AudioPlaybackSettings(navigationVolumePercent = 60),
            audioDiagnosticLog = { diagnostics += it })
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, 101, "navigation")
        try {
            sink.onAudioStarted(101, format, 0)
            repeat(5) { sequence ->
                val silence = ByteArray(12 + 960 * 2)
                silence[3] = sequence.toByte()
                sink.onAudioRtp(101, format, silence, sequence * 960)
            }
            val speech = ByteArray(12 + speechFrames * 2)
            speech[3] = 5
            for (i in 12 until speech.size step 2) { speech[i] = 0x75; speech[i + 1] = 0x30 }
            sink.onAudioRtp(101, format, speech, silenceFrames)
            assertTrue(done.await(10, TimeUnit.SECONDS))
            val out = synchronized(received) { received.flatMap { it.toList() }.toByteArray() }
            val onset = silenceFrames * 2
            fun sample(offset: Int) = (out[offset].toInt() and 0xff) or (out[offset + 1].toInt() shl 8)
            assertEquals(18, sample(onset)) // 30000 * 60% / 960 attack frames.
            assertEquals(18000, sample(out.size - 2))
            assertTrue((onset until out.size step 2).all { sample(it) in 0..18000 })
            assertTrue(diagnostics.any { it.contains("role=NAVIGATION") && it.contains("gain=60%") })
            assertTrue(diagnostics.any { it.contains("audio navigation onset=1") && it.contains("firstOutputPeak=18") })
        } finally {
            sink.close()
            ShadowAudioTrack.removeAudioDataListener(listener)
        }
    }

    @Test fun latePacketsAndStartCallbacksCannotRecreateAStoppedOrClosedSink() {
        val sink = AndroidMediaSink(RuntimeEnvironment.getApplication())
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, 101, "navigation")
        val packet = ByteArray(14)
        try {
            sink.onAudioStarted(101, format, 0)
            sink.onAudioStopped(101)
            sink.onAudioRtp(101, format, packet, 0)
            val field = AndroidMediaSink::class.java.getDeclaredField("audioRenderers").apply { isAccessible = true }
            assertTrue((field.get(sink) as Map<*, *>).isEmpty())
            sink.close()
            sink.onAudioStarted(101, format, 0)
            sink.onAudioRtp(101, format, packet, 0)
            assertTrue((field.get(sink) as Map<*, *>).isEmpty())
        } finally { sink.close() }
    }

    @Test fun decodedNavigationGainReachesLocalAudioTrackWithoutAffectingOtherStreams() {
        val context: Context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(AudioManager::class.java)
        val before = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        for ((type, payload) in listOf("default" to 101, "media" to 102,
            "telephony" to 100, "speechRecognition" to 100)) {
            val received = AtomicReference<ByteArray>()
            val done = CountDownLatch(1)
            val bytesWritten = AtomicInteger(0)
            val listener = ShadowAudioTrack.OnAudioDataWrittenListener { _, pcm, _ ->
                if (pcm.size >= 4) received.set(pcm.takeLast(4).toByteArray())
                if (bytesWritten.addAndGet(pcm.size) >= 2400) done.countDown()
            }
            ShadowAudioTrack.addAudioDataListener(listener)
            val sink = AndroidMediaSink(context,
                audioPlaybackSettings = AudioPlaybackSettings(navigationVolumePercent = 25, musicStream = 3, navigationStream = 14))
            try {
                val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, payload, type)
                sink.onAudioStarted(1, format, 0)
                // Enough samples to observe PCM after the ordinary startup fade. RTP is big endian.
                val pcm = ByteArray(2400) { index -> byteArrayOf(0x27, 0x10, 0xd8.toByte(), 0xf0.toByte())[index % 4] }
                sink.onAudioRtp(1, format, ByteArray(12) + pcm, 0)
                assertTrue("$type must reach the local AudioTrack", done.await(10, TimeUnit.SECONDS))
                val expected = if (payload == 101) byteArrayOf(0xc4.toByte(), 0x09, 0x3c, 0xf6.toByte())
                    else byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte())
                assertArrayEquals(type, expected, received.get())
                assertEquals(before, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            } finally {
                sink.close()
                ShadowAudioTrack.removeAudioDataListener(listener)
            }
        }
    }
}
