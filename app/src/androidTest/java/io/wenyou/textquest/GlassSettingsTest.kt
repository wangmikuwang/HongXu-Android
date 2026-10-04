package io.wenyou.textquest

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.ui.screens.AppearanceContent
import io.wenyou.textquest.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun wallpaperSwitchIsHiddenForGlassAndRetainsItsPreference() {
        var style by mutableStateOf(ThemeStyle.MATERIAL)
        var prefs by mutableStateOf(AppearancePrefs(glassEnabled = false, colorSource = "wallpaper"))
        compose.setContent { WenYouTheme(style = style, appearance = prefs) {
            AppearanceContent(prefs, style, ThemeMode.LIGHT, true, false, emptyList(), "",
                { change -> prefs = change(prefs) }, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("主题颜色来源"))
        compose.onNodeWithText("壁纸取色").assertIsDisplayed()
        compose.runOnIdle { style = ThemeStyle.APPLE }
        compose.onNodeWithText("壁纸取色").assertDoesNotExist()
        compose.runOnIdle { style = ThemeStyle.MATERIAL }
        compose.onNodeWithText("壁纸取色").assertIsDisplayed()
        compose.runOnIdle { assertEquals("wallpaper", prefs.colorSource) }
    }
}
