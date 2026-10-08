package io.wenyou.textquest

import io.wenyou.textquest.data.parseMirrorRelease
import io.wenyou.textquest.data.validateAppRelease
import io.wenyou.textquest.data.AppUpdates
import io.wenyou.textquest.data.parseAppRelease
import io.wenyou.textquest.data.UpdatePolicy
import io.wenyou.textquest.data.parseUpdatePolicy
import io.wenyou.textquest.data.requiresAppUpdate
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun release(version: String = "4.2.0", url: String = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v$version/${BuildConfig.APP_FILE_PREFIX}-v$version.apk") = """
        {"tag_name":"v$version","draft":false,"prerelease":false,"body":"更新说明",
         "assets":[{"name":"${BuildConfig.APP_FILE_PREFIX}-v$version.apk","state":"uploaded","size":1024,"digest":"sha256:${"0".repeat(64)}","browser_download_url":"$url"}]}
    """.trimIndent()

    @Test fun versionsAndDownloadsAreRestrictedToThisApp() {
        fun parse(text: String, current: String = "4.1.0") = parseAppRelease(text, BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, current)
        assertEquals("4.2.0", parse(release())!!.version)
        val legacy = release().replace(BuildConfig.APP_FILE_PREFIX, "WenYou-${BuildConfig.FLAVOR}")
        assertEquals("WenYou-${BuildConfig.FLAVOR}-v4.2.0.apk", parse(legacy)!!.fileName)
        assertNotNull(parse(release("4.10.0"), "4.9.99"))
        assertNotNull(parse(release("5.0.0"), "4.9.99-α"))
        assertNull(parse(release(), "4.2.0-β"))
        assertNull(parse(release(), "5.0.0"))
        assertNull(parse(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(parse(release().replace("\"prerelease\":false", "\"prerelease\":true")))
        for (bad in listOf(release(url = "https://example.com/app.apk"),
            release(url = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v4.2.0/other.apk"),
            release().replace("\"size\":1024", "\"size\":0"),
            release().replace("\"size\":1024", "\"size\":536870913"),
            release().replace("sha256:", "md5:"),
            release().replace("\"state\":\"uploaded\"", "\"state\":\"starter\""),
            release().replace("\"draft\":false", "\"draft\":\"unknown\""),
            release().replace("${BuildConfig.APP_FILE_PREFIX}", "Other"),
            release().replace("v4.2.0", "v4.2.0-rc1"))) {
            assertThrows(Exception::class.java) { parse(bad) }
        }
    }

    @Test fun requestUsesPublicOwnRepositoryAndHandlesFailures() = runBlocking {
        // The F-Droid index mirror fails here, so every GitHub API failure still surfaces.
        fun checker(code: Int, body: String, mirrorCode: Int = 503, mirrorBody: String = "") = AppUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            val mirror = url.host == "wangmikuwang.github.io"
            assertEquals(if (mirror) "/fdroid/repo/index-v1.json" else "/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest", url.encodedPath)
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (mirror) mirrorCode else code).message("test")
                .body((if (mirror) mirrorBody else body).toResponseBody()).build()
        }.build())
        // A rate-limited API falls back to the index: same APK, downloaded from the GitHub release.
        val sha = "b".repeat(64)
        val index = """{"packages":{"${BuildConfig.APPLICATION_ID}":[{"versionName":"99.0.0","versionCode":9900,"hashType":"sha256","hash":"${"a".repeat(64)}","size":5},
            {"versionName":"99.1.0","versionCode":9910,"hashType":"sha256","hash":"$sha","size":7}]}}"""
        val viaMirror = checker(403, "rate limited", 200, index).check()!!
        assertEquals("99.1.0", viaMirror.version); assertEquals(sha, viaMirror.sha256); assertEquals(7L, viaMirror.size)
        assertEquals("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v99.1.0/${BuildConfig.APP_FILE_PREFIX}-v99.1.0.apk", viaMirror.url)
        validateAppRelease(viaMirror)
        assertNull(parseMirrorRelease(index, BuildConfig.APPLICATION_ID, BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, "99.1.0"))
        try { checker(403, "rate limited", 200, index.replace("sha256", "md5")).check(); fail("Unverifiable mirror accepted") } catch (_: Exception) { }
        assertNull(checker(404, "").check())
        for ((code, body) in listOf(403 to "rate limited", 500 to "error", 200 to "not-json", 200 to "x".repeat(1_048_577))) {
            try { checker(code, body).check(); fail("Invalid response accepted") } catch (_: Exception) { }
        }
        assertNull(checker(200, release("1.0.0")).check())
    }

    @Test fun minimumVersionCannotBeBelowFiveAndUsesCodeAndVersion() {
        val baseline = parseUpdatePolicy("""{"minimumVersionCode":90,"minimumVersion":"5.0.0"}""")
        assertTrue(requiresAppUpdate(89, "4.3.2", baseline))
        assertFalse(requiresAppUpdate(90, "5.0.0-α", baseline))
        assertTrue(requiresAppUpdate(90, "5.0.0", UpdatePolicy(91, "5.0.1")))
        assertTrue(requiresAppUpdate(91, "5.0.0", UpdatePolicy(91, "5.1.0")))
        assertFalse(requiresAppUpdate(91, "5.1.0-β", UpdatePolicy(91, "5.1.0")))
        for (bad in listOf("{}", """{"minimumVersionCode":89,"minimumVersion":"5.0.0"}""",
            """{"minimumVersionCode":90,"minimumVersion":"4.9.99"}""",
            """{"minimumVersionCode":90,"minimumVersion":"v5.0.0"}""", " ".repeat(16_385))) {
            assertThrows(Exception::class.java) { parseUpdatePolicy(bad) }
        }
    }

    @Test fun policyUsesOwnPublicRepositoryAndBoundsResponses() = runBlocking {
        fun checker(code: Int, body: String) = AppUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("raw.githubusercontent.com", chain.request().url.host)
            assertEquals("/${BuildConfig.UPDATE_REPOSITORY}/main/update-policy.json", chain.request().url.encodedPath)
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build()
        }.build())
        assertNull(checker(404, "").policy())
        assertEquals(UpdatePolicy(), checker(200, """{"minimumVersionCode":90,"minimumVersion":"5.0.0"}""").policy())
        for ((code, body) in listOf(403 to "limited", 200 to "not-json", 200 to "x".repeat(16_385))) {
            assertThrows(Exception::class.java) { runBlocking { checker(code, body).policy() } }
        }
    }
}
