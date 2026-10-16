// Adapted from shihabal3amri/DiPlay at 1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70 (GPL-3.0).
package com.shilapi.xcertplay.adb

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** An already approved local shell. Background use never offers a new key or opens a dialog. */
internal class LocalAdbShell(private val tag: String) {
    private var adb: LocalAdb? = null
    private var retryAtMillis = 0L
    @Volatile var lastAccess: LocalAdb.Access? = null
        private set

    @Synchronized
    fun run(context: Context, command: String, timeoutMillis: Int): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < retryAtMillis) return null
        val client = LocalAdb(AdbKeys.load(context)).also { adb = it }
        // Socket timeouts restart on every read. This deadline also bounds partial/dripping replies.
        val deadline = deadlines.schedule({ client.cancelPendingOperations() }, timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        try {
            val access = client.connect(mayAsk = false)
            lastAccess = access
            if (access != LocalAdb.Access.READY) {
                retryAtMillis = now + 30_000
                Log.w(tag, "Local ADB access $access")
                return null
            }
            return client.shell(command, timeoutMillis)
        } finally {
            deadline.cancel(false)
            client.close()
            adb = null
        }
    }

    @Synchronized fun close() { adb?.close(); adb = null }

    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "xcertplay-adb-deadline").apply { isDaemon = true }
        }
    }
}
