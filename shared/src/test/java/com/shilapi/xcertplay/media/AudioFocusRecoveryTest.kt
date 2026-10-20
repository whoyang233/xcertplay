package com.shilapi.xcertplay.media

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AudioFocusRecoveryTest {
    @Test fun aMissingGainCallbackCanBeRecoveredOnlyWhenAndroidGrantsFocus() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
        val shadow = shadowOf(manager)
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val focus = AndroidMediaSink.AudioFocusSession(manager)
        val owner = Any()
        focus.retain(owner)
        focus.requestFromHost()
        assertTrue(ReflectionHelpers.getField<Boolean>(focus, "hasFocus"))
        ReflectionHelpers.callInstanceMethod<Unit>(focus, "onFocusChanged",
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        focus.requestOnPlaybackResume()
        assertFalse(ReflectionHelpers.getField<Boolean>(focus, "hasFocus"))
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        focus.requestOnPlaybackResume()
        assertTrue(ReflectionHelpers.getField<Boolean>(focus, "hasFocus"))
        focus.release(owner)
        focus.requestOnPlaybackResume()
        assertFalse(ReflectionHelpers.getField<Boolean>(focus, "hasFocus"))
    }

    @Test fun disabledFocusDoesNotReclaimTheCarAudioSource() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val focus = AndroidMediaSink.AudioFocusSession(manager)
        focus.retain(Any())
        focus.setEnabled(false)
        focus.requestOnPlaybackResume()
        assertFalse(ReflectionHelpers.getField<Boolean>(focus, "hasFocus"))
    }
}
