package io.wenyou.textquest

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.WenYouAppRoot
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryToolsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun container(): WenYouApp.AppContainer {
        val prefix = "library-tools-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        return WenYouApp.AppContainer(context, ChatClient())
    }

    @Test fun searchNarrowsStoriesAndCharactersAndSavesCanBeRenamed() {
        val container = container()
        val story = Story("lt-moon", "检索乙月", subtitle = "月下的约定", nodes = mapOf("start" to StoryNode("start", text = "开场")))
        val save = SaveSlot("lt-save", autoSaveName(story.title, 3), 1L, 2L, SessionState(story.id, "start"))
        runBlocking {
            container.library.upsertStory(story)
            container.library.upsertStory(Story("lt-star", "检索甲星", nodes = mapOf("start" to StoryNode("start", text = "开场"))))
            container.library.upsertCharacter(CharacterData("lt-char", "检索角色丙", tagline = "钟楼守夜人"))
            container.library.upsertSave(save)
        }
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }

        compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("story-search").performTextInput("月下")
        compose.onNodeWithText("检索乙月").assertIsDisplayed()
        compose.onNodeWithText("检索甲星").assertDoesNotExist()

        // The single remaining card's save picker can rename its save.
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("读取存档").performClick()
        compose.onNodeWithText("0 步", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("重命名存档").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("钟楼前的决定")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { container.library.saves.value.single().name == "钟楼前的决定" }
        compose.onNodeWithText("钟楼前的决定").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()

        compose.onNodeWithTag("story-search").performTextClearance()
        compose.onNodeWithTag("story-search").performTextInput("不存在的剧情名")
        compose.onNodeWithText("没有找到", substring = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("角色", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("character-search").performTextInput("守夜人")
        compose.onNodeWithText("检索角色丙").assertIsDisplayed()
        compose.onNodeWithTag("character-search").performTextClearance()
        compose.onNodeWithTag("character-search").performTextInput("不存在的角色名")
        compose.onNodeWithText("没有找到", substring = true).assertIsDisplayed()
    }
}
