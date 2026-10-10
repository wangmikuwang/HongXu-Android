package io.wenyou.textquest

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.ui.screens.AchievementsContent
import io.wenyou.textquest.ui.theme.WenYouTheme
import io.wenyou.textquest.ui.vm.PlayStage
import io.wenyou.textquest.ui.vm.PlayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AchievementsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun entryAndOfflineEndingShowDurableProgress() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.app_name)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("成就馆 ·", substring = true).performClick()
        compose.onNodeWithText("成就馆").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭成就馆").performClick()
        val dir = File(compose.activity.cacheDir, "achievement-test-${UUID.randomUUID()}")
        val library = LocalLibrary(dir)
        val story = Story("test", "测试剧情", nodes = mapOf(
            "start" to StoryNode("start", text = "开场", choices = listOf(ChoiceData("留下", "@self"), ChoiceData("结束", "end"))),
            "end" to StoryNode("end", NodeKind.ENDING, text = "结束")
        ))
        runBlocking { library.upsertStory(story) }
        lateinit var vm: PlayViewModel
        compose.runOnIdle { vm = PlayViewModel("test", "new", library, AiDirector(ChatClient())) { null } }
        // New journeys start at role selection; play as the free identity.
        compose.waitUntil(5_000) { vm.ui.value.stage == PlayStage.ROLE_SELECT }
        compose.runOnIdle { vm.selectPlayerCharacter("") }
        compose.waitUntil(5_000) { vm.ui.value.stage == PlayStage.AUTHORED }
        compose.runOnIdle { repeat(10) { vm.chooseAuthored(0) }; vm.chooseAuthored(1) }
        compose.waitUntil(5_000) { library.achievements.value.count { it.unlockedAt > 0L } == 3 && vm.ui.value.achievementMessages.contains("FIRST_ENDING") }
        assertEquals(11, vm.ui.value.session!!.choicesTaken)
        assertTrue(vm.ui.value.achievementMessages.contains("FIRST_ENDING"))
        assertEquals(library.achievements.value, LocalLibrary(dir).achievements.value)
        compose.runOnIdle {
            compose.activity.setContent {
                val records by library.achievements.collectAsStateWithLifecycle()
                WenYouTheme { AchievementsContent(records, {}) }
            }
        }
        compose.onNodeWithText("已解锁 3 / 7").assertIsDisplayed()
        compose.onNodeWithText("🏆 初次启程").assertIsDisplayed()
        compose.onNodeWithTag("achievements-list").performScrollToNode(hasText("🏆 命运抉择"))
        compose.onNodeWithText("🏆 命运抉择").assertIsDisplayed()
        compose.onNodeWithTag("achievements-list").performScrollToNode(hasText("🏆 旅途终章"))
        compose.onNodeWithText("🏆 旅途终章").assertIsDisplayed()
        compose.onNodeWithTag("achievements-list").performScrollToKey("ENDING_COLLECTOR")
        compose.onNodeWithText("🔒 结局收藏家").assertIsDisplayed()
        compose.onAllNodesWithText("未解锁 · 1 / 3").onFirst().assertExists()
    }
}
