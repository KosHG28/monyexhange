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
import app.dirhamrub.domain.price.orientedWidth
import app.dirhamrub.domain.wallet.Currency
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import kotlin.math.hypot

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
    /** Рамка вокруг цены на ценнике. */
    val box: RectF,
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
    /** Сумма в итоговой валюте по выбранной карте (или ЦБ) — для карточки, шторки и TalkBack. */
    val homeAmount: BigDecimal?,
    /** Сумма по курсу ЦБ — для карточки фокуса (null — не показывать). */
    val officialAmount: BigDecimal?,
)

private val EMPTY_RECT = RectF()

/** Цвета ценника под ценой: бумага и цифры (ARGB). */
class TagColors(val paper: Int, val ink: Int)

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

    /** Вписывать суммы в ценники (иначе — плашки над ценами). */
    @Volatile var embedded: Boolean = true
        private set
    private val embedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = metrics.mainTypeface }
    /** Цвета ценников по меткам — их берёт из кадров [TagColorAnalyzer], сглаженно. */
    private val colors = HashMap<Long, TagColors>()

    private var lastTracked: List<TrackedPrice> = emptyList()
    private var focusId = NO_FOCUS
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

    fun setEmbedded(value: Boolean) {
        synchronized(this) {
            embedded = value
            publish()
        }
    }

    /** Цены, у которых нужны цвета ценника (фоновый поток анализатора). */
    fun sampleTargets(): List<SampleTarget> = synchronized(this) {
        if (!embedded) return emptyList()
        lastTracked.filter { it.confident }.mapNotNull { t ->
            val e = t.extent
            if (t.angle == 0f) {
                SampleTarget(t.id, e.centerX, e.centerY, e.width, e.height, 0f)
            } else {
                val w = orientedWidth(e.width, t.height, t.angle) ?: return@mapNotNull null
                SampleTarget(t.id, e.centerX, e.centerY, w, t.height, t.angle)
            }
        }
    }

    /** Новые цвета ценника для метки [id]: смешиваются с прежними, чтобы подложка не мигала. */
    fun setColors(id: Long, paper: Int, ink: Int) {
        synchronized(this) {
            val old = colors[id]
            colors[id] = if (old == null) TagColors(paper, ink) else TagColors(blend(old.paper, paper), blend(old.ink, ink))
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
                focusId = NO_FOCUS
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
        val home = home
        val spend = spend
        val rate = homePerUnit
        val official = officialPerUnit
        val m = metrics
        val alive = lastTracked.mapTo(HashSet()) { it.id }
        shownAt.keys.retainAll(alive)
        colors.keys.retainAll(alive)

        // Фокус — актуальная цена ближе всех к центру свободной части экрана (уверенные важнее догадок).
        // Прежний фокус держим, пока он не заметно дальше нового: метка не прыгает от дрожания рук.
        val cx = viewWidth / 2f
        val cy = (topInset + viewHeight - bottomInset) / 2f
        fun distance(t: TrackedPrice) = hypot(t.box.centerX - cx, t.box.centerY - cy)
        val current = lastTracked.filter { !it.isOld }
        val focusable = current.filter { it.confident }.ifEmpty { current }
        val nearest = focusable.minByOrNull(::distance)
        val previous = focusable.firstOrNull { it.id == focusId }
        val focus = if (previous != null && nearest != null && distance(previous) <= FOCUS_STICKY * distance(nearest)) previous else nearest
        focusId = focus?.id ?: NO_FOCUS

        // Препятствия: рамки всех найденных цен (плашка не закрывает чужую цену) и уже поставленные плашки.
        val boxes = lastTracked.associate { it.id to RectF(it.box.left, it.box.top, it.box.right, it.box.bottom) }
        val placed = ArrayList<RectF>(MAX_LABELS)
        val labels = ArrayList<PriceLabel>(MAX_LABELS)

        // Фокус первым, потом уверенные цены по скорингу, потом догадки и старые цены.
        val order = lastTracked.sortedWith(
            compareByDescending<TrackedPrice> { it.id == focusId }
                .thenBy { it.isOld }
                .thenBy { !it.confident }
                .thenByDescending { it.score },
        )
        for (t in order) {
            if (labels.size >= MAX_LABELS) break
            val focused = t.id == focusId
            val homeAmount = rate?.let { t.value.multiply(it, MC) }
            val amount = homeAmount?.let { "${Numbers.cheat(it)} ${home.symbol}" } ?: "${Numbers.money(t.value)} ${spend.symbol}"
            val text = when {
                t.isOld -> "было $amount"
                !t.confident -> "$amount?"
                focused -> "≈ $amount"
                else -> amount
            }
            val first = shownAt.getOrPut(t.id) { lastNowMs }
            val appear = ((lastNowMs - first) / APPEAR_MS.toFloat()).coerceIn(0f, 1f)
            val alpha = t.alpha * (0.25f + 0.75f * appear)
            // Вписываем только уверенные цены с пересчётом: догадку, выглядящую как настоящая цена, не рисуем.
            if (embedded && t.confident && homeAmount != null) {
                val label = embeddedLabel(t, focused, alpha, "${Numbers.cheat(homeAmount)} ${home.symbol}", homeAmount, official)
                if (label != null) {
                    labels += label
                    continue
                }
                // Почти вертикальный ценник — плашкой.
            }
            val paint = if (focused) mainPaint else subPaint
            val fm = if (focused) mainMetrics else subMetrics
            val padX = if (focused) m.padX else m.padX * 0.7f
            val padY = if (focused) m.padY else m.padY * 0.6f
            val textW = paint.measureText(text)
            val pillW = textW + 2 * padX
            val pillH = fm.descent - fm.ascent + 2 * padY
            val tail = if (focused) m.tail else 0f

            val box = boxes.getValue(t.id)
            var left = box.centerX() - pillW / 2f
            if (viewWidth > 0f) left = left.coerceIn(m.margin, maxOf(m.margin, viewWidth - m.margin - pillW))
            val aboveTop = box.top - m.gap - tail - pillH
            val above = RectF(left, aboveTop, left + pillW, aboveTop + pillH)
            val belowTop = box.bottom + m.gap + tail
            val below = RectF(left, belowTop, left + pillW, belowTop + pillH)

            fun outside(r: RectF): Boolean = r.top < topInset + m.margin || (viewHeight > 0f && r.bottom > viewHeight - bottomInset)
            fun blocked(r: RectF): Boolean =
                outside(r) || placed.any { RectF.intersects(r, it) } ||
                    boxes.any { (id, b) -> id != t.id && RectF.intersects(r, b) }

            // Плашка — только над ценой или прямо под ней. Нет места — без плашки: лучше меньше меток,
            // чем метки, уехавшие от своих цен. Фокус показываем всегда.
            val pill = when {
                !blocked(above) -> above
                !blocked(below) -> below
                focused -> if (!outside(above)) above else below
                else -> null
            } ?: continue
            placed += pill
            val pointsDown = pill.bottom <= box.top

            labels += PriceLabel(
                id = t.id, value = t.value, isOld = t.isOld, unsure = !t.confident, focused = focused,
                alpha = alpha,
                box = box, pill = pill,
                bubble = bubblePath(pill, box, pointsDown, if (focused) m.radius else m.radius * 0.8f, tail),
                text = text,
                textX = pill.centerX() - textW / 2f,
                textY = pill.top + padY - fm.ascent,
                homeAmount = homeAmount,
                officialAmount = official?.let { t.value.multiply(it, MC) },
            )
        }
        _frames.value = LabelFrame(labels, ++version)
    }

    /**
     * Сумма, вписанная в ценник: подложка цвета бумаги закрывает цену вместе с валютой, сумма — цветом цифр,
     * той же высоты и от того же левого края; если она длиннее старой цены, подложка растёт вправо.
     */
    private fun embeddedLabel(
        t: TrackedPrice,
        focused: Boolean,
        alpha: Float,
        text: String,
        homeAmount: BigDecimal,
        official: BigDecimal?,
    ): PriceLabel? {
        // Наклонный ценник: рамки OCR — вдоль осей экрана и выше самих цифр. Берём высоту цифр
        // «как на ценнике» (по углам слов) и ширину строки из её рамки и наклона, всё рисуем ровно
        // в системе строки, а холст поворачиваем при отрисовке. Почти вертикальную строку не вписываем.
        val px = t.extent.centerX
        val py = t.extent.centerY
        val angle = t.angle
        val h: Float
        val ext: Box
        if (angle == 0f) {
            h = t.box.height
            ext = t.extent
        } else {
            h = t.height
            val w = orientedWidth(t.extent.width, h, angle) ?: return null
            ext = Box(px - w / 2f, py - h / 2f, px + w / 2f, py + h / 2f)
        }
        val c = colors[t.id]
        val digitHeight = h * DIGIT_SHARE
        embedPaint.textSize = digitHeight / DIGIT_TO_SIZE
        val textW = embedPaint.measureText(text)
        val padX = h * 0.2f
        val padY = h * 0.14f
        val left = ext.left - padX
        val right = maxOf(ext.right + padX, left + textW + 2 * padX)
        val patch = RectF(left, ext.top - padY, right, ext.bottom + padY)
        val feather = RectF(patch.left - padY, patch.top - padY, patch.right + padY, patch.bottom + padY)
        val textX = left + padX
        // Центр цифр в системе строки: у ровной — как есть, у наклонной — повёрнут обратно вокруг центра.
        val (_, by) = if (angle == 0f) t.box.centerX to t.box.centerY else rotate(t.box.centerX, t.box.centerY, px, py, -angle)
        val baseline = by + digitHeight / 2f
        val thick = maxOf(2f, h * 0.06f)
        // Старая цена — зачёркнута по середине цифр, новая — подчёркнута.
        val markY = if (t.isOld) baseline - digitHeight / 2f - thick / 2f else baseline + h * 0.14f
        return PriceLabel(
            id = t.id, value = t.value, isOld = t.isOld, unsure = false, focused = focused,
            alpha = alpha,
            box = RectF(t.box.left, t.box.top, t.box.right, t.box.bottom),
            pill = patch,
            bubble = EMPTY_PATH,
            text = text,
            textX = textX,
            textY = baseline,
            embedded = true,
            paper = c?.paper ?: DEFAULT_PAPER,
            ink = c?.ink ?: DEFAULT_INK,
            textSize = embedPaint.textSize,
            feather = feather,
            radius = h * 0.18f,
            mark = RectF(textX, markY, textX + textW, markY + thick),
            angle = angle,
            pivotX = px,
            pivotY = py,
            homeAmount = homeAmount,
            officialAmount = official?.let { t.value.multiply(it, MC) },
        )
    }

    /** Точка ([x], [y]), повёрнутая на [deg] градусов вокруг ([cx], [cy]). */
    private fun rotate(x: Float, y: Float, cx: Float, cy: Float, deg: Float): Pair<Float, Float> {
        val r = Math.toRadians(deg.toDouble())
        val cos = kotlin.math.cos(r).toFloat()
        val sin = kotlin.math.sin(r).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * cos - dy * sin) to (cy + dx * sin + dy * cos)
    }

    /** Смешивание цветов: новое значение с весом [COLOR_SMOOTHING]. */
    private fun blend(old: Int, new: Int): Int {
        fun ch(shift: Int): Int {
            val o = (old shr shift) and 0xFF
            val n = (new shr shift) and 0xFF
            return (o + (n - o) * COLOR_SMOOTHING).toInt().coerceIn(0, 255) shl shift
        }
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    /** Скруглённая плашка; у фокуса — с треугольным хвостиком к цене, если плашка рядом с ней. */
    private fun bubblePath(pill: RectF, box: RectF, pointsDown: Boolean, radius: Float, tail: Float): Path {
        val path = Path()
        path.addRoundRect(pill, radius, radius, Path.Direction.CW)
        if (tail <= 0f) return path
        val tailX = box.centerX().coerceIn(pill.left + radius + tail, pill.right - radius - tail)
        if (pointsDown && box.top - pill.bottom <= metrics.gap + tail * 1.5f) {
            path.moveTo(tailX - tail, pill.bottom - 1f)
            path.lineTo(tailX + tail, pill.bottom - 1f)
            path.lineTo(tailX, pill.bottom + tail)
            path.close()
        } else if (!pointsDown && pill.top - box.bottom <= metrics.gap + tail * 1.5f) {
            path.moveTo(tailX - tail, pill.top + 1f)
            path.lineTo(tailX + tail, pill.top + 1f)
            path.lineTo(tailX, pill.top - tail)
            path.close()
        }
        return path
    }

    private companion object {
        const val APPEAR_MS = 160L
        /** Меток на экране — не больше: фокус и несколько соседей. */
        const val MAX_LABELS = 4
        /** Прежний фокус держится, пока он не дальше нового от центра больше чем в 1,4 раза. */
        const val FOCUS_STICKY = 1.4f
        const val NO_FOCUS = -1L
        /** Высота цифр — доля высоты рамки OCR; высота цифр Inter Tight — доля размера шрифта. */
        const val DIGIT_SHARE = 0.78f
        const val DIGIT_TO_SIZE = 0.727f
        const val COLOR_SMOOTHING = 0.35f
        /** Пока цвета ценника не взяты из кадра: светлая бумага и почти чёрные цифры. */
        const val DEFAULT_PAPER = 0xFFF4F2EE.toInt()
        const val DEFAULT_INK = 0xFF1E1E22.toInt()
        val EMPTY_PATH = Path()
    }
}
