package io.wenyou.textquest.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.wenyou.textquest.MainActivity
import io.wenyou.textquest.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.wenyou.textquest.ui.common.AppText as Text

/**
 * Android only lets an app switch between its own built-in launcher icons, so a picture of the user's choice
 * becomes a pinned home-screen shortcut that opens the app.
 */
object CustomIcon {
    private const val SIZE = 432 // 108dp adaptive canvas at xxxhdpi

    /** Center-crops the picture into the visible 72dp of an adaptive icon; the bleed area takes its average color. */
    fun render(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张图片" }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val source = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("无法读取这张图片")
        try {
            val side = minOf(source.width, source.height)
            val crop = Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Bitmap.createScaledBitmap(source, 1, 1, true).let { px -> px.getPixel(0, 0).also { px.recycle() } } or 0xFF000000.toInt())
            // Slightly larger than the 72dp mask so no launcher shows the fill color at the edges.
            val inset = SIZE * 16 / 108
            canvas.drawBitmap(source, crop, Rect(inset, inset, SIZE - inset, SIZE - inset), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            return out
        } finally { source.recycle() }
    }

    /** Returns false when the launcher does not support pinning shortcuts. */
    fun pin(context: Context, label: String, icon: Bitmap): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfoCompat.Builder(context, "custom-icon-${System.currentTimeMillis()}")
            .setShortLabel(label).setIcon(IconCompat.createWithAdaptiveBitmap(icon)).setIntent(intent).build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}

@Composable
fun CustomIconCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var icon by remember { mutableStateOf<Bitmap?>(null) }
    val appName = androidx.compose.ui.res.stringResource(R.string.app_name)
    var label by remember { mutableStateOf(appName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { runCatching { CustomIcon.render(context, uri) } }
                .onSuccess { icon = it }
                .onFailure { Toast.makeText(context, it.message ?: "无法读取这张图片", Toast.LENGTH_SHORT).show() }
        }
    }
    TonalCard {
        Text("自定义桌面图标", style = MaterialTheme.typography.titleMedium)
        Text("用相册里的图片和自定义名称，在桌面添加一个打开本应用的图标。原应用图标仍保留，可以移到文件夹或从桌面移除。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("选择图片") }
    }
    icon?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { icon = null },
            title = { Text("添加到桌面") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Preview the part a launcher shows: the center 72dp of the 108dp canvas.
                    val visible = remember(bitmap) {
                        val inset = bitmap.width / 6
                        Bitmap.createBitmap(bitmap, inset, inset, bitmap.width - inset * 2, bitmap.height - inset * 2).asImageBitmap()
                    }
                    Image(visible, null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                    OutlinedTextField(label, { label = it.take(20) }, label = { Text("图标名称") }, singleLine = true)
                }
            },
            confirmButton = {
                AppTextButton(enabled = label.isNotBlank(), onClick = {
                    val ok = CustomIcon.pin(context, label.trim(), bitmap)
                    Toast.makeText(context, if (ok) "已请求添加；若桌面没有出现，请在系统设置中允许本应用创建桌面快捷方式" else "当前桌面不支持添加快捷图标", Toast.LENGTH_LONG).show()
                    icon = null
                }) { Text("添加") }
            },
            dismissButton = { AppTextButton(onClick = { icon = null }) { Text("取消") } }
        )
    }
}
