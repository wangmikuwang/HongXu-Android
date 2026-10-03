package io.wenyou.textquest

import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import io.wenyou.textquest.ui.common.generationClock
import io.wenyou.textquest.data.llm.*
import io.wenyou.textquest.data.model.ApiProfile
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun elapsedClockStopsInBackgroundAndResumes() {
        val observed = AtomicLong(0)
        val ticks = AtomicLong(0)
        compose.runOnIdle { compose.activity.setContent {
            val time = generationClock(true) { ticks.incrementAndGet(); System.nanoTime() }
            SideEffect { observed.set(time) }
        } }
        compose.waitForIdle()
        val first = observed.get()
        compose.waitUntil(3000) { observed.get() > first }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val stopped = observed.get()
        val stoppedTicks = ticks.get()
        Thread.sleep(1300)
        assertEquals("Background UI must not keep its timer awake", stoppedTicks, ticks.get())
        assertEquals(stopped, observed.get())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(3000) { observed.get() > stopped }
    }

    @Test fun requestAndReportedBillingSurviveAnActivityStop(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val tracker = UsageTracker()
        val client = ChatClient(OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"message":{"content":"完成"}}],"usage":{"prompt_tokens":1000,"completion_tokens":200,"prompt_cache_hit_tokens":400,"prompt_cache_miss_tokens":600}}""".toResponseBody()).build()
        }.build(), tracker)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val request = scope.async { client.streamText(ApiProfile("fixture", "fixture", baseUrl = "https://fixture.invalid/v1", model = "fixture",
                inputPrice = 2.0, outputPrice = 4.0, cachedPrice = 0.5), "fixture", "fixture") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            release.countDown()
            assertEquals("完成", withTimeout(5000) { request.await() }.content)
            assertEquals(0.0022, tracker.records.value.last().estimatedCost!!, 1e-12)
            assertTrue(tracker.active.value.isEmpty())
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        } finally { release.countDown(); scope.cancel() }
    }
}
