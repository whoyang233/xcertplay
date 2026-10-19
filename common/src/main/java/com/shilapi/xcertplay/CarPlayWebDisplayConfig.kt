package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig

/** An enabled web display owns its negotiated size; window changes only resize its preview. */
data class CarPlayWebDisplayConfig(
    val enabled: Boolean = false,
    val width: Int = 1280,
    val height: Int = 720,
    val preview: Boolean = true,
) {
    init {
        require(width in 320..3840 && height in 240..2160 && width % 2 == 0 && height % 2 == 0) {
            "Resolution must use even dimensions between 320 x 240 and 3840 x 2160"
        }
    }

    fun override(display: AirPlayDisplayConfig): AirPlayDisplayConfig = if (!CarPlayWebDisplayFactory.ENABLED || !enabled) display else display.copy(
        widthPixels = width,
        heightPixels = height,
        heightPhysicalMm = display.widthPhysicalMm?.let { maxOf(1, Math.round(it * height.toDouble() / width).toInt()) },
        viewArea = null,
        safeArea = null,
        viewAreas = emptyList(),
        initialViewArea = 0,
    )
}

