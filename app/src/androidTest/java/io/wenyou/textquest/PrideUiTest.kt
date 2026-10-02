package io.wenyou.textquest

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.wenyou.textquest.data.repo.SettingsStore
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.common.PrideFlag
import io.wenyou.textquest.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PrideUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun silentOrderedUnlockAndPermanentSecondarySettings() {
        val prefix = "pride-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val prefs = context.getSharedPreferences("wenyou_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("pride_tag_taps", 14).putString("pride_theme", "TRANS")
            .putBoolean("adult_content", false).putBoolean("content_unlocked_v1", true).commit()
        val container = WenYouApp.AppContainer(context)
        val original = container.settings.state.value
        assertFalse(original.prideThemesUnlocked)
        assertNull(original.prideTheme)
        container.settings.setPrideTheme(PrideTheme.TRANS)
        assertNull(container.settings.state.value.prideTheme)
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
        compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).performClick()
        val label = compose.onNodeWithTag("pride-filter")
        label.performScrollTo()
        repeat(10) { label.performClick() }
        compose.onNodeWithText("旗帜墙").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        val version = compose.onNodeWithTag("pride-version")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("pride-version"))
        repeat(9) { version.performClick() }
        compose.onNodeWithText("旗帜墙").assertDoesNotExist()
        version.performClick()
        compose.onNodeWithText("旗帜墙").assertIsDisplayed()
        compose.onNodeWithText(PrideTheme.GAY.description).assertExists()
        compose.onNodeWithText(PrideTheme.LESBIAN.description).assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(compose.activity.cacheDir, "pride-grid-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val first = compose.onNodeWithTag("pride-flag-GAY").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("pride-flag-LESBIAN").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithTag("pride-flag-TRANS").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left < second.left && first.top == second.top && third.top > first.top)
        compose.onNodeWithTag("pride-flag-LESBIAN").performClick()
        compose.onNodeWithText("顺序", substring = true).assertDoesNotExist()
        compose.onNodeWithText("已完成", substring = true).assertDoesNotExist()
        compose.onNodeWithText("已点击", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("pride-enabled").assertDoesNotExist()
        assertFalse(container.settings.state.value.prideThemesUnlocked)
        PrideTheme.entries.forEachIndexed { index, theme ->
            compose.onNodeWithTag("pride-flags").performScrollToIndex(index)
            compose.onNodeWithTag("pride-flag-${theme.name}").performClick()
            assertEquals(index == PrideTheme.entries.lastIndex, container.settings.state.value.prideThemesUnlocked)
        }
        compose.onNodeWithText("已解锁", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("pride-enabled").assertIsOn()
        compose.onNodeWithTag("pride-content").assertIsOn()
        compose.onNodeWithTag("pride-flags").performScrollToIndex(2)
        compose.onNodeWithTag("pride-flag-TRANS").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("旗帜配色"))
        compose.onNodeWithText("旗帜配色").assertIsDisplayed()
        compose.onNodeWithText("动态取色（壁纸配色）").assertDoesNotExist()
        val saved = container.settings.state.value
        assertTrue(saved.prideThemesUnlocked)
        assertEquals(PrideTheme.TRANS, saved.prideTheme)
        assertEquals(saved.prideTheme, SettingsStore(context).state.value.prideTheme)
        assertEquals(original.showLgbt, saved.showLgbt)
        assertEquals(original.adultContent, saved.adultContent)
        assertEquals(original.contentUnlocked, saved.contentUnlocked)
        compose.runOnIdle { container.settings.setPrideTheme(null) }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("动态取色（壁纸配色）"))
        compose.onNodeWithText("动态取色（壁纸配色）").assertIsDisplayed()
        assertEquals(original.dynamicColor, container.settings.state.value.dynamicColor)
        container.settings.setPrideTheme(PrideTheme.TRANS)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("pride-version"))
        version.performClick()
        compose.onNodeWithText("旗帜墙").assertIsDisplayed()
        compose.onNodeWithTag("pride-enabled").performClick().assertIsOff()
        assertNull(container.settings.state.value.prideTheme)
        assertFalse(SettingsStore(context).state.value.prideThemesEnabled)
        compose.onNodeWithTag("pride-content").performClick().assertIsOff()
        assertFalse(container.settings.state.value.showLgbt)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("动态取色（壁纸配色）"))
        compose.onNodeWithText("旗帜配色").assertDoesNotExist()
        compose.onNodeWithText("动态取色（壁纸配色）").assertIsDisplayed()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("pride-version"))
        compose.onNodeWithText("显示 LGBT（LGBTQ+）内容").assertDoesNotExist()
        version.performClick()
        compose.onNodeWithTag("pride-enabled").performClick().assertIsOn()
        compose.onNodeWithTag("pride-content").assertIsOff()
        assertEquals(PrideTheme.TRANS, container.settings.state.value.prideTheme)
        compose.onNodeWithTag("pride-flags").performScrollToIndex(0)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(compose.activity.cacheDir, "pride-grid-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        prefs.edit().putInt("pride_unlock_version", BuildConfig.VERSION_CODE - 1).commit()
        val upgraded = SettingsStore(context)
        assertTrue(upgraded.state.value.prideThemesUnlocked)
        assertTrue(upgraded.state.value.prideThemesEnabled)
        assertEquals(PrideTheme.TRANS, upgraded.state.value.prideTheme)
        assertFalse(upgraded.state.value.showLgbt)
        assertEquals(original.adultContent, upgraded.state.value.adultContent)
        assertEquals(original.dynamicColor, upgraded.state.value.dynamicColor)
        // Users who already unlocked the old entry keep their unlock after upgrading.
        prefs.edit().remove("pride_unlocked").putInt("pride_tag_taps", 15).commit()
        assertTrue(SettingsStore(context).state.value.prideThemesUnlocked)
        val style = mutableStateOf(ThemeStyle.MATERIAL)
        compose.runOnIdle { compose.activity.setContent {
            WenYouTheme(mode = if (style.value == ThemeStyle.APPLE) ThemeMode.DARK else ThemeMode.LIGHT,
                dynamicColor = true, style = style.value, prideTheme = PrideTheme.TRANS) {
                Button(onClick = {}, modifier = Modifier.testTag("raw-flag-button")) { Text("原色") }
            }
        } }
        for (appearance in listOf(ThemeStyle.MATERIAL, ThemeStyle.APPLE)) {
            compose.runOnIdle { style.value = appearance }
            val image = compose.onNodeWithTag("raw-flag-button").captureToImage()
            assertEquals(PrideTheme.TRANS.accent.toArgb(), image.toPixelMap()[(image.width * 0.1f).toInt(), image.height / 2].toArgb())
        }
    }

    @Test fun flagsRenderStripesTriangleAndRingInsteadOfEmojiFallbacks() {
        val theme = mutableStateOf(PrideTheme.GAY)
        compose.runOnIdle { compose.activity.setContent { WenYouTheme { PrideFlag(theme.value, Modifier.width(180.dp).testTag("flag")) } } }
        fun expect(flag: PrideTheme, x: Float, y: Float, argb: Long) {
            compose.runOnIdle { theme.value = flag }
            val image = compose.onNodeWithTag("flag").captureToImage()
            val pixel = image.toPixelMap()[(image.width * x).toInt(), (image.height * y).toInt()]
            assertEquals(flag.name, Color(argb).toArgb(), pixel.toArgb())
        }
        expect(PrideTheme.GAY, 0.8f, 0.07f, 0xFF078D70)
        expect(PrideTheme.LESBIAN, 0.8f, 0.9f, 0xFFA30262)
        expect(PrideTheme.TRANS, 0.8f, 0.5f, 0xFFFFFFFF)
        expect(PrideTheme.BI, 0.8f, 0.3f, 0xFFD60270)
        expect(PrideTheme.BI, 0.8f, 0.5f, 0xFF9B4F96)
        expect(PrideTheme.DEMISEXUAL, 0.05f, 0.5f, 0xFF000000)
        expect(PrideTheme.DEMISEXUAL, 0.8f, 0.5f, 0xFF800080)
        expect(PrideTheme.INTERSEX, 0.5f, 0.5f, 0xFFFFD800)
        expect(PrideTheme.INTERSEX, 0.668f, 0.5f, 0xFF7902AA)
    }
}
