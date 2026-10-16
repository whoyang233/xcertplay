package com.shilapi.xcertplay.network

import java.io.IOException
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class WifiChannelPolicyTest {
    @Test fun policiesRequestOnlyKnownNonDfsChannels() {
        assertEquals(0, WifiChannelPolicy.SYSTEM_5_GHZ.requestedChannel(36))
        assertEquals(36, WifiChannelPolicy.PREFER_LOW_5_GHZ.requestedChannel(149))
        assertEquals(149, WifiChannelPolicy.PREFER_HIGH_5_GHZ.requestedChannel(36))
        WifiChannelPolicy.fixedChannels.forEach {
            assertEquals(it, WifiChannelPolicy.FIXED_5_GHZ.requestedChannel(it))
        }
        listOf(0, 6, 14, 52, 100, 144, 177, -1).forEach {
            assertThrows(IllegalArgumentException::class.java) { WifiChannelPolicy.FIXED_5_GHZ.requestedChannel(it) }
        }
    }

    @Test fun successfulRequestIsNotRetriedOrMisreported() {
        val calls = mutableListOf<Int>()
        val observed = startWithWifiChannelFallback(149, true, { false }, {}) {
            calls += it
            36 // Firmware may override; preserve its observation.
        }
        assertEquals(36, observed)
        assertEquals(listOf(149), calls)
    }

    @Test fun rejectedRequestFallsBackOnceToSystemFiveGhz() {
        val calls = mutableListOf<Int>()
        val messages = mutableListOf<String>()
        val result = startWithWifiChannelFallback(149, true, { false }, messages::add) {
            calls += it
            if (it != 0) throw IOException("startup", WifiChannelRejectedException("unsupported channel"))
            "connected"
        }
        assertEquals("connected", result)
        assertEquals(listOf(149, 0), calls)
        assertEquals(1, messages.size)
    }

    @Test fun disabledFallbackAndAutomaticModeDoNotRetry() {
        for ((channel, enabled) in listOf(149 to false, 0 to true)) {
            var calls = 0
            assertThrows(WifiChannelRejectedException::class.java) {
                startWithWifiChannelFallback(channel, enabled, { false }, {}) {
                    calls++
                    throw WifiChannelRejectedException("rejected")
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test fun timeoutPermissionAndInterruptionNeverTriggerChannelFallback() {
        listOf(IOException("timeout"), IOException("denied", SecurityException()),
            InterruptedIOException("cancelled"), IOException("interrupted", InterruptedException())).forEach { error ->
            var calls = 0
            assertSame(error, assertThrows(IOException::class.java) {
                startWithWifiChannelFallback(36, true, { false }, {}) { calls++; throw error }
            })
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationBeforeOrDuringRejectionPreventsAnotherGroup() {
        var calls = 0
        assertThrows(IOException::class.java) {
            startWithWifiChannelFallback(36, true, { true }, {}) { calls++ }
        }
        assertEquals(0, calls)
        var cancelled = false
        assertThrows(IOException::class.java) {
            startWithWifiChannelFallback(36, true, { cancelled }, {}) {
                calls++; cancelled = true; throw WifiChannelRejectedException("rejected")
            }
        }
        assertEquals(1, calls)
    }

    @Test fun failedAutomaticFallbackIsNotLooped() {
        val calls = mutableListOf<Int>()
        assertThrows(IOException::class.java) {
            startWithWifiChannelFallback(36, true, { false }, {}) {
                calls += it
                throw WifiChannelRejectedException("failed")
            }
        }
        assertEquals(listOf(36, 0), calls)
    }

    @Test fun aChannelRejectionDoesNotPowerCycleTheRadio() {
        var recovered = false
        var attempts = 0
        assertThrows(IOException::class.java) {
            startHotspotWithRecovery(true, { false }, { recovered = true }) {
                attempts++
                throw IOException("startup", WifiChannelRejectedException("unsupported"))
            }
        }
        assertEquals(1, attempts)
        assertFalse(recovered)
    }
}
