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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigationAudioPipelineTest {
    @Test fun decodedNavigationGainReachesWebOutputExactlyOnceWithoutAffectingOtherStreams() {
        val received = ConcurrentHashMap<Int, ByteArray>()
        val done = CountDownLatch(4)
        val tap = object : MediaTap {
            override fun forwardsVideo(type: Int) = false
            override fun takesAudio() = true
            override fun onAudioPcm(stream: Int, sampleRate: Int, channels: Int, pcm: ByteArray, offset: Int, length: Int) {
                received[stream] = pcm.copyOfRange(offset, offset + length)
                done.countDown()
            }
        }
        val context: Context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(AudioManager::class.java)
        val before = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val sink = AndroidMediaSink(context, mediaTap = tap,
            audioPlaybackSettings = AudioPlaybackSettings(navigationOutput = AudioOutputChannel.MEDIA,
                navigationVolumePercent = 25, musicStream = 3, navigationStream = 14))
        try {
            for ((stream, type, payload) in listOf(
                Triple(1, "default", 101), Triple(2, "media", 102),
                Triple(3, "telephony", 100), Triple(4, "speechRecognition", 100),
            )) {
                val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, payload, type)
                sink.onAudioStarted(stream, format, 0)
                // RTP carries big-endian PCM. 10000, -10000 become little-endian after decoding.
                sink.onAudioRtp(stream, format, ByteArray(12) + byteArrayOf(0x27, 0x10, 0xd8.toByte(), 0xf0.toByte()), 0)
            }
            assertTrue("All audio workers must deliver PCM", done.await(10, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(0xc4.toByte(), 0x09, 0x3c, 0xf6.toByte()), received[1])
            for (stream in 2..4) assertArrayEquals(byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte()), received[stream])
            assertEquals(before, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        } finally { sink.close() }
    }
}
