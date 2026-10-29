package app.dirhamrub.ui.camera

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import app.dirhamrub.ui.theme.AppTheme

/**
 * Метки цен поверх превью. Все строки и прямоугольники готовит [PricePipeline];
 * здесь только рисование заранее созданными Paint — без аллокаций в draw.
 * [frame] читается внутри draw, поэтому новый кадр перерисовывает Canvas без рекомпозиции.
 */
@Composable
fun PriceOverlay(
    frame: State<LabelFrame>,
    mainTypeface: Typeface?,
    subTypeface: Typeface?,
    metrics: LabelMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val paints = remember(colors, mainTypeface, subTypeface, metrics) {
        val accent = colors.accent.toArgb()
        val stroke = with(density) { 2f * this.density }
        OverlayPaints(
            boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = accent
            },
            boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = accent
            },
            pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
            main = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = metrics.mainTextPx
                typeface = mainTypeface
                color = android.graphics.Color.WHITE
            },
            sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = metrics.subTextPx
                typeface = subTypeface
                color = android.graphics.Color.WHITE
            },
            strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = stroke * 0.75f
                color = android.graphics.Color.WHITE
            },
            accent = accent,
            old = 0xFF5D6975.toInt(),
            radius = with(density) { 10f * this.density },
            boxRadius = with(density) { 6f * this.density },
        )
    }
    Canvas(modifier) {
        val labels = frame.value.labels
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            for (i in labels.indices) {
                val l = labels[i]
                val a = (l.alpha * 255f).toInt().coerceIn(0, 255)
                val tint = if (l.isOld) paints.old else paints.accent

                paints.boxFill.color = tint
                paints.boxFill.alpha = (a * 0.18f).toInt()
                c.drawRoundRect(l.box, paints.boxRadius, paints.boxRadius, paints.boxFill)
                paints.boxStroke.color = tint
                paints.boxStroke.alpha = if (l.isOld) (a * 0.7f).toInt() else a
                c.drawRoundRect(l.box, paints.boxRadius, paints.boxRadius, paints.boxStroke)

                paints.pill.color = tint
                paints.pill.alpha = if (l.isOld) (a * 0.8f).toInt() else (a * 0.94f).toInt()
                c.drawRoundRect(l.pill, paints.radius, paints.radius, paints.pill)

                paints.main.alpha = a
                c.drawText(l.main, l.mainX, l.mainY, paints.main)
                paints.sub.alpha = (a * 0.8f).toInt()
                c.drawText(l.sub, l.subX, l.subY, paints.sub)
                if (l.isOld) {
                    // Старая цена — зачёркнутая и приглушённая.
                    paints.strike.alpha = a
                    val y = l.mainY - metrics.mainTextPx * 0.3f
                    c.drawLine(l.pill.left + metrics.padX * 0.6f, y, l.pill.right - metrics.padX * 0.6f, y, paints.strike)
                }
            }
        }
    }
}

private class OverlayPaints(
    val boxStroke: Paint,
    val boxFill: Paint,
    val pill: Paint,
    val main: Paint,
    val sub: Paint,
    val strike: Paint,
    val accent: Int,
    val old: Int,
    val radius: Float,
    val boxRadius: Float,
)
