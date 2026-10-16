package com.shilapi.xcertplay.network

import java.io.IOException

/** Requests apply only to a new Wi-Fi Direct group; the firmware enforces its regulatory domain. */
enum class WifiChannelPolicy {
    SYSTEM_5_GHZ, PREFER_LOW_5_GHZ, PREFER_HIGH_5_GHZ, FIXED_5_GHZ;

    fun requestedChannel(fixedChannel: Int): Int = when (this) {
        SYSTEM_5_GHZ -> 0
        PREFER_LOW_5_GHZ -> 36
        PREFER_HIGH_5_GHZ -> 149
        FIXED_5_GHZ -> fixedChannel.also { require(it in fixedChannels) { "Unsupported fixed 5 GHz channel" } }
    }

    companion object {
        // Avoid DFS channels: radar detection and channel evacuation can interrupt CarPlay.
        val fixedChannels: List<Int> = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
    }
}

/** Only an explicit platform rejection is eligible for channel fallback, not a timeout/cancel. */
internal class WifiChannelRejectedException(message: String) : IOException(message)

internal fun <T> startWithWifiChannelFallback(
    requestedChannel: Int,
    fallbackEnabled: Boolean,
    isCancelled: () -> Boolean,
    report: (String) -> Unit,
    start: (Int) -> T,
): T {
    if (isCancelled()) throw IOException("Wireless startup cancelled")
    return try {
        start(requestedChannel)
    } catch (failure: IOException) {
        var cause: Throwable? = failure
        var rejected = false
        while (cause != null) {
            if (cause is WifiChannelRejectedException) rejected = true
            if (cause is InterruptedException || cause is SecurityException) throw failure
            cause = cause.cause
        }
        if (requestedChannel == 0 || !fallbackEnabled || !rejected || isCancelled() || Thread.currentThread().isInterrupted) {
            throw failure
        }
        report("Wi-Fi Direct channel $requestedChannel rejected; retrying once with system-selected 5 GHz")
        start(0)
    }
}
