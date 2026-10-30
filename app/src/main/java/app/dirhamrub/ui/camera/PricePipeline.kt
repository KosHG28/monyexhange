package app.dirhamrub.ui.camera

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.price.Box
import app.dirhamrub.domain.price.PriceCurrency
import app.dirhamrub.domain.price.PriceParser
import app.dirhamrub.domain.price.PriceTracker
import app.dirhamrub.domain.price.TrackedPrice
import app.dirhamrub.domain.wallet.Currency
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
    /** Догадка: крупное число без валюты и копеек — рисуется серой меткой с «?». */
    val unsure: Boolean,
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
    /** Сумма в итоговой валюте по выбранной карте (или ЦБ) — для шторки и TalkBack. */
    val homeAmount: BigDecimal?,
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
    private var parser = PriceParser(PriceCurrency.AED)
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

    @Volatile private var spend: Currency = Currency.AED
    @Volatile private var home: Currency = Currency.RUB
    @Volatile private var homePerUnit: BigDecimal? = null
    @Volatile private var officialPerUnit: BigDecimal? = null
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

    /**
     * Валюты и курсы для подписей: [homePerUnit] — по выбранной карте (или ЦБ, если карт нет),
     * [officialPerUnit] — курс ЦБ для второй строки (null — не показывать).
     */
    fun setRates(spend: Currency, home: Currency, homePerUnit: BigDecimal?, officialPerUnit: BigDecimal?) {
        synchronized(this) {
            if (spend != this.spend) {
                // Другая валюта трат — другой разбор ценников; старые метки сбрасываем.
                parser = PriceParser(PriceCurrency.of(spend.code))
                tracker.clear()
                shownAt.clear()
                lastTracked = emptyList()
            }
            this.spend = spend
            this.home = home
            this.homePerUnit = homePerUnit
            this.officialPerUnit = officialPerUnit
            publish()
        }
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
            // Границы экрана: цены у левого и правого края могли обрезаться — их не показываем.
            val bounds = if (viewWidth > 0f && viewHeight > 0f) Box(0f, 0f, viewWidth, viewHeight) else null
            val candidates = parser.parse(OcrMapper.map(text), bounds)
            lastTracked = tracker.update(candidates, nowMs)
            lastNowMs = nowMs
            publish()
        }
    }

    private fun relayout() {
        synchronized(this) { publish() }
    }

    private fun publish() {
        val spend = spend
        val home = home
        val rate = homePerUnit
        val official = officialPerUnit
        val m = metrics
        val labels = ArrayList<PriceLabel>(lastTracked.size)
        shownAt.keys.retainAll(lastTracked.mapTo(HashSet()) { it.id })

        // Препятствия: рамки всех найденных цен (метка не должна закрывать чужую цену)
        // и уже поставленные пилюли.
        val boxes = lastTracked.associate { it.id to RectF(it.box.left, it.box.top, it.box.right, it.box.bottom) }
        val placed = ArrayList<RectF>(lastTracked.size)

        // Сначала уверенные актуальные цены по убыванию скоринга — им достаются лучшие места, потом догадки и старые.
        val order = lastTracked.sortedWith(compareBy<TrackedPrice> { it.isOld }.thenBy { !it.confident }.thenByDescending { it.score })
        for (t in order) {
            val homeAmount = rate?.let { t.value.multiply(it, MC) }
            val officialAmount = official?.let { t.value.multiply(it, MC) }
            val homeText = homeAmount?.let { "${Numbers.cheat(it)} ${home.symbol}" } ?: "—"
            // Распознанная цена — чтобы сразу видеть, правильно ли прочитан ценник.
            val price = "${Numbers.money(t.value)} ${spend.symbol}" + if (t.confident) "" else "?"
            val main: String
            val sub: String
            if (t.isOld) {
                // Старая цена — компактная однострочная метка.
                main = ""
                sub = "было $price ≈ $homeText"
            } else {
                main = "≈ $homeText"
                sub = if (officialAmount != null) "$price · ЦБ ${Numbers.cheat(officialAmount)} ${home.symbol}" else price
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
                id = t.id, value = t.value, isOld = t.isOld, unsure = !t.confident,
                alpha = t.alpha * (0.25f + 0.75f * appear),
                box = box, pill = pill, bubble = bubble,
                main = main, mainX = mainX, mainY = mainY,
                sub = sub, subX = subX, subY = subY,
                homeAmount = homeAmount,
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
