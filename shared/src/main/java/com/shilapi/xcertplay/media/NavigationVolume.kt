package com.shilapi.xcertplay.media

/** Software gain applied once to decoded navigation PCM, before local or web playback. */
object NavigationVolume {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 200
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 5
    const val STEP_COUNT = (MAX_PERCENT - MIN_PERCENT) / STEP_PERCENT + 1

    fun sanitize(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
    fun percentForStep(step: Int): Int = step.coerceIn(0, STEP_COUNT - 1) * STEP_PERCENT
    fun stepForPercent(percent: Int): Int = (sanitize(percent) + STEP_PERCENT / 2) / STEP_PERCENT

    internal fun applyPcm16InPlace(
        buffer: ByteArray,
        offset: Int,
        length: Int,
        selection: AudioChannelSelection,
        percent: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (selection.channel != AudioChannel.NAVIGATION) return
        val gain = sanitize(percent)
        if (gain == DEFAULT_PERCENT) return
        val end = offset + length
        var index = offset
        while (index + 1 < end) {
            val sample = ((buffer[index].toInt() and 0xff) or
                (buffer[index + 1].toInt() shl 8)).toShort().toInt()
            val value = (sample * gain / 100).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[index] = value.toByte()
            buffer[index + 1] = (value ushr 8).toByte()
            index += 2
        }
    }
}
