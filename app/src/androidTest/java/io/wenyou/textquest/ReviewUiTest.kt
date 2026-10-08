package io.wenyou.textquest

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.repo.Baseline
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.screens.BaselineCard
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

    @Test fun baselineEditorSavesTheSingleBaselineForThisLibraryOnly() {
        val container = container()
        val text = "测试基调-${UUID.randomUUID()}"
        compose.runOnIdle { compose.activity.setContent { WenYouTheme { BaselineCard(container) } } }
        compose.onNodeWithTag("baseline-field").performTextClearance()
        compose.onNodeWithTag("baseline-field").performTextInput(text)
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { container.library.baseline.value == text }
        assertEquals(text, container.library.currentBaseline())
        assertNotEquals(text, (compose.activity.application as WenYouApp).container.library.currentBaseline())
        compose.onNodeWithText("恢复默认").performClick()
        compose.waitUntil(5_000) { container.library.currentBaseline() == Baseline.DEFAULT }
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
