package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the CarPlay host after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AirPlayPersistence.loadAutoStartOnBoot(context)) return

        val pending = goAsync()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish = { if (completed.compareAndSet(false, true)) pending?.finish(); Unit }
        try {
            StartupForeground.afterBoot(context, finish)
        } catch (error: RuntimeException) {
            finish()
            Log.w(TAG, "Boot auto-start could not launch CarPlayHostActivity", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
