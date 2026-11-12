package app.dirhamrub.domain.price

import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.NumberStyle
import java.math.BigDecimal
import kotlin.math.max

/** Найденная на кадре цена. */
data class PriceCandidate(
    val value: BigDecimal,
    val box: Box,
    val score: Float,
    val hasCurrency: Boolean,
    /** Старая цена (рядом WAS или она зачёркнута/перебита новой). */
    val isOld: Boolean,
    /**
     * Похоже на цену уверенно: валюта рядом или в том же блоке, или копейки. Голое крупное целое
     * («200» на этикетке) — догадка, её метка рисуется иначе.
     */
    val confident: Boolean = true,
    /** Цена вместе со знаком валюты рядом («17 990 ТГ») — что закрывать, когда сумма вписывается в ценник. */
    val extent: Box = box,
    /** Наклон строки с ценой, градусы. */
    val angle: Float = 0f,
    /** Высота цифр «как на ценнике» (у наклонённой строки меньше высоты [box]). */
    val height: Float = box.height,
)

/**
 * Валюта, цены в которой ищет [PriceParser]: подписи валюты рядом с числом, разумный диапазон,
 * точка как разделитель разрядов («1.299,90 TL», «45.000 ₫») и с какой длины ряд цифр — уже не цена,
 * а телефон или код.
 */
class PriceCurrency(
    val pattern: Regex,
    val min: BigDecimal,
    val max: BigDecimal,
    val dotThousands: Boolean = false,
    val longRunDigits: Int = 7,
    /** Индийские разряды: «1,50,000» — полтора лакха. */
    val indian: Boolean = false,
    /** Знаков после запятой у цен: 3 у динаров Кувейта, Бахрейна, Иордании, Туниса и риала Омана. */
    val decimals: Int = 2,
) {
    /** Как выглядит число-цена в этой валюте. */
    internal val number: Regex = run {
        val f = "\\d{1,$decimals}"
        val alternatives = buildList {
            // У трёхзначных динаров цены маленькие, а «1,250» и «1.250» — это 1,25: разрядов не ищем.
            if (decimals < 3) {
                if (indian) add("\\d{1,2}(?:,\\d{2})+,\\d{3}(?:\\.$f)?")
                if (dotThousands) add("\\d{1,3}(?:\\.\\d{3})+(?:,$f)?")
                add("\\d{1,3}(?:,\\d{3})+(?:\\.$f)?")
            }
            add("\\d+(?:[.,]$f)?")
        }
        Regex("(?<![\\d.,])(" + alternatives.joinToString("|") + ")(?![\\d]|[.,]\\d)")
    }

    /** Есть ли у числа копейки («49,95», «1.250» у динара). */
    internal val fraction: Regex = Regex("[.,]\\d{1,$decimals}$")

    companion object {
        private fun words(vararg w: String) = "(?<![A-Za-z])(?:${w.joinToString("|")})(?![A-Za-z])\\.?"
        private fun currency(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

        val AED = PriceCurrency(
            currency(words("AED", "DHS", "DH", "DIRHAMS?") + "|د\\.?\\s?إ|درهم"),
            BigDecimal("0.5"), BigDecimal("100000"),
        )

        /**
         * Тенге: ₸, KZT, TENGE, «тг»; латинский OCR часто читает ₸ как одиночную T,
         * а кириллическое «ТГ» — как «TF» или «Tr».
         */
        val KZT = PriceCurrency(
            currency(words("KZT", "TENGE", "TG", "TF", "TR", "T") + "|₸|(?<![А-Яа-я])(?:тг|тенге)(?![А-Яа-я])\\.?"),
            BigDecimal("10"), BigDecimal("10000000"), longRunDigits = 9,
        )

        val TRY = PriceCurrency(
            currency(words("TL", "TRY", "YTL") + "|₺"),
            BigDecimal("1"), BigDecimal("1000000"), dotThousands = true,
        )

        val THB = PriceCurrency(
            currency(words("THB", "BAHT", "BHT") + "|฿|บาท"),
            BigDecimal("1"), BigDecimal("1000000"),
        )

        val USD = PriceCurrency(
            currency(words("USD", "US") + "|\\$"),
            BigDecimal("0.1"), BigDecimal("100000"),
        )

        val EUR = PriceCurrency(
            currency(words("EUR", "EURO") + "|€"),
            BigDecimal("0.1"), BigDecimal("100000"), dotThousands = true,
        )

        val CNY = PriceCurrency(
            currency(words("CNY", "RMB", "YUAN") + "|[¥￥元]"),
            BigDecimal("0.5"), BigDecimal("1000000"),
        )

        val GEL = PriceCurrency(
            currency(words("GEL", "LARI") + "|₾|ლ"),
            BigDecimal("0.1"), BigDecimal("100000"),
        )

        val AMD = PriceCurrency(
            currency(words("AMD", "DRAM") + "|֏|դր\\.?"),
            BigDecimal("10"), BigDecimal("10000000"), longRunDigits = 9,
        )

        /** Донг: «45.000đ», «45,000 VND»; одиночная d/đ сразу после числа — тоже донг. */
        val VND = PriceCurrency(
            currency(words("VND", "VNĐ", "DONG") + "|₫|(?<![A-Za-z])[đd](?![A-Za-z])"),
            BigDecimal("1000"), BigDecimal("100000000"), dotThousands = true, longRunDigits = 10,
        )

        /**
         * Местные подписи валют на ценниках — сверх кода и знака из справочника. Латиница — словом
         * (без учёта регистра), остальное — как есть.
         */
        private val LOCAL: Map<String, List<String>> = mapOf(
            "IDR" to listOf("RP"), "INR" to listOf("RS", "₹"), "LKR" to listOf("RS", "SLRS"), "PKR" to listOf("RS"),
            "NPR" to listOf("RS", "NRS"), "MUR" to listOf("RS"), "SCR" to listOf("SR"), "MVR" to listOf("RF", "MRF"),
            "MYR" to listOf("RM"), "PHP" to listOf("₱"), "MXN" to listOf("MN", "$"), "MAD" to listOf("DH", "DHS", "DIRHAMS?"),
            "EGP" to listOf("LE", "L\\.E", "E£", "£E"), "JPY" to listOf("YEN", "¥", "円"), "KRW" to listOf("WON", "₩", "원"),
            "GBP" to listOf("£"), "CHF" to listOf("FR"), "CZK" to listOf("KČ", "KC"), "PLN" to listOf("ZŁ", "ZL"),
            "HUF" to listOf("FT"), "SEK" to listOf("KR"), "NOK" to listOf("KR"), "DKK" to listOf("KR"), "ISK" to listOf("KR"),
            "RSD" to listOf("DIN", "дин"), "BAM" to listOf("KM"), "MKD" to listOf("DEN", "ден"), "ALL" to listOf("LEK"),
            "TND" to listOf("DT"), "JOD" to listOf("JD"), "ILS" to listOf("NIS", "₪"), "SAR" to listOf("SR"),
            "QAR" to listOf("QR"), "OMR" to listOf("RO"), "KWD" to listOf("KD"), "BHD" to listOf("BD"),
            "HKD" to listOf("HK$", "$"), "SGD" to listOf("S$", "$"), "TWD" to listOf("NT$", "NT"), "AUD" to listOf("A$", "$"),
            "NZD" to listOf("NZ$", "$"), "CAD" to listOf("C$", "CA$", "$"), "BRL" to listOf("R$"), "ARS" to listOf("$"),
            "CLP" to listOf("$"), "COP" to listOf("$"), "PEN" to listOf("S/"), "KHR" to listOf("៛", "RIELS?"),
            "LAK" to listOf("₭", "KIP"), "MMK" to listOf("KS", "KYATS?"), "MNT" to listOf("₮"), "AZN" to listOf("₼", "MAN"),
            "TJS" to listOf("SM", "смн"), "TMT" to listOf("MANAT"), "UAH" to listOf("₴", "грн"), "MDL" to listOf("LEI"),
            "RON" to listOf("LEI"), "TZS" to listOf("TSH"), "KES" to listOf("KSH"), "ZAR" to listOf("R"),
            "DOP" to listOf("RD$", "$"), "CUP" to listOf("$"), "NGN" to listOf("₦"), "GHS" to listOf("GH₵", "₵"),
            "LBP" to listOf("LL", "L\\.L"), "BDT" to listOf("TK", "৳"), "IRR" to listOf("RIALS?", "﷼"),
        )

        /**
         * Правила для любой валюты справочника: подписи — код, знак и местные сокращения; формат чисел —
         * как пишут в стране; диапазон цен и длина «не цены» (телефона, кода) — от размера валюты.
         */
        fun generic(c: Currency): PriceCurrency {
            val marks = (listOf(c.code, c.symbol) + LOCAL[c.code].orEmpty()).distinct()
            val latin = marks.filter { m -> m.all { it in 'A'..'Z' || it in 'a'..'z' || it == '?' || it == '\\' || it == '.' || it == 'Č' || it == 'Ł' } }
            val cyrillic = marks.filter { m -> m.all { it in 'а'..'я' || it in 'А'..'Я' } }
            val other = marks - latin.toSet() - cyrillic.toSet()
            val parts = buildList {
                if (latin.isNotEmpty()) add(words(*latin.toTypedArray()))
                if (cyrillic.isNotEmpty()) add("(?<![А-Яа-я])(?:${cyrillic.joinToString("|")})(?![А-Яа-я])\\.?")
                other.forEach { add(Regex.escape(it)) }
            }
            val unit = BigDecimal.TEN.pow(c.magnitude)
            return PriceCurrency(
                currency(parts.joinToString("|")),
                min = unit.divide(BigDecimal.TEN),
                max = unit.multiply(BigDecimal(100_000)),
                dotThousands = c.style == NumberStyle.DOT,
                longRunDigits = maxOf(7, c.magnitude + 6),
                indian = c.style == NumberStyle.INDIAN,
                decimals = if (c.digits == 3) 3 else 2,
            )
        }

        /** По трёхбуквенному коду: у частых валют — подобранные вручную правила, у остальных — общие. */
        fun of(code: String): PriceCurrency = when (code) {
            "AED" -> AED
            "KZT" -> KZT
            "TRY" -> TRY
            "THB" -> THB
            "USD" -> USD
            "EUR" -> EUR
            "CNY" -> CNY
            "GEL" -> GEL
            "AMD" -> AMD
            "VND" -> VND
            else -> Currency.byCode(code)?.let(::generic) ?: AED
        }
    }
}

/**
 * Ищет цены в результате OCR (по умолчанию — в дирхамах).
 *
 * 1. Склеивает разорванные части числа: «79» + «.95», «1,299» + «.00», копейки верхним индексом.
 * 2. Маскирует телефоны, длинные коды, даты и время, отбрасывает проценты, объём, вес, размеры.
 * 3. Оценивает каждую находку: валюта рядом, дробная часть, высота текста относительно кадра.
 * 4. Если в кадре есть цены с валютой, голые целые без валюты и копеек отбрасывает: это надписи
 *    на упаковке («200 years» на бутылке), номера полок и т. п. Без таких цен — оставляет как догадку.
 * 5. Отбрасывает числа у левого и правого края кадра [bounds]: ценник обрезан, «8» может быть «8.50» или «18».
 * 6. Помечает старые цены: слово WAS/OLD/RRP рядом или мелкая и бо́льшая цена возле крупной новой.
 */
class PriceParser(private val currency: PriceCurrency = PriceCurrency.AED) {

    fun parse(blocks: List<OcrBlock>, bounds: Box? = null): List<PriceCandidate> {
        val raw = ArrayList<Raw>()
        for (block in blocks) {
            val lines = mergeSuperscripts(block.lines)
            val blockText = lines.joinToString("\n") { it.text }
            val blockHasCurrency = currency.pattern.containsMatchIn(blockText)
            lines.forEachIndexed { index, line ->
                val prevLine = if (index > 0) lines[index - 1] else null
                parseLine(glueFragments(line.words), prevLine, blockHasCurrency, raw, line.angle)
            }
        }
        if (raw.isEmpty()) return emptyList()

        val maxHeight = raw.maxOf { it.box.height }.coerceAtLeast(1f)
        val accepted = ArrayList<Raw>()
        for (r in raw) {
            val relHeight = r.box.height / maxHeight
            val plausible = r.hasCurrency || r.blockCurrency || r.hasFraction || relHeight >= TALL_TEXT
            if (!plausible) continue
            if (!r.hasCurrency && !r.hasFraction && r.digits < 2) continue
            var score = 2f * relHeight
            if (r.hasCurrency) score += 3f else if (r.blockCurrency) score += 1f
            if (r.hasFraction) score += 1f
            r.score = score
            accepted += r
        }
        val anyCurrency = accepted.any { it.hasCurrency }
        val kept = accepted.filter { r ->
            when {
                anyCurrency && !r.confident -> false
                bounds != null && nearSideEdge(r.box, bounds) -> false
                else -> true
            }
        }
        markStruckOut(kept)
        return dedupe(kept)
            .sortedByDescending { it.score }
            .map { PriceCandidate(it.value, it.box, it.score, it.hasCurrency, it.isOld, it.confident, it.extent, it.angle, it.height) }
    }

    /** Число у левого или правого края (ближе 0,6 высоты цифр) или за ним — могло обрезаться. */
    private fun nearSideEdge(box: Box, bounds: Box): Boolean {
        val margin = EDGE_MARGIN * box.height
        return box.left < bounds.left + margin || box.right > bounds.right - margin
    }

    private class Raw(
        val value: BigDecimal,
        val box: Box,
        val hasCurrency: Boolean,
        val blockCurrency: Boolean,
        val hasFraction: Boolean,
        val digits: Int,
        var isOld: Boolean,
        val extent: Box,
        val angle: Float,
        val height: Float,
    ) {
        var score: Float = 0f
        val confident: Boolean get() = hasCurrency || blockCurrency || hasFraction
    }

    private class Token(val text: String, val box: Box, val start: Int, val height: Float) {
        val end: Int get() = start + text.length
    }

    private fun parseLine(words: List<OcrWord>, prevLine: OcrLine?, blockCurrency: Boolean, out: MutableList<Raw>, angle: Float) {
        if (words.isEmpty()) return
        val tokens = ArrayList<Token>(words.size)
        val sb = StringBuilder()
        for (w in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            tokens += Token(w.text, w.box, sb.length, w.height)
            sb.append(w.text)
        }
        val text = sb.toString()

        val masked = BooleanArray(text.length)
        fun mask(regex: Regex, predicate: (MatchResult) -> Boolean = { true }) {
            for (m in regex.findAll(text)) if (predicate(m)) for (i in m.range) masked[i] = true
        }
        mask(DATE)
        mask(SLASHED)
        mask(TIME)
        mask(DIGIT_RUN) { m -> m.value.count { it.isDigit() } >= currency.longRunDigits }
        mask(PHONE_PREFIX)

        val currencies = currency.pattern.findAll(text).map { it.range }.toList()
        val oldKeyword = OLD_WORD.containsMatchIn(text) ||
            (prevLine != null && OLD_WORD.containsMatchIn(prevLine.text) && NUMBER.find(prevLine.text) == null)

        for (m in currency.number.findAll(text)) {
            if (m.range.any { masked[it] }) continue
            val numberText = m.value
            val after = text.substring(m.range.last + 1)
            val before = text.substring(0, m.range.first)
            if (UNIT_AFTER.containsMatchIn(after)) continue
            if (BAD_BEFORE.containsMatchIn(before)) continue
            val value = parseNumber(numberText) ?: continue
            if (value < currency.min || value > currency.max) continue

            val marker = currencies.firstOrNull { c ->
                val gapBefore = m.range.first - c.last - 1
                val gapAfter = c.first - m.range.last - 1
                gapBefore in 0..CURRENCY_GAP || gapAfter in 0..CURRENCY_GAP
            }
            val hasCurrency = marker != null
            val box = boxOf(tokens, m.range.first, m.range.last + 1) ?: continue
            val extent = marker?.let { c -> boxOf(tokens, minOf(c.first, m.range.first), maxOf(c.last, m.range.last) + 1) } ?: box
            out += Raw(
                value = value,
                box = box,
                hasCurrency = hasCurrency,
                blockCurrency = blockCurrency,
                hasFraction = currency.fraction.containsMatchIn(numberText),
                digits = numberText.count { it.isDigit() },
                isOld = oldKeyword,
                extent = extent,
                angle = angle,
                height = tokens.filter { maxOf(it.start, m.range.first) < minOf(it.end, m.range.last + 1) }.maxOfOrNull { it.height } ?: box.height,
            )
        }
    }

    /** «1,299.00» → 1299.00, «49,95» → 49.95, «379» → 379; при [PriceCurrency.dotThousands] «1.299,90» → 1299.90. */
    private fun parseNumber(text: String): BigDecimal? {
        val normalized = when {
            currency.decimals == 3 -> text.replace(',', '.')
            currency.dotThousands && DOT_THOUSANDS.matches(text) -> text.replace(".", "").replace(',', '.')
            THOUSANDS.matches(text) -> text.replace(",", "")
            // «1,50,000» — индийские разряды.
            currency.indian && INDIAN.matches(text) -> text.replace(",", "")
            else -> text.replace(',', '.')
        }
        return normalized.toBigDecimalOrNull()
    }

    /**
     * Рамка подстроки [start, end) строки: части слов — пропорционально ширине символов. В «179.00DHS»
     * буквы «DHS» шире цифр, а точка уже: по одному числу символов рамка цены съезжала на «D».
     */
    private fun boxOf(tokens: List<Token>, start: Int, end: Int): Box? {
        var result: Box? = null
        for (t in tokens) {
            val s = max(start, t.start)
            val e = minOf(end, t.end)
            if (s >= e) continue
            val total = widthOf(t.text, 0, t.text.length).coerceAtLeast(1e-3f)
            val w = t.box.width
            val part = Box(
                left = t.box.left + w * widthOf(t.text, 0, s - t.start) / total,
                top = t.box.top,
                right = t.box.left + w * widthOf(t.text, 0, e - t.start) / total,
                bottom = t.box.bottom,
            )
            result = result?.union(part) ?: part
        }
        return result
    }

    /** Склеивает соседние слова одной строки, если это части одного числа. */
    private fun glueFragments(words: List<OcrWord>): List<OcrWord> {
        if (words.size < 2) return words
        val sorted = words.sortedBy { it.box.left }
        val out = ArrayList<OcrWord>(sorted.size)
        for (w in sorted) {
            val prev = out.lastOrNull()
            if (prev != null) {
                val h = max(prev.box.height, w.box.height)
                val gap = w.box.left - prev.box.right
                val glued = when {
                    // «79» + «.95», «1,299» + «.00»
                    ENDS_WITH_DIGIT.containsMatchIn(prev.text) && SEPARATED_FRACTION.matches(w.text) &&
                        gap < GLUE_GAP * h -> prev.text + w.text
                    // «79.» + «95»
                    ENDS_WITH_SEPARATOR.containsMatchIn(prev.text) && TWO_DIGITS.matches(w.text) &&
                        gap < GLUE_GAP * h -> prev.text + w.text
                    // «1» + «299.00» (пробел как разделитель разрядов)
                    SHORT_INT.matches(prev.text) && THOUSAND_GROUP.matches(w.text) &&
                        gap < THOUSAND_GAP * h && similarHeight(prev.box, w.box) -> prev.text + w.text
                    else -> null
                }
                if (glued != null) {
                    out[out.lastIndex] = OcrWord(glued, prev.box.union(w.box), maxOf(prev.height, w.height))
                    continue
                }
            }
            out += w
        }
        return out
    }

    /**
     * Копейки верхним индексом: мелкие «95» справа сверху от крупного «79» → «79.95».
     * Индекс может попасть в ту же строку или в соседнюю строку блока.
     */
    private fun mergeSuperscripts(lines: List<OcrLine>): List<OcrLine> {
        val all = lines.flatMap { it.words }
        if (all.size < 2) return lines
        val replaced = HashMap<OcrWord, OcrWord>()
        val removed = HashSet<OcrWord>()
        for (big in all) {
            if (big in removed || !ENDS_WITH_INT.containsMatchIn(big.text)) continue
            val bh = big.box.height
            val small = all.firstOrNull { s ->
                s !== big && s !in removed && !replaced.containsKey(s) &&
                    TWO_DIGITS.matches(s.text) &&
                    s.box.height < SUPERSCRIPT_RATIO * bh &&
                    s.box.left >= big.box.right - 0.25f * bh &&
                    s.box.left <= big.box.right + 0.6f * bh &&
                    s.box.top >= big.box.top - 0.3f * bh &&
                    s.box.top <= big.box.top + 0.3f * bh
            } ?: continue
            replaced[big] = OcrWord(big.text + "." + small.text, big.box.union(small.box), big.height)
            removed += small
        }
        if (replaced.isEmpty()) return lines
        return lines.map { line ->
            line.copy(words = line.words.filter { it !in removed }.map { replaced[it] ?: it })
        }.filter { it.words.isNotEmpty() }
    }

    /**
     * Зачёркнутую цену OCR не отличает, поэтому эвристика: рядом с крупной ценой стоит
     * заметно более мелкая, но бо́льшая по значению — это старая цена до скидки.
     */
    private fun markStruckOut(items: List<Raw>) {
        for (small in items) {
            if (small.isOld) continue
            for (big in items) {
                if (big === small || big.isOld) continue
                if (small.box.height >= STRUCK_HEIGHT_RATIO * big.box.height) continue
                if (small.value <= big.value) continue
                if (small.box.gap(big.box) > STRUCK_DISTANCE * big.box.height) continue
                small.isOld = true
                small.score -= 1f
                break
            }
        }
    }

    /**
     * OCR иногда отдаёт одно и то же дважды, а то и два прочтения одного места («200» и «20») —
     * на одном месте оставляем лучшую находку.
     */
    private fun dedupe(items: List<Raw>): List<Raw> {
        val sorted = items.sortedByDescending { it.score }
        val out = ArrayList<Raw>(sorted.size)
        for (r in sorted) {
            if (out.none { it.box.iou(r.box) > SAME_PLACE_IOU }) out += r
        }
        return out
    }

    /** Относительная ширина символов [from, to) строки [text]: цифры — 1, точка и запятая уже, прописные шире. */
    private fun widthOf(text: String, from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) {
            val c = text[i]
            sum += when {
                c.isDigit() -> 1f
                c in NARROW_CHARS -> 0.5f
                c in WIDE_CHARS -> 1.5f
                c.isUpperCase() -> 1.3f
                else -> 1f
            }
        }
        return sum
    }

    private fun similarHeight(a: Box, b: Box): Boolean {
        val big = max(a.height, b.height)
        return big <= 0f || kotlin.math.min(a.height, b.height) / big > 0.75f
    }

    companion object {
        private const val NARROW_CHARS = ".,:;'’ |!iIlj"
        private const val WIDE_CHARS = "MWmw"
        private const val TALL_TEXT = 0.75f
        private const val CURRENCY_GAP = 2
        private const val GLUE_GAP = 0.8f
        private const val THOUSAND_GAP = 0.6f
        private const val SUPERSCRIPT_RATIO = 0.75f
        private const val STRUCK_HEIGHT_RATIO = 0.8f
        private const val STRUCK_DISTANCE = 2.5f
        private const val EDGE_MARGIN = 0.6f
        private const val SAME_PLACE_IOU = 0.5f

        private val NUMBER = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?![\d]|[.,]\d)""")
        private val DOT_THOUSANDS = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?""")
        private val THOUSANDS = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,3})?""")
        private val INDIAN = Regex("""\d{1,2}(?:,\d{2})+,\d{3}(?:\.\d{1,2})?""")

        private val OLD_WORD = Regex(
            """(?<![A-Za-z])(?:WAS|OLD|BEFORE|RRP|REG|REGULAR|ORIGINAL)(?![A-Za-z])""",
            RegexOption.IGNORE_CASE,
        )

        private val DATE = Regex("""\d{1,4}[./-]\d{1,2}[./-]\d{1,4}""")
        /** Дроби и артикулы через слеш целиком: «1/2», «9598/202/251» (раньше «251» в конце оставался числом). */
        private val SLASHED = Regex("""\d+(?:\s?/\s?\d+)+""")
        private val TIME = Regex("""\d{1,2}:\d{2}(?::\d{2})?|\d{1,2}[.:]\d{2}\s?(?:am|pm)\b""", RegexOption.IGNORE_CASE)
        private val DIGIT_RUN = Regex("""\+?\d(?:[ \-()]?\d)+""")
        private val PHONE_PREFIX = Regex("""\+\s?971[\d \-()]*""")

        private val UNIT_AFTER = Regex(
            """^\s?(?:%|(?:ml|cl|l|ltr|ltrs|litre|litres|liter|liters|g|gm|gms|gr|grams?|kg|kgs|mg|cm|mm|m|pcs|pc|pieces?|oz|lb|lbs|x|mah|gb|tb|mb|w|v|in|inch|ft|pack|pk|ct|cal|kcal|sachets?|tabs?|caps?)(?![A-Za-z]))""",
            RegexOption.IGNORE_CASE,
        )
        private val BAD_BEFORE = Regex(
            """(?:(?<![A-Za-z])[x×]\s?|[+#№]\s?|(?<![A-Za-z])(?:no|sku|art|item|code|ref|tel|ph|mob|fax)\.?:?\s?)$""",
            RegexOption.IGNORE_CASE,
        )

        private val ENDS_WITH_DIGIT = Regex("""\d$""")
        private val ENDS_WITH_INT = Regex("""(?<![.,\d])\d{1,6}$""")
        private val ENDS_WITH_SEPARATOR = Regex("""\d[.,]$""")
        private val SEPARATED_FRACTION = Regex("""[.,]\d{1,2}""")
        private val TWO_DIGITS = Regex("""\d{2}""")
        private val SHORT_INT = Regex("""\d{1,3}""")
        private val THOUSAND_GROUP = Regex("""\d{3}(?:[.,]\d{1,2})?""")
    }
}
