package com.shilapi.xcertplay.media

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE)
class CarPlayTouchSequenceTest {
    private val rect=CarPlayTouchMapper.contentRect(800,600,1920,1080)
    private fun contacts(sequence:CarPlayTouchSequence,action:Int,x:Float,y:Float)=
        MotionEvent.obtain(0,1,action,x,y,0).let { event -> try { sequence.contacts(event,rect) } finally { event.recycle() } }
    @Test fun gestureStartingInBlackBarNeverBecomesAnEdgeTouch() {
        val sequence=CarPlayTouchSequence()
        assertTrue(contacts(sequence,MotionEvent.ACTION_DOWN,400f,10f).isEmpty())
        assertTrue(contacts(sequence,MotionEvent.ACTION_MOVE,400f,300f).isEmpty())
        assertTrue(contacts(sequence,MotionEvent.ACTION_UP,400f,300f).isEmpty())
        assertEquals(0.5,contacts(sequence,MotionEvent.ACTION_DOWN,400f,300f).single().x,0.0001)
    }
    @Test fun gestureStartingInsideMayDragToAnEdgeAndAlwaysReleases() {
        val sequence=CarPlayTouchSequence()
        assertTrue(contacts(sequence,MotionEvent.ACTION_DOWN,400f,300f).single().down)
        assertEquals(0.0,contacts(sequence,MotionEvent.ACTION_MOVE,400f,0f).single().y,0.0001)
        assertFalse(contacts(sequence,MotionEvent.ACTION_CANCEL,400f,0f).single().down)
        assertTrue(contacts(sequence,MotionEvent.ACTION_MOVE,400f,300f).isEmpty())
    }
    @Test fun activityPauseResetCannotResumeAnOldGesture() {
        val sequence=CarPlayTouchSequence()
        contacts(sequence,MotionEvent.ACTION_DOWN,400f,300f)
        sequence.reset()
        assertTrue(contacts(sequence,MotionEvent.ACTION_MOVE,400f,300f).isEmpty())
    }
}
