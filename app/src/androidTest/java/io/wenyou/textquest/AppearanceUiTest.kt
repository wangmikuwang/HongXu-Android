package io.wenyou.textquest

import io.wenyou.textquest.ui.common.AppIcons
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import io.wenyou.textquest.data.repo.SettingsStore
import io.wenyou.textquest.ui.common.GlassDock
import io.wenyou.textquest.ui.common.DockItem
import io.wenyou.textquest.ui.screens.AppearanceContent
import io.wenyou.textquest.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun screenshot(name: String) {
        File(compose.activity.cacheDir, "ui-${BuildConfig.VERSION_CODE}-$name").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun boldSwitchImmediatelyChangesRenderedTextAndRestoresWeight() {
        var prefs by mutableStateOf(AppearancePrefs(fontWeight = 300))
        var weight: androidx.compose.ui.text.font.FontWeight? = null
        compose.setContent { WenYouTheme(appearance = prefs) {
            Column {
                Box(Modifier.fillMaxWidth().height(64.dp).background(androidx.compose.ui.graphics.Color.White).testTag("bold-proof")) {
                    io.wenyou.textquest.ui.common.RawText("虹叙故事 ABC", color = androidx.compose.ui.graphics.Color.Black,
                        fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
                        onTextLayout = { weight = it.layoutInput.style.fontWeight })
                }
                Box(Modifier.weight(1f)) {
                    AppearanceContent(prefs, ThemeStyle.MATERIAL, ThemeMode.LIGHT, false, false, emptyList(), "",
                        { change -> prefs = change(prefs) }, {}, {}, {}, {}, {}, {}, {})
                }
            }
        } }
        fun darkPixels(): Int {
            val bitmap = compose.onNodeWithTag("bold-proof").captureToImage().asAndroidBitmap()
            var count = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 128) count++
            }
            bitmap.recycle()
            return count
        }
        val normal = darkPixels()
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("字体加粗"))
        val toggle = compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) and hasAnySibling(hasText("字体加粗"))).onFirst()
        toggle.performClick().assertIsOn()
        compose.runOnIdle { assertTrue(prefs.fontBold); assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, weight) }
        assertTrue("WenKai must visibly render bolder strokes", darkPixels() > normal * 1.05)
        screenshot("font-bold.png")
        toggle.performClick().assertIsOff()
        compose.runOnIdle { assertEquals(300, prefs.fontWeight); assertEquals(androidx.compose.ui.text.font.FontWeight.Light, weight) }
        assertEquals(normal, darkPixels())
    }
    @Test fun bundledWenKaiIsDefaultAndImportedFontStillOverridesIt() {
        val context = compose.activity
        val asset = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.lxgw_wenkai_regular)!!
        assertTrue("Bundled font must contain Chinese glyphs", android.graphics.Paint().apply { typeface = asset }.hasGlyph("叙"))
        val base = androidx.compose.material3.Typography()
        val defaults = appearanceTypography(base, AppearancePrefs(), context)
        assertEquals(DefaultAppFont, defaults.bodyLarge.fontFamily)
        assertEquals(DefaultAppFont, defaults.headlineLarge.fontFamily)
        val font = io.wenyou.textquest.data.AppearanceFiles.importFont(context,
            android.net.Uri.fromFile(File("/system/fonts/Roboto-Regular.ttf")))
        try {
            val imported = appearanceTypography(base, AppearancePrefs(fontFile = font.first), context)
            assertNotEquals(DefaultAppFont, imported.bodyLarge.fontFamily)
            assertEquals(DefaultAppFont, appearanceTypography(base, AppearancePrefs(fontFile = "font-missing.ttf"), context).bodyLarge.fontFamily)
            assertEquals(DefaultAppFont, appearanceTypography(base, AppearancePrefs(), context).bodyLarge.fontFamily)
        } finally { io.wenyou.textquest.data.AppearanceFiles.removeFont(context, font.first) }
    }
    @Test fun importedFontsAndWallpapersAreValidatedAndRemainLocal() {
        val context = compose.activity
        val font = io.wenyou.textquest.data.AppearanceFiles.importFont(context, android.net.Uri.fromFile(File("/system/fonts/Roboto-Regular.ttf")))
        assertTrue(File(io.wenyou.textquest.data.AppearanceFiles.fontDirectory(context), font.first).isFile)
        val corrupt = File(context.cacheDir, "invalid-font.ttf").apply { writeText("invalid font") }
        assertTrue(runCatching { io.wenyou.textquest.data.AppearanceFiles.importFont(context, android.net.Uri.fromFile(corrupt)) }.isFailure)
        val source = File(context.cacheDir, "appearance-wallpaper.png")
        val image = android.graphics.Bitmap.createBitmap(3000, 1500, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val name = io.wenyou.textquest.data.AppearanceFiles.importWallpaper(context, android.net.Uri.fromFile(source))
        val saved = io.wenyou.textquest.data.AppearanceFiles.wallpaper(context, name)!!
        val decoded = android.graphics.BitmapFactory.decodeFile(saved.path)
        assertTrue(maxOf(decoded.width, decoded.height) <= 1440); decoded.recycle()
        assertNull(io.wenyou.textquest.data.AppearanceFiles.wallpaper(context, "../../providers.json"))
        io.wenyou.textquest.data.AppearanceFiles.removeFont(context, font.first); saved.delete(); source.delete(); corrupt.delete()
    }
    @Test fun launcherVariantsSwitchWithoutDisablingTheActivity() {
        val context = compose.activity
        val manager = context.packageManager
        val components = listOf("Default", "Light", "Dark", "Ink").map { android.content.ComponentName(context.packageName, "${context.packageName}.Launcher$it") }
        val original = components.associateWith(manager::getComponentEnabledSetting)
        try {
            io.wenyou.textquest.data.AppearanceFiles.applyLauncher(context, AppearancePrefs(launcherIcon = "ink"), false)
            assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, manager.getComponentEnabledSetting(components.last()))
            assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, manager.getComponentEnabledSetting(components.first()))
            val main = android.content.ComponentName(context, MainActivity::class.java)
            assertNotEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, manager.getComponentEnabledSetting(main))
        } finally { original.forEach { (component, state) -> manager.setComponentEnabledSetting(component, state, android.content.pm.PackageManager.DONT_KILL_APP) } }
    }
    @Test fun theLargestFontAndScaledInterfaceKeepTheDockUsable() {
        val prefs = AppearancePrefs(glassEnabled = true, fontScale = 1.3f, fontBold = true, uiScale = 1.1f, displayScale = 110)
        compose.setContent { WenYouTheme(style = ThemeStyle.APPLE, appearance = prefs) {
            Box(Modifier.fillMaxWidth()) { GlassDock(listOf("主页", "剧情", "角色", "设置").map { DockItem(it, AppIcons.Home) }, 0, {}, Modifier.testTag("large-dock")) }
        } }
        compose.onNode(hasText("角色") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("设置") and hasClickAction()).assertIsDisplayed()
        screenshot("dock-large-font.png")
    }
    @Test fun appearancePersistsWithoutTouchingStoryOrIdentitySettings() {
        compose.setContent { WenYouTheme(appearance = AppearancePrefs(language = "en")) {
            Column {
                io.wenyou.textquest.ui.common.AppText("角色", Modifier.testTag("ui-label"))
                io.wenyou.textquest.ui.common.RawText("角色", Modifier.testTag("story-content"))
            }
        } }
        compose.onNodeWithTag("ui-label").assertTextEquals("Cast")
        compose.onNodeWithTag("story-content").assertTextEquals("角色")
        val id = UUID.randomUUID().toString()
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("appearance-test-$id-$name", mode)
        }
        val store = SettingsStore(context)
        store.setShowLgbt(false); store.setThemeStyle(ThemeStyle.APPLE)
        store.updateAppearance { it.copy(seed = "#FF6688", fontBold = true, fontScale = 1.15f, uiScale = .9f, displayScale = 110, amoled = true, popupStyle = "dialog", splashEnabled = true, language = "en") }
        val restored = SettingsStore(context).state.value
        assertEquals(store.state.value.appearance, restored.appearance)
        assertFalse(restored.showLgbt); assertFalse(restored.prideThemesUnlocked); assertEquals(ThemeStyle.APPLE, restored.themeStyle)
        assertTrue(restored.appearance.glassEnabled)
        context.getSharedPreferences("wenyou_settings", Context.MODE_PRIVATE).edit().putString("appearance_v1", "invalid json").commit()
        assertEquals(AppearancePrefs(), SettingsStore(context).state.value.appearance)
    }
    @Test fun colorIconsKeepBackArrowAndDockFreeOfExtraTiles() {
        var prefs by mutableStateOf(AppearancePrefs(iconStyle = "color"))
        compose.setContent { WenYouTheme(style = ThemeStyle.APPLE, appearance = prefs) {
            Column(Modifier.width(360.dp).background(androidx.compose.ui.graphics.Color.White)) {
                Row {
                    io.wenyou.textquest.ui.common.AppIcon(AppIcons.ArrowBack, "返回",
                        Modifier.size(24.dp).testTag("styled-back"), tint = androidx.compose.ui.graphics.Color.Black)
                    androidx.compose.material3.Icon(AppIcons.ArrowBack, null,
                        Modifier.size(24.dp).testTag("native-back"), tint = androidx.compose.ui.graphics.Color.Black)
                }
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.ui.graphics.Color.White) {
                    Row(Modifier.background(androidx.compose.ui.graphics.Color.Blue)) {
                        io.wenyou.textquest.ui.common.AppIcon(AppIcons.Home, null, Modifier.size(24.dp).testTag("button-glyph"))
                        androidx.compose.material3.Icon(AppIcons.Home, null, Modifier.size(24.dp).testTag("button-native"))
                    }
                }
                GlassDock(listOf("主页", "剧情", "角色", "设置").map { DockItem(it, AppIcons.Home) }, 0, {}, Modifier.testTag("plain-dock"))
            }
        } }
        val back = compose.onNodeWithTag("styled-back").captureToImage().asAndroidBitmap()
        val native = compose.onNodeWithTag("native-back").captureToImage().asAndroidBitmap()
        assertTrue("Back arrow must have native glyph size and no tile", back.sameAs(native))
        val button = compose.onNodeWithTag("button-glyph").captureToImage().asAndroidBitmap()
        val buttonNative = compose.onNodeWithTag("button-native").captureToImage().asAndroidBitmap()
        assertTrue("Colored preferences must preserve solid-button contrast", button.sameAs(buttonNative))
        val colored = compose.onNodeWithTag("plain-dock").captureToImage().asAndroidBitmap()
        compose.runOnIdle { prefs = prefs.copy(iconStyle = "mono") }
        val plain = compose.onNodeWithTag("plain-dock").captureToImage().asAndroidBitmap()
        assertTrue("Dock chrome must not acquire icon tiles from content preferences", colored.sameAs(plain))
        screenshot("plain-navigation-icons.png")
    }

    @Test fun dockClickAndDragSelectWholeItemsWithoutClipping() {
        var selected by mutableIntStateOf(0)
        compose.setContent { WenYouTheme(style = ThemeStyle.APPLE) {
            Box(Modifier.width(360.dp)) { GlassDock(listOf("主页", "剧情", "角色", "设置").map { DockItem(it, AppIcons.Home) }, selected, { selected = it }, Modifier.testTag("dock")) }
        } }
        compose.onNode(hasText("角色") and hasClickAction()).performClick().assertIsSelected()
        assertEquals(2, selected)
        compose.onNodeWithTag("dock").performTouchInput { swipe(centerLeft + androidx.compose.ui.geometry.Offset(12f, 0f), centerRight - androidx.compose.ui.geometry.Offset(12f, 0f), 500) }
        compose.runOnIdle { assertEquals(3, selected) }
        compose.onNode(hasText("设置") and hasClickAction()).assertIsSelected()
        screenshot("dock-whole-item.png")
    }
    @Test fun appearanceOptionsAreWiredAndTheGlassHidesWallpaperColor() {
        var prefs by mutableStateOf(AppearancePrefs(glassEnabled = true))
        compose.setContent { WenYouTheme(style = ThemeStyle.APPLE, appearance = prefs) {
            AppearanceContent(prefs, ThemeStyle.APPLE, ThemeMode.LIGHT, false, false, emptyList(), "", { change -> prefs = change(prefs) }, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("壁纸取色").assertDoesNotExist()
        screenshot("appearance-display.png")
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("图标样式"))
        compose.onNodeWithText("单色图标").performClick()
        compose.onNodeWithText("彩色图标").performClick()
        compose.runOnIdle { assertEquals("color", prefs.iconStyle) }
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("选项弹窗样式"))
        compose.onNodeWithText("跟随选项弹出").performClick()
        compose.onNodeWithText("居中弹窗").performClick()
        compose.runOnIdle { assertEquals("dialog", prefs.popupStyle) }
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("应用语言"))
        compose.onNodeWithText("跟随系统").performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithTag("appearance-list").performScrollToNode(hasText("App language"))
        compose.onNodeWithText("App language").assertIsDisplayed()
        screenshot("appearance-language.png")
    }
}
