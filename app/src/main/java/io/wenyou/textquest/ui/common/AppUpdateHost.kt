package io.wenyou.textquest.ui.common

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.data.appInstallIntent
import io.wenyou.textquest.ui.vm.AppUpdateState
import io.wenyou.textquest.ui.vm.AppUpdateViewModel

/** One owner coordinates startup, settings, download progress and system install requests. */
@Composable
internal fun AppUpdateHost(vm: AppUpdateViewModel, content: @Composable () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentUri by rememberUpdatedState(state.installUri)
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: Exception) { vm.showOpenError() }
    }
    fun install(uri: Uri) {
        if (vm.consumeInstallRequest()) open(appInstallIntent(context, uri))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) currentUri?.let(::install)
        else vm.installPermissionDenied()
    }
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.resume()
                Lifecycle.Event.ON_STOP -> vm.stopMonitoring()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.installUri) {
        state.installUri?.let { uri ->
            if (context.packageManager.canRequestPackageInstalls()) install(uri)
            else try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) { vm.consumeInstallRequest(); vm.showOpenError() }
        }
    }
    val openDownloads = { open(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
    val openRelease = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"))) }
    val exit = { (context as? Activity)?.finish(); Unit }
    BackHandler(enabled = state.required, onBack = exit)
    if (state.required) AppUpdateGate(state, { vm.check() }, vm::download, vm::install, openDownloads, openRelease, exit)
    else {
        content()
        if (state.promptVisible) AlertDialog(onDismissRequest = vm::dismissPrompt,
            title = { Text("发现新版本") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                AppUpdateCard(state, { vm.check() }, vm::download, openDownloads, openRelease, vm::install)
            } },
            confirmButton = { AppTextButton(onClick = vm::dismissPrompt) { Text(if (state.downloading) "后台下载" else "稍后再说") } })
    }
}

@Composable
internal fun AppUpdateGate(state: AppUpdateState, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit,
                           onOpenDownloads: () -> Unit, onOpenRelease: () -> Unit, onExit: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("需要升级后才能使用", style = MaterialTheme.typography.headlineSmall)
            Text("当前版本 ${BuildConfig.VERSION_NAME.substringBefore('-')}，最低支持版本 ${state.policy.minimumVersion}。升级将保留本地资料。")
            AppUpdateCard(state, onCheck, onDownload, onOpenDownloads, onOpenRelease, onInstall)
            AppTextButton(onClick = onExit) { Text("退出应用") }
        }
    }
}
