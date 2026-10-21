package com.shilapi.xcertplay.ui.settings

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.ListRow
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun DisplayCompatibilitySettings(
    fastViewAreaSwitching: Boolean,
    dimensions: String,
    onFastViewAreaSwitching: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    SectionHeader(tr("settings.display.compatibility"))
    SettingsGroup {
        row { ListRow(headline = tr("settings.display.current_dimensions"), support = dimensions) }
        row { SwitchRow(
            title = tr("settings.display.fast_areas"),
            support = tr("settings.display.fast_areas.support"),
            checked = fastViewAreaSwitching,
            onCheckedChange = onFastViewAreaSwitching,
        ) }
        row { ExpandableHelp(tr("settings.display.compatibility.support")) }
        row { OutlinedButton(onClick = onReset) { Text(tr("settings.display.restore_defaults")) } }
    }
    HelpText(tr("settings.display.restore_defaults.support"))
}
