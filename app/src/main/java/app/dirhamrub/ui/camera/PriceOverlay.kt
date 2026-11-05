package app.dirhamrub.ui.camera

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.flow.collectLatest

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
    // Часы для проявления вписанных сумм: тикают каждый кадр, только пока какая-то сумма проявляется.
    val reduceMotion = LocalReduceMotion.current
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(frame, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        snapshotFlow { frame.value }.collectLatest { f ->
            while (true) {
                val now = withFrameMillis { it }
                clock.longValue = now
                if (f.labels.none { now - it.shownAt < REVEAL_MS }) break
            }
        }
    }
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
        val now = clock.longValue
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // Сначала обычные плашки, фокус — поверх.
            for (pass in 0..1) {
                for (i in labels.indices) {
                    val l = labels[i]
                    if (l.focused != (pass == 1)) continue
                    val a = (l.alpha * 255f).toInt().coerceIn(0, 255)
                    if (l.embedded) {
                        val reveal = if (reduceMotion) 1f else ((now - l.shownAt) / REVEAL_MS.toFloat()).coerceIn(0f, 1f)
                        drawEmbedded(c, l, a, paints, reveal)
                        continue
                    }
                    val tint = when {
                        l.isOld -> paints.old
                        l.unsure -> paints.unsure
                        else -> paints.accent
                    }
                    // Лёгкая подсветка цены вместе с валютой: у фокуса заметнее и с уголками «прицела».
                    val b = l.frame
                    // Плашка выпрыгивает: от 0,6 размера с лёгким перелётом, из хвостика к цене.
                    val pop = if (reduceMotion) 1f else ((now - l.shownAt) / REVEAL_MS.toFloat()).coerceIn(0f, 1f)
                    val popAlpha = (pop * 4f).coerceAtMost(1f)
                    val popScale = if (pop >= 1f) 1f else 0.6f + 0.4f * (1f - exp(-6f * pop) * cos(9f * pop))
                    paints.boxFill.color = tint
                    paints.boxFill.alpha = (a * popAlpha * if (l.focused) 0.16f else 0.08f).toInt()
                    c.drawRoundRect(b, paints.boxRadius, paints.boxRadius, paints.boxFill)
                    if (l.focused) {
                        paints.corner.color = tint
                        paints.corner.alpha = (a * popAlpha).toInt()
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

                    val pa = (a * popAlpha).toInt()
                    val popping = popScale != 1f
                    if (popping) {
                        val pointsDown = l.pill.bottom <= l.box.top
                        c.save()
                        c.scale(popScale, popScale, l.box.centerX().coerceIn(l.pill.left, l.pill.right), if (pointsDown) l.pill.bottom else l.pill.top)
                    }
                    paints.bubble.color = tint
                    // Соседние плашки чуть прозрачнее фокуса — взгляд сразу идёт к нему.
                    paints.bubble.alpha = if (l.focused) pa else (pa * 0.88f).toInt()
                    c.drawPath(l.bubble, paints.bubble)
                    if (l.unsure) {
                        paints.outline.alpha = (pa * 0.7f).toInt()
                        c.drawPath(l.bubble, paints.outline)
                    }
                    val text = if (l.focused) paints.main else paints.sub
                    text.alpha = pa
                    c.drawText(l.text, l.textX, l.textY, text)
                    if (popping) c.restore()
                }
            }
        }
    }
}

/**
 * Сумма, вписанная в ценник: подложка цвета бумаги с мягким краем закрывает старую цену,
 * сумма — цветом цифр, под ней красная черта (у старой цены — зачёркивание цветом цифр);
 * у цены в фокусе — тонкие красные уголки.
 * [reveal] 0..1 — проявление: подложка закрывает цену слева направо, затем проступает сумма,
 * дорисовывается черта, уголки сходятся к цене.
 */
private fun drawEmbedded(c: android.graphics.Canvas, l: PriceLabel, a: Int, paints: OverlayPaints, reveal: Float) {
    val wipe = easeOut((reveal / 0.7f).coerceIn(0f, 1f))
    val textIn = ((reveal - 0.3f) / 0.45f).coerceIn(0f, 1f)
    val markIn = easeOut(((reveal - 0.5f) / 0.5f).coerceIn(0f, 1f))
    val cornersIn = easeOut(reveal)
    // Наклонный ценник: всё посчитано ровно в системе строки — поворачиваем холст вокруг её центра.
    val tilted = l.angle != 0f
    if (tilted) {
        c.save()
        c.rotate(l.angle, l.pivotX, l.pivotY)
    }
    // Бумага — переходом от начала строки к концу, если ценник освещён неровно; закрывает цену слева направо.
    val f = l.feather
    val wiping = wipe < 1f
    if (wiping) {
        c.save()
        c.clipRect(f.left, f.top - f.height(), f.left + f.width() * wipe, f.bottom + f.height())
    }
    paints.patch.color = l.paper
    paints.patch.shader = l.paperShader
    paints.patch.alpha = (a * 0.55f).toInt()
    c.drawRoundRect(f, l.radius * 1.6f, l.radius * 1.6f, paints.patch)
    paints.patch.alpha = a
    c.drawRoundRect(l.pill, l.radius, l.radius, paints.patch)
    paints.patch.shader = null
    if (wiping) c.restore()

    if (textIn > 0f) {
        paints.embed.textSize = l.textSize
        paints.embed.color = l.ink
        paints.embed.alpha = (a * textIn).toInt()
        c.drawText(l.text, l.textX, l.textY, paints.embed)
    }

    if (markIn > 0f) {
        val m = l.mark
        paints.patch.color = if (l.isOld) l.ink else paints.accent
        paints.patch.alpha = a
        c.drawRect(m.left, m.top, m.left + m.width() * markIn, m.bottom, paints.patch)
    }

    if (l.focused && cornersIn > 0f) {
        // Уголки сходятся к цене: от 1,25 размера к рамке.
        val grow = 1f + 0.25f * (1f - cornersIn)
        val hw = f.width() / 2f * grow
        val hh = f.height() / 2f * grow
        val cx = f.centerX()
        val cy = f.centerY()
        val left = cx - hw
        val right = cx + hw
        val top = cy - hh
        val bottom = cy + hh
        val len = min(paints.cornerLen, min(f.width(), f.height()) / 3f)
        paints.corner.color = paints.accent
        paints.corner.alpha = (a * cornersIn).toInt()
        c.drawLine(left, top, left + len, top, paints.corner)
        c.drawLine(left, top, left, top + len, paints.corner)
        c.drawLine(right, top, right - len, top, paints.corner)
        c.drawLine(right, top, right, top + len, paints.corner)
        c.drawLine(left, bottom, left + len, bottom, paints.corner)
        c.drawLine(left, bottom, left, bottom - len, paints.corner)
        c.drawLine(right, bottom, right - len, bottom, paints.corner)
        c.drawLine(right, bottom, right, bottom - len, paints.corner)
    }
    if (tilted) c.restore()
}

private fun easeOut(p: Float): Float {
    val q = 1f - p
    return 1f - q * q * q
}

/** Длительность проявления вписанной суммы и «выпрыгивания» плашки. */
private const val REVEAL_MS = 380L

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
