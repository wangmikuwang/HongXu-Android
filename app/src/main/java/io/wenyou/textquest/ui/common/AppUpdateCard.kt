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
                           onOpenDownloads: () -> Unit, onOpenRelease: () -> Unit) {
    TonalCard {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("从官方发布页面检查更新，下载由系统管理，不会上传剧情、存档或 AI 服务密钥。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
        state.release?.let { release ->
            Text("新版 ${release.version} · ${String.format(Locale.ROOT, "%.1f", release.size / 1_048_576.0)} MB")
            if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall,
                maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onCheck, enabled = !state.busy) { Text("检查更新") }
            if (state.release != null) OutlinedButton(onClick = onDownload, enabled = !state.busy) {
                Text(if (state.downloaded) "重试下载" else "下载新版 APK")
            }
            OutlinedButton(onClick = onOpenDownloads) { Text("查看下载") }
            TextButton(onClick = onOpenRelease) { Text("打开发布页面") }
        }
    }
}
