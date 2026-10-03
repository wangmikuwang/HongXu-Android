package io.wenyou.textquest.ui.screens

import io.wenyou.textquest.ui.common.AppOutlinedButton

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.CrashLog
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.AppUpdateCard
import io.wenyou.textquest.ui.vm.AppUpdateViewModel
import io.wenyou.textquest.ui.common.AppDropdown
import io.wenyou.textquest.ui.common.EasterEggTitle
import io.wenyou.textquest.ui.common.SectionHeader
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.theme.PrideTheme
import io.wenyou.textquest.ui.common.LocalPrideGalleryOpen
import io.wenyou.textquest.ui.theme.ThemeMode
import io.wenyou.textquest.ui.theme.ThemeStyle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import io.wenyou.textquest.ui.vm.SettingsViewModel
import io.wenyou.textquest.ui.vm.Vms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: WenYouApp.AppContainer, nav: NavHostController, updateVm: AppUpdateViewModel = viewModel()) {
    val vm: SettingsViewModel = viewModel(factory = Vms.factory { SettingsViewModel(container) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateState by updateVm.ui.collectAsStateWithLifecycle()
    val openUpdatePage: (Intent) -> Unit = { intent ->
        try { context.startActivity(intent) }
        catch (_: Exception) { updateVm.showOpenError() }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(vm.exportString().toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            vm.setMessage(if (ok) "已导出全部数据（剧情/角色/服务/存档）" else "导出失败")
        }
    }

    // 连点版本号解锁内容开关（α 版默认隐藏 LGBT/18+ 开关）
    var lastTapAt by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }
    val openPrideGallery = LocalPrideGalleryOpen.current
    val onVersionTap = {
        if (ui.prideUnlocked) openPrideGallery()
        else {
            val now = System.currentTimeMillis()
            if (now - lastTapAt > 2000L) tapCount = 0
            lastTapAt = now
            tapCount++
            if (tapCount >= 10) {
                tapCount = 0
                vm.unlockContentPrefs()
                openPrideGallery()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) vm.setMessage("读取文件失败")
            else vm.importString(text)
        }
    }

    // 选择崩溃日志保存目录（系统“文档/Documents”）
    val crashDirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.setCrashDir(uri.toString())
        vm.setMessage("崩溃日志目录已设为「Documents」")
    }

    HubScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        nav = nav
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag("settings-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    EasterEggTitle(stringResource(io.wenyou.textquest.R.string.app_name), MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        tapMessage = "🎬 幕后导演\n导演悄悄递来一张纸条：最精彩的剧情，往往从你不按套路的选择开始。\n今天，主角的名字叫你。",
                        holdMessage = "🪄 第四面墙\n旁白：你长按了标题。\n角色：等等，谁在故事外面戳我们？\n导演：嘘，这是主角的新能力。")
                    Text("外观、AI 与本地数据", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                AppUpdateCard(updateState, { updateVm.check() }, updateVm::download,
                    onOpenDownloads = { openUpdatePage(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) },
                    onOpenRelease = { openUpdatePage(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"))) }, onInstall = updateVm::install)
            }

            if (ui.message.isNotBlank()) {
                item {
                    TonalCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(ui.message, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            item { SectionHeader("外观") }
            item {
                TonalCard {
                    AppDropdown(
                        label = "界面风格",
                        options = listOf("Material You" to ThemeStyle.MATERIAL, "液态玻璃" to ThemeStyle.APPLE),
                        selected = ui.style,
                        onSelect = vm::setStyle
                    )
                    Spacer(Modifier.height(8.dp))
                    AppDropdown(
                        label = "主题模式",
                        options = listOf(
                            "跟随系统" to ThemeMode.SYSTEM,
                            "浅色" to ThemeMode.LIGHT,
                            "深色" to ThemeMode.DARK
                        ),
                        selected = ui.mode,
                        onSelect = { vm.setMode(it) }
                    )
                }
            }
            if (ui.prideUnlocked && ui.prideEnabled) item {
                TonalCard {
                    AppDropdown(label = "旗帜配色",
                        options = listOf("默认配色" to null) + PrideTheme.entries.map { it.label to it },
                        selected = ui.prideTheme, onSelect = vm::setPrideTheme)
                    ui.prideTheme?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (ui.style == ThemeStyle.MATERIAL && ui.prideTheme == null) item {
                TonalCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("动态取色（壁纸配色）", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "从壁纸生成整套色调角色（Android 12+）"
                                else "此设备需要 Android 12+ 才能使用动态取色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ui.dynamicColor,
                            onCheckedChange = { vm.setDynamic(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    }
                }
            }

            item { SectionHeader("AI 与生成") }
            item {
                TonalCard {
                    AppDropdown(
                        label = "默认服务",
                        options = listOf("（使用第一个可用）" to "") +
                            ui.providers.map { it.name to it.id },
                        selected = ui.defaultProviderId ?: "",
                        onSelect = { id -> vm.setDefaultProvider(id.ifBlank { null }) }
                    )
                }
            }

            item { io.wenyou.textquest.ui.common.GenerationNotificationSettings(container.settings) }

            // 解锁后，LGBT 内容开关仅在旗帜墙显示。
            if (ui.contentUnlocked && !ui.prideUnlocked) {
                item { SectionHeader("内容偏好") }
                item {
                    TonalCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("显示 LGBT（LGBTQ+）内容", style = MaterialTheme.typography.labelLarge)
                                Text("关闭后隐藏 LGBT 预设剧情与人物。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = ui.showLgbt, onCheckedChange = { vm.setShowLgbt(it) })
                        }
                    }
                }
            }

            if (ui.contentUnlocked) {
                item { SectionHeader("成人内容") }
                item {
                    TonalCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("显示成人（18+）内容", style = MaterialTheme.typography.labelLarge)
                                Text("开启后显示成人预设，允许成年、自愿的亲密描写；关闭后保持非露骨。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = ui.adultContent, onCheckedChange = { vm.setAdultContent(it) })
                        }
                    }
                }
            }
            item { SectionHeader("数据备份") }
            item {
                TonalCard {
                    Text("整体备份剧情、人物、AI 服务与存档，供恢复或迁移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            exportLauncher.launch("${BuildConfig.APP_FILE_PREFIX}-backup-${System.currentTimeMillis()}.json")
                        }) { Text("导出备份") }
                        AppOutlinedButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }) { Text("导入备份") }
                    }
                }
            }

            item { SectionHeader("角色规则") }
            item {
                TonalCard {
                    Text("底层基调", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("角色优先遵守这些规则。在人物编辑中选择要应用的规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { nav.navigate(R.bottomRuleEdit("new")) }) { Text("新建底层基调") }
                        AppOutlinedButton(onClick = { nav.navigate(R.BOTTOM_RULES) }) { Text("管理底层基调") }
                    }
                }
            }

            item { SectionHeader("诊断与关于") }
            item {
                TonalCard {
                    Text("崩溃日志保存位置", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    val dir = vm.crashDir()
                    Text(if (dir != null) "已设置：$dir" else "默认保存在应用内。可选择系统文档目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppOutlinedButton(onClick = { crashDirPicker.launch(null) }) { Text("选择系统文档目录") }
                        Button(onClick = {
                            val t = "测试日志 time=${System.currentTimeMillis()}\nversion=${BuildConfig.VERSION_NAME}\n"
                            CrashLog.write(context, t, vm.crashDir())
                            vm.setMessage("已写入测试日志（请到所选 Documents 目录查看 crash.log）")
                        }) { Text("写入测试日志") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("日志文件：crash.log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            item {
                TonalCard {
                    Text("版本", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("v${BuildConfig.VERSION_NAME.substringBefore('-')}（build ${BuildConfig.VERSION_CODE}）\nAI 密钥保存在本机。\n",
                        modifier = Modifier.testTag("pride-version").clickable(onClickLabel = "版本号", onClick = onVersionTap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
