package io.wenyou.textquest

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.common.StoryBranchTreeDialog
import io.wenyou.textquest.ui.theme.WenYouTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StoryBranchTreeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapsingAndSelectingANodeWorks() {
        val story = Story(id = "tree-test", title = "树状图测试", nodes = linkedMapOf(
            "start" to StoryNode(id = "start", title = "开场", choices = listOf(ChoiceData("左侧路线", "left"), ChoiceData("右侧路线", "right"))),
            "left" to StoryNode(id = "left", title = "左侧结局", kind = NodeKind.ENDING),
            "right" to StoryNode(id = "right", title = "右侧结局", kind = NodeKind.ENDING)))
        val open = mutableStateOf(true)
        var selected = ""
        compose.setContent {
            WenYouTheme {
                if (open.value) StoryBranchTreeDialog(story, onEdit = { selected = it; open.value = false }, onDismiss = { open.value = false })
            }
        }
        compose.onNodeWithText("左侧结局").assertIsDisplayed()
        compose.onNodeWithText("折叠分支").performClick()
        compose.onNodeWithText("左侧结局").assertDoesNotExist()
        compose.onNodeWithText("展开分支").assertIsDisplayed()
        compose.onNodeWithText("全部展开").performClick()
        compose.onNodeWithText("右侧结局").performClick()
        compose.runOnIdle { assertEquals("right", selected) }
        compose.onNodeWithText("剧情分支图").assertDoesNotExist()
    }

    @Test fun playMapMarksCurrentNodeAndIsReadOnly() {
        val story = Story(id = "tree-play", title = "游玩分支图", nodes = linkedMapOf(
            "start" to StoryNode(id = "start", title = "开场", choices = listOf(ChoiceData("左侧路线", "left"), ChoiceData("右侧路线", "right"))),
            "left" to StoryNode(id = "left", title = "左侧结局", kind = NodeKind.ENDING),
            "right" to StoryNode(id = "right", title = "右侧结局", kind = NodeKind.ENDING)))
        val open = mutableStateOf(true)
        compose.setContent {
            WenYouTheme {
                if (open.value) StoryBranchTreeDialog(story, onEdit = null, onDismiss = { open.value = false }, currentNodeId = "start", visited = setOf("start", "left"))
            }
        }
        compose.onNodeWithText("已标出当前位置", substring = true).assertIsDisplayed()
        // The hint and the current node marker.
        compose.onAllNodesWithText("当前位置", substring = true).assertCountEquals(2)
        compose.onNodeWithTag("branch-progress").assertTextEquals("已到达 2 / 3 个节点 · 已解锁结局 1 / 2")
        compose.onNodeWithText("结局 · 🏆 已解锁").assertIsDisplayed()
        // Nodes are not editable while playing: tapping keeps the map open.
        compose.onNodeWithText("右侧结局").performClick()
        compose.onNodeWithText("剧情分支图").assertIsDisplayed()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("剧情分支图").assertDoesNotExist()
    }
}
