package com.shilapi.xcertplay.media

/** Queue limits, not an artificial playback delay or an end-to-end latency promise. */
object VideoBufferPolicy {
    const val COMPATIBILITY = -1
    const val DEFAULT_DURATION_MS = COMPATIBILITY
    val presetsMs = listOf(COMPATIBILITY, 0, 20, 50, 100, 150, 200, 250, 300)

    fun sanitize(durationMs: Int): Int = durationMs.takeIf { it in presetsMs } ?: DEFAULT_DURATION_MS

    /** Compressed references remain FIFO and use backpressure; a fraction of a frame needs one slot. */
    fun queueCapacity(durationMs: Int, fps: Int): Int {
        val budget = sanitize(durationMs)
        if (budget == COMPATIBILITY) return 8
        return ((budget.toLong() * fps.coerceIn(1, 120) + 999) / 1000).toInt().coerceIn(1, 36)
    }
}

/** Only decoded output can be skipped. The newest/last output is always released for rendering. */
internal class VideoOutputBuffer(durationMs: Int) {
    data class Frame(val index: Int, val ptsUs: Long, val renderable: Boolean, val size: Int = 0)
    private val budget = VideoBufferPolicy.sanitize(durationMs)
    private var pending: Frame? = null

    fun offer(frame: Frame, nowUs: Long, release: (Frame, Boolean) -> Unit) {
        if (budget == VideoBufferPolicy.COMPATIBILITY) {
            release(frame, frame.renderable)
            return
        }
        if (!frame.renderable) {
            release(frame, false)
            return
        }
        pending?.let { older ->
            val inBudget = budget > 0 && nowUs - older.ptsUs <= budget * 1000L
            release(older, older.renderable && inBudget)
        }
        pending = frame
    }

    /** Called on every drain, including idle ticks; no frame waits for another packet or a timer. */
    fun finish(release: (Frame, Boolean) -> Unit) {
        val last = pending ?: return
        pending = null
        release(last, last.renderable)
    }

    /** Codec teardown releases its output indices; never carry them to a new codec. */
    fun clear() { pending = null }
}
