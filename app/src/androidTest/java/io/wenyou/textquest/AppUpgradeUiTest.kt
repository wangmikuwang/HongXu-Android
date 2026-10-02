package io.wenyou.textquest

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import io.wenyou.textquest.data.*
import io.wenyou.textquest.ui.WenYouAppRoot
import io.wenyou.textquest.ui.vm.AppUpdateViewModel
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AppUpgradeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class TestApplication(context: Context) : Application() { init { attachBaseContext(context) } }

    @Test fun automaticPolicyBlocksNavigationAndCachedMinimumSurvivesOffline() {
        val prefix = "upgrade-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        val application = TestApplication(context)
        val current = BuildConfig.VERSION_NAME.substringBefore('-')
        val next = current.substringBeforeLast('.') + "." + (current.substringAfterLast('.').toInt() + 1)
        val minimum = AtomicInteger(BuildConfig.VERSION_CODE + 1)
        val offline = AtomicBoolean(false)
        val updates = AppUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            if (offline.get()) throw IOException("offline test")
            val body = if (chain.request().url.host == "raw.githubusercontent.com")
                """{"minimumVersionCode":${minimum.get()},"minimumVersion":"${if (minimum.get() == BuildConfig.VERSION_CODE) current else next}"}"""
            else """{"tag_name":"v$next","draft":false,"prerelease":false,"assets":[{"name":"${BuildConfig.APP_FILE_PREFIX}-v$next.apk","state":"uploaded","size":1024,"digest":"sha256:${"0".repeat(64)}","browser_download_url":"https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v$next/${BuildConfig.APP_FILE_PREFIX}-v$next.apk"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(body.toResponseBody()).build()
        }.build())
        val container = (compose.activity.application as WenYouApp).container
        val first = AppUpdateViewModel(application, updates)
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container, first) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("需要升级后才能使用").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("稍后再说").assertDoesNotExist()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("下载并升级").fetchSemanticsNodes().isNotEmpty() }
        first.viewModelScope.cancel()
        offline.set(true)
        val restarted = AppUpdateViewModel(application, updates)
        assertTrue(restarted.ui.value.required)
        compose.runOnIdle { compose.activity.setContent { WenYouAppRoot(container, restarted) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("检查失败", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("需要升级后才能使用").assertIsDisplayed()
        compose.onNodeWithContentDescription("剧情", useUnmergedTree = true).assertDoesNotExist()
        offline.set(false); minimum.set(BuildConfig.VERSION_CODE)
        compose.runOnIdle { restarted.check() }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("剧情", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(restarted.ui.value.required)
        restarted.viewModelScope.cancel()
    }

    @Test fun verifiedOwnApkOpensSystemInstallerAndBadApksAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val source = File(context.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(65_536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        val version = BuildConfig.VERSION_NAME.substringBefore('-')
        val name = "${BuildConfig.APP_FILE_PREFIX}-v$version.apk"
        val release = AppRelease(version, "", name, "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v$version/$name",
            source.length(), digest.digest().joinToString("") { "%02x".format(it) })
        val target = File(context.cacheDir, "updates/$version.apk")
        val backup = File(context.cacheDir, "upgrade-backup-${UUID.randomUUID()}.apk")
        if (target.exists()) target.copyTo(backup)
        try {
            assertThrows(Exception::class.java) { source.inputStream().use { verifyAppApk(context, release.copy(sha256 = "0".repeat(64)), it) } }
            val wrong = release.copy(version = "9.0.0", fileName = "${BuildConfig.APP_FILE_PREFIX}-v9.0.0.apk",
                url = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v9.0.0/${BuildConfig.APP_FILE_PREFIX}-v9.0.0.apk")
            assertThrows(Exception::class.java) { source.inputStream().use { verifyAppApk(context, wrong, it) } }
            val uri = source.inputStream().use { verifyAppApk(context, release, it) }
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.updates", uri.authority)
            assertTrue(context.packageManager.canRequestPackageInstalls())
            context.startActivity(appInstallIntent(context, uri))
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var installer = false
            while (SystemClock.elapsedRealtime() < deadline) {
                val window = automation.rootInActiveWindow
                val packageName = window?.packageName?.toString().orEmpty()
                installer = packageName.contains("packageinstaller") && listOf("更新", "安装", "Update", "Install").any { label ->
                    window?.findAccessibilityNodeInfosByText(label)?.any { it.isEnabled && it.text?.toString()?.equals(label, ignoreCase = true) == true } == true
                }
                if (installer) break
                SystemClock.sleep(500)
            }
            assertTrue("System install confirmation did not open", installer)
            automation.takeScreenshot()?.let { image -> File(context.cacheDir, "installer-test-preview.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
            // Stop at the system confirmation page; never replace the app during a test.
        } finally {
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (backup.exists()) { backup.copyTo(target, overwrite = true); backup.delete() } else target.delete()
        }
    }
}
