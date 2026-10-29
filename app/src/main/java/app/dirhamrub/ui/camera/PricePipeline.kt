package app.dirhamrub.ui.camera

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Defaults
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.price.PriceParser
import app.dirhamrub.domain.price.PriceTracker
import app.dirhamrub.domain.price.TrackedPrice
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/** Готовая к отрисовке метка: все строки, прямоугольники и контур посчитаны заранее, draw только рисует. */
class PriceLabel(
    val id: Long,
    val value: BigDecimal,
    val isOld: Boolean,
    /** Итоговая прозрачность 0..1 (появление и исчезновение). */
    val alpha: Float,
    /** Рамка вокруг цены на ценнике. */
    val box: RectF,
    /** «Пилюля» с суммой в рублях. */
    val pill: RectF,
    /** Пилюля + хвостик, указывающий на цену. */
    val bubble: Path,
    val main: String,
    val mainX: Float,
    val mainY: Float,
    val sub: String,
    val subX: Float,
    val subY: Float,
    /** Рубли по выбранной и другой карте — для шторки и TalkBack. */
    val rubSelected: BigDecimal,
    val rubOther: BigDecimal,
)

class LabelFrame(val labels: List<PriceLabel>, val version: Long) {
    companion object {
        val EMPTY = LabelFrame(emptyList(), 0)
    }
}

/** Размеры и шрифты меток в пикселях. */
class LabelMetrics(
    val mainTextPx: Float,
    val subTextPx: Float,
    val padX: Float,
    val padY: Float,
    val lineGap: Float,
    val gap: Float,
    val margin: Float,
    val radius: Float,
    val tail: Float,
    val mainTypeface: Typeface?,
    val subTypeface: Typeface?,
)

/**
 * Конвейер камеры: OCR → [PriceParser] → [PriceTracker] → раскладка меток.
 * [onText] вызывается на фоновом executor-е анализатора; [frames] читает Canvas.
 */
class PricePipeline(private val metrics: LabelMetrics) {
    private val parser = PriceParser()
    private val tracker = PriceTracker()
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.mainTextPx
        typeface = metrics.mainTypeface
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.subTextPx
        typeface = metrics.subTypeface
    }
    private val mainMetrics = mainPaint.fontMetrics
    private val subMetrics = subPaint.fontMetrics

    @Volatile private var rates: Map<Card, CardRate> = Defaults.rates()
    @Volatile private var selected: Card = Card.CNY
    @Volatile private var viewWidth = 0f
    @Volatile private var viewHeight = 0f
    @Volatile private var topInset = 0f
    @Volatile private var bottomInset = 0f
    @Volatile var paused: Boolean = false
        private set

    private var lastTracked: List<TrackedPrice> = emptyList()
    private var lastNowMs = 0L
    /** Когда метка впервые показана — для плавного появления. */
    private val shownAt = HashMap<Long, Long>()
    private var version = 0L

    private val _frames = MutableStateFlow(LabelFrame.EMPTY)
    val frames: StateFlow<LabelFrame> = _frames.asStateFlow()

    fun setRates(rates: Map<Card, CardRate>, selected: Card) {
        this.rates = rates
        this.selected = selected
        relayout()
    }

    fun setViewport(width: Float, height: Float, top: Float, bottom: Float) {
        viewWidth = width
        viewHeight = height
        topInset = top
        bottomInset = bottom
        relayout()
    }

    fun setPaused(value: Boolean) {
        synchronized(this) {
            paused = value
            if (!value) {
                tracker.clear()
                shownAt.clear()
                lastTracked = emptyList()
            }
        }
        if (!value) _frames.value = LabelFrame.EMPTY
    }

    /** Кадр OCR (фоновый поток). */
    fun onText(text: Text?, nowMs: Long) {
        synchronized(this) {
            if (paused) return
            val candidates = parser.parse(OcrMapper.map(text))
            lastTracked = tracker.update(candidates, nowMs)
            lastNowMs = nowMs
            publish()
        }
    }

    private fun relayout() {
        synchronized(this) { publish() }
    }

    private fun publish() {
        val rates = rates
        val selected = selected
        val selectedRate = rates.getValue(selected)
        val otherRate = rates.getValue(selected.other)
        val m = metrics
        val labels = ArrayList<PriceLabel>(lastTracked.size)
        shownAt.keys.retainAll(lastTracked.mapTo(HashSet()) { it.id })

        // Препятствия: рамки всех найденных цен (метка не должна закрывать чужую цену)
        // и уже поставленные пилюли.
        val boxes = lastTracked.associate { it.id to RectF(it.box.left, it.box.top, it.box.right, it.box.bottom) }
        val placed = ArrayList<RectF>(lastTracked.size)

        // Сначала актуальные цены по убыванию скоринга — им достаются лучшие места, потом старые.
        val order = lastTracked.sortedWith(compareBy<TrackedPrice> { it.isOld }.thenByDescending { it.score })
        for (t in order) {
            val rubSelected = t.value.multiply(selectedRate.rubPerAed, MC)
            val rubOther = t.value.multiply(otherRate.rubPerAed, MC)
            // Распознанная цена в AED — чтобы сразу видеть, правильно ли прочитан ценник.
            val aed = "${Numbers.money(t.value)} AED"
            val main: String
            val sub: String
            if (t.isOld) {
                // Старая цена — компактная однострочная метка.
                main = ""
                sub = "было $aed ≈ ${Numbers.cheat(rubSelected)} ₽"
            } else {
                main = "≈ ${Numbers.cheat(rubSelected)} ₽"
                sub = "$aed · ${selected.other.symbol} ${Numbers.cheat(rubOther)} ₽"
            }

            val mainW = if (main.isEmpty()) 0f else mainPaint.measureText(main)
            val subW = subPaint.measureText(sub)
            val mainH = if (main.isEmpty()) 0f else mainMetrics.descent - mainMetrics.ascent + m.lineGap
            val subH = subMetrics.descent - subMetrics.ascent
            val padY = if (t.isOld) m.padY * 0.7f else m.padY
            val pillW = maxOf(mainW, subW) + 2 * m.padX
            val pillH = mainH + subH + 2 * padY

            val box = boxes.getValue(t.id)
            var left = box.centerX() - pillW / 2f
            if (viewWidth > 0f) left = left.coerceIn(m.margin, maxOf(m.margin, viewWidth - m.margin - pillW))
            val aboveTop = box.top - m.gap - m.tail - pillH
            val belowTop = box.bottom + m.gap + m.tail
            val pill = RectF(left, aboveTop, left + pillW, aboveTop + pillH)

            fun blocked(r: RectF): Boolean =
                r.top < topInset + m.margin ||
                    (viewHeight > 0f && r.bottom > viewHeight - bottomInset) ||
                    placed.any { RectF.intersects(r, it) } ||
                    boxes.any { (id, b) -> id != t.id && RectF.intersects(r, b) }

            // Над ценой; если занято — под ней; если и там занято — сдвигаем вниз до свободного места.
            var above = true
            if (blocked(pill)) {
                pill.offsetTo(left, belowTop)
                above = false
                var guard = 0
                while (blocked(pill) && guard++ < 16) {
                    val hit = placed.firstOrNull { RectF.intersects(pill, it) }
                        ?: boxes.entries.firstOrNull { (id, b) -> id != t.id && RectF.intersects(pill, b) }?.value
                        ?: break
                    pill.offsetTo(left, hit.bottom + m.gap)
                }
            }
            if (viewHeight > 0f && pill.bottom > viewHeight - bottomInset) {
                pill.offsetTo(left, maxOf(topInset, viewHeight - bottomInset - pillH))
            }
            placed += pill

            val bubble = bubblePath(pill, box, above, m)

            val mainX = pill.centerX() - mainW / 2f
            val mainY = pill.top + padY - mainMetrics.ascent
            val subX = pill.centerX() - subW / 2f
            val subY = pill.top + padY + mainH - subMetrics.ascent

            val first = shownAt.getOrPut(t.id) { lastNowMs }
            val appear = ((lastNowMs - first) / APPEAR_MS.toFloat()).coerceIn(0f, 1f)
            labels += PriceLabel(
                id = t.id, value = t.value, isOld = t.isOld,
                alpha = t.alpha * (0.25f + 0.75f * appear),
                box = box, pill = pill, bubble = bubble,
                main = main, mainX = mainX, mainY = mainY,
                sub = sub, subX = subX, subY = subY,
                rubSelected = rubSelected, rubOther = rubOther,
            )
        }
        _frames.value = LabelFrame(labels, ++version)
    }

    /** Скруглённая пилюля с треугольным хвостиком к цене (если пилюлю не сдвинули далеко). */
    private fun bubblePath(pill: RectF, box: RectF, above: Boolean, m: LabelMetrics): Path {
        val path = Path()
        path.addRoundRect(pill, m.radius, m.radius, Path.Direction.CW)
        val pointsDown = above && pill.bottom <= box.top
        val pointsUp = !above && pill.top >= box.bottom
        val tailX = box.centerX().coerceIn(pill.left + m.radius + m.tail, pill.right - m.radius - m.tail)
        if (pointsDown && box.top - pill.bottom <= m.gap + m.tail * 1.5f) {
            path.moveTo(tailX - m.tail, pill.bottom - 1f)
            path.lineTo(tailX + m.tail, pill.bottom - 1f)
            path.lineTo(tailX, pill.bottom + m.tail)
            path.close()
        } else if (pointsUp && pill.top - box.bottom <= m.gap + m.tail * 1.5f) {
            path.moveTo(tailX - m.tail, pill.top + 1f)
            path.lineTo(tailX + m.tail, pill.top + 1f)
            path.lineTo(tailX, pill.top - m.tail)
            path.close()
        }
        return path
    }

    private companion object {
        const val APPEAR_MS = 160L
    }
}
