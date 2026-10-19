package com.shilapi.xcertplay.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
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
@Config(sdk=[33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisplayCompatibilitySettingsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private var fast by mutableStateOf(false)
    private var resets=0
    private var saves=0
    private fun render(fontScale:Float=1f) {
        I18n.init(ApplicationProvider.getApplicationContext())
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                XcertplayTheme { ProvideSettingsUi {
                    SettingsScaffold(listOf(SettingsDestination("display","显示与视频","画面比例",R.drawable.ic_ms_crop_free)),
                        "display",{}, {}, {saves++}, {}, {}) {
                        DisplayCompatibilitySettings(fast,"1920 × 978 → 1920 × 978",{fast=it},{resets++;fast=false})
                    }
                } }
            }
        }
    }
    private fun screenshot(name:String) = compose.runOnIdle {
        val view=compose.activity.findViewById<android.view.View>(android.R.id.content)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("build/reports/settings-layout/$name.png");file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test @Config(qualifiers="zh-rCN-w1920dp-h1080dp-mdpi")
    fun compatibilityOptInResetAndSaveAreAvailableInFullHd() {
        render()
        compose.onNodeWithText("1920 × 978 → 1920 × 978").assertIsDisplayed()
        compose.onNodeWithText("多区域快速切换（实验性）").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(fast);assertEquals(0,saves) }
        compose.onNodeWithText("恢复显示默认值").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(fast);assertEquals(1,resets);assertEquals(0,saves) }
        compose.onNodeWithTag("settings-restart").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,saves) }
        screenshot("1080p-display-compatibility")
    }
    @Test @Config(qualifiers="zh-rCN-w480dp-h360dp-mdpi")
    fun compatibilityActionsScrollIntoViewAt150PercentFonts() {
        render(1.5f)
        compose.onNodeWithText("恢复显示默认值").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-restart").assertIsDisplayed()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,resets) }
        screenshot("narrow-font150-display-compatibility")
    }
}
