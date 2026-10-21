package com.shilapi.xcertplay.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.HandshakeTimeline
import com.shilapi.xcertplay.HandshakeTimeline.PhaseKind
import com.shilapi.xcertplay.HandshakeTimeline.State
import com.shilapi.xcertplay.ScreenLogLine
import com.shilapi.xcertplay.ui.i18n.I18n
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
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var settingsOpened = 0
    private var retries = 0

    @Before fun loadChinese() { I18n.init(ApplicationProvider.getApplicationContext()) }

    @Test @Config(qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
    fun fullHdShowsCalmOverviewAndKeepsRawLogsBehindDetailsEvenInDebugMode() {
        render()
        compose.onNodeWithText("正在与 iPhone 建立连接").assertIsDisplayed()
        compose.onNodeWithText("Wireless CarPlay control running").assertDoesNotExist()
        compose.onNodeWithTag("home-log").assertDoesNotExist()
        compose.onNodeWithTag("home-checks").assertIsDisplayed()
        verifyToolbar()
        snapshot("1080p-320dpi-overview")
        compose.onNodeWithTag("home-details").performClick()
        compose.onNodeWithTag("home-log").assertIsDisplayed()
        compose.onNodeWithText("Wireless CarPlay control running").assertIsDisplayed()
        compose.onNodeWithTag("home-details").performClick()
        compose.onNodeWithTag("home-log").assertDoesNotExist()
        compose.onNodeWithTag("home-settings").performClick()
        compose.runOnIdle { assertEquals(1, settingsOpened); assertEquals(0, retries) }
    }

    @Test @Config(qualifiers = "zh-rCN-w1920dp-h1080dp-mdpi")
    fun lowDensityFullHdKeepsStatusAndConnectionChecksInTwoColumns() {
        render()
        val status = compose.onNodeWithTag("home-status").getUnclippedBoundsInRoot()
        val checks = compose.onNodeWithTag("home-checks").getUnclippedBoundsInRoot()
        assertTrue(status.right <= checks.left)
        compose.onNodeWithText("连接成功后将自动显示 CarPlay 画面").assertIsDisplayed()
        verifyToolbar()
        snapshot("1080p-160dpi-overview")
    }

    @Test @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun narrowLargeFontCanReachRetryAndChecksWithoutLosingToolbar() {
        render(fontScale = 1.5f, failed = true)
        compose.onNodeWithTag("home-retry").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-checks").performScrollTo().assertIsDisplayed()
        verifyToolbar()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, settingsOpened) }
        snapshot("narrow-font150-scrolled")
    }

    private fun render(fontScale: Float = 1f, failed: Boolean = false) {
        val timeline = HandshakeTimeline(true, 1)
        timeline.phases.first { it.kind == PhaseKind.IAP2 }.state = State.ACTIVE
        timeline.links.first { it.kind == HandshakeTimeline.LinkKind.WIFI }.state = HandshakeTimeline.LinkState.UP
        timeline.links.first { it.kind == HandshakeTimeline.LinkKind.RFCOMM }.state = HandshakeTimeline.LinkState.UP
        if (failed) timeline.fail("Connection failed", "Bluetooth timed out", 2)
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale)) {
                XcertplayTheme(darkTheme = true) {
                    HomeScreen(timeline, timeline.revision, "Wireless CarPlay control running", true,
                        true, listOf(ScreenLogLine(1, 1000, "Wireless CarPlay control running")),
                        { settingsOpened++ }, if (failed) ({ retries++ }) else null, versionName = "1.4.2B")
                }
            }
        }
    }

    private fun verifyToolbar() {
        val root = compose.onNodeWithTag("home-root").getUnclippedBoundsInRoot()
        for (tag in listOf("home-details", "home-settings")) {
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= root.left && bounds.right <= root.right)
            assertTrue(bounds.top >= root.top && bounds.bottom <= root.bottom)
        }
    }

    private fun snapshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val directory = File("build/reports/home-layout").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            image.recycle()
        }
    }
}
