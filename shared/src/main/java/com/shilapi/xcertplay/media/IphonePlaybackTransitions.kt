package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.iap2.message.Iap2PlaybackStatus

/** Metadata refreshes while playing must not compete with another car audio source for focus. */
internal class IphonePlaybackTransitions {
    private var playing = false
    @Synchronized fun resumed(status: Iap2PlaybackStatus): Boolean {
        val active = status == Iap2PlaybackStatus.PLAYING || status == Iap2PlaybackStatus.SEEKING_FORWARD ||
            status == Iap2PlaybackStatus.SEEKING_BACKWARD
        val resumed = active && !playing
        playing = active
        return resumed
    }
}
