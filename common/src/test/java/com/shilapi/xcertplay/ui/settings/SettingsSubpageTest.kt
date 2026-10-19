package com.shilapi.xcertplay.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.ui.components.ListRow
import com.shilapi.xcertplay.ui.i18n.I18n
import com.shilapi.xcertplay.ui.theme.ProvideSettingsUi
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSubpageTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun qualitySubpageStartsAtTopAndBackReturnsToAudioWithoutLosingFixedActions() {
        I18n.init(ApplicationProvider.getApplicationContext())
        compose.setContent {
            XcertplayTheme { ProvideSettingsUi {
                var subpage by remember { mutableStateOf(false) }
                SettingsScaffold(
                    destinations=listOf(SettingsDestination("audio","音频","声音设置",R.drawable.ic_ms_graphic_eq)),
                    selectedId="audio",onSelect={},onBack={ subpage=false },onSave={},onExit={},
                    detailTitle=if(subpage) "音质页面" else null,detailKey=subpage,
                ) {
                    if(subpage) Text("音质页面顶部") else {
                        Text("音频页面顶部")
                        Spacer(Modifier.height(900.dp))
                        ListRow(headline="进入音质页面",onClick={ subpage=true })
                    }
                }
            } }
        }
        compose.onNodeWithText("进入音质页面").performScrollTo().performClick()
        compose.onNodeWithText("音质页面").assertIsDisplayed()
        compose.onNodeWithText("音质页面顶部").assertIsDisplayed()
        compose.onNodeWithTag("settings-restart").assertIsDisplayed()
        compose.onNodeWithTag("settings-exit").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("音频页面顶部").assertIsDisplayed()
    }
}
