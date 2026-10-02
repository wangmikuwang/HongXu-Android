package io.wenyou.textquest.ui.vm

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.data.*
import io.wenyou.textquest.data.model.AppJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class AppUpdateState(
    val busy: Boolean = false, val release: AppRelease? = null, val message: String = "",
    val downloaded: Boolean = false, val downloading: Boolean = false,
    val receivedBytes: Long = 0, val totalBytes: Long = 0,
    val policy: UpdatePolicy = UpdatePolicy(), val promptVisible: Boolean = false, val installUri: Uri? = null
) {
    val required get() = requiresAppUpdate(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, policy)
}

class AppUpdateViewModel @JvmOverloads constructor(application: Application, private val updates: AppUpdates = AppUpdates()) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val manager = application.getSystemService(DownloadManager::class.java)
    private val cachedPolicy = runCatching { prefs.getString("policy", null)?.let(::parseUpdatePolicy) }.getOrNull() ?: UpdatePolicy()
    private val cachedRelease = runCatching {
        prefs.getString("download_release", null)?.let { AppJson.decodeFromString<AppRelease>(it) }?.also(::validateAppRelease)
            ?.takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) }
    }.getOrNull()
    private val state = MutableStateFlow(AppUpdateState(policy = cachedPolicy, release = cachedRelease))
    val ui = state.asStateFlow()
    private var monitor: Job? = null
    private var lastCheck = -1L
    private var dismissedVersion: String? = null
    private var foreground = false
    private var verifiedPolicy: UpdatePolicy? = null

    fun resume() {
        foreground = true
        if (lastCheck < 0 || SystemClock.elapsedRealtime() - lastCheck >= 300_000) check(automatic = true)
        else monitorSavedDownload()
    }

    fun stopMonitoring() {
        foreground = false
        monitor?.cancel()
        monitor = null
        state.value = state.value.copy(downloading = false)
    }

    fun check(automatic: Boolean = false) {
        if (state.value.busy || state.value.downloading) return
        lastCheck = SystemClock.elapsedRealtime()
        state.value = state.value.copy(busy = true, message = "正在检查更新…")
        viewModelScope.launch {
            // A failed policy fetch must retain the last known minimum, including while offline.
            try {
                updates.policy()?.let { policy ->
                    prefs.edit().putString("policy", AppJson.encodeToString(policy)).apply()
                    state.value = state.value.copy(policy = policy)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Retain the cached or built-in policy. */ }
            try {
                val release = updates.check()
                val unchanged = release?.url == state.value.release?.url
                state.value = state.value.copy(busy = false, release = release,
                    downloaded = unchanged && state.value.downloaded,
                    installUri = if (unchanged) state.value.installUri else null,
                    promptVisible = automatic && release != null && release.version != dismissedVersion && !state.value.required,
                    message = if (release != null) "发现新版本 ${release.version}" else if (state.value.required)
                        "此版本已停止支持，请升级。安装包暂未就绪，可重试或打开发布页面。" else "当前没有可用的新版本")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                state.value = state.value.copy(busy = false, message = "检查失败，请检查网络或稍后重试，也可打开发布页面。")
            }
            monitorSavedDownload()
        }
    }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.busy || state.value.downloading) return
        state.value = state.value.copy(busy = true, downloaded = false, installUri = null)
        prefs.edit().putBoolean("install_after_download", true).apply()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { downloadAppRelease(getApplication(), release) }
                state.value = state.value.copy(busy = false)
                monitorSavedDownload()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                prefs.edit().putBoolean("install_after_download", false).apply()
                state.value = state.value.copy(busy = false, message = "无法创建下载任务，请重试或打开发布页面。")
            }
        }
    }

    private fun monitorSavedDownload() {
        val release = state.value.release ?: return
        val id = prefs.getLong("download_id", -1)
        if (!foreground || id < 0 || prefs.getString("download_url", null) != release.url || monitor?.isActive == true || state.value.busy || state.value.installUri != null) return
        state.value = state.value.copy(downloading = true)
        monitor = viewModelScope.launch {
            try {
                while (isActive) {
                    val info = withContext(Dispatchers.IO) {
                        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                            if (!cursor.moveToFirst()) Triple(-1, 0L, 0L) else Triple(
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                        }
                    }
                    if (info.first == DownloadManager.STATUS_FAILED || info.first == -1) {
                        prefs.edit().putBoolean("install_after_download", false).apply()
                        state.value = state.value.copy(downloading = false, downloaded = false, message = "下载失败或文件已清除，请重试下载。")
                        break
                    }
                    state.value = state.value.copy(receivedBytes = info.second.coerceAtLeast(0), totalBytes = info.third.coerceAtLeast(0))
                    if (info.first == DownloadManager.STATUS_SUCCESSFUL) {
                        state.value = state.value.copy(downloading = false, downloaded = true, message = "下载完成，可安装升级。")
                        if (prefs.getBoolean("install_after_download", false)) install()
                        break
                    }
                    state.value = state.value.copy(message = if (info.first == DownloadManager.STATUS_PAUSED) "等待网络或下载服务…" else "正在下载新版…")
                    delay(1_000)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = state.value.copy(downloading = false, message = "无法读取下载进度，请重试或查看系统下载。") }
        }
    }

    fun install() {
        val release = state.value.release ?: return
        if (state.value.busy || state.value.downloading || !state.value.downloaded) return
        val id = prefs.getLong("download_id", -1)
        val policy = state.value.policy
        state.value = state.value.copy(busy = true, message = "正在核对安装包…")
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { prepareAppInstall(getApplication(), release, id, policy) }
                verifiedPolicy = policy
                state.value = state.value.copy(busy = false, installUri = uri, message = "安装包已验证，请按系统提示完成升级。")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                withContext(Dispatchers.IO) { runCatching { manager.remove(id) } }
                prefs.edit().remove("download_id").remove("download_url").remove("download_release").putBoolean("install_after_download", false).apply()
                state.value = state.value.copy(busy = false, downloaded = false, message = "安装包验证失败或已清除，请重新下载。")
            }
        }
    }

    fun consumeInstallRequest(): Boolean {
        val valid = verifiedPolicy == state.value.policy
        prefs.edit().putBoolean("install_after_download", false).apply()
        state.value = state.value.copy(installUri = null, promptVisible = false,
            message = if (valid) state.value.message else "最低版本要求已变化，请再次点击安装升级。")
        return valid
    }
    fun installPermissionDenied() { consumeInstallRequest(); state.value = state.value.copy(message = "请允许本应用安装更新，再点击「安装升级」。") }
    fun dismissPrompt() { dismissedVersion = state.value.release?.version; state.value = state.value.copy(promptVisible = false) }
    fun showOpenError() { state.value = state.value.copy(message = "无法打开系统安装界面或发布页面，请检查系统设置。") }
}
