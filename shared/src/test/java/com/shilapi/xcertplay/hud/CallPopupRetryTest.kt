package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test

class CallPopupRetryTest {
    @Test fun transientBootAndApprovalFailuresHaveABoundedRetryWindow() {
        for (failure in listOf("ADB_UNREACHABLE", "ADB_NOT_APPROVED", "HELPER_BUSY")) {
            assertEquals(listOf(2000L, 4000L, 8000L, 15000L, 30000L, 30000L),
                (0..5).map { CallPopupRetry.delayMillis(failure, it) })
            assertNull(CallPopupRetry.delayMillis(failure, 6))
        }
    }

    @Test fun anUnconfirmedMutationWaitsForDetachedRecoveryBeforeAnotherAttempt() {
        for (failure in listOf("HELPER_CONTROL_FAILED", "HELPER_TIMEOUT", "RESTORE_UNCONFIRMED", "ADB_CONNECTION_LOST")) {
            assertEquals(30000L, CallPopupRetry.delayMillis(failure, 0))
        }
    }

    @Test fun unsupportedFirmwareAndDeniedPermissionsDoNotLoop() {
        for (failure in listOf("ADB_UNSUPPORTED", "HELPER_NO_NOHUP", "ERROR QUERY_FAILED", "HIDE_FAILED", "ERROR HELPER_FAILED")) {
            assertNull(CallPopupRetry.delayMillis(failure, 0))
        }
    }
}
