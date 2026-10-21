package com.shilapi.xcertplay.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.ProvideSettingsUi
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandableSettingsSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun loadChinese() { I18n.init(ApplicationProvider.getApplicationContext()) }

    @Test fun collapsingAdvancedOptionsPreservesTheDraftAndDoesNotInvokeRuntimeCallbacks() {
        var changes = 0
        var enabled = false
        compose.setContent { XcertplayTheme { ProvideSettingsUi {
            var draft by remember { mutableStateOf(false) }
            ExpandableSettingsSection("无线连接高级选项", "连接稳定性与 ADB") {
                SettingsGroup { row { SwitchRow("测试开关", draft, { draft = it; enabled = it; changes++ }) } }
            }
        } } }
        compose.onNodeWithText("测试开关").assertDoesNotExist()
        compose.onNodeWithText("无线连接高级选项").performClick()
        compose.onNodeWithText("测试开关").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(enabled); assertEquals(1, changes) }
        compose.onNodeWithText("无线连接高级选项").performClick()
        compose.onNodeWithText("测试开关").assertDoesNotExist()
        compose.onNodeWithText("无线连接高级选项").performClick()
        compose.onNode(isToggleable()).assertIsOn()
        compose.runOnIdle { assertTrue(enabled); assertEquals(1, changes) }
    }

    @Test fun longHelpIsAvailableOnDemandAndWideSelectPlacesCurrentValueOnTheRight() {
        compose.setContent { XcertplayTheme { ProvideSettingsUi {
            Column {
                SettingsGroup { row { SelectRow("认证方式", listOf(0 to "USB / CH341"), 0, {}) } }
                ExpandableHelp("认证详细说明内容")
            }
        } } }
        val label = compose.onNodeWithText("认证方式", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val value = compose.onNodeWithText("USB / CH341", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(value.left > label.right)
        assertTrue(kotlin.math.abs(value.top.value - label.top.value) < 10f)
        compose.onNodeWithText("认证详细说明内容").assertDoesNotExist()
        compose.onNodeWithText("查看详细说明").performClick()
        compose.onNodeWithText("认证详细说明内容").assertIsDisplayed()
        compose.onNodeWithText("收起说明").performClick()
        compose.onNodeWithText("认证详细说明内容").assertDoesNotExist()
    }
}
