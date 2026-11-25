package app.dirhamrub.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val DARK_SURFACE_OUTLINE = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.02f)),
)
private val LIGHT_SURFACE_OUTLINE = Brush.verticalGradient(
    listOf(LightColors.ink.copy(alpha = 0.15f), LightColors.ink.copy(alpha = 0.045f)),
)

/** Градиентный контур матовых карточек во всех темах. Ставить после clip и фона. */
fun Modifier.surfaceOutline(shape: Shape): Modifier = composed {
    border(1.dp, if (AppTheme.colors.isDark) DARK_SURFACE_OUTLINE else LIGHT_SURFACE_OUTLINE, shape)
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

/**
 * Свечение поля ввода — цветом его же заливки, без акцента: поле будто светится изнутри. В тёмной теме — на тон
 * светлее заливки (на чёрном фоне цвет самой заливки почти не виден), в светлой — белым ореолом на сером фоне.
 */
fun Modifier.fieldGlow(corner: Dp): Modifier = composed {
    val c = AppTheme.colors
    softGlow(if (c.isDark) c.surface2 else c.surface, radius = 16.dp, corner = corner)
}
