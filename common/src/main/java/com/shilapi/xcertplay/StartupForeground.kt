package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Only initial app/boot launches enter this path; CarPlay reconnects never do. */
internal object StartupForeground {
    private const val TAG = "xcertplay-startup"
    private const val EXTRA_INTERNAL_LAUNCH = "com.shilapi.xcertplay.STARTUP_FOREGROUND_REQUEST"
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var attempt: StartupForegroundAttempt? = null
    private var resumedHost: Int? = null
    private var generation = 0
    private val statusState = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentStatus: kotlinx.coroutines.flow.StateFlow<String?> = statusState

    fun launchIntent(context: Context): Intent = Intent.makeMainActivity(
        ComponentName(context.packageName, CarPlayHostActivity::class.java.name),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_INTERNAL_LAUNCH, true)

    fun isLaunchIntent(intent: Intent): Boolean = !intent.getBooleanExtra(EXTRA_INTERNAL_LAUNCH, false) &&
        ((intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) ||
            intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)

    internal fun adbCommand(packageName: String): String {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]*")))
        val component = packageName + "/" + CarPlayHostActivity::class.java.name
        return "am start --user current -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-f 0x34200000 --ez $EXTRA_INTERNAL_LAUNCH true -n '$component'"
    }

    fun afterBoot(context: Context, finished: () -> Unit) = begin(context, native = true, finished = finished)
    fun observeLaunch(context: Context) = begin(context, native = false) {}

    private fun begin(context: Context, native: Boolean, finished: () -> Unit) {
        if (attempt != null) {
            // A USB launch may already be connecting when BOOT_COMPLETED arrives.
            // The boot request must still bring that existing task in front.
            if (native) runCatching { context.startActivity(launchIntent(context)) }
                .onFailure { Log.w(TAG, "Native foreground launch refused", it) }
            finished()
            return
        }
        val app = context.applicationContext
        val token = ++generation
        record(app, "requested")
        val adbEnabled = AirPlayPersistence.loadStartupForegroundAdb(app)
        val next = StartupForegroundAttempt(handler,
            nativeLaunch = { if (native) {
                runCatching { app.startActivity(launchIntent(app)) }
                    .onFailure { Log.w(TAG, "Native foreground launch refused", it) }
            } },
            adbFallback = if (adbEnabled) ({ startAdb(app, token) }) else null,
            onFinished = { result ->
                if (generation == token) {
                    attempt = null
                    val previous = status(app)
                    record(app, if (result == "not_visible" && previous.startsWith("adb_")) previous else result)
                }
                finished()
            })
        attempt = next
        next.start()
        if (resumedHost != null) next.onResumed()
    }

    fun onResumed(owner: Any) {
        resumedHost = System.identityHashCode(owner)
        attempt?.onResumed()
    }
    fun onPaused(owner: Any) {
        if (resumedHost == System.identityHashCode(owner)) {
            resumedHost = null
            attempt?.onPaused()
        }
    }
    fun onUserLeft() { attempt?.onUserLeft() }

    fun status(context: Context): String = context.getSharedPreferences("xcertplay_startup", Context.MODE_PRIVATE)
        .getString("status", "none").orEmpty()

    private fun record(context: Context, status: String) {
        context.getSharedPreferences("xcertplay_startup", Context.MODE_PRIVATE).edit().putString("status", status).apply()
        statusState.value = status
        Log.i(TAG, "startup foreground=$status")
    }

    private fun startAdb(context: Context, token: Int): Closeable {
        val cancelled = AtomicBoolean(false)
        val client = java.util.concurrent.atomic.AtomicReference<LocalAdb?>()
        Thread({
            try {
                if (cancelled.get()) return@Thread
                LocalAdb(AdbKeys.load(context)).use { adb ->
                    client.set(adb)
                    if (cancelled.get()) { adb.cancelPendingOperations(); return@use }
                    // Startup must never open the car's ADB authorization dialog.
                    val access = adb.connect(mayAsk = false)
                    val result = when (access) {
                        LocalAdb.Access.READY -> if (!cancelled.get() && adb.shell(adbCommand(context.packageName), 2_000) != null)
                            "adb_requested" else "adb_failed"
                        LocalAdb.Access.NOT_APPROVED -> "adb_not_approved"
                        LocalAdb.Access.UNSUPPORTED -> "adb_unsupported"
                        else -> "adb_unavailable"
                    }
                    handler.post { if (!cancelled.get() && generation == token && attempt != null) record(context, result) }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Startup ADB foreground request failed", error)
                handler.post { if (!cancelled.get() && generation == token && attempt != null) record(context, "adb_failed") }
            } finally { client.set(null) }
        }, "xcertplay-startup-foreground").apply { isDaemon = true; start() }
        return Closeable { cancelled.set(true); client.get()?.cancelPendingOperations() }
    }
}
