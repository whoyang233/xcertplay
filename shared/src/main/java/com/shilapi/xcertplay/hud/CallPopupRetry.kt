package com.shilapi.xcertplay.hud

/** Retry boot/ADB races, but do not repeatedly run a helper the firmware cannot support. */
internal object CallPopupRetry {
    private val delays = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000, 30_000)

    fun delayMillis(failure: String, retry: Int): Long? {
        if (retry !in delays.indices) return null
        return when (failure) {
            "ADB_UNREACHABLE", "ADB_NOT_APPROVED", "HELPER_BUSY" -> delays[retry]
            "HELPER_NOT_READY", "HELPER_CONTROL_FAILED", "HELPER_TIMEOUT", "RESTORE_UNCONFIRMED", "ADB_CONNECTION_LOST" ->
                maxOf(30_000L, delays[retry]) // Let the detached lease finish recovery first.
            else -> null
        }
    }
}
