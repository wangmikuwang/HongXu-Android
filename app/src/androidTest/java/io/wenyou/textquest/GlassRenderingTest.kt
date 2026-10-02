package io.wenyou.textquest

import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.wenyou.textquest.ui.common.GlassBackdrop
import io.wenyou.textquest.ui.common.liquidGlass
import io.wenyou.textquest.ui.theme.ThemeMode
import io.wenyou.textquest.ui.theme.ThemeStyle
import io.wenyou.textquest.ui.theme.WenYouTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class GlassRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassSamplesAndSoftensTheBackdrop() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val swapped = mutableStateOf(false)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            WenYouTheme(mode = mode.value, style = ThemeStyle.APPLE) {
                GlassBackdrop(content = {
                    Canvas(Modifier.size(200.dp, 80.dp)) {
                        drawRect(if (swapped.value) Color.Blue else Color.Red, size = Size(size.width / 2, size.height))
                        drawRect(if (swapped.value) Color.Red else Color.Blue, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                    }
                }, controls = {
                    Box(Modifier.size(200.dp, 80.dp).testTag("glass").liquidGlass())
                })
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val y = pixels.height / 2
        assertTrue("Light glass must retain the backdrop color instead of washing it out",
            pixels[pixels.width / 4, y].red - pixels[pixels.width / 4, y].blue > 0.45f)
        assertTrue("Blue backdrop must blur across the red boundary",
            pixels[pixels.width / 2 - 2, y].blue > pixels[pixels.width / 4, y].blue + 0.01f)
        assertTrue("Red backdrop must blur across the blue boundary",
            pixels[pixels.width / 2 + 2, y].red > pixels[pixels.width * 3 / 4, y].red + 0.01f)
        compose.runOnIdle { swapped.value = true }
        compose.waitForIdle()
        val updated = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        assertTrue("Glass must update when the content behind it changes",
            updated[pixels.width / 4, y].blue > pixels[pixels.width / 4, y].blue + 0.1f)
        compose.runOnIdle { mode.value = ThemeMode.DARK }
        compose.waitForIdle()
        val dark = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        assertTrue("Dark glass must retain the backdrop color",
            dark[pixels.width / 4, y].blue - dark[pixels.width / 4, y].red > 0.55f)
    }
}
