package com.shilapi.xcertplay.ui.settings

import org.junit.Assert.*
import org.junit.Test

class BydCallPopupStatusTest {
    @Test fun approvalFailureAndPermissionMutationFailureAreDistinguished() {
        assertEquals("settings.call_popup.state.not_approved", callPopupStatusKey("ADB_NOT_APPROVED"))
        assertEquals("settings.call_popup.state.denied", callPopupStatusKey("HIDE_FAILED"))
        assertEquals("settings.call_popup.state.restore_failed", callPopupStatusKey("RESTORE_UNCONFIRMED"))
        assertEquals("settings.call_popup.state.hidden", callPopupStatusKey("HIDDEN"))
        assertEquals("settings.call_popup.state.retrying", callPopupStatusKey("RETRYING"))
        assertEquals("settings.call_popup.state.helper_failed", callPopupStatusKey("unexpected output"))
    }
}
