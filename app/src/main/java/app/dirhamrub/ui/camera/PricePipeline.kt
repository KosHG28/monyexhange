package app.dirhamrub.ui.camera

import android.graphics.Paint
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

/** Готовая к отрисовке метка: все строки и прямоугольники посчитаны заранее, draw только рисует. */
class PriceLabel(
    val id: Long,
    val value: BigDecimal,
    val isOld: Boolean,
    val alpha: Float,
    /** Рамка вокруг цены. */
    val box: RectF,
    /** «Пилюля» с суммой в рублях. */
    val pill: RectF,
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

/** Размеры меток в пикселях. */
class LabelMetrics(
    val mainTextPx: Float,
    val subTextPx: Float,
    val padX: Float,
    val padY: Float,
    val lineGap: Float,
    val gap: Float,
    val margin: Float,
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
        val labels = ArrayList<PriceLabel>(lastTracked.size)
        val placed = ArrayList<RectF>(lastTracked.size)
        // Сверху вниз: так раздвигание вниз не уводит метку под соседнюю.
        for (t in lastTracked.sortedBy { it.box.top }) {
            val rubSelected = t.value.multiply(selectedRate.rubPerAed, MC)
            val rubOther = t.value.multiply(otherRate.rubPerAed, MC)
            val main = "≈ ${Numbers.cheat(rubSelected)} ₽"
            val sub = "${selected.other.symbol} ${Numbers.cheat(rubOther)} ₽"
            val mainW = mainPaint.measureText(main)
            val subW = subPaint.measureText(sub)
            val mainH = mainMetrics.descent - mainMetrics.ascent
            val subH = subMetrics.descent - subMetrics.ascent
            val pillW = maxOf(mainW, subW) + 2 * metrics.padX
            val pillH = mainH + metrics.lineGap + subH + 2 * metrics.padY

            val box = RectF(t.box.left, t.box.top, t.box.right, t.box.bottom)
            var left = box.centerX() - pillW / 2f
            if (viewWidth > 0f) left = left.coerceIn(metrics.margin, maxOf(metrics.margin, viewWidth - metrics.margin - pillW))
            var top = box.top - metrics.gap - pillH
            if (top < topInset + metrics.margin) top = box.bottom + metrics.gap
            val pill = RectF(left, top, left + pillW, top + pillH)

            // Перекрывающиеся метки раздвигаем по вертикали.
            var moved = true
            var guard = 0
            while (moved && guard++ < 12) {
                moved = false
                for (other in placed) {
                    if (RectF.intersects(pill, other)) {
                        pill.offsetTo(pill.left, other.bottom + metrics.gap)
                        moved = true
                    }
                }
            }
            if (viewHeight > 0f && pill.bottom > viewHeight - bottomInset) {
                pill.offsetTo(pill.left, maxOf(topInset, viewHeight - bottomInset - pillH))
            }
            placed += pill

            val mainX = pill.centerX() - mainW / 2f
            val mainY = pill.top + metrics.padY - mainMetrics.ascent
            val subX = pill.centerX() - subW / 2f
            val subY = mainY + mainMetrics.descent + metrics.lineGap - subMetrics.ascent
            labels += PriceLabel(
                id = t.id, value = t.value, isOld = t.isOld, alpha = t.alpha,
                box = box, pill = pill,
                main = main, mainX = mainX, mainY = mainY,
                sub = sub, subX = subX, subY = subY,
                rubSelected = rubSelected, rubOther = rubOther,
            )
        }
        _frames.value = LabelFrame(labels, ++version)
    }
}
