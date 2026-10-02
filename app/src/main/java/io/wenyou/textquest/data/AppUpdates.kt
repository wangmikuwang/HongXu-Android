package io.wenyou.textquest.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.data.model.AppJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AppRelease(val version: String, val notes: String, val fileName: String, val url: String, val size: Long)

internal fun parseAppRelease(text: String, repository: String, flavor: String, currentVersion: String): AppRelease? {
    fun version(value: String): List<Int> {
        val match = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[αβ])?").matchEntire(value)
            ?: throw IOException("发布版本格式无效")
        return match.groupValues.drop(1).take(3).map { it.toIntOrNull() ?: throw IOException("发布版本格式无效") }
    }
    val release = AppJson.parseToJsonElement(text).jsonObject
    val draft = release["draft"]?.jsonPrimitive?.booleanOrNull ?: throw IOException("发布信息无效")
    val prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: throw IOException("发布信息无效")
    if (draft || prerelease) return null
    val tag = release.getValue("tag_name").jsonPrimitive.content
    val latest = version(tag)
    if (tag != "v${latest.joinToString(".")}") throw IOException("发布版本格式无效")
    val current = version(currentVersion)
    if ((latest.zip(current).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0) <= 0) return null
    val name = "WenYou-$flavor-v${latest.joinToString(".")}.apk"
    val asset = release.getValue("assets").jsonArray.map { it.jsonObject }.singleOrNull {
        it["name"]?.jsonPrimitive?.content == name && it["state"]?.jsonPrimitive?.content == "uploaded"
    } ?: throw IOException("新版安装包尚未就绪")
    val url = asset.getValue("browser_download_url").jsonPrimitive.content
    if (url != "https://github.com/$repository/releases/download/$tag/$name") throw IOException("安装包来源无效")
    val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
    if (size <= 0L) throw IOException("安装包尚未就绪")
    return AppRelease(latest.joinToString("."), release["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(8_000), name, url, size)
}

class AppUpdates(private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()) {
    suspend fun check(): AppRelease? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "WenYou-update").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code == 404) return@runCatching null
                        if (!it.isSuccessful) throw IOException("更新服务暂时不可用")
                        val source = it.body?.source() ?: throw IOException("更新信息为空")
                        source.request(1_048_577)
                        if (source.buffer.size > 1_048_576) throw IOException("更新信息过大")
                        val text = source.readUtf8()
                        parseAppRelease(text, BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, BuildConfig.VERSION_NAME)
                    }
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
}

/** System downloads survive navigation and process exit; only one active task per asset. */
internal fun downloadAppRelease(context: Context, release: AppRelease): Long {
    // Validate again at the download boundary, including callers other than the settings UI.
    val name = "WenYou-${BuildConfig.FLAVOR}-v${release.version}.apk"
    require(Regex("\\d+\\.\\d+\\.\\d+").matches(release.version) && release.fileName == name)
    require(release.url == "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v${release.version}/$name")
    val manager = context.getSystemService(DownloadManager::class.java)
    val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    val previous = prefs.getLong("download_id", -1)
    if (prefs.getString("download_url", null) == release.url && previous >= 0) {
        manager.query(DownloadManager.Query().setFilterById(previous))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) return previous
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    try { manager.openDownloadedFile(previous)?.use { return previous } }
                    catch (_: IOException) { /* File was removed; enqueue a replacement. */ }
                }
            }
        }
    }
    val request = DownloadManager.Request(Uri.parse(release.url)).setTitle(release.fileName)
        .setDescription("文游更新安装包").setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    // Android 8/9 use the app's download directory without requesting broad storage access.
    val destinationName = "${System.currentTimeMillis()}-${release.fileName}"
    if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationName)
    else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, destinationName)
    val id = manager.enqueue(request)
    prefs.edit().putLong("download_id", id).putString("download_url", release.url).apply()
    return id
}
