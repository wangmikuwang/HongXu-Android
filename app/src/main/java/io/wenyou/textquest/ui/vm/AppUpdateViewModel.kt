package io.wenyou.textquest.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.data.AppRelease
import io.wenyou.textquest.data.AppUpdates
import io.wenyou.textquest.data.downloadAppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateState(val busy: Boolean = false, val release: AppRelease? = null, val message: String = "", val downloaded: Boolean = false)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updates = AppUpdates()
    private val state = MutableStateFlow(AppUpdateState())
    val ui = state.asStateFlow()

    fun check() {
        if (state.value.busy) return
        state.value = AppUpdateState(busy = true, message = "正在检查更新…")
        viewModelScope.launch {
            try {
                val release = updates.check()
                state.value = AppUpdateState(release = release, message = if (release == null) "当前没有可用的新版本" else "发现新版本 ${release.version}")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = AppUpdateState(message = "检查失败，请检查网络或稍后重试，也可打开发布页面。") }
        }
    }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.busy) return
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { downloadAppRelease(getApplication(), release) }
                state.value = state.value.copy(busy = false, downloaded = true, message = "已交给系统下载，可在「查看下载」或通知栏查看进度。下载后点击 APK 按系统提示安装。")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = state.value.copy(busy = false, downloaded = false, message = "无法创建下载任务，请稍后重试或打开发布页面下载。") }
        }
    }

    fun showOpenError() { state.value = state.value.copy(message = "此设备无法打开该页面，请检查浏览器或下载管理器是否已启用。") }
}
