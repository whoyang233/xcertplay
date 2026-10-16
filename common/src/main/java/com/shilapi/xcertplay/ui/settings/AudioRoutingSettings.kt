package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.media.AudioOutputChannel
import com.shilapi.xcertplay.media.AudioPlaybackSettings
import com.shilapi.xcertplay.media.NavigationVolume
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SliderRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun AudioRoutingSettings(
    settings: AudioPlaybackSettings,
    onChange: (AudioPlaybackSettings) -> Unit,
) {
    val outputs = listOf(
        AudioOutputChannel.MEDIA to tr("settings.audio.output.media"),
        AudioOutputChannel.NAVIGATION to tr("settings.audio.output.navigation"),
    )
    SectionHeader(tr("settings.audio.routing"))
    SettingsGroup {
        row {
            SelectRow(
                title = tr("settings.audio.music_output"),
                options = outputs,
                selected = settings.musicOutput,
                onSelected = { onChange(settings.copy(musicOutput = it)) },
                support = tr("settings.audio.music_output.support"),
            )
        }
        row {
            SelectRow(
                title = tr("settings.audio.navigation_output"),
                options = outputs,
                selected = settings.navigationOutput,
                onSelected = { onChange(settings.copy(navigationOutput = it)) },
                support = tr("settings.audio.navigation_output.support"),
            )
        }
        row {
            SliderRow(
                title = tr("settings.audio.navigation_volume"),
                support = tr("settings.audio.navigation_volume.support"),
                valueLabel = "${NavigationVolume.sanitize(settings.navigationVolumePercent)}%",
                index = NavigationVolume.stepForPercent(settings.navigationVolumePercent),
                count = NavigationVolume.STEP_COUNT,
                onIndexChange = { onChange(settings.copy(navigationVolumePercent = NavigationVolume.percentForStep(it))) },
            )
        }
    }
    HelpText(tr("settings.audio.routing.help"))
}
