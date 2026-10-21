package com.shilapi.xcertplay.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AudioPlaybackSettings
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
@Config(sdk = [33], qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioRoutingSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var settings by mutableStateOf(AudioPlaybackSettings())

    @Test fun bindingsAndNavigationSliderWorkWithinFullHdLayout() {
        I18n.init(ApplicationProvider.getApplicationContext())
        compose.setContent {
            XcertplayTheme { ProvideSettingsUi {
                SettingsScaffold(
                    destinations = listOf(SettingsDestination("audio", "音频", "音乐和导航", R.drawable.ic_ms_graphic_eq)),
                    selectedId = "audio", onSelect = {}, onBack = {}, onSave = {}, onExit = {}, onLayout = {},
                ) { AudioRoutingSettings(settings) { settings = it } }
            } }
        }
        compose.onNodeWithText("导航声道绑定").assertDoesNotExist()
        compose.onNodeWithText("音乐声道绑定").assertDoesNotExist()
        compose.onNodeWithText("导航音频通道（编号）").performScrollTo().performClick()
        compose.onNode(hasText("14 · 厂商通道（部分车机为导航）") and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(14, settings.navigationStream) }
        compose.onNodeWithText("音乐音频通道（编号）").performScrollTo().performClick()
        compose.onNode(hasText("3 · 媒体") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3, settings.musicStream) }
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("0%").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.runOnIdle { assertEquals(50, settings.navigationVolumePercent) }
        compose.onNodeWithTag("settings-restart").assertIsDisplayed()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/settings-layout/1080p-audio-routing.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
