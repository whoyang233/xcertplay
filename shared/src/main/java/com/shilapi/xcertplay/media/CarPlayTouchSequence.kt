package com.shilapi.xcertplay.media

import android.view.MotionEvent
import com.shilapi.xcertplay.airplay.AirPlayContact

/** A gesture that starts in a letterbox bar must not become a touch on a later MOVE. */
class CarPlayTouchSequence {
    private var startedInContent = false
    fun reset() { startedInContent = false }
    fun contacts(event: MotionEvent, rect: DoubleArray): List<AirPlayContact> {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) startedInContent =
            event.x >= rect[0] && event.x <= rect[0] + rect[2] &&
                event.y >= rect[1] && event.y <= rect[1] + rect[3]
        if (!startedInContent) return emptyList()
        val contacts = CarPlayTouchMapper.contacts(event, rect)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) reset()
        return contacts
    }
}
