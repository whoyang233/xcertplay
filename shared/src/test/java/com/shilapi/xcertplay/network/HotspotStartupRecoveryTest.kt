package com.shilapi.xcertplay.network

import java.io.IOException
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class HotspotStartupRecoveryTest {
    @Test fun successfulStartupDoesNotTouchTheRadio() {
        var resets = 0
        assertEquals("ready", startHotspotWithRecovery(true, { false }, { resets++ }) { "ready" })
        assertEquals(0, resets)
    }

    @Test fun failureIsFollowedByRecoveryAndOneFreshStart() {
        val events = mutableListOf<String>()
        var starts = 0
        val result = startHotspotWithRecovery(true, { false }, { events += "reset" }) {
            events += "start"
            if (starts++ == 0) {
                events += "close failed hotspot"
                throw IOException("busy")
            }
            "ready"
        }
        assertEquals("ready", result)
        assertEquals(listOf("start", "close failed hotspot", "reset", "start"), events)
    }

    @Test fun secondFailureKeepsTheFirstFailureAndStopsRetrying() {
        var starts = 0
        val first = IOException("first")
        val last = IOException("last")
        val thrown = assertThrows(IOException::class.java) {
            startHotspotWithRecovery(true, { false }, {}) {
                if (starts++ == 0) throw first else throw last
            }
        }
        assertSame(last, thrown)
        assertArrayEquals(arrayOf(first), thrown.suppressed)
        assertEquals(2, starts)
    }

    @Test fun recoveryDisabledDoesNotRetryOrReset() {
        var resets = 0
        var starts = 0
        assertThrows(IOException::class.java) {
            startHotspotWithRecovery(false, { false }, { resets++ }) {
                starts++
                throw IOException("manual hotspot or recovery disabled")
            }
        }
        assertEquals(1, starts)
        assertEquals(0, resets)
    }

    @Test fun permissionFailureDoesNotResetTheRadio() {
        var resets = 0
        assertThrows(IOException::class.java) {
            startHotspotWithRecovery(true, { false }, { resets++ }) {
                throw IOException("permission denied", SecurityException())
            }
        }
        assertEquals(0, resets)
    }

    @Test fun wrappedInterruptionDoesNotResetTheRadio() {
        var resets = 0
        assertThrows(IOException::class.java) {
            startHotspotWithRecovery(true, { false }, { resets++ }) {
                throw IOException("cancelled", InterruptedException())
            }
        }
        assertEquals(0, resets)
    }

    @Test fun cancellationDuringRecoveryPreventsTheSecondStart() {
        var cancelled = false
        var starts = 0
        assertThrows(InterruptedIOException::class.java) {
            startHotspotWithRecovery(true, { cancelled }, { cancelled = true }) {
                starts++
                throw IOException("busy")
            }
        }
        assertEquals(1, starts)
    }

    @Test fun cancellationBeforeStartupDoesNotCreateAHotspot() {
        var starts = 0
        assertThrows(InterruptedIOException::class.java) {
            startHotspotWithRecovery(true, { true }, {}) { starts++ }
        }
        assertEquals(0, starts)
    }
}
