package com.shilapi.xcertplay.media

/** A vendor stream can throw or return an unusable AudioTrack. Release it before falling back. */
internal object AudioStreamFallback {
    fun <T : Any> build(
        create: () -> T,
        initialized: (T) -> Boolean,
        release: (T) -> Unit,
        fallback: () -> T,
    ): T {
        val candidate = try { create() } catch (_: RuntimeException) { null }
        if (candidate != null) {
            if (initialized(candidate)) return candidate
            release(candidate)
        }
        return fallback()
    }
}
