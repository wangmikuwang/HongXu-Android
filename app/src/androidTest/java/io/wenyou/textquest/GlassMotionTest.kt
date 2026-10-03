package io.wenyou.textquest

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class GlassMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun scrollingKeepsGlassNavigationUsable() {
        val prefix = "glass-motion-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val container = WenYouApp.AppContainer(context)
        runBlocking { repeat(60) { i ->
            container.library.upsertStory(Story("s$i", "旅程 $i"))
            container.library.upsertSave(SaveSlot("save$i", "旅程 $i", i.toLong(), i.toLong(), SessionState("s$i")))
        } }
        container.settings.setDynamicColor(false)
        container.settings.setThemeStyle(ThemeStyle.APPLE)
        container.settings.setThemeMode(ThemeMode.LIGHT)
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container) } }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performTouchInput { swipeUp(durationMillis = 500) }
        list.performScrollToIndex(0)
        compose.waitForIdle()
        val worker = HandlerThread("glass-frames").apply { start() }
        val frames = CopyOnWriteArrayList<Long>()
        val gpu = CopyOnWriteArrayList<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            gpu.add(metrics.getMetric(FrameMetrics.GPU_DURATION))
        }
        compose.runOnIdle { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
        try {
            repeat(8) { list.performTouchInput { swipeUp(durationMillis = 500) } }
            compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
            assertTrue(compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("主页", useUnmergedTree = true).performClick()
            assertTrue("Collect real rendered frames", frames.size > 20)
            fun percentile(values: List<Long>) = values.sorted()[(values.size * .9).toInt().coerceAtMost(values.lastIndex)] / 1e6
            val result = "{\"frames\":${frames.size},\"p90Ms\":${percentile(frames)},\"meanMs\":${frames.average()/1e6},\"gpuP90Ms\":${percentile(gpu)}}"
            File(compose.activity.cacheDir, "glass-frame-metrics.json").writeText(result)
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.cacheDir, "glass-motion.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            compose.runOnIdle { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely()
        }
    }
}
