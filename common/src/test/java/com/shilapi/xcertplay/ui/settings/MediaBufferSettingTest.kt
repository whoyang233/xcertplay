package com.shilapi.xcertplay.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.ProvideSettingsUi
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaBufferSettingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var duration by mutableIntStateOf(500)
    private fun render(fontScale: Float) {
        AirPlayPersistence.saveMainMediaAudioBufferDurationMs(context, 500)
        I18n.init(context)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                XcertplayTheme { ProvideSettingsUi {
                    SettingsScaffold(listOf(SettingsDestination("audio", "音频", "媒体延迟", R.drawable.ic_ms_graphic_eq)),
                        "audio", {}, { duration = AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context) },
                        { AirPlayPersistence.saveMainMediaAudioBufferDurationMs(context, duration) }, {}, {}) {
                        SettingsGroup { row { MediaBufferSetting(duration) { duration = it } } }
                    }
                } }
            }
        }
        compose.onNodeWithText("500 ms").assertIsDisplayed()
    }
    private fun choose(value: Int) {
        compose.onNodeWithText("主媒体缓冲（延迟调节）").performScrollTo().performClick()
        compose.onNode(hasText(if (value == 0) I18n.text("settings.audio.buffer.immediate") else "$value ms") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(value, duration)
            assertEquals(500, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
        }
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/settings-layout/$name.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test @Config(qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
    fun fullHdShowsAllNewPresetsAndPreservesTheOldValueUntilSave() {
        render(1f)
        for (value in listOf(0, 20, 50, 100, 150, 200, 250, 300, 1000, 20)) choose(value)
        compose.onNodeWithTag("settings-restart").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(20, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context)) }
        capture("1080p-low-latency-buffer")
    }
    @Test @Config(qualifiers = "zh-rCN-w480dp-h360dp-mdpi")
    fun narrowLargeFontChoiceCanBeDiscardedWithoutChangingTheSavedValue() {
        render(1.5f)
        choose(50)
        compose.onNodeWithContentDescription(I18n.text("common.back")).performClick()
        compose.runOnIdle { assertEquals(500, duration) }
        choose(20)
        compose.onNodeWithTag("settings-restart").assertIsDisplayed()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        capture("narrow-font150-low-latency-buffer")
    }
}
