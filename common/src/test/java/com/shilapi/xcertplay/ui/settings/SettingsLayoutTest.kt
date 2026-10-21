package com.shilapi.xcertplay.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.ui.components.FieldRow
import com.shilapi.xcertplay.ui.components.HelpText
import com.shilapi.xcertplay.ui.components.SectionHeader
import com.shilapi.xcertplay.ui.components.SelectRow
import com.shilapi.xcertplay.ui.components.SettingsGroup
import com.shilapi.xcertplay.ui.components.SwitchRow
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.ProvideSettingsUi
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var settingsDensity = 0f
    private var settingsFontScale = 0f
    private var bodyFontSize = 0f
    private var twoPane = false
    private var saves = 0
    private var exits = 0
    private var selectedChannel by mutableIntStateOf(36)
    private val destinations = listOf("连接", "车辆数据", "音频", "显示与视频", "标识与外观", "启动与窗口", "诊断", "关于")
        .mapIndexed { index, title -> SettingsDestination("section$index", title, "查看并调整$title", R.drawable.ic_ms_wifi) }

    @Before fun loadChineseStrings() { I18n.init(ApplicationProvider.getApplicationContext()) }

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h720dp-hdpi")
    fun fullHdAt240DpiKeepsWindowDensityAndUsesCompactTwoPaneSettings() {
        render()
        compose.runOnIdle {
            assertEquals(1.5f, settingsDensity, 0.01f)
            assertEquals(14f, bodyFontSize, 0.01f)
            assertTrue(twoPane)
        }
        compose.onNodeWithTag("settings-sidebar").assertIsDisplayed()
        verifyToolbar()
        snapshot("1080p-240dpi")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
    fun fullHdAt320DpiNoLongerCollapsesIntoTheEnlargedSinglePane() {
        render()
        compose.runOnIdle { assertEquals(2f, settingsDensity, 0.01f); assertTrue(twoPane) }
        compose.onNodeWithTag("settings-sidebar").assertIsDisplayed()
        verifyToolbar()
        compose.onNodeWithText("末尾配置项").performScrollTo().assertIsDisplayed()
        verifyToolbar()
        snapshot("1080p-320dpi-scrolled")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w1920dp-h1080dp-mdpi")
    fun lowDensityFullHdSettingsDoNotUseTheHomeScreensMaximumEnlargement() {
        render()
        compose.runOnIdle { assertEquals(1.2f, settingsDensity, 0.01f); assertTrue(twoPane) }
        verifyToolbar()
        snapshot("1080p-160dpi")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun narrowLargeFontSettingsCanReachEveryCategoryAndKeepBothActions() {
        render(fontScale = 1.5f)
        compose.runOnIdle { assertFalse(twoPane); assertEquals(1.5f, settingsFontScale, 0.01f) }
        compose.onNodeWithText("关于").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("末尾配置项").performScrollTo().assertIsDisplayed()
        verifyToolbar()
        compose.onNodeWithTag("settings-restart").performClick()
        compose.onNodeWithTag("settings-exit").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(1, exits) }
        snapshot("narrow-font150-scrolled")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-hdpi")
    fun channelDialogScrollsToItsLastOptionInAShortWindow() {
        render(initial = "section0", fontScale = 1.3f)
        compose.onNodeWithText("请求信道").performScrollTo().performClick()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("165 · 5825 MHz").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(165, selectedChannel) }
        compose.onNodeWithText("取消").assertDoesNotExist()
        verifyToolbar()
        snapshot("short-window-channel165")
    }

    private fun render(initial: String? = null, fontScale: Float = 1f) {
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale)) {
                XcertplayTheme {
                    ProvideSettingsUi {
                        val density = LocalDensity.current
                        val fontSize = MaterialTheme.typography.bodyLarge.fontSize.value
                        SideEffect { settingsDensity = density.density; settingsFontScale = density.fontScale; bodyFontSize = fontSize }
                        var selected by remember { mutableStateOf(initial) }
                        SettingsScaffold(
                            destinations = destinations,
                            selectedId = selected,
                            onSelect = { selected = it },
                            onBack = { selected = null },
                            onSave = { saves++ },
                            onExit = { exits++ },
                            onLayout = { twoPane = it },
                        ) { SettingsFixture() }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsFixture() {
        SectionHeader("MFi 认证")
        SettingsGroup {
            row { SelectRow("认证方式", listOf(0 to "USB / CH341", 1 to "本地文件"), 0, {}) }
            row { ExpandableHelp("使用 CH341 USB 转 I2C 桥接上的 MFi 协处理器。") }
        }
        SectionHeader("Wi-Fi 信道策略")
        SettingsGroup {
            row {
                SelectRow(
                    "请求信道",
                    listOf(36, 40, 44, 48, 149, 153, 157, 161, 165).map { it to "$it · ${5000 + it * 5} MHz" },
                    selectedChannel,
                    { selectedChannel = it },
                )
            }
            row { SwitchRow("启动失败时重置 Wi-Fi", true, {}, "释放失败热点并重试；系统不允许开关 Wi-Fi 时仅重建热点。") }
            row { FieldRow("远程认证服务器地址", "https://example.invalid/mfi", {}) }
        }
        ExpandableSettingsSection("无线连接高级选项", "连接稳定性、Wi-Fi 恢复和 ADB 抗干扰设置") {
            SettingsGroup { row { SwitchRow("Wi-Fi 高性能模式", true, {}) } }
        }
        repeat(10) { index ->
            SectionHeader("配置分组 ${index + 1}")
            SettingsGroup { row { SwitchRow("可滚动设置项 ${index + 1}", false, {}, "完整显示说明，按钮保持可见。") } }
        }
        HelpText("末尾配置项")
        Spacer(Modifier.height(8.dp))
    }

    private fun verifyToolbar() {
        val root = compose.onNodeWithTag("settings-root").getUnclippedBoundsInRoot()
        for (tag in listOf("settings-restart", "settings-exit")) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag must fit horizontally", bounds.left >= root.left && bounds.right <= root.right)
            assertTrue("$tag must stay in the viewport", bounds.top >= root.top && bounds.bottom <= root.bottom)
        }
    }

    private fun snapshot(name: String) {
        // PixelCopy's forced hardware-frame callback does not run in this Robolectric version.
        // Draw the same measured Android view with the native software canvas instead.
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            assertTrue(view.width > 0 && view.height > 0)
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val directory = File("build/reports/settings-layout").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
        }
    }
}
