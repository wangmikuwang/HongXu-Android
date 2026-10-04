package io.wenyou.textquest

import androidx.compose.ui.graphics.toArgb
import io.wenyou.textquest.ui.theme.*
import io.wenyou.textquest.colorutilities.contrast.Contrast
import io.wenyou.textquest.colorutilities.utils.ColorUtils
import org.junit.Assert.*
import org.junit.Test

class AppearancePolicyTest {
    @Test fun corruptAndOutOfRangePreferencesCannotBreakTheInterface() {
        val prefs = AppearancePrefs(seed = "bad", fontScale = Float.NaN, uiScale = Float.POSITIVE_INFINITY,
            displayScale = 500, fontWeight = 9000, fontFile = "../../credentials", roleColors = mapOf("light_text" to "oops")).normalized()
        assertEquals("#0066CC", prefs.seed); assertEquals(1f, prefs.fontScale); assertEquals(1f, prefs.uiScale)
        assertEquals(120, prefs.displayScale); assertEquals(0, prefs.fontWeight); assertEquals("", prefs.fontFile); assertTrue(prefs.roleColors.isEmpty())
        assertNull(normalizeHex("#123")); assertEquals("#ABCDEF", normalizeHex(" abcdef "))
    }
    @Test fun everyPaletteAndSpecificationProducesUsableForegroundsInBothModes() {
        for (style in listOf("tonal", "vibrant", "expressive", "neutral", "mono", "fidelity", "content", "rainbow", "fruit")) {
            for (spec in listOf("2021", "2025")) for (dark in listOf(false, true)) {
                val scheme = customColors(if (dark) DarkColors else LightColors, AppearancePrefs(seed = "#00A99D", paletteStyle = style, colorSpec = spec), dark)
                val ratio = Contrast.ratioOfTones(ColorUtils.lstarFromArgb(scheme.primary.toArgb()), ColorUtils.lstarFromArgb(scheme.onPrimary.toArgb()))
                assertTrue("$style/$spec/$dark foreground contrast $ratio", ratio >= 4.4)
                assertNotEquals(scheme.background, scheme.onBackground)
            }
        }
    }
    @Test fun advancedLightAndDarkRolesRemainIndependent() {
        val prefs = AppearancePrefs(advancedColors = true, roleColors = mapOf("light_background" to "#EEDDCC", "dark_background" to "#001122", "light_control" to "#123456"))
        assertEquals(hexArgb("#EEDDCC"), overrideColors(LightColors, prefs, false).background.toArgb())
        assertEquals(hexArgb("#001122"), overrideColors(DarkColors, prefs, true).background.toArgb())
        assertEquals(DarkColors.primary, overrideColors(DarkColors, prefs, true).primary)
        val yellow = overrideColors(LightColors, prefs.copy(roleColors = mapOf("light_control" to "#FFFF00")), false)
        assertEquals(androidx.compose.ui.graphics.Color.Black, yellow.onPrimary)
    }
}
