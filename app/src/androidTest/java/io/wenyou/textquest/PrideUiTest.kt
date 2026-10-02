package io.wenyou.textquest

import android.content.Context
import android.content.ContextWrapper
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

    @Test fun tenThenFiveTapsUnlockPersistentThemesWithoutChangingContentPrefs() {
        val prefix = "pride-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val container = WenYouApp.AppContainer(context)
        val original = container.settings.state.value
        container.settings.setPrideTheme(PrideTheme.TRANS)
        assertNull(container.settings.state.value.prideTheme)
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
        compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).performClick()
        val label = compose.onNodeWithTag("pride-filter")
        label.performScrollTo()
        repeat(9) { label.performClick() }
        compose.onNodeWithText("骄傲旗帜馆").assertDoesNotExist()
        label.performClick()
        compose.onNodeWithText("骄傲旗帜馆").assertIsDisplayed()
        compose.onNodeWithContentDescription("男同性恋旗帜").assertIsDisplayed()
        compose.onNodeWithContentDescription("女同性恋旗帜").assertIsDisplayed()
        compose.onNodeWithContentDescription("跨性别旗帜").assertIsDisplayed()
        repeat(4) { compose.onNodeWithTag("pride-gallery-tap").performClick() }
        compose.onNodeWithText("应用配色").assertDoesNotExist()
        compose.onNodeWithTag("pride-gallery-tap").performClick()
        compose.onNodeWithText("已解锁 20 款旗帜配色", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("pride-flags").performScrollToKey("TRANS")
        compose.onAllNodesWithText("应用配色").onFirst().performClick()
        compose.onNodeWithText("关闭旗帜馆").performClick()
        compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("旗帜配色").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("动态取色（壁纸配色）").assertDoesNotExist()
        val saved = container.settings.state.value
        assertTrue(saved.prideThemesUnlocked)
        assertNotNull(saved.prideTheme)
        assertEquals(saved.prideTheme, SettingsStore(context).state.value.prideTheme)
        assertEquals(15, SettingsStore(context).state.value.prideTapCount)
        assertEquals(original.showLgbt, saved.showLgbt)
        assertEquals(original.adultContent, saved.adultContent)
        assertEquals(original.contentUnlocked, saved.contentUnlocked)
        compose.runOnIdle { container.settings.setPrideTheme(null) }
        compose.onNodeWithText("动态取色（壁纸配色）").performScrollTo().assertIsDisplayed()
        assertEquals(original.dynamicColor, container.settings.state.value.dynamicColor)
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
