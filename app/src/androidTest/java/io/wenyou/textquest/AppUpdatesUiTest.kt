package io.wenyou.textquest

import android.app.DownloadManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.wenyou.textquest.data.AppRelease
import io.wenyou.textquest.data.downloadAppRelease
import io.wenyou.textquest.data.parseAppRelease
import io.wenyou.textquest.ui.common.AppUpdateCard
import io.wenyou.textquest.ui.theme.WenYouTheme
import io.wenyou.textquest.ui.vm.AppUpdateState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.URL

class AppUpdatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** These tests use the real release; an exhausted anonymous GitHub quota skips them instead of failing. */
    private fun latestReleaseJson(): String {
        val connection = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 20_000; connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "WenYou-update-test")
        val code = connection.responseCode
        assumeTrue("GitHub API unavailable (HTTP $code), likely the anonymous rate limit", code == 200)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    @Test fun settingsCheckAndUpdateActionsWork() {
        latestReleaseJson()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.app_name)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("系统与关于").performClick()
        compose.onNodeWithText("检查更新").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("当前没有可用的新版本").fetchSemanticsNodes().isNotEmpty() }
        val release = AppRelease("9.0.0", "一段很长的更新说明。".repeat(100), "update.apk", "", 35_000_000)
        var downloads = 0
        var opens = 0
        var installs = 0
        val preview = mutableStateOf(AppUpdateState(release = release, message = "发现新版本 9.0.0"))
        compose.runOnIdle {
            compose.activity.setContent { WenYouTheme {
                AppUpdateCard(preview.value, {}, { downloads++ }, { opens++ }, {}, { installs++ })
            } }
        }
        compose.onNodeWithText("下载并升级").assertIsDisplayed().performClick()
        compose.onNodeWithText("查看下载").assertIsDisplayed().performClick()
        compose.runOnIdle { preview.value = preview.value.copy(downloaded = true) }
        compose.onNodeWithText("安装升级").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(1, opens); assertEquals(1, installs)
            preview.value = preview.value.copy(downloaded = false, downloading = true, receivedBytes = 17_500_000, totalBytes = 35_000_000)
        }
        compose.onNodeWithText("50% ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("下载并升级").assertIsNotEnabled()
    }

    @Test fun systemDownloadUsesRealOwnApkAndDeduplicates() {
        val context = compose.activity
        val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        val previousId = prefs.getLong("download_id", -1)
        val previousUrl = prefs.getString("download_url", null)
        val previousRelease = prefs.getString("download_release", null)
        val release = parseAppRelease(latestReleaseJson(),
            BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, "0.0.0")!!
        val manager = context.getSystemService(DownloadManager::class.java)
        var id = -1L
        try {
            id = downloadAppRelease(context, release)
            assertEquals(id, downloadAppRelease(context, release))
            val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
            var completed = false
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    assertNotEquals(DownloadManager.STATUS_FAILED, status)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        assertEquals(release.size, cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                        assertNotNull(manager.getUriForDownloadedFile(id))
                        completed = true
                    }
                }
                if (completed) break
                android.os.SystemClock.sleep(1_000)
            }
            assertTrue("System download did not complete", completed)
            assertEquals(id, downloadAppRelease(context, release))
            if (id != previousId) {
                manager.remove(id)
                val removedId = id
                id = downloadAppRelease(context, release)
                assertNotEquals(removedId, id)
                assertEquals(id, downloadAppRelease(context, release))
            }
        } finally {
            // Remove only the task created by this test, never a pre-existing user download.
            if (id >= 0 && id != previousId) manager.remove(id)
            prefs.edit().putLong("download_id", previousId).putString("download_url", previousUrl).putString("download_release", previousRelease).commit()
        }
    }
}
