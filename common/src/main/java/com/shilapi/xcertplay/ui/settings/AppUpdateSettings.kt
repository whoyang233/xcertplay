package com.shilapi.xcertplay.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.ListRow
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.tr
import com.shilapi.xcertplay.update.AppUpdateState
import com.shilapi.xcertplay.update.UpdatePhase

@Composable
internal fun AppUpdateSettings(state: AppUpdateState, onAutomatic: (Boolean) -> Unit,
    onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit,
    onDownloads: () -> Unit = {}, onBrowser: () -> Unit = {}) {
    val busy = state.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)
    SectionHeader(tr("settings.update.section"))
    SettingsGroup {
        row { SwitchRow(title = tr("settings.update.automatic"), support = tr("settings.update.automatic.help"),
            checked = state.automatic, onCheckedChange = onAutomatic) }
        row { ListRow(headline = tr("settings.update.check"), enabled = !busy, onClick = onCheck) }
    }
    HelpText(tr("settings.update.state." + state.phase.name.lowercase()))
    state.downloadReason?.let { HelpText(tr("settings.update.download_reason") + " $it") }
    val release = state.release
    if (release != null && state.phase != UpdatePhase.CURRENT) {
        SettingsGroup {
            row { ListRow(headline = tr("settings.update.available"), support = "${release.versionName} · ${release.buildId}") }
            row {
                val ready = state.phase in setOf(UpdatePhase.READY, UpdatePhase.INSTALL_PERMISSION, UpdatePhase.INSTALL_ERROR)
                ListRow(headline = tr(if (ready) "settings.update.install" else "settings.update.download"),
                    enabled = !busy, onClick = if (ready) onInstall else onDownload)
            }
            row { ListRow(headline = tr("settings.update.system_downloads"), onClick = onDownloads) }
            if (state.phase == UpdatePhase.DOWNLOAD_ERROR) {
                row { ListRow(headline = tr("settings.update.browser_download"), onClick = onBrowser) }
            }
        }
    }
    if (state.phase == UpdatePhase.DOWNLOADING) {
        Column(Modifier.fillMaxWidth()) {
            if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                HelpText("${state.progress}%")
            }
        }
    }
}

@Composable
internal fun AppUpdatePrompt(state: AppUpdateState, onLater: () -> Unit, onDownload: () -> Unit) {
    val release = state.release ?: return
    if (!state.showPrompt) return
    AlertDialog(onDismissRequest = onLater, title = { Text(tr("settings.update.available")) },
        text = { Text("${release.versionName} · ${release.buildId}\n" + tr("settings.update.prompt")) },
        confirmButton = { TextButton(onClick = onDownload) { Text(tr("settings.update.download")) } },
        dismissButton = { TextButton(onClick = onLater) { Text(tr("settings.update.later")) } })
}
