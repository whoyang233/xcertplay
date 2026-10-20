package com.shilapi.xcertplay

import android.content.Context
import android.os.Build

internal object BydCallPopupAvailability {
    fun supported(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 && runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo("com.byd.bluetoothcall", 0)
        true
    }.getOrDefault(false)
}
