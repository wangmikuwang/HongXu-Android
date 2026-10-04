package io.wenyou.textquest.data

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import io.wenyou.textquest.ui.theme.AppearancePrefs
import java.io.RandomAccessFile
import java.io.File
import java.util.UUID

/** Imports are validated in a temporary file before publishing their new preference. */
object AppearanceFiles {
    fun fontDirectory(context: Context) = File(context.filesDir, "appearance-fonts").apply { mkdirs() }
    fun importFont(context: Context, uri: Uri): Pair<String, String> {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "字体.ttf"
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in listOf("ttf", "otf", "ttc")) { "请选择 .ttf、.otf 或 .ttc 字体" }
        val file = File(fontDirectory(context), "font-${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取字体" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 32 * 1024 * 1024) { "字体不能超过 32 MB" }; output.write(buffer, 0, count) }
                }
            }
            RandomAccessFile(file, "r").use { font ->
                require(font.length() >= 12) { "字体文件无效" }
                val signature = font.readInt()
                require(signature in listOf(0x00010000, 0x4F54544F, 0x74727565, 0x74746366)) { "字体文件无效" }
                val offset = if (signature == 0x74746366) {
                    font.readInt()
                    require(font.readInt() > 0 && font.length() >= 16) { "字体集合无效" }
                    font.readInt().toLong() and 0xffffffffL
                } else 0L
                require(offset <= font.length() - 12) { "字体文件不完整" }
                font.seek(offset + 4)
                val tables = font.readUnsignedShort()
                require(tables > 0 && offset + 12L + tables * 16L <= font.length()) { "字体目录无效" }
                repeat(tables) { index ->
                    font.seek(offset + 12L + index * 16L + 8L)
                    val start = font.readInt().toLong() and 0xffffffffL
                    val length = font.readInt().toLong() and 0xffffffffL
                    require(start <= font.length() && length <= font.length() - start) { "字体文件不完整" }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) android.graphics.fonts.Font.Builder(file).build()
            Typeface.createFromFile(file)
            return file.name to name
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    fun removeFont(context: Context, name: String) {
        if (name.matches(Regex("font-[a-zA-Z0-9-]+\\.(ttf|otf|ttc)"))) File(fontDirectory(context), name).delete()
    }
    fun importWallpaper(context: Context, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别图片" }
        var scale = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / scale > 1440) scale *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = scale })
        } ?: error("无法读取图片")
        val folder = File(context.filesDir, "appearance-wallpapers").apply { mkdirs() }
        val name = "wallpaper-${UUID.randomUUID()}.jpg"
        try { File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        finally { bitmap.recycle() }
        return name
    }
    fun wallpaper(context: Context, name: String): File? = name.takeIf {
        it.matches(Regex("wallpaper-[a-zA-Z0-9-]+\\.jpg"))
    }?.let { File(context.filesDir, "appearance-wallpapers/$it").takeIf(File::isFile) }

    fun applyLauncher(context: Context, prefs: AppearancePrefs, dark: Boolean) {
        val selected = when (prefs.launcherIcon) {
            "default" -> when (prefs.launcherShell) { "light" -> "Light"; "dark" -> "Dark"; else -> if (dark) "Dark" else "Default" }
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "Ink"
        }
        val manager = context.packageManager
        // Enable first to retain at least one launchable shortcut throughout the change.
        val order = listOf(selected) + listOf("Default", "Light", "Dark", "Ink").filter { it != selected }
        order.forEach { name ->
            val component = ComponentName(context.packageName, "${context.packageName}.Launcher$name")
            val desired = if (name == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (manager.getComponentEnabledSetting(component) != desired) manager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
        }
    }
}
