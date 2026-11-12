package app.dirhamrub.ui.camera

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.math.BigDecimal

/**
 * Готовая к отрисовке метка: строка, прямоугольники и контур посчитаны заранее, draw только рисует.
 *
 * Два вида. Вписанная ([embedded]): старая цена закрашена цветом бумаги ценника, сумма в итоговой валюте
 * написана поверх цветом его цифр и подчёркнута красным (старая цена — зачёркнута).
 * Плашка: метка в фокусе (цена ближе всех к центру экрана) — крупнее, с хвостиком и уголками «прицела»,
 * остальные — короткие плашки «224 ₽» над ценой. Подробности фокуса — в карточке внизу экрана.
 */
class PriceLabel(
    val id: Long,
    val value: BigDecimal,
    val isOld: Boolean,
    /** Догадка: крупное число без валюты и копеек — графитовая плашка с «?». */
    val unsure: Boolean,
    val focused: Boolean,
    /** Итоговая прозрачность 0..1 (появление и исчезновение). */
    val alpha: Float,
    /** Рамка вокруг цифр цены на ценнике (препятствие для плашек, попадание пальцем). */
    val box: RectF,
    /** Рамка цены вместе со знаком валюты — её подсвечивают уголки фокуса. */
    val frame: RectF = box,
    /** Плашка с суммой в итоговой валюте. */
    val pill: RectF,
    /** Плашка (у фокуса — с хвостиком к цене). */
    val bubble: Path,
    val text: String,
    val textX: Float,
    val textY: Float,
    /** Сумма вписана в ценник вместо старой цены. */
    val embedded: Boolean = false,
    /** Вписанная: цвет бумаги и цифр ценника (из кадра), размер шрифта, мягкий край и скругление подложки. */
    val paper: Int = 0,
    /** Вписанная: переход цвета бумаги от начала строки к концу (ценник освещён неровно); null — ровный цвет. */
    val paperShader: Shader? = null,
    val ink: Int = 0,
    val textSize: Float = 0f,
    val feather: RectF = EMPTY_RECT,
    val radius: Float = 0f,
    /** Вписанная: подчёркивание (знак пересчёта) или зачёркивание у старой цены. */
    val mark: RectF = EMPTY_RECT,
    /** Вписанная: наклон ценника, градусы; рисуется поворотом холста вокруг ([pivotX], [pivotY]). */
    val angle: Float = 0f,
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
    /** Когда метка впервые появилась (мс, uptime) — вписанная сумма проявляется от этого момента. */
    val shownAt: Long = 0L,
    /** Сумма в итоговой валюте по выбранной карте (или ЦБ) — для карточки, шторки и TalkBack. */
    val homeAmount: BigDecimal?,
    /** Сумма по курсу ЦБ — для карточки фокуса (null — не показывать). */
    val officialAmount: BigDecimal?,
)

internal val EMPTY_RECT = RectF()

/** Цвета ценника под ценой: бумага у начала и у конца строки и цифры (ARGB). */
class TagColors(val paperStart: Int, val paperEnd: Int, val ink: Int)

/**
 * Цена, у которой нужно взять цвета ценника: строка «цена + валюта» с центром ([cx], [cy]),
 * размерами «как на ценнике» [width] × [height] и наклоном [angle] (градусы) — в координатах экрана.
 */
class SampleTarget(val id: Long, val cx: Float, val cy: Float, val width: Float, val height: Float, val angle: Float)

class LabelFrame(val labels: List<PriceLabel>, val version: Long) {
    val focus: PriceLabel? get() = labels.firstOrNull { it.focused }

    companion object {
        val EMPTY = LabelFrame(emptyList(), 0)
    }
}

/** Размеры и шрифты меток в пикселях: [mainTextPx] — метка в фокусе, [subTextPx] — остальные. */
class LabelMetrics(
    val mainTextPx: Float,
    val subTextPx: Float,
    val padX: Float,
    val padY: Float,
    val gap: Float,
    val margin: Float,
    val radius: Float,
    val tail: Float,
    val mainTypeface: Typeface?,
    val subTypeface: Typeface?,
)
