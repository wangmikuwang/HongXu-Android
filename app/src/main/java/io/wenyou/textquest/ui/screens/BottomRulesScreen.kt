package io.wenyou.textquest.ui.screens

import io.wenyou.textquest.ui.common.AppIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wenyou.textquest.ui.common.AppTextButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import io.wenyou.textquest.ui.common.AppIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import io.wenyou.textquest.ui.common.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.vm.BottomRulesViewModel
import io.wenyou.textquest.ui.vm.Vms

/** 底层基调管理：列出所有不可动摇规则，可新建／编辑／删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomRulesScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: BottomRulesViewModel = viewModel(factory = Vms.factory { BottomRulesViewModel(container) })
    val rules by vm.rules.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("底层基调") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(AppIcons.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有底层基调", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "底层基调是「不可动摇规则」，角色扮演时先执行它、再按人设扮演。\n点右下角新建，然后在角色编辑里选择要执行的角色。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rules, key = { it.id }) { r ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    io.wenyou.textquest.ui.common.RawText(r.name, style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    io.wenyou.textquest.ui.common.RawText(r.content, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { nav.navigate(R.bottomRuleEdit(r.id)) }) {
                                    Icon(AppIcons.Edit, "编辑")
                                }
                                IconButton(onClick = { pendingDelete = r.id }) {
                                    Icon(AppIcons.Delete, "删除", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(R.bottomRuleEdit("new")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                icon = { Icon(AppIcons.Add, null) },
                text = { Text("新建底层基调") }
            )
        }
    }

    pendingDelete?.let { id ->
        val name = rules.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除底层基调？") },
            text = { Text("「$name」将被删除，并自动从所有引用它的角色上移除。") },
            confirmButton = {
                AppTextButton(onClick = {
                    vm.delete(id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { AppTextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}
