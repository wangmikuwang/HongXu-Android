package io.wenyou.textquest.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.R
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.repo.ShareCode
import io.wenyou.textquest.data.repo.ShareInbox
import kotlinx.coroutines.launch
import java.io.File
import io.wenyou.textquest.ui.common.AppText as Text

/** Reads the clipboard only when it changed (Android 12+ announces each read) and offers any share code in it. */
fun checkClipboardForShare(context: Context, inbox: ShareInbox) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val description = clipboard.primaryClipDescription ?: return
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return
    // Android 8.0+ stamps each clip; before that (no read notices yet) the text itself tells whether it changed.
    if (Build.VERSION.SDK_INT >= 26 && description.timestamp == inbox.lastClipStamp) return
    val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull() ?: return
    val stamp = if (Build.VERSION.SDK_INT >= 26) description.timestamp else text.hashCode().toLong()
    if (stamp == inbox.lastClipStamp) return
    inbox.lastClipStamp = stamp
    inbox.offer(text, fromClipboard = true)
}

/** Sender side: a link message, a .wenyou file, or the bare code, each recognised by every import path. */
object ShareActions {
    private const val LINK_BASE = "https://wangmikuwang.github.io/fdroid/s/"

    fun link(code: String) = "$LINK_BASE?a=${BuildConfig.SHARE_ORIGIN}#$code"

    private fun message(context: Context, kind: String, title: String, code: String): String {
        val app = context.getString(R.string.app_name)
        return if (kind == "角色") trf("我在「{0}」分享了角色《{1}》，点开链接即可导入：\n{2}\n（也可以复制这段文字，再打开「{0}」自动识别）", app, title, link(code))
        else trf("我在「{0}」分享了剧情《{1}》，点开链接即可导入：\n{2}\n（也可以复制这段文字，再打开「{0}」自动识别）", app, title, link(code))
    }

    fun sendLink(context: Context, kind: String, title: String, code: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, message(context, kind, title, code))
        context.startActivity(Intent.createChooser(send, "分享「$title」"))
    }


    /** Writes the code to a small .wenyou file; the recipient opens it with this app to import. */
    fun sendFile(context: Context, title: String, code: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40).ifBlank { "share" }
        val file = File(dir, "$name.wenyou").apply { writeText(code + "\n") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, "发送「$title」"))
    }
}

/** Previews whatever the share inbox holds and imports only after confirmation, without overwriting. */
@Composable
fun SharedImportHost(container: WenYouApp.AppContainer) {
    val code by container.shareInbox.pending.collectAsStateWithLifecycle()
    val pending = code ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stories by container.library.stories.collectAsStateWithLifecycle()
    val characters by container.library.characters.collectAsStateWithLifecycle()
    val foreign = remember(pending) { ShareCode.foreign(pending) }
    val bundle = remember(pending) { if (foreign) null else ShareCode.decode(pending) }
    var importing by remember(pending) { mutableStateOf(false) }
    val dismiss = container.shareInbox::dismiss

    if (bundle == null || (bundle.stories.isEmpty() && bundle.characters.isEmpty())) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("无法导入") },
            text = { Text(if (foreign) "这段内容不是由本应用分享的，无法导入。" else "分享内容不完整或已损坏，请让对方重新分享。") },
            confirmButton = { AppTextButton(onClick = dismiss) { Text("知道了") } }
        )
        return
    }
    val storyIds = stories.mapTo(mutableSetOf()) { it.id }
    val characterIds = characters.mapTo(mutableSetOf()) { it.id }
    val newStories = bundle.stories.count { it.id !in storyIds }
    val newCharacters = bundle.characters.count { it.id !in characterIds }
    AlertDialog(
        onDismissRequest = { if (!importing) dismiss() },
        title = { Text("导入分享内容") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bundle.stories.forEach { s ->
                    RawText("${s.coverEmoji} ${s.title}" + if (s.id in storyIds) "（已有）" else "", style = MaterialTheme.typography.bodyLarge)
                }
                bundle.characters.forEach { c ->
                    RawText("${c.emoji} ${c.name}" + if (c.id in characterIds) "（已有）" else "", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (newStories + newCharacters == 0) "这些内容都已经在资料库里了。"
                    else if (newCharacters == 0) "将新增 $newStories 部剧情；已有内容会跳过，不会被覆盖。"
                    else if (newStories == 0) "将新增 $newCharacters 位角色；已有内容会跳过，不会被覆盖。"
                    else "将新增 $newStories 部剧情和 $newCharacters 位角色；已有内容会跳过，不会被覆盖。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AppTextButton(enabled = !importing && newStories + newCharacters > 0, onClick = {
                importing = true
                scope.launch {
                    val message = runCatching { container.library.importShared(bundle) }
                        .fold({ "导入成功：新增 ${it.added} 条内容" }, { "导入失败：${it.message}" })
                    Toast.makeText(context, io.wenyou.textquest.ui.common.tr(message), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }) { Text("导入") }
        },
        dismissButton = { AppTextButton(enabled = !importing, onClick = dismiss) { Text("取消") } }
    )
}
