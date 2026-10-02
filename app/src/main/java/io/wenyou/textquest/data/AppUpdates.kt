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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AppRelease(val version: String, val notes: String, val fileName: String, val url: String, val size: Long, val sha256: String = "")

@Serializable
data class UpdatePolicy(val minimumVersionCode: Int = 90, val minimumVersion: String = "5.0.0")

private fun versionParts(value: String): List<Int> {
    val match = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[αβ])?").matchEntire(value)
        ?: throw IOException("发布版本格式无效")
    return match.groupValues.drop(1).take(3).map { it.toIntOrNull() ?: throw IOException("发布版本格式无效") }
}

internal fun isNewerVersion(latest: String, current: String) =
    (versionParts(latest).zip(versionParts(current)).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0) > 0

internal fun requiresAppUpdate(code: Int, version: String, policy: UpdatePolicy) =
    code < policy.minimumVersionCode || isNewerVersion(policy.minimumVersion, version)

internal fun parseUpdatePolicy(text: String): UpdatePolicy {
    if (text.toByteArray().size > 16_384) throw IOException("更新策略过大")
    val json = AppJson.parseToJsonElement(text).jsonObject
    val code = json["minimumVersionCode"]?.jsonPrimitive?.intOrNull ?: throw IOException("最低版本无效")
    val version = json["minimumVersion"]?.jsonPrimitive?.contentOrNull ?: throw IOException("最低版本无效")
    if (code < 90 || !Regex("\\d+\\.\\d+\\.\\d+").matches(version) || isNewerVersion("5.0.0", version)) throw IOException("最低版本无效")
    return UpdatePolicy(code, version)
}

// Legacy filename keeps installed versions and earlier official releases compatible.
internal fun appApkNames(version: String, flavor: String) =
    listOf("${BuildConfig.APP_FILE_PREFIX}-v$version.apk", "WenYou-$flavor-v$version.apk")

internal fun parseAppRelease(text: String, repository: String, flavor: String, currentVersion: String): AppRelease? {
    val release = AppJson.parseToJsonElement(text).jsonObject
    val draft = release["draft"]?.jsonPrimitive?.booleanOrNull ?: throw IOException("发布信息无效")
    val prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: throw IOException("发布信息无效")
    if (draft || prerelease) return null
    val tag = release.getValue("tag_name").jsonPrimitive.content
    val latest = versionParts(tag)
    if (tag != "v${latest.joinToString(".")}") throw IOException("发布版本格式无效")
    if (!isNewerVersion(tag, currentVersion)) return null
    val names = appApkNames(latest.joinToString("."), flavor)
    val assets = release.getValue("assets").jsonArray.map { it.jsonObject }
    val asset = names.firstNotNullOfOrNull { name -> assets.singleOrNull {
        it["name"]?.jsonPrimitive?.content == name && it["state"]?.jsonPrimitive?.content == "uploaded"
    } } ?: throw IOException("新版安装包尚未就绪")
    val name = asset.getValue("name").jsonPrimitive.content
    val url = asset.getValue("browser_download_url").jsonPrimitive.content
    if (url != "https://github.com/$repository/releases/download/$tag/$name") throw IOException("安装包来源无效")
    val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
    if (size !in 1..536_870_912L) throw IOException("安装包大小无效")
    val digest = asset["digest"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (!Regex("sha256:[0-9a-f]{64}").matches(digest)) throw IOException("安装包缺少有效校验信息")
    return AppRelease(latest.joinToString("."), release["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(8_000), name, url, size, digest.removePrefix("sha256:"))
}

class AppUpdates(private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()) {
    suspend fun check(): AppRelease? = requestText("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest", 1_048_576)?.let {
        parseAppRelease(it, BuildConfig.UPDATE_REPOSITORY, BuildConfig.FLAVOR, BuildConfig.VERSION_NAME)
    }

    suspend fun policy(): UpdatePolicy? = requestText("https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPOSITORY}/main/update-policy.json", 16_384)?.let(::parseUpdatePolicy)

    private suspend fun requestText(url: String, limit: Long): String? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json").header("User-Agent", "${BuildConfig.APP_FILE_PREFIX}-update").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code == 404) return@runCatching null
                        if (!it.isSuccessful) throw IOException("更新服务暂时不可用")
                        val source = it.body?.source() ?: throw IOException("更新信息为空")
                        source.request(limit + 1)
                        if (source.buffer.size > limit) throw IOException("更新信息过大")
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
}

internal fun validateAppRelease(release: AppRelease) {
    val name = release.fileName
    require(Regex("\\d+\\.\\d+\\.\\d+").matches(release.version) && name in appApkNames(release.version, BuildConfig.FLAVOR))
    require(release.url == "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v${release.version}/$name")
    require(release.size in 1..536_870_912L && Regex("[0-9a-f]{64}").matches(release.sha256))
}

/** System downloads survive navigation and process exit; only one active task per asset. */
internal fun downloadAppRelease(context: Context, release: AppRelease): Long {
    validateAppRelease(release)
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
    val request = DownloadManager.Request(Uri.parse(release.url)).setTitle("${context.getString(io.wenyou.textquest.R.string.app_name)} ${release.version}")
        .setDescription("${context.getString(io.wenyou.textquest.R.string.app_name)}更新安装包").setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    // Android 8/9 use the app's download directory without requesting broad storage access.
    val destinationName = "${System.currentTimeMillis()}-${release.fileName}"
    if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationName)
    else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, destinationName)
    val id = manager.enqueue(request)
    prefs.edit().putLong("download_id", id).putString("download_url", release.url)
        .putString("download_release", AppJson.encodeToString(release)).apply()
    return id
}
