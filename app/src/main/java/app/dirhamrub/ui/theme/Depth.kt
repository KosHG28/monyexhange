package app.dirhamrub.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Глубина в тёмной теме: у верхнего края карточки — тонкая светлая полоса, как свет сверху. Слои читаются без
 * резких рамок. В светлой теме — ничего. Ставить после clip и фона карточки.
 */
fun Modifier.topLight(): Modifier = composed {
    if (!AppTheme.colors.isDark) return@composed this
    drawWithContent {
        drawContent()
        val h = 1.5.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), startY = 0f, endY = h * 2),
            size = Size(size.width, h * 2),
        )
    }
}

/**
 * Мягкое свечение вокруг элемента (поле цены на главном): размытый ореол цвета [color] за краями формы со
 * скруглением [corner]. Размытие рисует видеокарта — с Android 9; на старых — ничего (там остаётся обводка).
 * Ставить до clip, иначе ореол обрежется.
 */
fun Modifier.softGlow(color: Color, radius: Dp, corner: Dp): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        this
    } else {
        drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.toArgb()
                    maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                val r = corner.toPx()
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
        }
    }

/** Свечение поддерживается (Android 9+): иначе вместо него — обычная обводка. */
val glowSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
