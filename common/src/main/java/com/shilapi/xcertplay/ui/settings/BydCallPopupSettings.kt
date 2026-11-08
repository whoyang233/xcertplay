package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shilapi.xcertplay.hud.BydCallPopupGuard
import kotlinx.coroutines.delay
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun BydCallPopupSettings(enabled: Boolean, onChange: (Boolean) -> Unit) {
    var execution by remember { mutableStateOf(BydCallPopupGuard.status to BydCallPopupGuard.lastFailure) }
    LaunchedEffect(Unit) {
        while (true) {
            execution = BydCallPopupGuard.status to BydCallPopupGuard.lastFailure
            delay(500)
        }
    }
    SectionHeader(tr("settings.call_popup.section"))
    SettingsGroup {
        row {
            SwitchRow(title = tr("settings.call_popup.title"), support = tr("settings.call_popup.support"),
                checked = enabled, onCheckedChange = onChange)
        }
        row {
            HelpText(tr("settings.call_popup.execution") + "：" + tr(callPopupStatusKey(execution.first)))
        }
        execution.second?.let { failure ->
            row { HelpText(tr("settings.call_popup.last_failure") + "：" + tr(callPopupStatusKey(failure))) }
        }
    }
    ExpandableHelp(tr("settings.call_popup.help"))
}

internal fun callPopupStatusKey(status: String): String = "settings.call_popup.state." + when (status) {
    "OFF", "CANCELLED" -> "idle"
    "CONNECTING", "STARTING" -> "starting"
    "HIDDEN" -> "hidden"
    "ALREADY_HIDDEN" -> "already_hidden"
    "RESTORED" -> "restored"
    "RETRYING" -> "retrying"
    "ADB_UNREACHABLE", "ADB_CONNECTION_LOST" -> "adb_unreachable"
    "ADB_NOT_APPROVED" -> "not_approved"
    "ADB_UNSUPPORTED" -> "adb_unsupported"
    "UNSUPPORTED", "HELPER_NO_NOHUP" -> "unsupported"
    "HELPER_BUSY" -> "busy"
    "ERROR QUERY_FAILED", "HIDE_FAILED", "ERROR HELPER_FAILED" -> "denied"
    "ERROR RESTORE_FAILED", "RESTORE_UNCONFIRMED" -> "restore_failed"
    "EXTERNAL_CHANGE" -> "external_change"
    else -> "helper_failed"
}
