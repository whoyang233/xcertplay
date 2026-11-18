package com.shilapi.xcertplay

import android.os.Handler
import java.io.Closeable

/** One bounded launch attempt. User navigation always wins over automatic foregrounding. */
internal class StartupForegroundAttempt(
    private val handler: Handler,
    private val nativeLaunch: () -> Unit,
    private val adbFallback: (() -> Closeable)?,
    private val onFinished: (String) -> Unit,
) {
    private var closed = false
    private var resumed = false
    private var fallback: Closeable? = null
    private val stable = Runnable { if (resumed) finish("foreground") }
    private val timeout = Runnable { finish(if (resumed) "foreground" else "not_visible") }
    private val retry = Runnable {
        if (!closed && !resumed) {
            try { fallback = adbFallback?.invoke() }
            catch (_: RuntimeException) { finish("adb_failed") }
        }
    }

    fun start() {
        if (closed) return
        handler.postDelayed(timeout, 8_000)
        handler.postDelayed(retry, 2_000)
        runCatching(nativeLaunch)
    }

    fun onResumed() {
        if (closed) return
        resumed = true
        handler.removeCallbacks(stable)
        handler.postDelayed(stable, 1_000)
    }

    fun onPaused() {
        resumed = false
        handler.removeCallbacks(stable)
    }

    fun onUserLeft() { finish("user_left") }

    private fun finish(result: String) {
        if (closed) return
        closed = true
        handler.removeCallbacks(stable)
        handler.removeCallbacks(retry)
        handler.removeCallbacks(timeout)
        fallback?.close()
        onFinished(result)
    }
}
