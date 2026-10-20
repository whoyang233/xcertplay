package com.shilapi.xcertplay

/** A delayed retry must not tear down a newly recovered session, or ignore a second loss. */
internal class SessionRecoveryGate {
    private var revision = 0L
    private var active = false
    fun connected() { revision++; active = true }
    fun disconnected() { active = false }
    fun checkpoint(): Long = revision
    fun recoveredSince(checkpoint: Long): Boolean = active && revision > checkpoint
}
