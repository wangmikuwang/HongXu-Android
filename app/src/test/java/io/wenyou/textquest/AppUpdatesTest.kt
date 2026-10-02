package io.wenyou.textquest

import io.wenyou.textquest.data.AppUpdates
import io.wenyou.textquest.data.parseAppRelease
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun release(version: String = "4.2.0", url: String = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v$version/WenYou-${BuildConfig.FLAVOR}-v$version.apk") = """
        {"tag_name":"v$version","draft":false,"prerelease":false,"body":"更新说明",
         "assets":[{"name":"WenYou-${BuildConfig.FLAVOR}-v$version.apk","state":"uploaded","size":1024,"browser_download_url":"$url"}]}
    """.trimIndent()

    @Test fun versionsAndDownloadsAreRestrictedToThisApp() {
        fun parse(text: String, current: String = "4.1.0") = parseAppRelease(text, BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, current)
        assertEquals("4.2.0", parse(release())!!.version)
        assertNotNull(parse(release("4.10.0"), "4.9.99"))
        assertNotNull(parse(release("5.0.0"), "4.9.99-α"))
        assertNull(parse(release(), "4.2.0-β"))
        assertNull(parse(release(), "5.0.0"))
        assertNull(parse(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(parse(release().replace("\"prerelease\":false", "\"prerelease\":true")))
        for (bad in listOf(release(url = "https://example.com/app.apk"),
            release(url = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v4.2.0/other.apk"),
            release().replace("\"size\":1024", "\"size\":0"),
            release().replace("\"state\":\"uploaded\"", "\"state\":\"starter\""),
            release().replace("\"draft\":false", "\"draft\":\"unknown\""),
            release().replace("WenYou-${BuildConfig.FLAVOR}", "Other"),
            release().replace("v4.2.0", "v4.2.0-rc1"))) {
            assertThrows(Exception::class.java) { parse(bad) }
        }
    }

    @Test fun requestUsesPublicOwnRepositoryAndHandlesFailures() = runBlocking {
        fun checker(code: Int, body: String) = AppUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest", chain.request().url.encodedPath)
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build()
        }.build())
        assertNull(checker(404, "").check())
        for ((code, body) in listOf(403 to "rate limited", 500 to "error", 200 to "not-json", 200 to "x".repeat(1_048_577))) {
            try { checker(code, body).check(); fail("Invalid response accepted") } catch (_: Exception) { }
        }
        assertNull(checker(200, release("1.0.0")).check())
    }
}
