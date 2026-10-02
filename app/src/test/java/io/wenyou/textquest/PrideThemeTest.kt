package io.wenyou.textquest

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.ui.theme.PrideTheme
import io.wenyou.textquest.ui.theme.appleColors
import io.wenyou.textquest.ui.theme.prideColors
import org.junit.Assert.*
import org.junit.Test

class PrideThemeTest {
    @Test fun namedOrientationsHavePalettesAndUnknownPreferencesFallBack() {
        SexualOrientation.entries.filter { it != SexualOrientation.UNKNOWN }.forEach {
            assertNotNull("Missing ${it.name}", PrideTheme.fromStored(it.name))
        }
        assertEquals(20, PrideTheme.entries.size)
        assertNull(PrideTheme.fromStored("broken"))
        assertNull(PrideTheme.fromStored(null))
    }

    @Test fun allPalettesKeepReadableTextInBothStylesAndModes() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        for (dark in listOf(false, true)) for (base in listOf(appleColors(dark), if (dark) darkColorScheme() else lightColorScheme())) {
            for (theme in PrideTheme.entries) {
                val c = prideColors(base, theme, dark)
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
