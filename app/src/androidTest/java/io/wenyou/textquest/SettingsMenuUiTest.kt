package io.wenyou.textquest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import io.wenyou.textquest.ui.screens.SettingsMenuContent
import io.wenyou.textquest.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
class SettingsMenuUiTest {
 @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
 @Test fun searchFiltersRealSettingsAndOpensTheSelectedCategory() {
  var selected = ""
  var dark by mutableStateOf(false)
  compose.setContent { WenYouTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, appearance = AppearancePrefs(fontScale = 1.3f)) {
   SettingsMenuContent({ selected = it })
  } }
  compose.onNodeWithTag("settings-appearance").assertIsDisplayed()
  compose.onNodeWithTag("settings-search").performTextInput("备份")
  compose.onNodeWithTag("settings-ai").assertDoesNotExist()
  compose.onNodeWithTag("settings-backup").performClick()
  assertEquals("backup", selected)
  compose.onNodeWithTag("settings-search").performTextReplacement("找不到的设置")
  compose.onNodeWithText("没有找到相关设置").assertIsDisplayed()
  compose.onNodeWithContentDescription("清除搜索").performClick()
  compose.onNodeWithTag("settings-ai").assertIsDisplayed()
  for (isDark in listOf(false, true)) {
   compose.runOnIdle { dark = isDark }
   compose.onNodeWithTag("settings-menu").performScrollToNode(hasText("系统与关于"))
   compose.onNodeWithTag("settings-system").performClick()
   assertEquals("system", selected)
   File(compose.activity.cacheDir, "ui-110-settings-menu-$isDark.png").outputStream().use {
    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
   }
  }
 }
}
