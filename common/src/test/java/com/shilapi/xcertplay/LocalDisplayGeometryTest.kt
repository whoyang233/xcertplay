package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.CarPlayTouchMapper
import org.junit.Assert.*
import org.junit.Test

/** Geometry remains covered in the local-only APK, after removing the browser backend. */
class LocalDisplayGeometryTest {
    @Test fun touchCoordinatesFitWideCarPlayInsideTheLocalView() {
        assertArrayEquals(doubleArrayOf(0.0,75.0,800.0,450.0),
            CarPlayTouchMapper.contentRect(800,600,1920,1080),0.001)
        assertArrayEquals(doubleArrayOf(0.0,210.0,480.0,180.0),
            CarPlayTouchMapper.contentRect(480,600,1920,720),0.001)
    }

}
