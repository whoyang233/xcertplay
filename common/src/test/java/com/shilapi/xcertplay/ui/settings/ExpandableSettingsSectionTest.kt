package com.shilapi.xcertplay.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.components.ListRow
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

    @Test fun adjacentAndExpandedCardsHaveGapsAndAdbButtonHasPaddingWithoutEmptyStatusSpace() {
        var authorized = 0
        compose.setContent { XcertplayTheme { ProvideSettingsUi {
            Column(Modifier.fillMaxWidth()) {
                SettingsGroup { row { ListRow("前一卡片", modifier = Modifier.testTag("previous-row")) } }
                ExpandableSettingsSection("连接诊断信息", "查看连接状态") {
                    SettingsGroup { row { ListRow("连接状态", modifier = Modifier.testTag("status-row")) } }
                }
                SettingsGroup { row { AdbAuthorizationAction(false, null) { authorized++ } } }
                SettingsGroup { row { ListRow("音频质量设置", modifier = Modifier.testTag("quality-row")) } }
            }
        } } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val previous = bounds("previous-row")
        val header = compose.onNodeWithText("连接诊断信息").getUnclippedBoundsInRoot()
        assertTrue("Adjacent cards must be separated", header.top.value - previous.bottom.value >= 8f)
        compose.onNodeWithText("连接诊断信息").performClick()
        val status = bounds("status-row")
        assertTrue("Expanded status must not touch its header", status.top.value - header.bottom.value >= 8f)
        val action = bounds("adb-action")
        val button = bounds("adb-authorize")
        assertTrue(button.left.value - action.left.value >= 12f)
        assertTrue(button.top.value - action.top.value >= 10f)
        assertTrue(action.bottom.value - button.bottom.value >= 10f)
        assertTrue("Idle action must not reserve an empty status row", action.bottom.value - action.top.value <= 72f)
        assertTrue(button.bottom.value - button.top.value >= 48f)
        assertTrue(bounds("quality-row").top.value - action.bottom.value >= 8f)
        compose.onNodeWithTag("adb-authorize").performClick()
        compose.runOnIdle { assertEquals(1, authorized) }
    }

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
