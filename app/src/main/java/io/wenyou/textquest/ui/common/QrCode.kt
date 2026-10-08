package io.wenyou.textquest.ui.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.data.repo.ShareCode
import java.io.File

/** 把一段文本编码成二维码位图，支持保存到本地，以及从位图识别二维码文本。 */
object QrCode {
    fun encode(content: String, size: Int = 640): Bitmap? {
        if (content.isBlank()) return null
        return try {
            // 容错等级 M + 加大留白：对“手机拍屏幕”产生的摩尔纹更有韧性
            val hints: Map<EncodeHintType, Any> = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 4
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /** 从位图中识别二维码文本（识别不到返回 null）。 */
    fun decode(bitmap: Bitmap): String? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val bmp = BinaryBitmap(HybridBinarizer(source))
            val hints: Map<DecodeHintType, Any> = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",
                DecodeHintType.TRY_HARDER to true
            )
            MultiFormatReader().decode(bmp, hints).text
        } catch (_: Throwable) {
            null
        }
    }

    /** 识别一张或一套分片二维码图片（含一张图里有多个码的分享海报），并返回完整分享码。 */
    fun decodeShareImages(context: Context, uris: List<Uri>): String? {
        val texts = uris.flatMap { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@use emptyList()
                    try { decodeAll(bitmap) } finally { bitmap.recycle() }
                }
            }.getOrNull().orEmpty()
        }
        return ShareCode.assembleQrTexts(texts)
    }

    /** Every QR code in one image; falls back to the single-code reader. Text is never trimmed (Base45 may end in a space). */
    private fun decodeAll(bitmap: Bitmap): List<String> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        val hints: Map<DecodeHintType, Any> = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.TRY_HARDER to true)
        val found = runCatching { QRCodeMultiReader().decodeMultiple(image, hints).map { it.text } }.getOrNull().orEmpty()
        return found.ifEmpty { listOfNotNull(decode(bitmap)) }
    }

    /** One shareable image: the title, then every page of the QR book in a numbered grid that album import reads at once. */
    fun poster(title: String, appName: String, pages: List<String>): Bitmap? {
        if (pages.isEmpty()) return null
        val cols = when { pages.size == 1 -> 1; pages.size <= 4 -> 2; else -> 3 }
        val rows = (pages.size + cols - 1) / cols
        val cell = 440; val gap = 40; val pad = 56; val label = 52
        val gridWidth = cols * cell + (cols - 1) * gap
        val width = maxOf(gridWidth + pad * 2, 760)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF625E6C.toInt(); textSize = 30f }
        val hint = StaticLayout.Builder.obtain(
            "共 ${pages.size} 张 · 保存这张图，在「$appName」中选择「导入 → 相册识别」即可一次导入", 0,
            "共 ${pages.size} 张 · 保存这张图，在「$appName」中选择「导入 → 相册识别」即可一次导入".length, textPaint, width - pad * 2
        ).build()
        val header = pad + 64 + 16 + hint.height + 40
        val height = header + rows * (cell + label) + (rows - 1) * gap + pad
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D1B22.toInt(); textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(TextUtils.ellipsize(title, titlePaint, (width - pad * 2).toFloat(), TextUtils.TruncateAt.END).toString(), pad.toFloat(), (pad + 56).toFloat(), titlePaint)
        canvas.save(); canvas.translate(pad.toFloat(), (pad + 64 + 16).toFloat()); hint.draw(canvas); canvas.restore()
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF625E6C.toInt(); textSize = 28f; textAlign = Paint.Align.CENTER }
        val left = (width - gridWidth) / 2
        pages.forEachIndexed { i, page ->
            val qr = encode(page, cell) ?: return null
            val x = left + (i % cols) * (cell + gap)
            val y = header + (i / cols) * (cell + label + gap)
            canvas.drawBitmap(qr, x.toFloat(), y.toFloat(), null)
            qr.recycle()
            if (pages.size > 1) canvas.drawText("第 ${i + 1} 张", (x + cell / 2).toFloat(), (y + cell + 38).toFloat(), labelPaint)
        }
        return bmp
    }

    /**
     * 从相机 YUV 的 Y 平面识别二维码：[rowStride] 可大于图像宽度（行尾填充被裁掉）。
     * 二维码定位与方向无关，传感器旋转无需处理（该亮度源也不支持旋转）。
     */
    fun decodeYuv(data: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        return try {
            val source: LuminanceSource = PlanarYUVLuminanceSource(
                data, rowStride, height, 0, 0, width, height, false
            )
            val bmp = BinaryBitmap(HybridBinarizer(source))
            val hints: Map<DecodeHintType, Any> = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",
                DecodeHintType.TRY_HARDER to true
            )
            MultiFormatReader().decode(bmp, hints).text
        } catch (_: Throwable) {
            null
        }
    }

    /** 保存二维码位图到本地（相册 Pictures/WenYou 或应用图片目录），返回位置或 null。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, title: String): String? {
        return try {
            val safeTitle = title.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_").take(40).ifBlank { "share_qr" }
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$safeTitle.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${BuildConfig.APP_FILE_PREFIX}")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
                if (!ok) return null
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/${BuildConfig.APP_FILE_PREFIX}"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
                dir.mkdirs()
                val f = File(dir, "$safeTitle.png")
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.absolutePath
            }
        } catch (_: Throwable) {
            null
        }
    }
}
