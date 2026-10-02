package io.wenyou.textquest

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.wenyou.textquest.data.llm.*
import org.junit.Assert.*
import org.junit.Test

class GenerationNotificationTest {
    @Test fun progressSurvivesBackgroundAndCleansUpOnFinishDisableAndCancel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as WenYouApp
        val settings = app.container.settings
        val original = settings.state.value.generationNotifications
        val manager = app.getSystemService(NotificationManager::class.java)
        val usage = app.container.chatClient.usage
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        var id = java.util.UUID.randomUUID().toString()
        fun visible(id: Int) = manager.activeNotifications.firstOrNull { it.id == id }
        fun await(message: String, check: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 8000
            while (!check() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue(message, check())
        }
        fun start() = usage.start(GenerationProgress(id, "Test", "Test", System.nanoTime()))
        fun finish(status: String) = usage.finish(id, UsageRecord("Test", "Test", 0, 100, status, TokenUsage()))
        try {
            settings.setGenerationNotifications(true)
            start()
            await("Ongoing notification must appear") { visible(GenerationService.ONGOING_ID) != null }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
            usage.progress(id, "正在思考", 10)
            await("Phase must update in background") { visible(GenerationService.ONGOING_ID)?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.contains("正在思考") == true }
            assertTrue(visible(GenerationService.ONGOING_ID)!!.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            visible(GenerationService.ONGOING_ID)!!.notification.contentIntent.send()
            finish("完成")
            await("Finished request must leave an ordinary completion notification") { visible(GenerationService.ONGOING_ID) == null && visible(GenerationService.FINISHED_ID) != null }
            assertEquals("AI请求已完成", visible(GenerationService.FINISHED_ID)!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals(0, visible(GenerationService.FINISHED_ID)!!.notification.flags and Notification.FLAG_ONGOING_EVENT)
            id = java.util.UUID.randomUUID().toString()
            start()
            await("Second request must appear") { visible(GenerationService.ONGOING_ID) != null }
            finish("已取消")
            await("Cancellation must remove progress without a completion notification") { visible(GenerationService.ONGOING_ID) == null && visible(GenerationService.FINISHED_ID) == null }
            id = java.util.UUID.randomUUID().toString()
            start()
            await("Third request must appear") { visible(GenerationService.ONGOING_ID) != null }
            settings.setGenerationNotifications(false)
            await("Disabling notifications must remove progress") { visible(GenerationService.ONGOING_ID) == null }
            finish("已取消")
        } finally {
            if (id in usage.active.value) finish("已取消")
            settings.setGenerationNotifications(original)
            manager.cancel(GenerationService.ONGOING_ID)
            manager.cancel(GenerationService.FINISHED_ID)
        }
    }
}
