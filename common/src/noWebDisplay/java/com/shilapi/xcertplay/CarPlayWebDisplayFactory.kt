@file:Suppress("UNUSED_PARAMETER")

package com.shilapi.xcertplay

import android.content.Context
import android.view.Surface
import com.shilapi.xcertplay.airplay.AirPlayContact
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.media.MediaTap
import java.io.Closeable

/** Local-only build: no server, certificates, network discovery or remote audio/video ownership. */
object CarPlayWebDisplayFactory {
    const val ENABLED = false
    const val MAIN = 110
    const val CLUSTER = 111

    fun find(sink: AndroidMediaSink?): Session? = null
    fun urls(type: Int): List<String> = emptyList()
    fun create(
        context: Context,
        configs: Map<Int, CarPlayWebDisplayConfig>,
        sendTouch: (List<AirPlayContact>) -> Boolean,
    ): Session? = null

    /** API compatibility for the shared host; disabled builds never create a session. */
    class Session private constructor() : MediaTap, Closeable {
        fun bind(sink: AndroidMediaSink) = Unit
        fun owns(type: Int) = false
        fun preview(type: Int, surface: Surface?) = Unit
        fun releasePreview(type: Int, surface: Surface) = Unit
        override fun forwardsVideo(type: Int) = false
        override fun wantsVideoFrames(type: Int) = false
        override fun close() = Unit
    }
}
