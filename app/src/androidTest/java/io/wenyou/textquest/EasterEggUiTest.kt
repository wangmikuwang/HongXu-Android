package io.wenyou.textquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EasterEggUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun discoverThreeSurprisesWithoutChangingSettingsOrAchievements() {
        assertEquals("虹叙", compose.activity.packageManager.getApplicationLabel(compose.activity.applicationInfo).toString())
        compose.waitUntil(5_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.app_name)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("虹叙").assertIsDisplayed()
        val container = (compose.activity.application as WenYouApp).container
        val settings = container.settings.state.value
        val achievements = container.library.achievements.value
        compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("系统与关于").performClick()
        val title = compose.onNodeWithText(compose.activity.getString(R.string.app_name))
        repeat(4) { title.performClick() }
        compose.onNodeWithText("你发现了彩蛋！").assertDoesNotExist()
        title.performClick()
        compose.onNodeWithText("🎬 幕后导演", substring = true).assertIsDisplayed()
        compose.onNodeWithText("收下惊喜").performClick()
        title.performClick()
        compose.onNodeWithText("你发现了彩蛋！").assertDoesNotExist()
        title.performTouchInput { longClick() }
        compose.onNodeWithText("🪄 第四面墙", substring = true).assertIsDisplayed()
        compose.onNodeWithText("收下惊喜").performClick()
        // A long press resets any unfinished tap sequence.
        repeat(4) { title.performClick() }
        compose.onNodeWithText("你发现了彩蛋！").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("主页", useUnmergedTree = true).performClick()
        compose.onNodeWithText("成就馆 ·", substring = true).performClick()
        compose.onNodeWithText("成就馆").performTouchInput { longClick() }
        compose.onNodeWithText("🏅 隐藏奖杯：好奇心万岁", substring = true).assertIsDisplayed()
        compose.onNodeWithText("收下惊喜").performClick()
        compose.onNodeWithText("成就馆").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭成就馆").performClick()
        compose.runOnIdle {
            assertEquals(settings, container.settings.state.value)
            assertEquals(achievements, container.library.achievements.value)
        }
    }
}
