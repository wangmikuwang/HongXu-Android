package io.wenyou.textquest.data

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class AppUpdateFileProvider : FileProvider()

internal fun prepareAppInstall(context: Context, release: AppRelease, id: Long, policy: UpdatePolicy): Uri {
    val manager = context.getSystemService(DownloadManager::class.java)
    manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
        if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) {
            throw IOException("安装包尚未下载完成")
        }
    }
    return ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id)).use {
        verifyAppApk(context, release, it, policy)
    }
}

/** Only a verified, same-app APK is exposed from the private update directory. */
@Suppress("DEPRECATION")
internal fun verifyAppApk(context: Context, release: AppRelease, input: InputStream, policy: UpdatePolicy = UpdatePolicy()): Uri {
    validateAppRelease(release)
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val temporary = File(directory, "${release.version}.part.apk")
    val apk = File(directory, "${release.version}.apk")
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val buffer = ByteArray(65_536)
        temporary.outputStream().use { out ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                if (bytes > release.size) throw IOException("安装包大小不匹配")
                digest.update(buffer, 0, count)
                out.write(buffer, 0, count)
            }
        }
        val expected = release.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (bytes != release.size || !MessageDigest.isEqual(digest.digest(), expected)) throw IOException("安装包校验失败")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(temporary.absolutePath, flags) ?: throw IOException("安装包无效")
        val installed = manager.getPackageInfo(context.packageName, flags)
        val code = PackageInfoCompat.getLongVersionCode(archive)
        if (archive.packageName != context.packageName || archive.versionName?.substringBefore('-') != release.version ||
            code < PackageInfoCompat.getLongVersionCode(installed) || code < policy.minimumVersionCode ||
            isNewerVersion(policy.minimumVersion, release.version)) throw IOException("安装包不属于本应用或版本过旧")
        val signedByThisApp = if (Build.VERSION.SDK_INT >= 28) {
            val own = installed.signingInfo ?: throw IOException("无法核对签名")
            val incoming = archive.signingInfo ?: throw IOException("无法核对签名")
            if (own.hasMultipleSigners() || incoming.hasMultipleSigners()) own.apkContentsSigners.toSet() == incoming.apkContentsSigners.toSet()
            else own.apkContentsSigners.all { it in incoming.signingCertificateHistory.toSet() }
        } else {
            val own = installed.signatures
            !own.isNullOrEmpty() && own.toSet() == archive.signatures?.toSet()
        }
        if (!signedByThisApp) throw IOException("安装包签名不匹配")
        if (!temporary.renameTo(apk)) throw IOException("无法准备安装包")
        return FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    } finally {
        temporary.delete()
    }
}

internal fun appInstallIntent(context: Context, uri: Uri): Intent {
    require(uri.scheme == "content" && uri.authority == "${context.packageName}.updates")
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply { clipData = ClipData.newRawUri("update", uri) }
}
