package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.iap2.message.Iap2PlaybackStatus.*
import org.junit.Assert.*
import org.junit.Test

class IphonePlaybackTransitionsTest {
    @Test fun repeatedMetadataAndSeekingDoNotReclaimFocus() {
        val transitions = IphonePlaybackTransitions()
        assertTrue(transitions.resumed(PLAYING))
        repeat(10) { assertFalse(transitions.resumed(PLAYING)) }
        assertFalse(transitions.resumed(SEEKING_FORWARD))
        assertFalse(transitions.resumed(SEEKING_BACKWARD))
        assertFalse(transitions.resumed(PLAYING))
    }
    @Test fun aFreshPlayAfterAnInterruptionCanRecoverFocus() {
        val transitions = IphonePlaybackTransitions()
        assertTrue(transitions.resumed(PLAYING))
        assertFalse(transitions.resumed(PAUSED))
        assertTrue(transitions.resumed(PLAYING))
        assertFalse(transitions.resumed(STOPPED))
        assertTrue(transitions.resumed(PLAYING))
    }
}
