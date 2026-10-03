package io.wenyou.textquest

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.screens.PlayScreen
import io.wenyou.textquest.ui.screens.RoleSelectionContent
import io.wenyou.textquest.ui.theme.WenYouTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PlayerRoleUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun chooseRoleSaveLoadAndChooseAgainOnRestart() {
        val prefix = "role-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val container = WenYouApp.AppContainer(context)
        runBlocking {
            container.library.upsertCharacter(CharacterData("a", "阿雨", tagline = "旧城的侦探"))
            container.library.upsertCharacter(CharacterData("b", "小星"))
            container.library.upsertCharacter(CharacterData("outsider", "局外人"))
            container.library.upsertStory(Story("role-story", "雨城", characterIds = listOf("a", "b"), nodes = mapOf(
                "start" to StoryNode("start", NodeKind.ENDING, text = "这扇门已经打开。")
            )))
        }
        val stores = mutableListOf<ViewModelStore>()
        fun show(saveId: String) {
            val store = ViewModelStore().also { stores.add(it) }
            val owner = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                override val viewModelStore = store
                override val defaultViewModelProviderFactory get() = compose.activity.defaultViewModelProviderFactory
                override val defaultViewModelCreationExtras get() = compose.activity.defaultViewModelCreationExtras
            }
            compose.runOnIdle { compose.activity.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    WenYouTheme(dynamicColor = false) { PlayScreen(container, rememberNavController(), "role-story", saveId) }
                }
            } }
        }
        try {
            show("new")
            compose.onNodeWithText("选择扮演的角色").assertIsDisplayed()
            compose.onNodeWithTag("role-start").assertIsNotEnabled()
            compose.onNodeWithText("这扇门已经打开。").assertDoesNotExist()
            compose.onNodeWithTag("role-outsider").assertDoesNotExist()
            val preview = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
            File(compose.activity.cacheDir, "role-picker.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.onNodeWithTag("role-b").performClick().assertIsSelected()
            compose.onNodeWithTag("role-start").performClick()
            compose.onNodeWithText("选择扮演的角色").assertDoesNotExist()
            compose.onNodeWithText("这扇门已经打开。").assertIsDisplayed()
            compose.onNodeWithContentDescription("存档").performClick()
            compose.waitUntil(5_000) { container.library.saves.value.isNotEmpty() }
            val save = container.library.saves.value.single()
            assertEquals("b", save.state.playerCharacterId)
            assertEquals("小星", save.state.playerCharacterName)
            show(save.id)
            compose.onNodeWithText("这扇门已经打开。").assertIsDisplayed()
            compose.onNodeWithText("选择扮演的角色").assertDoesNotExist()
            compose.onNodeWithText("再来一次").performScrollTo().performClick()
            compose.onNodeWithText("选择扮演的角色").assertIsDisplayed()
            compose.onNodeWithTag("role-start").assertIsNotEnabled()
            compose.onNodeWithTag("role-free").performClick()
            compose.onNodeWithTag("role-start").performClick()
            compose.onNodeWithContentDescription("存档").performClick()
            compose.waitUntil(5_000) { container.library.saves.value.size == 2 }
            assertTrue(container.library.saves.value.any { it.state.playerCharacterId.isBlank() })
        } finally { compose.runOnIdle { stores.forEach { it.clear() } } }
    }

    @Test fun emptyCastStillOffersFreeIdentityAndCancel() {
        var started: String? = null
        var cancelled = false
        compose.runOnIdle { compose.activity.setContent {
            WenYouTheme { RoleSelectionContent(emptyList(), "", {}, { started = it }, { cancelled = true }) }
        } }
        compose.onNodeWithTag("role-free").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("role-start").performClick()
        compose.runOnIdle { assertEquals("", started) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }
}
