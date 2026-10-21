package com.shilapi.xcertplay.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(tr(if (expanded) "settings.help.less" else "settings.help.more"))
        }
        if (expanded) HelpText(text)
    }
}

/** No reserved status space while idle; aligns the action with the card's row text. */
@Composable
internal fun AdbAuthorizationAction(busy: Boolean, status: String?, onAuthorize: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("adb-action").padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onAuthorize, enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("adb-authorize"),
        ) { Text(tr(if (busy) "settings.scan_pause.waiting" else "settings.scan_pause.authorize")) }
        status?.let { Text(tr(it), style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
    }
}
