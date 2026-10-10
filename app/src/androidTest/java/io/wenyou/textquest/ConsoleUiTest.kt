package io.wenyou.textquest

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.rememberNavController
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.screens.PlayScreen
import io.wenyou.textquest.ui.theme.WenYouTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** The play screen's options live in the console, which also lets the player set character states by hand. */
class ConsoleUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun consoleHoldsTheOptionsAndSavesHandSetStates() {
        val prefix = "console-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val container = WenYouApp.AppContainer(context)
        runBlocking {
            container.library.upsertCharacter(CharacterData("a", "阿雨"))
            container.library.upsertStory(Story("console-story", "雨城", mode = StoryMode.AI_DIRECTOR, characterIds = listOf("a")))
            container.library.upsertSave(SaveSlot("console-save", "雨城", 1, 1, SessionState("console-story",
                history = listOf(LogEntry(text = "雨还在下。")))))
        }
        compose.runOnIdle { compose.activity.setContent {
            WenYouTheme(dynamicColor = false) { PlayScreen(container, rememberNavController(), "console-story", "console-save") }
        } }
        compose.onNodeWithText("雨还在下。").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()

        compose.onNodeWithTag("open-console").performClick()
        compose.onNodeWithText("控制台").assertIsDisplayed()
        // Edge to edge: the console starts below the status bar instead of under it.
        val statusBar = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        assertTrue(compose.onNodeWithText("控制台").fetchSemanticsNode().boundsInWindow.top >= statusBar)
        for (option in listOf("推进节奏：", "总结并开启新篇章", "🏆 成就馆", "切换 AI 服务", "生成用量与费用统计", "导出对局文本"))
            compose.onNodeWithText(option, substring = true).assertExists()

        compose.onNodeWithTag("console").performScrollToNode(hasTestTag("adjust-a"))
        compose.onNodeWithTag("adjust-a").performClick()
        compose.onNodeWithTag("console").performScrollToNode(hasText("＋好感度"))
        compose.onNodeWithText("＋好感度").performClick()
        compose.onNodeWithTag("console").performScrollToNode(hasTestTag("save-a"))
        compose.onNodeWithTag("save-a").performClick()
        compose.onNodeWithTag("console").performScrollToNode(hasText("❤️ 好感度"))
        compose.onNodeWithText("❤️ 好感度").assertIsDisplayed()

        compose.activity.onBackPressedDispatcher.let { compose.runOnIdle { it.onBackPressed() } }
        compose.onNodeWithText("存档").performClick()
        compose.waitUntil(5_000) {
            container.library.saves.value.single().state.characterStates["a"]?.metrics?.get("affection") == 50.0
        }
        assertEquals("玩家手动调整", container.library.saves.value.single().state.characterStates.getValue("a").lastChangeReason)
    }
}
