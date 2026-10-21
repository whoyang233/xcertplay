package com.shilapi.xcertplay.ui.home

import com.shilapi.xcertplay.HandshakeTimeline
import com.shilapi.xcertplay.HandshakeTimeline.PhaseKind
import com.shilapi.xcertplay.HandshakeTimeline.State
import org.junit.Assert.*
import org.junit.Test

class ConnectionOverviewTest {
    @Test fun iap2ProgressUsesUserCopyWithoutChangingTheProtocolModel() {
        val timeline = HandshakeTimeline(true, 1)
        val phase = timeline.phases.first { it.kind == PhaseKind.IAP2 }
        phase.state = State.ACTIVE
        val revision = timeline.revision
        val view = ConnectionOverview.from(timeline, "Wireless CarPlay control running", false)
        assertEquals("home.wait.iap2.title", view.titleKey)
        assertEquals(1, view.stage)
        assertFalse(view.failed)
        assertFalse(view.done)
        assertEquals(State.ACTIVE, phase.state)
        assertEquals(revision, timeline.revision)
    }

    @Test fun completedHandshakeHasFullProgressButALaterFailureTakesPrecedence() {
        val timeline = HandshakeTimeline(true, 1)
        javaClass.classLoader!!.getResource("handshake-wireless.txt")!!.readText().lineSequence()
            .filter { it.length > 14 }.forEachIndexed { i, line -> timeline.accept(line.substring(14), 100L + i) }
        val completed = ConnectionOverview.from(timeline, "running", false)
        assertTrue(completed.done)
        assertEquals(1f, completed.fraction, 0f)
        timeline.fail("Disconnected", "Bluetooth connection closed", 20_000)
        val failed = ConnectionOverview.from(timeline, "running", false)
        assertFalse(failed.done)
        assertTrue(failed.failed)
        assertEquals("home.failure.bluetooth", failed.bodyKey)
    }

    @Test fun permissionAndRetryFailuresWorkBeforeATimelineExists() {
        assertEquals("home.failure.permission", ConnectionOverview.from(null, "Bluetooth permission not granted", false).bodyKey)
        val retry = ConnectionOverview.from(null, "transport closed", true)
        assertTrue(retry.failed)
        assertEquals("home.failure.generic", retry.bodyKey)
        assertEquals(0f, retry.fraction, 0f)
    }

    @Test fun wiredUsbAndWaitingVideoHaveDistinctStages() {
        val timeline = HandshakeTimeline(false, 1)
        val usb = timeline.phases.first { it.kind == PhaseKind.USB }
        usb.state = State.ACTIVE
        assertEquals("home.wait.usb.title", ConnectionOverview.from(timeline, "USB", false).titleKey)
        usb.state = State.DONE
        timeline.phases.first { it.kind == PhaseKind.STREAMS }.state = State.ACTIVE
        val view = ConnectionOverview.from(timeline, "stream", false)
        assertEquals(3, view.stage)
        assertTrue(view.fraction > 0f && view.fraction < 1f)
    }

    @Test fun runtimeErrorIsNotMisclassifiedAsATimeout() {
        val timeline = HandshakeTimeline(true, 1)
        timeline.fail("error", "RuntimeException", 2)
        assertEquals("home.failure.generic", ConnectionOverview.from(timeline, "", true).bodyKey)
    }
}
