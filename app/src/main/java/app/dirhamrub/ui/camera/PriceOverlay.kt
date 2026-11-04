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
 * Метки цен поверх превью. Фокус (цена ближе к центру) — крупная плашка с хвостиком и уголками «прицела»,
 * остальные — короткие плашки над ценой; старая цена — серая, догадка (число без валюты) — графитовая с контуром.
 * Все строки и контуры готовит [PricePipeline]; здесь только рисование заранее созданными
 * Paint — без аллокаций в draw. [frame] читается внутри draw, поэтому новый кадр
 * перерисовывает Canvas без рекомпозиции.
 */
@Composable
fun PriceOverlay(
    frame: State<LabelFrame>,
    metrics: LabelMetrics,
    reveal: State<Boolean>,
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
            patch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
            embed = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = metrics.mainTypeface },
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
        // Палец на экране — показываем настоящие ценники без меток.
        if (reveal.value) return@Canvas
        val labels = frame.value.labels
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // Сначала обычные плашки, фокус — поверх.
            for (pass in 0..1) {
                for (i in labels.indices) {
                    val l = labels[i]
                    if (l.focused != (pass == 1)) continue
                    val a = (l.alpha * 255f).toInt().coerceIn(0, 255)
                    if (l.embedded) {
                        drawEmbedded(c, l, a, paints)
                        continue
                    }
                    val tint = when {
                        l.isOld -> paints.old
                        l.unsure -> paints.unsure
                        else -> paints.accent
                    }
                    val b = l.box
                    // Лёгкая подсветка цены: у фокуса заметнее и с уголками «прицела».
                    paints.boxFill.color = tint
                    paints.boxFill.alpha = (a * if (l.focused) 0.16f else 0.08f).toInt()
                    c.drawRoundRect(b, paints.boxRadius, paints.boxRadius, paints.boxFill)
                    if (l.focused) {
                        paints.corner.color = tint
                        paints.corner.alpha = a
                        val len = min(paints.cornerLen, min(b.width(), b.height()) / 2.5f)
                        c.drawLine(b.left, b.top, b.left + len, b.top, paints.corner)
                        c.drawLine(b.left, b.top, b.left, b.top + len, paints.corner)
                        c.drawLine(b.right, b.top, b.right - len, b.top, paints.corner)
                        c.drawLine(b.right, b.top, b.right, b.top + len, paints.corner)
                        c.drawLine(b.left, b.bottom, b.left + len, b.bottom, paints.corner)
                        c.drawLine(b.left, b.bottom, b.left, b.bottom - len, paints.corner)
                        c.drawLine(b.right, b.bottom, b.right - len, b.bottom, paints.corner)
                        c.drawLine(b.right, b.bottom, b.right, b.bottom - len, paints.corner)
                    }

                    paints.bubble.color = tint
                    // Соседние плашки чуть прозрачнее фокуса — взгляд сразу идёт к нему.
                    paints.bubble.alpha = if (l.focused) a else (a * 0.88f).toInt()
                    c.drawPath(l.bubble, paints.bubble)
                    if (l.unsure) {
                        paints.outline.alpha = (a * 0.7f).toInt()
                        c.drawPath(l.bubble, paints.outline)
                    }
                    val text = if (l.focused) paints.main else paints.sub
                    text.alpha = a
                    c.drawText(l.text, l.textX, l.textY, text)
                }
            }
        }
    }
}

/**
 * Сумма, вписанная в ценник: подложка цвета бумаги с мягким краем закрывает старую цену,
 * сумма — цветом цифр, под ней красная черта (у старой цены — зачёркивание цветом цифр);
 * у цены в фокусе — тонкие красные уголки.
 */
private fun drawEmbedded(c: android.graphics.Canvas, l: PriceLabel, a: Int, paints: OverlayPaints) {
    // Наклонный ценник: всё посчитано ровно в системе строки — поворачиваем холст вокруг её центра.
    val tilted = l.angle != 0f
    if (tilted) {
        c.save()
        c.rotate(l.angle, l.pivotX, l.pivotY)
    }
    paints.patch.color = l.paper
    paints.patch.alpha = (a * 0.55f).toInt()
    c.drawRoundRect(l.feather, l.radius * 1.6f, l.radius * 1.6f, paints.patch)
    paints.patch.alpha = a
    c.drawRoundRect(l.pill, l.radius, l.radius, paints.patch)

    paints.embed.textSize = l.textSize
    paints.embed.color = l.ink
    paints.embed.alpha = a
    c.drawText(l.text, l.textX, l.textY, paints.embed)

    paints.patch.color = if (l.isOld) l.ink else paints.accent
    paints.patch.alpha = a
    c.drawRect(l.mark, paints.patch)

    if (l.focused) {
        val b = l.feather
        val len = min(paints.cornerLen, min(b.width(), b.height()) / 3f)
        paints.corner.color = paints.accent
        paints.corner.alpha = a
        c.drawLine(b.left, b.top, b.left + len, b.top, paints.corner)
        c.drawLine(b.left, b.top, b.left, b.top + len, paints.corner)
        c.drawLine(b.right, b.top, b.right - len, b.top, paints.corner)
        c.drawLine(b.right, b.top, b.right, b.top + len, paints.corner)
        c.drawLine(b.left, b.bottom, b.left + len, b.bottom, paints.corner)
        c.drawLine(b.left, b.bottom, b.left, b.bottom - len, paints.corner)
        c.drawLine(b.right, b.bottom, b.right - len, b.bottom, paints.corner)
        c.drawLine(b.right, b.bottom, b.right, b.bottom - len, paints.corner)
    }
    if (tilted) c.restore()
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
    val patch: Paint,
    val embed: Paint,
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
