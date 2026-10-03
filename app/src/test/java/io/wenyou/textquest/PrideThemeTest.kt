package io.wenyou.textquest

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.ui.theme.PrideTheme
import io.wenyou.textquest.ui.theme.appleColors
import io.wenyou.textquest.ui.theme.prideColors
import io.wenyou.textquest.ui.theme.LightColors
import io.wenyou.textquest.ui.theme.DarkColors
import io.wenyou.textquest.ui.theme.readableAccent
import org.junit.Assert.*
import org.junit.Test

class PrideThemeTest {
    @Test fun brandPalettesKeepTextReadableAcrossSurfaceLevels() {
        for (c in listOf(LightColors, DarkColors)) {
            val pairs = listOf(c.onPrimary to c.primary, c.onSecondary to c.secondary,
                c.onTertiary to c.tertiary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondaryContainer to c.secondaryContainer, c.onTertiaryContainer to c.tertiaryContainer,
                c.onBackground to c.background) + listOf(c.surface, c.surfaceContainerLow,
                c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest).flatMap {
                listOf(c.onSurface to it, c.onSurfaceVariant to it)
            }
            for ((foreground, background) in pairs) {
                val a = foreground.luminance(); val b = background.luminance()
                assertTrue("Unreadable brand text on $background",
                    (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
            }
            assertEquals(5, listOf(c.surfaceContainerLowest, c.surfaceContainerLow,
                c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest).distinct().size)
        }
    }
    @Test fun namedOrientationsHavePalettesAndUnknownPreferencesFallBack() {
        SexualOrientation.entries.filter { it != SexualOrientation.UNKNOWN }.forEach {
            assertNotNull("Missing ${it.name}", PrideTheme.fromStored(it.name))
        }
        assertEquals(20, PrideTheme.entries.size)
        assertTrue(PrideTheme.entries.all { it.description.isNotBlank() })
        assertNull(PrideTheme.fromStored("broken"))
        assertNull(PrideTheme.fromStored(null))
    }

    @Test fun allPalettesKeepReadableTextInBothStylesAndModes() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        for (dark in listOf(false, true)) for (base in listOf(appleColors(dark), if (dark) DarkColors else LightColors)) {
            for (theme in PrideTheme.entries) {
                val c = prideColors(base, theme)
                val accents = listOf(c.primary, c.secondary, c.tertiary, c.primaryContainer, c.secondaryContainer, c.tertiaryContainer, c.inversePrimary)
                val flagColors = theme.colors + if (theme == PrideTheme.DEMISEXUAL) listOf(Color.Black) else emptyList()
                assertTrue("Missing original flag colors: ${theme.name}", accents.containsAll(flagColors.distinct()))
                assertTrue("Non-flag accent: ${theme.name}", accents.all { it in flagColors })
                assertEquals(theme.accent, c.primary)
                assertEquals(Color.Transparent, c.surfaceTint)
                for (accent in listOf(c.primary, c.secondary, c.tertiary)) {
                    val readable = c.readableAccent(accent)
                    assertTrue("Flag accent must remain exact or use neutral text", readable == accent || readable == c.onSurface)
                    for (surface in listOf(c.background, c.surface, c.surfaceContainerLowest,
                        c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)) {
                        assertTrue("Unreadable ${theme.name} accent text, dark=$dark", contrast(readable, surface) >= 4.5f)
                    }
                }
                for ((foreground, background) in listOf(c.onPrimary to c.primary, c.onSecondary to c.secondary,
                    c.onTertiary to c.tertiary, c.onPrimaryContainer to c.primaryContainer,
                    c.onSecondaryContainer to c.secondaryContainer, c.onTertiaryContainer to c.tertiaryContainer,
                    c.onBackground to c.background, c.onSurface to c.surface, c.onSurface to c.surfaceContainerHighest)) {
                    assertTrue("${theme.name}, dark=$dark, contrast=${contrast(foreground, background)}", contrast(foreground, background) >= 4.5f)
                }
            }
        }
    }
}
