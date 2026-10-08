package io.wenyou.textquest

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.wenyou.textquest.ui.common.QrCode
import org.junit.Assert.assertEquals
import org.junit.Test

class QrScanDecodeTest {
    private val text = "WY2:camera-frame-测试-" + "x".repeat(300)

    /** A camera Y plane: white margin, padded rows (rowStride > width), optionally rotated like a portrait sensor. */
    private fun frame(rotated: Boolean): Triple<ByteArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
        val scale = 4
        val size = matrix.width * scale
        val width = size + 40
        val height = size + 20
        val stride = width + 24
        val data = ByteArray(stride * height) { 0xFF.toByte() }
        for (y in 0 until size) for (x in 0 until size) {
            if (matrix.get(x / scale, y / scale)) {
                // A portrait sensor stores the code rotated; detection must not depend on orientation.
                val (px, py) = if (rotated) (size - 1 - y) to x else x to y
                data[(py + 10) * stride + px + 20] = 0
            }
        }
        return Triple(data, stride, width)
    }

    @Test fun decodesPaddedAndRotatedCameraFrames() {
        val (plain, stride, width) = frame(rotated = false)
        assertEquals(text, QrCode.decodeYuv(plain, stride, width, plain.size / stride))
        val (turned, stride2, width2) = frame(rotated = true)
        assertEquals(text, QrCode.decodeYuv(turned, stride2, width2, turned.size / stride2))
    }
}
