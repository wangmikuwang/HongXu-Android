package io.wenyou.textquest.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.wenyou.textquest.ui.vm.AppUpdateState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppUpdateCard(state: AppUpdateState, onCheck: () -> Unit, onDownload: () -> Unit,
                           onOpenDownloads: () -> Unit, onOpenRelease: () -> Unit, onInstall: () -> Unit = {}) {
    TonalCard {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("启动时自动检查官方更新，在应用内下载并继续安装。升级保留本地资料，不上传剧情、存档或 AI 服务密钥。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (state.downloading) {
            if (state.totalBytes > 0) {
                LinearProgressIndicator(progress = { (state.receivedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(state.receivedBytes * 100 / state.totalBytes).coerceIn(0, 100)}% · ${String.format(Locale.ROOT, "%.1f / %.1f MB", state.receivedBytes / 1_048_576.0, state.totalBytes / 1_048_576.0)}")
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
        state.release?.let { release ->
            Text("新版 ${release.version} · ${String.format(Locale.ROOT, "%.1f", release.size / 1_048_576.0)} MB")
            if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall,
                maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onCheck, enabled = !state.busy && !state.downloading) { Text("检查更新") }
            if (state.release != null) AppOutlinedButton(onClick = onDownload, enabled = !state.busy && !state.downloading) {
                Text(if (state.downloaded) "重新下载" else "下载并升级")
            }
            if (state.downloaded) Button(onClick = onInstall, enabled = !state.busy) { Text("安装升级") }
            AppOutlinedButton(onClick = onOpenDownloads) { Text("查看下载") }
            AppTextButton(onClick = onOpenRelease) { Text("打开发布页面") }
        }
    }
}
