package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlayPhysicalSizeBasis
import org.junit.Assert.*
import org.junit.Test

class DisplayStartupPolicyTest {
    @Test fun startupCannotUseTheFullHeightBeforeSystemBarInsetsSettle() {
        val stability=DisplayLayoutStability()
        stability.observe(1920,1080,1,0)
        assertFalse(stability.ready(1920,1080,1,499))
        stability.observe(1920,978,1,400)
        assertFalse(stability.ready(1920,1080,1,500))
        assertFalse(stability.ready(1920,978,1,899))
        assertTrue(stability.ready(1920,978,1,900))
    }
    @Test fun unchangedCallbacksDoNotKeepPostponingStartup() {
        val stability=DisplayLayoutStability()
        for (time in 0L..450L step 50) stability.observe(1920,1080,1,time)
        assertEquals(50L,stability.remaining(1920,1080,1,450))
        assertTrue(stability.ready(1920,1080,1,500))
    }
    @Test fun quickAppSwitchInvalidatesOldLayoutEvenWhenSizeReturnsUnchanged() {
        val gate=DisplayResizeGate().apply { setResumed(true);setFocused(true) }
        val stability=DisplayLayoutStability();val old=gate.ticket()!!
        stability.observe(1920,978,old,0)
        gate.setFocused(false);stability.invalidate();gate.setFocused(true)
        assertFalse(stability.ready(1920,978,old,1000))
        val next=gate.ticket()!!;stability.observe(1920,978,next,1000)
        assertFalse(stability.ready(1920,978,next,1499))
        assertTrue(stability.ready(1920,978,next,1500))
    }
    @Test fun rotationAndClockRollbackNeedAnewStableObservation() {
        val stability=DisplayLayoutStability();stability.observe(1920,1080,1,1000)
        assertFalse(stability.ready(1920,1080,null,2000))
        assertFalse(stability.ready(1920,1080,1,999))
        stability.observe(1080,1920,1,2000)
        assertFalse(stability.ready(1920,1080,1,3000))
        assertTrue(stability.ready(1080,1920,1,2500))
    }
    @Test fun portraitAndLandscapeUseTheSamePhysicalDeviceEdges() {
        val landscape=DisplaySizingPolicy.physicalSize(1920,1080,1920,1080,200,AirPlayPhysicalSizeBasis.WIDTH)
        val portrait=DisplaySizingPolicy.physicalSize(1080,1920,1080,1920,200,AirPlayPhysicalSizeBasis.WIDTH)
        assertEquals(200,landscape.widthMm);assertEquals(113,landscape.heightMm)
        assertEquals(113,portrait.widthMm);assertEquals(200,portrait.heightMm)
        val byHeight=DisplaySizingPolicy.physicalSize(1080,1920,1080,1920,200,AirPlayPhysicalSizeBasis.HEIGHT)
        assertEquals(200,byHeight.widthMm);assertEquals(356,byHeight.heightMm)
    }
    @Test fun fullScreenReferenceDoesNotDependOnPreviouslyObservedSmallWindow() {
        assertEquals(1920 to 1080,DisplaySizingPolicy.reference(960,978,1920,1080))
        val split=DisplaySizingPolicy.physicalSize(960,978,1920,1080,200,AirPlayPhysicalSizeBasis.HEIGHT)
        assertEquals(178,split.widthMm);assertEquals(181,split.heightMm)
        assertEquals(1080 to 1920,DisplaySizingPolicy.reference(1080,1800,1920,1080))
        val first=DisplaySizingPolicy.physicalSize(1920,978,1920,1080,200,AirPlayPhysicalSizeBasis.HEIGHT)
        val again=DisplaySizingPolicy.physicalSize(1920,978,1080,1920,200,AirPlayPhysicalSizeBasis.HEIGHT)
        assertEquals(first,again);assertEquals(181,first.heightMm)
    }
    @Test fun invalidOemMetricsUseARealWindowPairWithoutIndependentMaxima() {
        assertEquals(1920 to 1080,DisplaySizingPolicy.reference(1920,1080,0,0))
        assertEquals(1920 to 1080,DisplaySizingPolicy.reference(1920,1080,1280,1200))
        assertEquals(1280 to 1200,DisplaySizingPolicy.reference(1280,1200,1920,1080))
    }
    @Test fun oldSafeAreaSizesFromAnotherDisplayCannotEnlargeTheNegotiatedStream() {
        val saved=listOf(1920 to 1080,1080 to 1920,1920 to 978,2560 to 1440,1920 to 1920,-1 to 720)
        assertEquals(saved.take(3),DisplaySizingPolicy.compatibleSavedSizes(saved,1920 to 1080))
    }
}
