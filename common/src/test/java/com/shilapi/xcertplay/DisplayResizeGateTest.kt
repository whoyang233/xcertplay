package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class DisplayResizeGateTest {
    @Test fun startupWaitsForBothResumeAndWindowFocus() {
        val gate = DisplayResizeGate()
        assertNull(gate.ticket())
        gate.setResumed(true)
        assertNull(gate.ticket())
        gate.setFocused(true)
        assertTrue(gate.accepts(gate.ticket()))
    }

    @Test fun quickAppSwitchCannotApplyTheOldQueuedLayoutAfterReturning() {
        val gate = foreground()
        val queuedBeforeSwitch = gate.ticket()
        gate.setFocused(false)
        gate.setResumed(false)
        assertNull(gate.ticket())
        gate.setResumed(true)
        gate.setFocused(true)
        assertFalse(gate.accepts(queuedBeforeSwitch))
        assertTrue(gate.accepts(gate.ticket()))
    }

    @Test fun losingFocusAloneInvalidatesTheResizeDuringTaskSwitchAnimation() {
        val gate = foreground()
        val queued = gate.ticket()
        gate.setFocused(false)
        assertFalse(gate.accepts(queued))
        gate.setFocused(true)
        assertFalse(gate.accepts(queued))
    }

    @Test fun pauseInvalidatesResizeEvenWhenFirmwareDoesNotReportFocusLoss() {
        val gate = foreground()
        val queued = gate.ticket()
        gate.setResumed(false)
        gate.setResumed(true)
        assertFalse(gate.accepts(queued))
    }

    @Test fun repeatedForegroundCallbacksDoNotDiscardARealResize() {
        val gate = foreground()
        val queued = gate.ticket()
        gate.setResumed(true)
        gate.setFocused(true)
        assertTrue(gate.accepts(queued))
    }

    private fun foreground() = DisplayResizeGate().apply {
        setResumed(true)
        setFocused(true)
    }
}
