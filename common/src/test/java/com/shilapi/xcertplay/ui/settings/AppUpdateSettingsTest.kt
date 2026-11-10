package com.shilapi.xcertplay.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import com.shilapi.xcertplay.update.AppUpdateState
import com.shilapi.xcertplay.update.UpdateCatalog
import com.shilapi.xcertplay.update.UpdatePhase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w960dp-h540dp")
class AppUpdateSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun automaticSwitchManualCheckAndInstallActionsRemainIndependent() {
        I18n.init(ApplicationProvider.getApplicationContext<Context>())
        var state by mutableStateOf(AppUpdateState())
        var checks = 0
        var downloads = 0
        var installs = 0
        compose.setContent { XcertplayTheme { Column { AppUpdateSettings(state,
            { state = state.copy(automatic = it) }, { checks++ }, { downloads++ }, { installs++ }) } } }
        compose.onNodeWithText("启动时自动检查更新").performClick()
        compose.runOnIdle { assertFalse(state.automatic) }
        compose.onNodeWithText("检查更新").performClick()
        compose.runOnIdle {
            assertEquals(1, checks)
            state = state.copy(phase = UpdatePhase.AVAILABLE,
                release = UpdateCatalog.release("xcertplay-1.4.2B-release-2026111003.apk"))
        }
        compose.onNodeWithText("1.4.2B · 2026111003").assertExists()
        compose.onNodeWithText("下载并安装").performClick()
        compose.runOnIdle { assertEquals(1, downloads); state = state.copy(phase = UpdatePhase.READY) }
        compose.onNodeWithText("安装更新").performClick()
        compose.runOnIdle { assertEquals(1, installs); state = state.copy(phase = UpdatePhase.CHECKING) }
        compose.onNodeWithText("检查更新").assertIsNotEnabled()
    }

    @Test fun availableVersionPromptsOnlyWhenRequestedAndCanBePostponed() {
        I18n.init(ApplicationProvider.getApplicationContext<Context>())
        var state by mutableStateOf(AppUpdateState(phase = UpdatePhase.AVAILABLE, showPrompt = true,
            release = UpdateCatalog.release("xcertplay-1.4.2B-release-2026111003.apk")))
        var downloads = 0
        compose.setContent { XcertplayTheme { AppUpdatePrompt(state,
            { state = state.copy(showPrompt = false) }, { downloads++ }) } }
        compose.onNodeWithText("发现新版本").assertIsDisplayed()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithText("发现新版本").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, downloads) }
    }
}
