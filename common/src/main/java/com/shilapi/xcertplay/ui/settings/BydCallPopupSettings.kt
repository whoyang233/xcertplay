package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun BydCallPopupSettings(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SectionHeader(tr("settings.call_popup.section"))
    SettingsGroup {
        row {
            SwitchRow(title = tr("settings.call_popup.title"), support = tr("settings.call_popup.support"),
                checked = enabled, onCheckedChange = onChange)
        }
    }
    ExpandableHelp(tr("settings.call_popup.help"))
}
