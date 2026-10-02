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
}
