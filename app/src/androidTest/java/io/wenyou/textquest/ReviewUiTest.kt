package io.wenyou.textquest

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.model.BottomRule
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.screens.BottomRulesScreen
import io.wenyou.textquest.ui.screens.BottomRuleEditScreen
import io.wenyou.textquest.ui.theme.ThemeStyle
import io.wenyou.textquest.ui.theme.WenYouTheme
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun container(): WenYouApp.AppContainer {
        val prefix = "review-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        return WenYouApp.AppContainer(context)
    }

    @Test fun ruleListAndEditorUseTheSuppliedLibrary() {
        val container = container()
        val name = "isolated-${UUID.randomUUID()}"
        val rule = BottomRule(id = "test-rule", name = name, content = "测试规则")
        runBlocking { container.library.upsertBottomRule(rule) }
        compose.runOnIdle { compose.activity.setContent { WenYouTheme {
            BottomRulesScreen(container, rememberNavController())
        } } }
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.runOnIdle { compose.activity.viewModelStore.clear(); compose.activity.setContent { WenYouTheme {
            BottomRuleEditScreen(container, rememberNavController(), rule.id)
        } } }
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("测试规则").assertExists()
        assertEquals(rule, container.library.bottomRules.value.single())
        assertFalse((compose.activity.application as WenYouApp).container.library.bottomRules.value.any { it.id == rule.id })
    }

    @Test fun navigationLabelsFollowTheChosenLanguageInBothStyles() {
        val container = container()
        container.settings.updateAppearance { it.copy(language = "en") }
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
        for (style in listOf(ThemeStyle.MATERIAL, ThemeStyle.APPLE)) {
            compose.runOnIdle { container.settings.setThemeStyle(style) }
            for (label in listOf("Home", "Stories", "Cast", "Settings")) {
                compose.onNodeWithText(label).assertIsDisplayed()
                compose.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
            }
        }
    }
}
