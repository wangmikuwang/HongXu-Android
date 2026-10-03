package io.wenyou.textquest

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.CancellationSignal
import android.view.ScrollCaptureSession
import android.view.ScrollCaptureTarget
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.common.GlassBackdrop
import io.wenyou.textquest.ui.theme.ThemeStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@SdkSuppress(minSdkVersion = 31)
class SystemScrollCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun target(view: View): ScrollCaptureTarget = compose.runOnIdle {
        val bounds = Rect().also { view.getLocalVisibleRect(it) }
        val offset = IntArray(2).also { view.getLocationInWindow(it) }
        val targets = mutableListOf<ScrollCaptureTarget>()
        view.onScrollCaptureSearch(bounds, Point(offset[0], offset[1])) { targets.add(it) }
        assertEquals("System must find the main scrolling content", 1, targets.size)
        targets.single()
    }

    @Test fun settingsExposeSystemTargetInBothAppearances() {
        val prefix = "scroll-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getFilesDir() = File(cacheDir, prefix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val container = WenYouApp.AppContainer(context)
        lateinit var view: View
        compose.runOnIdle { compose.activity.setContent {
            view = LocalView.current
            WenYouAppRoot(container)
        } }
        compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        for (style in listOf(ThemeStyle.MATERIAL, ThemeStyle.APPLE)) {
            compose.runOnIdle { container.settings.setThemeStyle(style) }
            compose.waitForIdle()
            val capture = target(view)
            assertTrue(capture.scrollBounds!!.height() > 0)
            assertTrue("$style bounds=${capture.scrollBounds}, viewHeight=${view.height}", capture.scrollBounds!!.height() < view.height)
        }
    }

    @Test fun realCaptureExcludesFloatingControlsAndRestoresPosition() {
        lateinit var view: View
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        compose.runOnIdle { compose.activity.setContent {
            view = LocalView.current
            state = rememberLazyListState()
            GlassBackdrop(content = {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("capture-list")) {
                    items(60) { index ->
                        Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Blue)) { Text("Line $index", color = Color.White) }
                    }
                }
            }, controls = {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp).background(Color.Red)) { Text("Floating controls") }
            })
        } }
        compose.onNodeWithTag("capture-list").performScrollToIndex(5)
        val original = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        val capture = target(view)
        val searched = AtomicReference<Rect>()
        compose.runOnIdle { capture.callback.onScrollCaptureSearch(CancellationSignal()) { searched.set(it) } }
        compose.waitUntil(5_000) { searched.get() != null }
        val bounds = searched.get()
        val height = minOf(320, bounds.height())
        ImageReader.newInstance(bounds.width(), height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT).use { reader ->
            val session = ScrollCaptureSession(reader.surface, bounds, capture.positionInWindow)
            val started = AtomicBoolean()
            compose.runOnIdle { capture.callback.onScrollCaptureStart(session, CancellationSignal()) { started.set(true) } }
            compose.waitUntil(5_000) { started.get() }
            try {
                // Cover the original bottom (under the floating controls), then content beyond one screen.
                for (y in listOf(bounds.height() - height, bounds.height(), bounds.height() + height)) {
                    val result = AtomicReference<Rect>()
                    compose.runOnIdle { capture.callback.onScrollCaptureImageRequest(session, CancellationSignal(),
                        Rect(0, y, bounds.width(), y + height)) { result.set(it) } }
                    compose.waitUntil(5_000) { result.get() != null }
                    assertEquals(height, result.get().height())
                    var image: android.media.Image? = null
                    compose.waitUntil(5_000) { image = reader.acquireNextImage(); image != null }
                    image!!.use { frame -> frame.hardwareBuffer!!.use { buffer ->
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))!!
                        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        hardware.recycle()
                        assertEquals("Floating controls must not cover any tile", android.graphics.Color.BLUE,
                            bitmap.getPixel(bitmap.width / 2, bitmap.height - 4))
                        bitmap.recycle()
                    } }
                }
            } finally {
                val ended = AtomicBoolean()
                compose.runOnIdle { capture.callback.onScrollCaptureEnd { ended.set(true) } }
                compose.waitUntil(5_000) { ended.get() }
            }
        }
        compose.runOnIdle { assertEquals(original, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
        compose.onNodeWithText("Floating controls").assertIsDisplayed()
    }
}
