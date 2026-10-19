package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioStreamFallbackTest {
    @Test fun supportedVendorStreamIsKeptWithoutFallback() {
        val track = Any()
        assertSame(track, AudioStreamFallback.build({ track }, { true }, { fail("must not release usable track") },
            { error("must not fall back") }))
    }

    @Test fun rejectedStreamUsesFallback() {
        val track = Any()
        for (failure in listOf(IllegalArgumentException(), UnsupportedOperationException(), SecurityException())) {
            assertSame(track, AudioStreamFallback.build<Any>({ throw failure }, { true },
                { fail("no track to release") }, { track }))
        }
    }

    @Test fun uninitializedTrackIsReleasedBeforeFallback() {
        val candidate = Any()
        val fallback = Any()
        var released = false
        assertSame(fallback, AudioStreamFallback.build({ candidate }, { false }, {
            assertSame(candidate, it)
            released = true
        }, {
            assertTrue(released)
            fallback
        }))
    }
}
