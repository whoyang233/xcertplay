package com.shilapi.xcertplay.network

import java.io.IOException
import java.io.InterruptedIOException

// A cancelled controller can still be restoring the radio after its UI has moved on.
// A new controller must not create its hotspot until that restoration has finished.
private val hotspotStartupLock = Any()

/** Retries only hotspot startup, once, after the caller has released the failed manager. */
internal fun <T> startHotspotWithRecovery(
    enabled: Boolean,
    isCancelled: () -> Boolean,
    recover: () -> Unit,
    start: () -> T,
): T = synchronized(hotspotStartupLock) {
    fun checkActive() {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Wi-Fi hotspot startup cancelled")
        }
    }
    checkActive()
    try {
        start()
    } catch (first: IOException) {
        if (!enabled || generateSequence<Throwable>(first) { it.cause }.any {
            it is SecurityException || it is InterruptedIOException || it is InterruptedException
        }) throw first
        checkActive()
        try {
            recover()
        } catch (failure: IOException) {
            if (failure !== first) failure.addSuppressed(first)
            throw failure
        }
        checkActive()
        try {
            start()
        } catch (last: IOException) {
            if (last !== first) last.addSuppressed(first)
            throw last
        }
    }
}

internal enum class WifiRadioState { ENABLED, DISABLED, ENABLING, DISABLING, UNKNOWN }

internal interface WifiRadio {
    fun state(): WifiRadioState
    fun setEnabled(enabled: Boolean): Boolean
}

/** Bounded radio reset. An accepted disable is always paired with an enable, even on cancellation. */
internal class WifiRadioRecovery(
    private val radio: WifiRadio,
    private val isCancelled: () -> Boolean,
    private val report: (String) -> Unit,
    private val nowMillis: () -> Long,
    private val waitMillis: (Long) -> Unit,
) {
    fun recover() {
        checkActive()
        val initial = radio.state()
        if (initial == WifiRadioState.UNKNOWN || initial == WifiRadioState.DISABLING) {
            report("wifi/recovery radio=$initial; rebuilding hotspot without radio reset")
            return
        }
        if (initial == WifiRadioState.DISABLED) {
            if (setEnabled(true)) awaitState(WifiRadioState.ENABLED)
            return
        }
        if (!setEnabled(false)) return
        // Cancellation must not leave a radio that we disabled switched off. Restoration runs
        // even after teardown starts; the startup lock keeps a new hotspot out until it is done.
        try {
            awaitState(WifiRadioState.DISABLED)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Wi-Fi reset interrupted").apply { initCause(failure) }
        } finally {
            val interrupted = Thread.interrupted()
            try {
                if (!setEnabled(true)) throw IOException("Could not re-enable Wi-Fi; enable it in system settings and retry")
                awaitState(WifiRadioState.ENABLED, restoring = true)
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
        checkActive()
    }

    private fun setEnabled(enabled: Boolean): Boolean = try {
        radio.setEnabled(enabled).also { accepted ->
            report("wifi/recovery switch=${if (enabled) "on" else "off"} accepted=$accepted")
        }
    } catch (_: SecurityException) {
        report("wifi/recovery switch=denied; rebuilding hotspot without radio reset")
        false
    }

    private fun awaitState(expected: WifiRadioState, restoring: Boolean = false) {
        val deadline = nowMillis() + STATE_TIMEOUT_MILLIS
        var interrupted = false
        try {
            while (radio.state() != expected) {
                if (!restoring) checkActive()
                if (nowMillis() >= deadline) throw IOException("Wi-Fi state transition timed out: $expected")
                try {
                    waitMillis(POLL_MILLIS)
                } catch (failure: InterruptedException) {
                    if (!restoring) throw failure
                    // Teardown interrupts the worker. Finish the bounded restore before leaving.
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun checkActive() {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Wi-Fi reset cancelled")
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
    }
}
