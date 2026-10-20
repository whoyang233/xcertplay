package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class SessionRecoveryGateTest {
    @Test fun retryIsCancelledOnlyAfterANewHealthySession() {
        val gate = SessionRecoveryGate()
        gate.connected()
        val checkpoint = gate.checkpoint()
        assertFalse(gate.recoveredSince(checkpoint))
        gate.connected()
        assertTrue(gate.recoveredSince(checkpoint))
    }
    @Test fun aSecondLossMustStillReconnect() {
        val gate = SessionRecoveryGate()
        val checkpoint = gate.checkpoint()
        gate.connected()
        gate.disconnected()
        assertFalse(gate.recoveredSince(checkpoint))
        gate.connected()
        assertTrue(gate.recoveredSince(checkpoint))
    }
}
