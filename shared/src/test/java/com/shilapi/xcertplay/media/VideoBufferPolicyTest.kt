package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class VideoBufferPolicyTest {
    @Test fun defaultKeepsOriginalQueueAndInvalidSettingsRecoverSafely() {
        assertEquals(8, VideoBufferPolicy.queueCapacity(VideoBufferPolicy.DEFAULT_DURATION_MS, 60))
        for (invalid in listOf(-2, 1, 19, 51, 301, Int.MAX_VALUE)) {
            assertEquals(VideoBufferPolicy.COMPATIBILITY, VideoBufferPolicy.sanitize(invalid))
            assertEquals(8, VideoBufferPolicy.queueCapacity(invalid, 60))
        }
    }
    @Test fun timeBudgetUsesFrameRateAndNeverCreatesAZeroCapacityQueue() {
        assertEquals(1, VideoBufferPolicy.queueCapacity(0, 60))
        assertEquals(1, VideoBufferPolicy.queueCapacity(20, 30))
        assertEquals(2, VideoBufferPolicy.queueCapacity(20, 60))
        assertEquals(3, VideoBufferPolicy.queueCapacity(50, 60))
        assertEquals(6, VideoBufferPolicy.queueCapacity(100, 60))
        assertEquals(36, VideoBufferPolicy.queueCapacity(300, Int.MAX_VALUE))
        assertEquals(1, VideoBufferPolicy.queueCapacity(300, 0))
    }
    @Test fun compatibilityReleasesEveryPictureImmediately() {
        val released = mutableListOf<Pair<Int, Boolean>>()
        val buffer = VideoOutputBuffer(-1)
        buffer.offer(frame(1, 0), 1_000_000) { frame, render -> released += frame.index to render }
        assertEquals(listOf(1 to true), released)
        buffer.offer(frame(2, 0), 1_000_000) { frame, render -> released += frame.index to render }
        buffer.finish { frame, render -> released += frame.index to render }
        assertEquals(listOf(1 to true, 2 to true), released)
    }
    @Test fun immediateModeSkipsOlderDecodedOutputButAlwaysRendersNewest() {
        assertEquals(listOf(1 to false, 2 to false, 3 to true), drain(0, listOf(0, 10, 20), 100_000))
    }
    @Test fun finiteBudgetPreservesFreshOutputAndSkipsOnlyExpiredPictures() {
        assertEquals(listOf(1 to false, 2 to true, 3 to true), drain(50, listOf(0, 80_000, 90_000), 100_000))
    }
    @Test fun finalStaticFrameRendersEvenWhenOlderThanTheBudget() {
        assertEquals(listOf(1 to true), drain(20, listOf(0), 10_000_000))
    }
    @Test fun codecConfigOutputDoesNotReplaceARealFinalPicture() {
        val buffer = VideoOutputBuffer(0)
        val released = mutableListOf<Pair<Int, Boolean>>()
        val release: (VideoOutputBuffer.Frame, Boolean) -> Unit = { frame, render -> released += frame.index to render }
        buffer.offer(frame(1, 0), 1_000_000, release)
        buffer.offer(VideoOutputBuffer.Frame(2, 0, false), 1_000_000, release)
        buffer.finish(release)
        assertEquals(listOf(2 to false, 1 to true), released)
    }
    @Test fun resettingCodecNeverReleasesAnOldIndexIntoItsReplacement() {
        val buffer = VideoOutputBuffer(0)
        buffer.offer(frame(1, 0), 100) { _, _ -> fail("One pending output") }
        buffer.clear()
        buffer.finish { _, _ -> fail("Codec teardown owns the old output") }
    }
    private fun frame(index: Int, pts: Long) = VideoOutputBuffer.Frame(index, pts, true)
    private fun drain(budget: Int, timestamps: List<Long>, now: Long): List<Pair<Int, Boolean>> {
        val buffer = VideoOutputBuffer(budget)
        val released = mutableListOf<Pair<Int, Boolean>>()
        val release: (VideoOutputBuffer.Frame, Boolean) -> Unit = { frame, render -> released += frame.index to render }
        timestamps.forEachIndexed { index, pts -> buffer.offer(frame(index + 1, pts), now, release) }
        buffer.finish(release)
        return released
    }
}
