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
    @Test fun rainbowBadgeUsesFlagColorsAndDoesNotCoverPortrait() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun render(id: Int): Bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
            context.getDrawable(id)!!.apply { setBounds(0, 0, 1080, 1080); draw(Canvas(bitmap)) }
        }
        val original = render(R.drawable.ic_launcher_fg)
        val rainbow = render(R.drawable.ic_launcher_rainbow)
        val colors = listOf(0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982)
        colors.forEachIndexed { i, expected ->
            // Sample each arc along the diagonal towards the fan centre at (900, 900).
            val d = ((222 - (i * 260 + 130) / 10) * 0.7071).toInt()
            val actual = rainbow.getPixel(900 - d, 900 - d)
            for (channel in listOf<(Int) -> Int>(Color::red, Color::green, Color::blue)) {
                assertTrue("Flag color $i differs", kotlin.math.abs(channel(actual) - channel(expected)) <= 14)
            }
        }
        val background = original.getPixel(950, 950)
        var changed = 0
        var covered = 0
        for (y in 0 until 1080) for (x in 0 until 1080) {
            if (original.getPixel(x, y) != rainbow.getPixel(x, y)) {
                assertTrue("Badge extends beyond its lower-right fan", kotlin.math.hypot(x - 900.0, y - 900.0) <= 245)
                val old = original.getPixel(x, y)
                if (listOf<(Int) -> Int>(Color::red, Color::green, Color::blue).any { kotlin.math.abs(it(old) - it(background)) > 32 }) covered++
                changed++
            }
        }
        // The fan stops short of the figure; only one small floating petal lies beneath it (faint frame lines are ignored).
        assertTrue("Badge covers portrait: $covered", covered <= 400)
        assertTrue("changed $changed", changed in 80_000..180_000)
        val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.ic_launcher_rainbow)!!.apply {
            setBounds(0, 0, 512, 512); draw(Canvas(preview))
        }
        File(context.cacheDir, "launcher-icon-preview.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
