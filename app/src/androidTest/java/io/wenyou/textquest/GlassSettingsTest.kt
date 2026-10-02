package io.wenyou.textquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.ui.theme.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun wallpaperSwitchIsHiddenForGlassAndRetainsItsPreference() {
        val settings = (compose.activity.application as WenYouApp).container.settings
        val original = settings.state.value
        try {
            compose.runOnIdle { settings.setThemeStyle(ThemeStyle.MATERIAL) }
            compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
            compose.onNodeWithText("动态取色（壁纸配色）").assertIsDisplayed()
            compose.runOnIdle { settings.setThemeStyle(ThemeStyle.APPLE) }
            compose.onNodeWithText("动态取色（壁纸配色）").assertDoesNotExist()
            compose.runOnIdle { settings.setThemeStyle(ThemeStyle.MATERIAL) }
            compose.onNodeWithText("动态取色（壁纸配色）").assertIsDisplayed()
            compose.runOnIdle { assertEquals(original.dynamicColor, settings.state.value.dynamicColor) }
        } finally {
            compose.runOnIdle { settings.setThemeStyle(original.themeStyle) }
        }
    }
}
