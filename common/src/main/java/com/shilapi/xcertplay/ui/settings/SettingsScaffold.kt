package com.shilapi.xcertplay.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.ListRow
import com.shilapi.xcertplay.ui.components.LvIcon
import com.shilapi.xcertplay.ui.components.LvIconButton
import com.shilapi.xcertplay.ui.components.ProvideGroupColor
import com.shilapi.xcertplay.ui.components.TrailingValue
import com.shilapi.xcertplay.ui.i18n.tr

internal data class SettingsDestination(
    val id: String,
    val title: String,
    val summary: String,
    @param:DrawableRes val icon: Int,
)

/** Fixed actions and separately scrollable navigation/content, measured after settings scaling. */
@Composable
internal fun SettingsScaffold(
    destinations: List<SettingsDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onLayout: (Boolean) -> Unit = {},
    content: @Composable (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize().testTag("settings-root"), color = scheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val twoPane = maxWidth >= 840.dp
            val compactActions = maxWidth.value / LocalDensity.current.fontScale < 600f
            val selected = destinations.firstOrNull { it.id == selectedId }
                ?: if (twoPane) destinations.first() else null
            val sidebarWidth = if (maxWidth < 1100.dp) 208.dp else 224.dp
            val overviewColumns = if (maxWidth >= 600.dp) 2 else 1
            SideEffect { onLayout(twoPane) }
            Column(Modifier.fillMaxSize()) {
                SettingsActions(
                    title = selected?.title ?: tr("settings.title"),
                    compact = compactActions,
                    backToCategories = !twoPane && selected != null,
                    onBack = onBack,
                    onSave = onSave,
                    onExit = onExit,
                )
                if (twoPane) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.width(sidebarWidth).fillMaxHeight()
                                .testTag("settings-sidebar")
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            destinations.forEach { destination ->
                                ListRow(
                                    headline = destination.title,
                                    leadingIcon = destination.icon,
                                    selected = destination.id == selected?.id,
                                    onClick = { onSelect(destination.id) },
                                )
                            }
                        }
                        Surface(
                            Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = scheme.surfaceContainerLow,
                        ) {
                            ProvideGroupColor(scheme.surfaceContainerHigh) {
                                SettingsDetail(selected!!.id, content)
                            }
                        }
                    }
                } else if (selected == null) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().testTag("settings-categories")
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HelpText(tr("settings.apply_help"))
                        // Two columns on wider single-pane windows; every card remains reachable.
                        destinations.chunked(overviewColumns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { destination ->
                                    ListRow(
                                        headline = destination.title,
                                        support = destination.summary,
                                        leadingIcon = destination.icon,
                                        leadingBox = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelect(destination.id) },
                                        trailing = { TrailingValue(null) },
                                    )
                                }
                                if (row.size == 1 && overviewColumns == 2) Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth()) { SettingsDetail(selected.id, content) }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetail(id: String, content: @Composable (String) -> Unit) {
    key(id) {
        Column(
            Modifier.fillMaxSize().testTag("settings-content")
                .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        ) { content(id) }
    }
}

@Composable
private fun SettingsActions(
    title: String,
    compact: Boolean,
    backToCategories: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("settings-toolbar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LvIconButton(
                if (backToCategories) R.drawable.ic_ms_arrow_back else R.drawable.ic_ms_close,
                if (backToCategories) tr("common.back") else tr("settings.discard"),
                onBack,
            )
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp).testTag("settings-restart")) {
                LvIcon(R.drawable.ic_ms_refresh, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(tr(if (compact) "settings.restart_short" else "settings.restart"), maxLines = 1)
            }
            TextButton(
                onClick = onExit,
                modifier = Modifier.heightIn(min = 48.dp).testTag("settings-exit"),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
            ) {
                LvIcon(R.drawable.ic_ms_power_settings_new, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(tr(if (compact) "settings.exit_short" else "settings.exit"), maxLines = 1)
            }
        }
    }
}
