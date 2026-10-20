package com.shilapi.xcertplay.ui.settings

import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.media.MainMediaAudioBuffer
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.i18n.tr

@Composable
internal fun MediaBufferSetting(durationMs: Int, onChange: (Int) -> Unit) {
    SelectRow(
        title = tr("settings.audio.buffer"),
        support = tr("settings.audio.buffer.support"),
        options = MainMediaAudioBuffer.presetsMs.map { it to "$it ms" },
        selected = MainMediaAudioBuffer.sanitizeDurationMs(durationMs),
        onSelected = onChange,
    )
}
