package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.media.VideoBufferPolicy
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun VideoBufferSetting(durationMs: Int, onChange: (Int) -> Unit) {
    SelectRow(
        title = tr("settings.video.buffer"),
        support = tr("settings.video.buffer.support"),
        selected = VideoBufferPolicy.sanitize(durationMs),
        options = VideoBufferPolicy.presetsMs.map { value -> value to when (value) {
            VideoBufferPolicy.COMPATIBILITY -> tr("settings.video.buffer.compatibility")
            0 -> tr("settings.video.buffer.immediate")
            else -> "$value ms"
        } },
        onSelected = onChange,
    )
}
