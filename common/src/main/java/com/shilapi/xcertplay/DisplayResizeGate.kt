package com.shilapi.xcertplay

/** Invalidates queued layouts when the window leaves the foreground, even if it returns quickly. */
internal class DisplayResizeGate {
    private var resumed = false
    private var focused = false
    private var revision = 0

    fun setResumed(value: Boolean) {
        if (resumed != value) revision++
        resumed = value
    }

    fun setFocused(value: Boolean) {
        if (focused != value) revision++
        focused = value
    }

    fun ticket(): Int? = revision.takeIf { resumed && focused }

    fun accepts(candidate: Int?): Boolean = candidate != null && candidate == ticket()
}
