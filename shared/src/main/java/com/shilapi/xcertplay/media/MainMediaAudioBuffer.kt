package com.shilapi.xcertplay.media

/**
 * Sizing rules for the main media AudioTrack (AirPlay stream types 100 and 102).
 *
 * The selectable durations include low-latency presets while retaining every legacy 100 ms step.
 * This is a PCM buffer capacity, not a promise of end-to-end audio/video latency.
 */
object MainMediaAudioBuffer {
    const val MIN_DURATION_MS = 0

    /** Longest buffered duration the settings slider offers. */
    const val MAX_DURATION_MS = 1_000

    /** Keep the previous default and saved values; opting into 0/20/50 ms is explicit. */
    const val DEFAULT_DURATION_MS = 100

    val presetsMs: List<Int> = listOf(0, 20) + (50..MAX_DURATION_MS step 50).toList()

    private const val BYTES_PER_PCM_16_SAMPLE = 2L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val STREAM_TYPE_MAIN_AUDIO = 100
    private const val STREAM_TYPE_MAIN_HIGH_AUDIO = 102

    /** Closest offered preset, preferring the larger buffer on ties. Uses Long for corrupt ints. */
    fun sanitizeDurationMs(durationMs: Int): Int = presetsMs.minWith(
        compareBy<Int> { kotlin.math.abs(it.toLong() - durationMs.toLong()) }.thenByDescending { it },
    )

    /**
     * Capacity in bytes for [durationMs] of 16-bit PCM at [sampleRate] and [channelCount].
     *
     * The system minimum reported by AudioTrack stays the lower bound, and the result never
     * exceeds [Int.MAX_VALUE] so it can be handed to the AudioTrack builder directly.
     */
    fun bufferSizeBytes(
        durationMs: Int,
        sampleRate: Int,
        channelCount: Int,
        minBufferBytes: Int,
    ): Int {
        require(sampleRate > 0)
        require(channelCount > 0)
        require(minBufferBytes > 0)
        val bytesPerFrame = channelCount * BYTES_PER_PCM_16_SAMPLE
        val frames = (sampleRate.toLong() * sanitizeDurationMs(durationMs) / MILLIS_PER_SECOND)
            .coerceAtMost(Int.MAX_VALUE / bytesPerFrame)
        val capacity = frames * bytesPerFrame
        return maxOf(minBufferBytes.toLong(), capacity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Never wait for more PCM than an unstarted track can hold. Keeps the old 4 KiB target. */
    fun startThresholdBytes(bufferBytes: Int, minBufferBytes: Int, durationMs: Int = DEFAULT_DURATION_MS): Int {
        require(bufferBytes > 0 && minBufferBytes > 0)
        if (sanitizeDurationMs(durationMs) == 0) return 0
        return minOf(bufferBytes, maxOf(minBufferBytes, 4 * 1024))
    }

    /** A blocking pre-play write must stop at the threshold so play() can start the consumer. */
    fun prebufferWriteLength(remaining: Int, buffered: Int, threshold: Int, bytesPerFrame: Int = 1): Int =
        if (threshold == 0) minOf(remaining, bytesPerFrame.coerceAtLeast(1))
        else minOf(remaining, 2 * 1024, (threshold - buffered).coerceAtLeast(1))

    /** True only for the main media stream, whose buffered duration is user configurable. */
    internal fun isMainMedia(audioType: String, payloadType: Int,
                             mode: AudioChannelMappingMode = AudioChannelMappingMode.MOBILE_COMPATIBLE): Boolean =
        (payloadType == STREAM_TYPE_MAIN_AUDIO || payloadType == STREAM_TYPE_MAIN_HIGH_AUDIO) &&
            AudioChannelMapper.map(audioType, payloadType, mode).channel == AudioChannel.MEDIA
}
