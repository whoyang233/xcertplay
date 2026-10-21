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
