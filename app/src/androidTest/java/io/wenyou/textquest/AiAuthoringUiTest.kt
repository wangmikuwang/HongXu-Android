package io.wenyou.textquest

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.*
import io.wenyou.textquest.ui.theme.ThemeStyle
import io.wenyou.textquest.ui.theme.ThemeMode
import io.wenyou.textquest.ui.theme.PrideTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.screens.CharacterEditScreen
import io.wenyou.textquest.ui.theme.WenYouTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AiAuthoringUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val generated = """{"story":{"title":"雨城","worldSummary":"旧物留有记忆","opening":"门响了","directorExtra":"尊重选择","initialVariables":{"clues":0}},"characters":[{"name":"阿雨","personality":"守约","background":"旧城居民","extraPrompt":"遵循身份","bottomPrompt":"不伤害无辜","initial":{"metrics":{"trust":30},"description":"灰色风衣"},"bottomRules":[{"name":"守约","content":"信守承诺"}]}]}"""

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun container(ok: OkHttpClient): WenYouApp.AppContainer {
        val prefix = "authoring-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        return WenYouApp.AppContainer(context, ChatClient(ok)).also { runBlocking {
            it.library.upsertProvider(ApiProfile("mock", "测试服务", baseUrl = "https://example.com", model = "fixture"))
        } }
    }
    private fun client() = OkHttpClient.Builder().addInterceptor { chain ->
        val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer)
        val body = AppJson.parseToJsonElement(buffer.readUtf8()).jsonObject
        val user = body.getValue("messages").jsonArray.last().jsonObject.getValue("content").jsonPrimitive.content
        val content = if (user.contains("原稿：")) {
            val original = AppJson.decodeFromString(AppBundle.serializer(), user.substringAfter("原稿："))
            assertTrue(original.providers.isEmpty()); assertTrue(original.saves.isEmpty())
            when {
                user.substringBefore("\n原稿：").contains("晴城") -> """{"story":{"title":"晴城"}}"""
                user.substringBefore("\n原稿：").contains("奇幻") -> """{"story":{"genre":"奇幻"}}"""
                else -> """{"characters":[{"id":"${original.characters.single().id}","greeting":"欢迎"}]}"""
            }
        } else generated
        val response = buildJsonObject { putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } } }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toString().toResponseBody()).build()
    }.build()

    @Test fun createRevisePreviewSaveAndReviseStoryForm() {
        val ok = client()
        try {
            val container = container(ok)
            compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
            compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).performClick()
            compose.onNodeWithText("AI 创建").performClick()
            compose.onNodeWithTag("creation-idea").performTextInput("雨城的侦探")
            compose.onNodeWithTag("creation-confirm").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("保存并编辑").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("creation-revision").performScrollTo().performTextInput("剧情改为晴城")
            compose.onNodeWithText("修改草稿").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("📖 晴城").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(container.library.stories.value.isEmpty())
            compose.onNodeWithTag("creation-confirm").performClick()
            compose.waitUntil(10_000) { container.library.stories.value.size == 1 }
            compose.onNodeWithText("一句话修改").performClick()
            compose.onNodeWithTag("revision-instruction").performTextInput("题材改为奇幻")
            compose.onNodeWithTag("revision-confirm").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("应用到表单").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("", container.library.stories.value.single().genre)
            compose.onNodeWithTag("revision-confirm").performClick()
            assertEquals("", container.library.stories.value.single().genre)
            compose.onNodeWithContentDescription("保存剧情").performClick()
            compose.waitUntil(10_000) { container.library.stories.value.single().genre == "奇幻" }
            val story = container.library.stories.value.single()
            assertEquals("晴城", story.title)
            assertEquals("尊重选择", story.ai.directorExtra)
            assertEquals(container.library.characters.value.single().id, story.characterIds.single())
            assertEquals(container.library.bottomRules.value.single().id, container.library.characters.value.single().bottomRuleIds.single())
        } finally { ok.dispatcher.executorService.shutdownNow(); ok.connectionPool.evictAll() }
    }

    @Test fun homeSettingsAndLibraryTagsKeepTheirActionsInBothAppearances() {
        val ok = client()
        try {
            val container = container(ok)
            runBlocking {
                container.library.upsertCharacter(CharacterData("c", "阿雨"))
                container.library.upsertStory(Story("s", "雨城", genre = "悬疑", mode = StoryMode.AI_DIRECTOR, characterIds = listOf("c"),
                    nodes = mapOf("start" to StoryNode("start", text = "雨落窗边"))))
            }
            compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
            compose.onNodeWithText("开始剧情").assertIsDisplayed()
            for (style in listOf(ThemeStyle.MATERIAL, ThemeStyle.APPLE)) {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    compose.runOnIdle {
                        container.settings.setDynamicColor(false)
                        container.settings.setThemeStyle(style)
                        container.settings.setThemeMode(mode)
                    }
                    compose.onNodeWithText("开始剧情").assertIsDisplayed()
                    compose.onNodeWithText("新建剧情").assertIsDisplayed()
                    screenshot("home-${style.name.lowercase()}-${mode.name.lowercase()}.png")
                }
            }
            compose.runOnIdle {
                container.settings.setThemeStyle(ThemeStyle.MATERIAL)
                container.settings.setThemeMode(ThemeMode.LIGHT)
            }
            compose.runOnIdle { container.settings.unlockPrideThemes() }
            for (theme in listOf(PrideTheme.TRANS, PrideTheme.PAN, PrideTheme.STRAIGHT)) {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    compose.runOnIdle {
                        container.settings.setThemeMode(mode)
                        container.settings.setPrideTheme(theme)
                    }
                    compose.onNodeWithText("开始剧情").assertIsDisplayed()
                    compose.onNodeWithText("全部剧情").assertIsDisplayed()
                    screenshot("home-pride-${theme.name.lowercase()}-${mode.name.lowercase()}.png")
                }
            }
            compose.runOnIdle {
                container.settings.setPrideTheme(null)
                container.settings.setThemeMode(ThemeMode.LIGHT)
            }
            screenshot("home-unified-preview.png")
            compose.onNodeWithText("AI 创建").performClick()
            compose.onNodeWithText("AI 一句话创建").assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).performClick()
            compose.onNodeWithText("悬疑").assertExists()
            compose.onNodeWithText("1 位人物").assertExists()
            compose.onNodeWithText("分支剧本").performClick().assertIsSelected()
            compose.onNodeWithText("雨城").assertDoesNotExist()
            compose.onAllNodesWithText("全部")[0].performClick()
            compose.onNodeWithText("雨城").assertExists()
            compose.onNodeWithContentDescription("角色", useUnmergedTree = true).performClick()
            compose.onNodeWithText("参演剧情 · 1").assertExists()
            compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
            for (style in listOf(ThemeStyle.MATERIAL, ThemeStyle.APPLE)) {
                compose.runOnIdle {
                    container.settings.setThemeStyle(style)
                    container.settings.setThemeMode(ThemeMode.DARK)
                }
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("默认服务"))
                compose.onNodeWithText("默认服务").assertIsDisplayed()
                compose.onNodeWithText("系统通知设置").assertExists()
                screenshot("settings-${style.name.lowercase()}-preview.png")
            }
        } finally { ok.dispatcher.executorService.shutdownNow(); ok.connectionPool.evictAll() }
    }

    @Test fun characterRevisionCanBeDiscardedAndOnlyExplicitSaveWrites() {
        val ok = client()
        try {
            val container = container(ok)
            val character = CharacterData("c", "阿雨", greeting = "请进", initial = CharacterState(description = "灰色风衣"))
            runBlocking { container.library.upsertCharacter(character) }
            compose.runOnIdle { compose.activity.setContent { WenYouTheme { CharacterEditScreen(container, rememberNavController(), "c") } } }
            fun preview() {
                compose.onNodeWithText("一句话修改").performClick()
                compose.onNodeWithTag("revision-instruction").performTextInput("招呼改为欢迎")
                compose.onNodeWithTag("revision-confirm").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("应用到表单").fetchSemanticsNodes().isNotEmpty() }
            }
            preview()
            compose.onNodeWithText("关闭").performClick()
            assertEquals(character, container.library.characters.value.single())
            preview()
            compose.onNodeWithTag("revision-confirm").performClick()
            assertEquals(character, container.library.characters.value.single())
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
            compose.onNodeWithText("保存角色").performClick()
            compose.waitUntil(10_000) { container.library.characters.value.single().greeting == "欢迎" }
            assertEquals(character.copy(greeting = "欢迎"), container.library.characters.value.single())
        } finally { ok.dispatcher.executorService.shutdownNow(); ok.connectionPool.evictAll() }
    }
}
