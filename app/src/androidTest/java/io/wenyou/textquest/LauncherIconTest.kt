package io.wenyou.textquest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LauncherIconTest {
    @Test fun rainbowUsesFlagColorsAndLeavesPortraitOutsideQuarterUnchanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun render(id: Int): Bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
            context.getDrawable(id)!!.apply { setBounds(0, 0, 1080, 1080); draw(Canvas(bitmap)) }
        }
        val original = render(R.drawable.ic_launcher_fg)
        val rainbow = render(R.drawable.ic_launcher_rainbow)
        val colors = listOf(0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982)
        colors.forEachIndexed { i, expected ->
            val y = when (i) { 0 -> 545; 5 -> 894; else -> 540 + i * 72 }
            val actual = rainbow.getPixel(550, y)
            for (channel in listOf<(Int) -> Int>(Color::red, Color::green, Color::blue)) {
                assertTrue("Flag color $i differs", kotlin.math.abs(channel(actual) - channel(expected)) <= 14)
            }
        }
        for ((x, y) in listOf(500 to 600, 600 to 500, 300 to 300, 880 to 880)) {
            assertEquals(original.getPixel(x, y), rainbow.getPixel(x, y))
        }
        val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        context.packageManager.getApplicationIcon(context.packageName).apply {
            setBounds(0, 0, 512, 512); draw(Canvas(preview))
        }
        File(context.cacheDir, "launcher-icon-preview.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
