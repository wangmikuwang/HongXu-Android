package io.wenyou.textquest.ui.common

import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.wenyou.textquest.ui.common.AppIcon as Icon
import io.wenyou.textquest.ui.common.AppText as Text
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
            Spacer(Modifier.width(12.dp))
            Switch(checked = prefs.generationNotifications, onCheckedChange = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else store.setGenerationNotifications(on)
            })
        }
        Text("超级岛需小米平台授权，展示由系统决定。通知不包含剧情或思考内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppOutlinedButton(onClick = {
            runCatching {
                context.startActivity(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            }
                .onFailure { Toast.makeText(context, io.wenyou.textquest.ui.common.tr("请在系统设置中打开本应用的通知设置"), Toast.LENGTH_SHORT).show() }
        }) { Text("系统通知设置") }
        if (Build.MANUFACTURER.equals("Xiaomi", true) || Build.MANUFACTURER.equals("Redmi", true)) {
            Text("切到后台后，澎湃 OS 可能暂停网络请求。若生成中断，可在应用信息中检查电池和后台设置；仅在需要持续生成时调整。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppOutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    .onFailure { Toast.makeText(context, io.wenyou.textquest.ui.common.tr("请在系统设置中打开本应用的信息"), Toast.LENGTH_SHORT).show() }
            }) { Text("电池与后台设置") }
        }
    }
}
