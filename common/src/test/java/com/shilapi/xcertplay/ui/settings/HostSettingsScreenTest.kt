package com.shilapi.xcertplay.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.MutableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.media.AudioQualityCapabilities
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

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
            if (category == "音频") alignContentTo(I18n.text("settings.audio.routing"))
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

    @Test fun completeGalleryCoversAllSettingsPagesAndTheirScrollableContent() {
        // Preview the automotive variant and installed BYD call component without running either.
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.bluetoothcall"
            applicationInfo = ApplicationInfo().apply { packageName = "com.byd.bluetoothcall" }
        })
        @Suppress("DEPRECATION")
        val appPackage = context.packageManager.getPackageInfo(context.packageName, 0)
        appPackage.versionName = "1.4.2B"
        shadowOf(context.packageManager).installPackage(appPackage)
        compose.runOnIdle {
            CarPlayHostActivity::class.java.getDeclaredField("advancedAudioChannelMappingSupported")
                .apply { isAccessible = true }.setBoolean(host, true)
            invoke("loadPersistedSettings")
            invoke("updateResolutionMenu")
            invoke("updateSafeAreaSummary")
            invoke("updateAirPlayIconPreview")
        }
        capturePages("01-connection")
        compose.onNodeWithText("无线连接高级选项").performScrollTo().performClick()
        compose.onNodeWithText("连接诊断信息").performScrollTo().performClick()
        capturePages("02-connection-expanded")
        for ((category, name) in listOf("车辆数据" to "03-vehicle", "音频" to "04-audio",
            "显示与视频" to "05-display", "标识与外观" to "06-identity",
            "启动与窗口" to "07-window", "诊断" to "08-diagnostics", "关于" to "09-about")) {
            select(category)
            capturePages(name)
        }
        // Quality options are layout samples. No codec capability is asserted for the user's car.
        select("音频")
        val sampleRates = listOf(8_000, 16_000, 24_000, 32_000, 44_100, 48_000)
        compose.runOnIdle {
            setState("audioQualityCapabilities", AudioQualityCapabilities.Result(
                supported = buildMap {
                    for (codec in AudioCodecKind.entries) for (channels in 1..2) put(codec to channels, sampleRates)
                },
                eldMicrophoneRates = listOf(16_000), microphoneCaptureRates = listOf(8_000, 16_000),
                eldFramesPerPacket = setOf(480), nativeOutputRates = setOf(48_000),
            ))
            setState("audioQualityOpen", true)
        }
        capturePages("10-audio-quality-sample")
    }

    private fun capturePages(name: String) {
        val content = compose.onNodeWithTag("settings-content")
        content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
        repeat(12) { index ->
            snapshot("gallery/$name-${(index + 1).toString().padStart(2, '0')}")
            val range = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            var value = 0f
            var maximum = 0f
            compose.runOnIdle { value = range.value(); maximum = range.maxValue() }
            if (value >= maximum - 1f) return
            val step = content.getUnclippedBoundsInRoot().height.value *
                compose.activity.resources.displayMetrics.density * 0.75f
            content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
            compose.runOnIdle { assertTrue("Scrolling must advance $name", range.value() > value) }
        }
        fail("$name did not reach its last setting within 12 captures")
    }

    @Suppress("UNCHECKED_CAST")
    private fun setState(name: String, value: Any?) {
        val field = CarPlayHostActivity::class.java.getDeclaredField(name + "\$delegate")
            .apply { isAccessible = true }
        (field.get(host) as MutableState<Any?>).value = value
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
        compose.onNodeWithText(title).performScrollTo()
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
            val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val view = dialog?.window?.decorView
                ?: compose.activity.findViewById<android.view.View>(android.R.id.content)
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/settings-layout/actual-$name.png").apply { parentFile.mkdirs() }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
