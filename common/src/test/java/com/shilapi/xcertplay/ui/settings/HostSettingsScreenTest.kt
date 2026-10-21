package com.shilapi.xcertplay.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real host's settings composables, without onCreate or any CarPlay/ADB hardware startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HostSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: CarPlayHostActivity
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun renderActualSettings() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("web_display_110_enabled", true).putBoolean("web_display_110_preview", false)
            .putBoolean("web_display_111_enabled", true).commit()
        I18n.init(context)
        // Attach a context, but deliberately do not run Activity.onCreate or start transports.
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        invoke("loadPersistedSettings")
        compose.setContent { XcertplayTheme(darkTheme = true) { host.SettingsScreen() } }
    }

    @Test fun actualPagesHaveNoWebCategoryAndProduceFullHdScreenshots() {
        compose.onNodeWithText("网页显示").assertDoesNotExist()
        snapshot("01-connection")
        compose.onNodeWithText("无线连接高级选项").performScrollTo().performClick()
        compose.onNodeWithText(I18n.text("settings.scan_pause")).performScrollTo().assertIsDisplayed()
        snapshot("02-connection-advanced")
        for ((category, file) in listOf("车辆数据" to "03-vehicle", "音频" to "04-audio-routing",
            "显示与视频" to "06-display", "启动与窗口" to "08-window", "诊断" to "09-diagnostics")) {
            select(category)
            snapshot(file)
        }
        select("音频")
        compose.onNodeWithText(I18n.text("settings.audio.buffer")).performScrollTo()
        alignContentTo(I18n.text("settings.audio.buffer"))
        snapshot("05-audio-buffer")
        select("显示与视频")
        compose.onNodeWithText("视频缓冲上限").performScrollTo()
        alignContentTo("视频缓冲上限")
        snapshot("07-video-buffer")
    }

    @Test fun videoZeroAndAllPresetsEditDraftOnlyUntilSavedAndCanBeDiscarded() {
        select("显示与视频")
        for (value in listOf(0, 20, 50, 100, 150, 200, 250, 300)) {
            choose("视频缓冲上限", if (value == 0) "即时显示（0 ms）" else "$value ms")
            compose.runOnIdle { assertEquals(-1, AirPlayPersistence.loadVideoBufferDurationMs(context)) }
        }
        compose.runOnIdle { invoke("loadPersistedSettings") }
        compose.onNodeWithText("兼容模式（原有行为）").assertExists()
        choose("视频缓冲上限", "即时显示（0 ms）")
        compose.runOnIdle {
            invoke("persistMenuSettings")
            assertEquals(0, AirPlayPersistence.loadVideoBufferDurationMs(context))
            invoke("loadPersistedSettings")
        }
        compose.onNodeWithText("即时显示（0 ms）").assertExists()
        compose.onNodeWithText("视频缓冲上限").performScrollTo().performClick()
        snapshot("10-video-presets")
        compose.onNodeWithText("取消").performClick()
    }

    @Test fun audioZeroIsStoredIndependentlyAndKeepsVideoAndNavigationDefaults() {
        select("音频")
        choose(I18n.text("settings.audio.buffer"), "即时播放（0 ms）")
        compose.runOnIdle {
            assertEquals(100, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
            invoke("persistMenuSettings")
            assertEquals(0, AirPlayPersistence.loadMainMediaAudioBufferDurationMs(context))
            assertEquals(-1, AirPlayPersistence.loadVideoBufferDurationMs(context))
            assertEquals(100, AirPlayPersistence.loadAudioPlaybackSettings(context).navigationVolumePercent)
            invoke("loadPersistedSettings")
        }
        compose.onNodeWithText("即时播放（0 ms）").assertExists()
        compose.onNodeWithText(I18n.text("settings.audio.buffer")).performScrollTo().performClick()
        snapshot("11-audio-presets")
        compose.onNodeWithText("取消").performClick()
    }

    private fun select(category: String) {
        compose.onNode(hasText(category) and hasAnyAncestor(hasTestTag("settings-sidebar")))
            .performScrollTo().performClick()
        toolbar()
    }
    private fun choose(title: String, option: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNode(hasText(option) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
    }
    private fun alignContentTo(title: String) {
        val target = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("settings-content").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("settings-content").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, (target.top.value - content.top.value - 20f) * 2f)
        }
    }
    private fun invoke(name: String) = CarPlayHostActivity::class.java.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(host)
    private fun toolbar() {
        compose.onNodeWithTag("settings-restart").assertIsDisplayed()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
    }
    private fun snapshot(name: String) {
        toolbar()
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/settings-layout/actual-$name.png").apply { parentFile.mkdirs() }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
