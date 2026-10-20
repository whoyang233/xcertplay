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
class BydCallPopupSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var enabled by mutableStateOf(false)
    private fun render(fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AirPlayPersistence.saveHideBydCallPopup(context, false)
        I18n.init(context)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                XcertplayTheme { ProvideSettingsUi {
                    SettingsScaffold(listOf(SettingsDestination("audio", "音频", "通话提示", R.drawable.ic_ms_graphic_eq)),
                        "audio", {}, {}, { AirPlayPersistence.saveHideBydCallPopup(context, enabled) }, {}, {}) {
                        BydCallPopupSettings(enabled) { enabled = it }
                    }
                } }
            }
        }
    }
    private fun verifyAndCapture(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText("隐藏比亚迪通话弹窗（实验性）").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(enabled)
            assertFalse("A draft must not change the live popup permission", AirPlayPersistence.loadHideBydCallPopup(context))
        }
        compose.onNodeWithTag("settings-restart").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(AirPlayPersistence.loadHideBydCallPopup(context))
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
    fun fullHdPopupControlIsStagedAndSaveable() { render(1f); verifyAndCapture("1080p-call-popup") }

    @Test @Config(qualifiers = "zh-rCN-w480dp-h360dp-mdpi")
    fun narrowLargeFontPopupControlKeepsTheSaveButtonVisible() { render(1.5f); verifyAndCapture("narrow-font150-call-popup") }
}
