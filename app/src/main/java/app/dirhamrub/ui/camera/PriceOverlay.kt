package app.dirhamrub.ui.camera

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min

/**
 * Метки цен поверх превью: уголки-«прицел» вокруг цены и пилюля с тенью и хвостиком;
 * старая цена — серая компактная метка; догадка (число без валюты) — графитовая метка с белым контуром.
 * Все строки и контуры готовит [PricePipeline]; здесь только рисование заранее созданными
 * Paint — без аллокаций в draw. [frame] читается внутри draw, поэтому новый кадр
 * перерисовывает Canvas без рекомпозиции.
 */
@Composable
fun PriceOverlay(
    frame: State<LabelFrame>,
    metrics: LabelMetrics,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val paints = remember(metrics, density) {
        OverlayPaints(
            corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
            },
            boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
            outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = android.graphics.Color.WHITE
            },
            bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                // Тень отделяет пилюлю от пёстрого фона ценника.
                setShadowLayer(6f * density, 0f, 2f * density, 0x66000000)
            },
            main = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = metrics.mainTextPx
                typeface = metrics.mainTypeface
                color = android.graphics.Color.WHITE
            },
            sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = metrics.subTextPx
                typeface = metrics.subTypeface
                color = android.graphics.Color.WHITE
            },
            accent = METRO_RED,
            old = OLD_PRICE,
            unsure = UNSURE_PRICE,
            boxRadius = 6f * density,
            cornerLen = 14f * density,
        )
    }
    Canvas(modifier) {
        val labels = frame.value.labels
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            for (i in labels.indices) {
                val l = labels[i]
                val a = (l.alpha * 255f).toInt().coerceIn(0, 255)
                val tint = when {
                    l.isOld -> paints.old
                    l.unsure -> paints.unsure
                    else -> paints.accent
                }
                val b = l.box

                // Лёгкая подсветка цены и уголки «прицела».
                paints.boxFill.color = tint
                paints.boxFill.alpha = (a * 0.14f).toInt()
                c.drawRoundRect(b, paints.boxRadius, paints.boxRadius, paints.boxFill)
                paints.corner.color = tint
                paints.corner.alpha = if (l.isOld) (a * 0.7f).toInt() else a
                val len = min(paints.cornerLen, min(b.width(), b.height()) / 2.5f)
                c.drawLine(b.left, b.top, b.left + len, b.top, paints.corner)
                c.drawLine(b.left, b.top, b.left, b.top + len, paints.corner)
                c.drawLine(b.right, b.top, b.right - len, b.top, paints.corner)
                c.drawLine(b.right, b.top, b.right, b.top + len, paints.corner)
                c.drawLine(b.left, b.bottom, b.left + len, b.bottom, paints.corner)
                c.drawLine(b.left, b.bottom, b.left, b.bottom - len, paints.corner)
                c.drawLine(b.right, b.bottom, b.right - len, b.bottom, paints.corner)
                c.drawLine(b.right, b.bottom, b.right, b.bottom - len, paints.corner)

                paints.bubble.color = tint
                paints.bubble.alpha = if (l.isOld) (a * 0.85f).toInt() else a
                c.drawPath(l.bubble, paints.bubble)
                if (l.unsure) {
                    paints.outline.alpha = (a * 0.7f).toInt()
                    c.drawPath(l.bubble, paints.outline)
                }

                if (l.main.isNotEmpty()) {
                    paints.main.alpha = a
                    c.drawText(l.main, l.mainX, l.mainY, paints.main)
                }
                // У старой цены одна приглушённая строка «было … AED ≈ … ₽».
                paints.sub.alpha = if (l.isOld) (a * 0.95f).toInt() else (a * 0.85f).toInt()
                c.drawText(l.sub, l.subX, l.subY, paints.sub)
            }
        }
    }
}

/**
 * Поверх камеры цвета не зависят от темы: красный метро (светлый акцент) даёт белому тексту
 * нужный контраст, серый — для старых цен.
 */
private const val METRO_RED = 0xFFC8272D.toInt()

private const val OLD_PRICE = 0xFF5D6975.toInt()

/** Догадка — тёмный графит: заметна, но не спорит с уверенными красными метками. */
private const val UNSURE_PRICE = 0xFF2A3139.toInt()

private class OverlayPaints(
    val corner: Paint,
    val boxFill: Paint,
    val outline: Paint,
    val bubble: Paint,
    val main: Paint,
    val sub: Paint,
    val accent: Int,
    val old: Int,
    val unsure: Int,
    val boxRadius: Float,
    val cornerLen: Float,
)
