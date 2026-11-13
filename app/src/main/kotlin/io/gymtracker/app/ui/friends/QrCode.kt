package io.gymtracker.app.ui.friends

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Código QR negro sobre blanco (siempre, también en tema oscuro, para que se escanee bien). */
@Composable
fun QrCode(content: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { qrBitmap(content) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun qrBitmap(text: String): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val scale = 8
    val size = matrix.width * scale
    val pixels = IntArray(size * size) { i ->
        val x = (i % size) / scale
        val y = (i / size) / scale
        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
