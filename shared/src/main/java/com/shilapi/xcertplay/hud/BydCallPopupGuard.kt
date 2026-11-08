// Adapted from shihabal3amri/DiPlay 6d3b8a8ce927cb4b115b8c3881f17619433e93b1 (GPL-3.0).
package com.shilapi.xcertplay.hud

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/** Owns only the current connected foreground host; all ADB work stays off the UI thread. */
object BydCallPopupGuard {
    private val gate = java.lang.Object()
    private val worker = Executors.newSingleThreadExecutor {
        Thread(it, "diplay-call-popup").apply { isDaemon = true }
    }
    private var owner: Any? = null
    private var sessionReady: () -> Boolean = { false }
    private var app: Context? = null
    private var generation = 0L
    private var attempted = -1L
    private var running = false
    private var leased = false
    private var adb: LocalAdb? = null
    @Volatile var status = "OFF"
        private set(value) {
            if (field != value) {
                field = value
                android.util.Log.i("xcertplay-call-popup", "state=$value")
            }
        }
    @Volatile var lastFailure: String? = null
        private set

    fun update(context: Context, candidate: Any, foregroundConnected: Boolean,
        connected: () -> Boolean) = synchronized(gate) {
        if (!foregroundConnected && owner !== candidate) return@synchronized
        if (foregroundConnected && owner === candidate) return@synchronized
        owner = if (foregroundConnected) candidate else null
        sessionReady = if (foregroundConnected) connected else ({ false })
        app = context.applicationContext
        generation++
        gate.notifyAll()
        // START may already have written IGNORE before its reply. Keep that socket for STOP.
        if (!leased) adb?.cancelPendingOperations()
        if (!running) {
            running = true
            worker.execute(::drain)
        }
    }

    private fun current(value: Long) = synchronized(gate) {
        if (owner == null || generation != value) false
        else if (sessionReady()) true
        else { owner = null; sessionReady = { false }; generation++; false }
    }

    private fun drain() {
        var retryGeneration = -1L
        var retries = 0
        while (true) {
            val selected = synchronized(gate) {
                if (owner == null || attempted == generation) { running = false; return }
                attempted = generation
                generation to app!!
            }
            val (value, context) = selected
            if (retryGeneration != value) { retryGeneration = value; retries = 0 }
            var failure: String? = null
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { status = "UNSUPPORTED"; continue }
                val client = LocalAdb(AdbKeys.load(context))
                client.use {
                    synchronized(gate) {
                        if (!current(value)) return@use
                        adb = client
                    }
                    status = "CONNECTING"
                    val access = client.connect(mayAsk = false)
                    if (access != LocalAdb.Access.READY) throw IOException(when (access) {
                        LocalAdb.Access.NOT_APPROVED -> "ADB_NOT_APPROVED"
                        LocalAdb.Access.UNSUPPORTED -> "ADB_UNSUPPORTED"
                        else -> "ADB_UNREACHABLE"
                    })
                    status = "STARTING"
                    val channel = BydCallPopupChannel.prepare(context.applicationInfo.sourceDir, client::shell)
                    channel.use {
                        runPrepared(value, channel)
                    }
                }
            } catch (error: Exception) {
                // A lost reply is not proof of restoration. The detached helper still owns recovery.
                val code = error.message?.takeIf { it in FAILURE_CODES } ?: "ADB_CONNECTION_LOST"
                failure = code
                status = code
                lastFailure = code
            } finally {
                synchronized(gate) { adb = null; leased = false }
            }
            val delay = failure?.let { CallPopupRetry.delayMillis(it, retries) }
            if (delay != null) synchronized(gate) {
                if (current(value)) {
                    retries++
                    status = "RETRYING"
                    val deadline = System.nanoTime() + delay * 1_000_000L
                    while (current(value)) {
                        val remaining = deadline - System.nanoTime()
                        if (remaining <= 0) break
                        java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(gate, remaining)
                    }
                    if (current(value)) attempted = -1L
                }
            }
        }
    }

    internal fun runPrepared(value: Long, channel: BydCallPopupChannel) {
        synchronized(gate) { leased = true }
        runSession(channel, { current(value) }, {
            synchronized(gate) { if (current(value)) gate.wait(2_000) }
        }) {
            status = it
            if (it == "HIDDEN" || it == "ALREADY_HIDDEN") lastFailure = null
            else if (it == "HIDE_FAILED") lastFailure = it
        }
    }

    internal fun runSession(channel: BydCallPopupChannel, current: () -> Boolean,
        waitHeartbeat: () -> Unit, report: (String) -> Unit) {
        var terminal = false
        try {
            if (!current()) return
            var reply = channel.exchange("START")
            while (true) {
                report(reply)
                if (reply in BydCallPopupChannel.TERMINAL) { terminal = true; return }
                if (reply != "HIDDEN") throw IOException("HELPER_STATE_UNKNOWN")
                if (!current()) break
                waitHeartbeat()
                if (!current()) break
                reply = channel.exchange("PING")
            }
        } finally {
            if (!terminal) {
                val restored = channel.exchange("STOP")
                if (restored !in BydCallPopupChannel.TERMINAL) throw IOException("RESTORE_UNCONFIRMED")
                report(restored)
            }
        }
    }

    private val FAILURE_CODES = setOf("ADB_UNREACHABLE", "ADB_NOT_APPROVED", "ADB_UNSUPPORTED",
        "ADB_CONNECTION_LOST", "HELPER_BUSY", "HELPER_NO_NOHUP", "HELPER_NOT_READY",
        "HELPER_CONTROL_FAILED", "HELPER_TIMEOUT", "RESTORE_UNCONFIRMED", "INVALID_STATUS",
        "ERROR QUERY_FAILED", "ERROR HELPER_FAILED", "ERROR RESTORE_FAILED")
}

internal class BydCallPopupChannel private constructor(
    private val base: String,
    private val shell: (String) -> String?,
) : Closeable {
    private var sequence = 0L
    private var terminal = false
    fun exchange(command: String): String {
        require(command in setOf("START", "PING", "STOP"))
        val next = ++sequence
        val write = "umask 077; test ! -L $base.control.tmp && " +
            "printf '%s\\n' '$next $command' > $base.control.tmp && mv $base.control.tmp $base.control && echo OK"
        if (shell(write)?.trim() != "OK") throw IOException("HELPER_CONTROL_FAILED")
        val deadline = System.nanoTime() + if (command == "STOP") 15_000_000_000L else 5_000_000_000L
        do {
            val parts = shell("cat $base.status")?.trim()?.split(' ', limit = 2) ?: throw IOException("ADB_CONNECTION_LOST")
            val ack = parts.firstOrNull()?.toLongOrNull()
            val state = parts.getOrNull(1)
            if (ack == null || ack < 0 || ack > next) throw IOException("INVALID_STATUS")
            if (state in TERMINAL) { terminal = true; return state!! }
            if (state?.startsWith("ERROR") == true) throw IOException(state)
            if (ack == next && state == "HIDDEN") return state
            if (state !in setOf("READY", "HIDDEN")) throw IOException("INVALID_STATUS")
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        throw IOException("HELPER_TIMEOUT")
    }

    override fun close() {
        // Keep recovery evidence when STOP was not confirmed.
        if (terminal) runCatching { shell("rm -f $base.control $base.control.tmp $base.status $base.status.tmp") }
    }

    companion object {
        val TERMINAL = setOf("RESTORED", "ALREADY_HIDDEN", "EXTERNAL_CHANGE", "CANCELLED", "HIDE_FAILED")
        fun prepare(apk: String, shell: (String) -> String?): BydCallPopupChannel {
            val token = UUID.randomUUID().toString().replace("-", "")
            val base = "${BydCallPopupTool.DIRECTORY}/$token"
            val quotedApk = "'" + apk.replace("'", "'\"'\"'") + "'"
            val launch = "umask 077; if test ! -x /system/bin/nohup; then echo HELPER_NO_NOHUP; exit 1; fi; " +
                "session=; if test -x /system/bin/setsid; then session=/system/bin/setsid; fi; " +
                "test ! -e $base.status && test ! -L $base.status && " +
                "{ CLASSPATH=$quotedApk /system/bin/nohup \$session /system/bin/app_process /system/bin " +
                "${BydCallPopupTool::class.java.name} $token </dev/null >/dev/null 2>&1 & " +
                "for n in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do " +
                "if test -s $base.status; then cat $base.status; exit 0; fi; sleep 0.1; done; echo ERROR_START; }"
            val reply = shell(launch)?.trim() ?: throw IOException("ADB_CONNECTION_LOST")
            if (reply != "0 READY") throw IOException(when {
                reply == "HELPER_NO_NOHUP" -> reply
                reply.endsWith("ERROR BUSY") -> "HELPER_BUSY"
                reply.endsWith("ERROR HELPER_FAILED") -> "ERROR HELPER_FAILED"
                else -> "HELPER_NOT_READY"
            })
            return BydCallPopupChannel(base, shell)
        }
    }
}
