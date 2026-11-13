package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Software blur cached as a bitmap: BlurMaskFilter also works on a hardware accelerated Compose Canvas.
 * The centered shadow is outside the clipped surface. Animation only changes draw alpha, not the bitmap. */
fun Modifier.paymentRowShadow(enabled: Boolean, opacity: () -> Float): Modifier = if (!enabled) this else drawWithCache {
    val inset = 24.dp.toPx()
    val bitmap = Bitmap.createBitmap(ceil(size.width + inset * 2).toInt().coerceAtLeast(1),
        ceil(size.height + inset * 2).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PayRowShadow.surround.toArgb()
        maskFilter = BlurMaskFilter(12.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    android.graphics.Canvas(bitmap).drawRoundRect(inset, inset, inset + size.width, inset + size.height,
        16.dp.toPx(), 16.dp.toPx(), paint)
    val shadow = bitmap.asImageBitmap()
    onDrawBehind { drawImage(shadow, topLeft = Offset(-inset, -inset), alpha = opacity().coerceIn(0f, 1f)) }
}
