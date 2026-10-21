package com.shilapi.xcertplay.ui.home

import com.shilapi.xcertplay.HandshakeTimeline
import com.shilapi.xcertplay.HandshakeTimeline.PhaseKind
import com.shilapi.xcertplay.HandshakeTimeline.State

/** UI-only copy and progress; it never changes the connection or retry state machine. */
internal data class ConnectionOverview(
    val titleKey: String, val bodyKey: String, val stage: Int,
    val fraction: Float, val failed: Boolean, val done: Boolean,
) {
    companion object {
        fun from(timeline: HandshakeTimeline?, status: String, manualRetry: Boolean): ConnectionOverview {
            val lowerStatus = status.lowercase(java.util.Locale.ROOT)
            val failed = timeline?.failure != null || manualRetry || "denied" in lowerStatus || "not granted" in lowerStatus
            val done = !failed && (timeline?.completedAtMillis ?: 0L) > 0
            val latest = timeline?.latestGroup?.phase
            val phase = if (failed) timeline?.phases?.firstOrNull { it.state == State.FAILED } ?: latest
                else latest?.takeIf { it.state == State.ACTIVE } ?: timeline?.phases?.lastOrNull { it.state == State.ACTIVE } ?: latest
            val kind = phase?.kind ?: PhaseKind.MFI
            val stage = when (kind) {
                PhaseKind.MFI, PhaseKind.HOTSPOT, PhaseKind.ADVERTISE -> 0
                PhaseKind.USB, PhaseKind.USB_DATA, PhaseKind.LOCKDOWN, PhaseKind.NETWORK,
                PhaseKind.BLUETOOTH, PhaseKind.IAP2 -> 1
                PhaseKind.PAIR, PhaseKind.SESSION, PhaseKind.HANDOFF -> 2
                PhaseKind.STREAMS -> 3
            }
            val text = (timeline?.failure?.detail ?: status).lowercase(java.util.Locale.ROOT)
            val reason = when {
                "permission" in text || "denied" in text || "not granted" in text -> "permission"
                "mfi" in text || "certificate" in text || "coprocessor" in text -> "authentication"
                "hotspot" in text || "wi-fi" in text -> "network"
                "bluetooth" in text || "bonded" in text -> "bluetooth"
                "usb" in text -> "usb"
                "timeout" in text || "timed out" in text -> "timeout"
                else -> "generic"
            }
            val stem = "home.wait.${kind.name.lowercase(java.util.Locale.ROOT)}"
            val count = timeline?.phases?.count { it.state == State.DONE || it.state == State.SKIPPED } ?: 0
            return ConnectionOverview(
                when { done -> "home.wait.done.title"; failed -> "home.wait.failed.title"; else -> "$stem.title" },
                when { done -> "home.wait.done.body"; failed -> "home.failure.$reason"; else -> "$stem.body" },
                stage, if (done) 1f else count.toFloat() / (timeline?.phases?.size ?: 1).coerceAtLeast(1), failed, done,
            )
        }
    }
}
