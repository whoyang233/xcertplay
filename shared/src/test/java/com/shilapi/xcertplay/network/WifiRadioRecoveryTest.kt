package com.shilapi.xcertplay.network

import java.io.IOException
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class WifiRadioRecoveryTest {
    private class Radio(var current: WifiRadioState = WifiRadioState.ENABLED) : WifiRadio {
        val requests = mutableListOf<Boolean>()
        var accepted = true
        var onSwitch: (Boolean) -> Unit = { current = if (it) WifiRadioState.ENABLED else WifiRadioState.DISABLED }
        override fun state() = current
        override fun setEnabled(enabled: Boolean): Boolean {
            requests += enabled
            val allowed = accepted
            if (allowed) onSwitch(enabled)
            return allowed
        }
    }

    private class Clock {
        var now = 0L
        var onWait: () -> Unit = {}
        fun advance(millis: Long) { now += millis; onWait() }
    }

    private fun recovery(radio: Radio, clock: Clock = Clock(), cancelled: () -> Boolean = { false }) =
        WifiRadioRecovery(radio, cancelled, {}, { clock.now }, clock::advance)

    @Test fun enabledRadioIsSwitchedOffThenBackOn() {
        val radio = Radio()
        recovery(radio).recover()
        assertEquals(listOf(false, true), radio.requests)
        assertEquals(WifiRadioState.ENABLED, radio.current)
    }

    @Test fun disabledRadioIsOnlyEnabled() {
        val radio = Radio(WifiRadioState.DISABLED)
        recovery(radio).recover()
        assertEquals(listOf(true), radio.requests)
    }

    @Test fun androidRejectingTheDisableStillAllowsHotspotRecreation() {
        val radio = Radio().apply { accepted = false }
        recovery(radio).recover()
        assertEquals(listOf(false), radio.requests)
        assertEquals(WifiRadioState.ENABLED, radio.current)
    }

    @Test fun permissionExceptionIsHandledWithoutPretendingTheRadioWasReset() {
        val radio = Radio().apply { onSwitch = { throw SecurityException("not permitted") } }
        recovery(radio).recover()
        assertEquals(listOf(false), radio.requests)
    }

    @Test fun cancellingAfterDisableRestoresWifiBeforeLeaving() {
        var cancelled = false
        val radio = Radio().apply {
            onSwitch = { enabled ->
                current = if (enabled) WifiRadioState.ENABLED else WifiRadioState.DISABLED
                if (!enabled) cancelled = true
            }
        }
        assertThrows(InterruptedIOException::class.java) { recovery(radio, cancelled = { cancelled }).recover() }
        assertEquals(listOf(false, true), radio.requests)
        assertEquals(WifiRadioState.ENABLED, radio.current)
    }

    @Test fun disableTimeoutIsBoundedAndStillRequestsEnable() {
        val clock = Clock()
        val radio = Radio().apply {
            onSwitch = { current = if (it) WifiRadioState.ENABLED else WifiRadioState.DISABLING }
        }
        assertThrows(IOException::class.java) { recovery(radio, clock).recover() }
        assertEquals(5_000L, clock.now)
        assertEquals(listOf(false, true), radio.requests)
    }

    @Test fun interruptionWhileWaitingForDisableStillRestoresWifi() {
        val clock = Clock().apply { onWait = { throw InterruptedException() } }
        val radio = Radio().apply {
            onSwitch = { current = if (it) WifiRadioState.ENABLED else WifiRadioState.DISABLING }
        }
        try {
            assertThrows(InterruptedIOException::class.java) { recovery(radio, clock).recover() }
            assertEquals(WifiRadioState.ENABLED, radio.current)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun teardownInterruptionDuringRestoreDoesNotAbandonTheEnableWait() {
        val clock = Clock()
        val radio = Radio().apply {
            onSwitch = { current = if (it) WifiRadioState.ENABLING else WifiRadioState.DISABLED }
        }
        var waits = 0
        clock.onWait = {
            if (waits++ == 0) throw InterruptedException()
            radio.current = WifiRadioState.ENABLED
        }
        try {
            assertThrows(InterruptedIOException::class.java) { recovery(radio, clock).recover() }
            assertEquals(WifiRadioState.ENABLED, radio.current)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun userDisablingWifiAndUnknownStateAreNotOverridden() {
        for (state in listOf(WifiRadioState.DISABLING, WifiRadioState.UNKNOWN)) {
            val radio = Radio(state)
            recovery(radio).recover()
            assertTrue(radio.requests.isEmpty())
        }
    }

    @Test fun enableRejectedAfterDisableIsReportedAsFailure() {
        val radio = Radio().apply {
            onSwitch = { current = WifiRadioState.DISABLED; accepted = false }
        }
        assertThrows(IOException::class.java) { recovery(radio).recover() }
        assertEquals(listOf(false, true), radio.requests)
    }
}
