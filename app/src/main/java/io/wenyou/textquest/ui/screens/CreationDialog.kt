package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.ai.CreationKind
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.UsagePanel
import io.wenyou.textquest.ui.common.AppDropdown
import io.wenyou.textquest.ui.common.AppField
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.vm.CreationViewModel
import io.wenyou.textquest.ui.vm.Vms

@Composable
fun CreationDialog(container: WenYouApp.AppContainer, nav: NavHostController, onDismiss: () -> Unit, initialKind: CreationKind = CreationKind.STORY) {
    val vm: CreationViewModel = viewModel(factory = Vms.factory { CreationViewModel(it) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { if (ui.idea.isBlank() && ui.draft == null) vm.setKind(initialKind) }
    val profiles by container.library.providers.collectAsStateWithLifecycle()
    val prefs by container.settings.state.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == prefs.defaultProviderId } ?: profiles.firstOrNull()
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            val draft = ui.draft ?: return@LaunchedEffect
            val route = draft.stories.firstOrNull()?.let { R.storyEdit(it.id) } ?: R.charEdit(draft.characters.first().id)
            vm.consumeSaved()
            onDismiss()
            nav.navigate(route)
        }
    }
    val close = { vm.cancel(); onDismiss() }
    AlertDialog(
        onDismissRequest = { if (!ui.busy) close() },
        title = { Text("AI 一句话创建") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppDropdown("创建内容", CreationKind.entries.map { it.label to it }, ui.kind, vm::setKind, enabled = !ui.busy)
                if (!ui.busy) {
                    AppField(ui.idea, vm::setIdea, "描述你的创意", minLines = 2, maxLines = 4,
                        placeholder = "例如：一位失忆侦探与能听见旧物记忆的少女，在雨城寻找失踪的人",
                        supporting = "${ui.idea.length}/2000 字")
                } else {
                    Text(ui.idea, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (ui.draft != null) "正在保存…" else if (ui.kind == CreationKind.STORY) "正在创作剧情与人设…" else "正在创作人物设定…")
                }
                if (ui.busy || ui.draft != null || ui.error.isNotBlank()) UsagePanel(container.chatClient.usage)
                Text(if (profile == null) "还没有配置 AI 服务" else "使用 AI 服务：${profile.name}", style = MaterialTheme.typography.bodySmall)
                if (ui.kind == CreationKind.STORY) Text("生成 AI 导演剧情：世界观、开场和关联人物，保存后即可游玩。", style = MaterialTheme.typography.bodySmall)
                if (profile == null) TextButton(onClick = { close(); nav.navigate(R.PROVIDERS) }) { Text("配置 AI 服务") }
                if (ui.error.isNotBlank()) Text(ui.error, color = MaterialTheme.colorScheme.error)
                ui.draft?.let { draft ->
                    draft.stories.firstOrNull()?.let { story ->
                        TonalCard {
                            Text("${story.coverEmoji} ${story.title}", style = MaterialTheme.typography.titleMedium)
                            Text(story.subtitle)
                            Text("世界观", style = MaterialTheme.typography.labelLarge)
                            Text(story.ai.worldSummary)
                            Text("开场", style = MaterialTheme.typography.labelLarge)
                            Text(story.nodes.getValue("start").text)
                        }
                    }
                    draft.characters.forEach { c ->
                        TonalCard {
                            Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.titleMedium)
                            Text(c.tagline)
                            Text("性格：${c.personality}")
                            Text("背景：${c.background}")
                            if (c.speechStyle.isNotBlank()) Text("说话习惯：${c.speechStyle}")
                            if (c.exampleDialogue.isNotBlank()) Text("台词：${c.exampleDialogue}")
                        }
                    }
                    if (!ui.busy) TextButton(onClick = vm::generate) { Text("重新生成") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { focus.clearFocus(); if (ui.draft == null) vm.generate() else vm.save() }, enabled = !ui.busy && (ui.draft != null || profile != null) && ui.idea.isNotBlank()) {
                Text(if (ui.draft == null) "开始创建" else "保存并编辑")
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !ui.busy || ui.draft == null) { Text(if (ui.busy) "取消生成" else "关闭") }
        }
    )
}
