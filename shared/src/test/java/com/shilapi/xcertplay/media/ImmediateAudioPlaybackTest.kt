package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioTrack
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImmediateAudioPlaybackTest {
    @Test fun zeroMediaBufferStartsAfterOnePcmFrameWithoutWaitingForTheTrackToFill() {
        val writes = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
        val done = CountDownLatch(1)
        val total = AtomicInteger(0)
        val listener = ShadowAudioTrack.OnAudioDataWrittenListener { track, data, _ ->
            writes += data.size to track.playState
            if (total.addAndGet(data.size) >= 2400) done.countDown()
        }
        ShadowAudioTrack.addAudioDataListener(listener)
        val context: Context = RuntimeEnvironment.getApplication()
        val sink = AndroidMediaSink(context, mainMediaAudioBufferDurationMs = 0)
        try {
            val format = AudioFormat(AudioCodecKind.LPCM, 48000, 2, 102, "media")
            sink.onAudioStarted(1, format, 0)
            sink.onAudioRtp(1, format, ByteArray(12 + 2400), 0)
            assertTrue("PCM must reach the local track", done.await(10, TimeUnit.SECONDS))
            assertEquals(4, writes.first().first)
            assertEquals(AudioTrack.PLAYSTATE_STOPPED, writes.first().second)
            assertTrue(writes.drop(1).all { it.second == AudioTrack.PLAYSTATE_PLAYING })
            assertEquals(2400, total.get())
        } finally {
            sink.close()
            ShadowAudioTrack.removeAudioDataListener(listener)
        }
    }
}
