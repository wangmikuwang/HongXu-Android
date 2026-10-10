package io.wenyou.textquest

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.wenyou.textquest.data.llm.islandPayload
import kotlinx.coroutines.*

/** ponytail: one notification represents all active requests; split only if concurrent jobs need separate controls. */
class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updates: Job? = null
    private val container get() = (application as WenYouApp).container
    private val manager get() = getSystemService(NotificationManager::class.java)
    private val seen = mutableSetOf<String>()
    private var protocol = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (updates?.isActive == true) return START_NOT_STICKY
        seen.clear()
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "AI生成进度", NotificationManager.IMPORTANCE_LOW))
        protocol = runCatching { Settings.System.getInt(contentResolver, "notification_focus_protocol", 0) }.getOrDefault(0)
        // Start immediately; even a request that finishes before service startup must satisfy the FGS deadline.
        try { startForeground(ONGOING_ID, notification("AI正在生成", "点击返回应用", true)) }
        catch (_: RuntimeException) { stopSelf(); return START_NOT_STICKY }
        updates = scope.launch {
            while (isActive && container.settings.state.value.generationNotifications && allowed()) {
                val active = container.chatClient.usage.active.value.values.toList()
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    val finished = container.chatClient.usage.records.value.filter { it.requestId in seen }
                    if (finished.isNotEmpty() && finished.any { it.status != "已取消" }) {
                        val failed = finished.any { it.status != "完成" }
                        manager.notify(FINISHED_ID, notification(if (failed) "AI请求已结束" else "AI请求已完成", "点击返回应用查看结果", false))
                    }
                    stopSelf(); return@launch
                }
                seen.addAll(active.map { it.id })
                val oldest = active.minBy { it.startNanos }
                val seconds = ((System.nanoTime() - oldest.startNanos) / 1_000_000_000).coerceAtLeast(0)
                val text = "${oldest.phase} · 已用${seconds}秒" + if (active.size > 1) " · ${active.size}项请求" else ""
                manager.cancel(FINISHED_ID)
                manager.notify(ONGOING_ID, notification("AI正在生成", text, true, oldest.phase, seconds, active.size))
                delay(1000)
            }
            ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    internal fun notification(title: String, text: String, ongoing: Boolean, phase: String = "等待服务响应", seconds: Long = 0, count: Int = 1): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = builder()
            .setSmallIcon(R.drawable.ic_generation).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setShowWhen(false)
            .setPublicVersion(builder().setSmallIcon(R.drawable.ic_generation)
                .setContentTitle("AI生成").setContentText("点击返回应用").setContentIntent(open).build())
        if (ongoing) {
            if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true).setStyledByProgress(false))
                    .setShortCriticalText("生成中")
                // Public Android 16 QPR2 extras key; base API 36 has no setter yet.
                builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            } else builder.setProgress(0, 0, true)
            if (protocol >= 2) builder.addExtras(Bundle().apply {
                putString("miui.focus.param", islandPayload(phase, seconds, count))
                putBundle("miui.focus.pics", Bundle().apply {
                    putParcelable("miui.focus.pic_generation", Icon.createWithResource(this@GenerationService, R.mipmap.ic_launcher))
                })
            })
        } else {
            builder.setAutoCancel(true)
            if (Build.VERSION.SDK_INT >= 26) builder.setTimeoutAfter(15_000)
        }
        return builder.build()
    }

    /** Channels exist from Android 8.0; earlier versions take the priority from the builder. */
    private fun builder(): Notification.Builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)

    private fun allowed() = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "ai_generation"
        const val ONGOING_ID = 380
        const val FINISHED_ID = 381
    }
}
