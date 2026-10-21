package com.shilapi.xcertplay.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.ListRow
import com.shilapi.xcertplay.ui.components.LvIcon
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.i18n.tr
import com.shilapi.xcertplay.host.R

/** Collapsing this section only changes visibility; the draft/persisted values stay intact. */
@Composable
internal fun ExpandableSettingsSection(title: String, summary: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        SettingsGroup { row {
            ListRow(headline = title, support = summary, onClick = { expanded = !expanded },
                trailing = { LvIcon(if (expanded) R.drawable.ic_ms_expand_more else R.drawable.ic_ms_chevron_right) })
        } }
        if (expanded) content()
    }
}

@Composable
internal fun ExpandableHelp(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(tr(if (expanded) "settings.help.less" else "settings.help.more"))
        }
        if (expanded) HelpText(text)
    }
}
