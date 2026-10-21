package com.shilapi.xcertplay.media

import java.util.Locale

data class AudioPlaybackSettings(
    val navigationVolumePercent: Int = NavigationVolume.DEFAULT_PERCENT,
    val musicStream: Int = 0,
    val navigationStream: Int = 0,
)

/** Matches DiPlay's 0 = usage routing, 1..20 = ROM legacy stream override. */
object AudioStreamBinding {
    val choices: IntRange = 0..20
    fun sanitize(value: Int): Int = value.takeIf { it in choices } ?: 0
}

internal enum class AudioChannelMappingMode {
    MOBILE_COMPATIBLE,
    AUTOMOTIVE_BUS,
}

internal enum class AudioChannel {
    MEDIA,
    PHONE,
    ASSISTANT,
    NAVIGATION,
}

internal enum class AudioContentType {
    MUSIC,
    SPEECH,
}

internal data class AudioChannelSelection(
    val channel: AudioChannel,
    val contentType: AudioContentType,
)

/**
 * Maps CarPlay stream metadata to Android AudioAttributes values.
 *
 * AAOS routes AudioTrack instances by usage, so the automotive mode keeps generic media streams
 * on the media bus instead of reusing mobile's navigation fallback for default/compatibility.
 */
internal object AudioChannelMapper {
    const val STREAM_TYPE_ALT_AUDIO = 101
    const val STREAM_TYPE_MAIN_HIGH_AUDIO = 102

    /** Numbered overrides follow the original role and never capture calls or Siri. */
    fun streamOverride(selection: AudioChannelSelection, settings: AudioPlaybackSettings): Int =
        AudioStreamBinding.sanitize(when (selection.channel) {
            AudioChannel.MEDIA -> settings.musicStream
            AudioChannel.NAVIGATION -> settings.navigationStream
            else -> 0
        })

    fun map(
        audioType: String,
        payloadType: Int,
        mode: AudioChannelMappingMode,
    ): AudioChannelSelection {
        val normalized = audioType.trim().lowercase(Locale.ROOT)
        // Explicit roles take precedence, then the dedicated high-quality/alternate streams.
        // In particular, 102/default must not be attenuated as navigation on mobile, and
        // 101/default must not become music when AAOS classification is enabled.
        when (normalized) {
            "telephony" -> return AudioChannelSelection(AudioChannel.PHONE, AudioContentType.SPEECH)
            "speechrecognition" -> return AudioChannelSelection(AudioChannel.ASSISTANT, AudioContentType.SPEECH)
            "media" -> return AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC)
            "alert", "navigation", "navigationguidance" ->
                return AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        }
        if (payloadType == STREAM_TYPE_MAIN_HIGH_AUDIO) {
            return AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC)
        }
        if (payloadType == STREAM_TYPE_ALT_AUDIO) {
            return AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        }
        return when (mode) {
            AudioChannelMappingMode.MOBILE_COMPATIBLE -> mapMobileCompatible(normalized, payloadType)
            AudioChannelMappingMode.AUTOMOTIVE_BUS -> mapAutomotiveBus(normalized, payloadType)
        }
    }

    private fun mapMobileCompatible(
        audioType: String,
        payloadType: Int,
    ): AudioChannelSelection = when (audioType) {
        "telephony" -> AudioChannelSelection(AudioChannel.PHONE, AudioContentType.SPEECH)
        "speechrecognition" ->
            AudioChannelSelection(AudioChannel.ASSISTANT, AudioContentType.SPEECH)
        "media" -> AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC)
        "default", "alert", "compatibility" ->
            AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        else -> mainHighAudioOrNavigation(payloadType)
    }

    private fun mapAutomotiveBus(
        audioType: String,
        payloadType: Int,
    ): AudioChannelSelection = when (audioType) {
        "telephony" -> AudioChannelSelection(AudioChannel.PHONE, AudioContentType.SPEECH)
        "speechrecognition" ->
            AudioChannelSelection(AudioChannel.ASSISTANT, AudioContentType.SPEECH)
        "media", "default", "compatibility" ->
            AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC)
        "alert" -> AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        else -> mainHighAudioOrNavigation(payloadType)
    }

    private fun mainHighAudioOrNavigation(payloadType: Int): AudioChannelSelection =
        if (payloadType == STREAM_TYPE_MAIN_HIGH_AUDIO) {
            AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC)
        } else {
            AudioChannelSelection(AudioChannel.NAVIGATION, AudioContentType.SPEECH)
        }
}
