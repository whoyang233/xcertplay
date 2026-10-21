package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.media.AudioPlaybackSettings
import com.shilapi.xcertplay.media.AudioStreamBinding
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
    val streams = AudioStreamBinding.choices.map { it to tr("settings.audio.stream.$it") }
    SectionHeader(tr("settings.audio.routing"))
    SettingsGroup {
        row {
            SelectRow(
                title = tr("settings.audio.music_stream"),
                options = streams,
                selected = AudioStreamBinding.sanitize(settings.musicStream),
                onSelected = { onChange(settings.copy(musicStream = it)) },
                support = tr("settings.audio.stream.support"),
            )
        }
        row {
            SelectRow(
                title = tr("settings.audio.navigation_stream"),
                options = streams,
                selected = AudioStreamBinding.sanitize(settings.navigationStream),
                onSelected = { onChange(settings.copy(navigationStream = it)) },
                support = tr("settings.audio.stream.support"),
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
