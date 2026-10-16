package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager

/** Android 10+ usually rejects this for ordinary apps; supported head-unit firmware may allow it. */
internal class AndroidWifiRadio(context: Context) : WifiRadio {
    private val manager = context.applicationContext.getSystemService(WifiManager::class.java)

    override fun state(): WifiRadioState = when (manager?.wifiState) {
        WifiManager.WIFI_STATE_ENABLED -> WifiRadioState.ENABLED
        WifiManager.WIFI_STATE_DISABLED -> WifiRadioState.DISABLED
        WifiManager.WIFI_STATE_ENABLING -> WifiRadioState.ENABLING
        WifiManager.WIFI_STATE_DISABLING -> WifiRadioState.DISABLING
        else -> WifiRadioState.UNKNOWN
    }

    @Suppress("DEPRECATION")
    override fun setEnabled(enabled: Boolean): Boolean = manager?.setWifiEnabled(enabled) == true
}
