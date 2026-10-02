package io.wenyou.textquest.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wenyou.textquest.data.repo.SettingsStore

@Composable
fun GenerationNotificationSettings(store: SettingsStore) {
    val prefs by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        store.setGenerationNotifications(it)
    }
    TonalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("生成实时通知", style = MaterialTheme.typography.labelLarge)
                Text("显示阶段和耗时，点击返回应用。支持的系统可显示实时更新或小米超级岛；其余显示普通通知。", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = prefs.generationNotifications, onCheckedChange = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else store.setGenerationNotifications(on)
            })
        }
        Text("超级岛需小米平台授权，展示由系统决定。通知不包含剧情或思考内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("系统通知设置") }
    }
}
