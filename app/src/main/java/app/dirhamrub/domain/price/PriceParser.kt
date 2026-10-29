package app.dirhamrub.domain.price

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
)

/**
 * Валюта, цены в которой ищет [PriceParser]: подписи валюты рядом с числом и разумный диапазон.
 */
class PriceCurrency(val pattern: Regex, val min: BigDecimal, val max: BigDecimal) {
    companion object {
        val AED = PriceCurrency(
            Regex(
                """(?<![A-Za-z])(?:AED|DHS|DH|DIRHAMS?)(?![A-Za-z])\.?|د\.?\s?إ|درهم""",
                RegexOption.IGNORE_CASE,
            ),
            BigDecimal("0.5"),
            BigDecimal("100000"),
        )

        /** Тенге: ₸, KZT, TENGE, «тг»; латинский OCR часто читает ₸ как одиночную T. */
        val KZT = PriceCurrency(
            Regex(
                """(?<![A-Za-z])(?:KZT|TENGE|TG|T)(?![A-Za-z])\.?|₸|(?<![А-Яа-я])(?:тг|тенге)(?![А-Яа-я])\.?""",
                RegexOption.IGNORE_CASE,
            ),
            BigDecimal("10"),
            BigDecimal("10000000"),
        )
    }
}

/**
 * Ищет цены в результате OCR (по умолчанию — в дирхамах).
 *
 * 1. Склеивает разорванные части числа: «79» + «.95», «1,299» + «.00», копейки верхним индексом.
 * 2. Маскирует телефоны, длинные коды, даты и время, отбрасывает проценты, объём, вес, размеры.
 * 3. Оценивает каждую находку: валюта рядом, дробная часть, высота текста относительно кадра.
 * 4. Помечает старые цены: слово WAS/OLD/RRP рядом или мелкая и бо́льшая цена возле крупной новой.
 */
class PriceParser(private val currency: PriceCurrency = PriceCurrency.AED) {

    fun parse(blocks: List<OcrBlock>): List<PriceCandidate> {
        val raw = ArrayList<Raw>()
        for (block in blocks) {
            val lines = mergeSuperscripts(block.lines)
            val blockText = lines.joinToString("\n") { it.text }
            val blockHasCurrency = currency.pattern.containsMatchIn(blockText)
            lines.forEachIndexed { index, line ->
                val prevLine = if (index > 0) lines[index - 1] else null
                parseLine(glueFragments(line.words), prevLine, blockHasCurrency, raw)
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
        markStruckOut(accepted)
        return dedupe(accepted)
            .sortedByDescending { it.score }
            .map { PriceCandidate(it.value, it.box, it.score, it.hasCurrency, it.isOld) }
    }

    private class Raw(
        val value: BigDecimal,
        val box: Box,
        val hasCurrency: Boolean,
        val blockCurrency: Boolean,
        val hasFraction: Boolean,
        val digits: Int,
        var isOld: Boolean,
    ) {
        var score: Float = 0f
    }

    private class Token(val text: String, val box: Box, val start: Int) {
        val end: Int get() = start + text.length
    }

    private fun parseLine(words: List<OcrWord>, prevLine: OcrLine?, blockCurrency: Boolean, out: MutableList<Raw>) {
        if (words.isEmpty()) return
        val tokens = ArrayList<Token>(words.size)
        val sb = StringBuilder()
        for (w in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            tokens += Token(w.text, w.box, sb.length)
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
        mask(DIGIT_RUN) { m -> m.value.count { it.isDigit() } >= LONG_RUN_DIGITS }
        mask(PHONE_PREFIX)

        val currencies = currency.pattern.findAll(text).map { it.range }.toList()
        val oldKeyword = OLD_WORD.containsMatchIn(text) ||
            (prevLine != null && OLD_WORD.containsMatchIn(prevLine.text) && NUMBER.find(prevLine.text) == null)

        for (m in NUMBER.findAll(text)) {
            if (m.range.any { masked[it] }) continue
            val numberText = m.value
            val after = text.substring(m.range.last + 1)
            val before = text.substring(0, m.range.first)
            if (UNIT_AFTER.containsMatchIn(after)) continue
            if (BAD_BEFORE.containsMatchIn(before)) continue
            val value = parseNumber(numberText) ?: continue
            if (value < currency.min || value > currency.max) continue

            val hasCurrency = currencies.any { c ->
                val gapBefore = m.range.first - c.last - 1
                val gapAfter = c.first - m.range.last - 1
                gapBefore in 0..CURRENCY_GAP || gapAfter in 0..CURRENCY_GAP
            }
            val box = boxOf(tokens, m.range.first, m.range.last + 1) ?: continue
            out += Raw(
                value = value,
                box = box,
                hasCurrency = hasCurrency,
                blockCurrency = blockCurrency,
                hasFraction = FRACTION.containsMatchIn(numberText),
                digits = numberText.count { it.isDigit() },
                isOld = oldKeyword,
            )
        }
    }

    /** «1,299.00» → 1299.00, «49,95» → 49.95, «379» → 379. */
    private fun parseNumber(text: String): BigDecimal? {
        val normalized = if (THOUSANDS.matches(text)) {
            text.replace(",", "")
        } else {
            text.replace(',', '.')
        }
        return normalized.toBigDecimalOrNull()
    }

    /** Рамка подстроки [start, end) строки: части слов — пропорционально числу символов. */
    private fun boxOf(tokens: List<Token>, start: Int, end: Int): Box? {
        var result: Box? = null
        for (t in tokens) {
            val s = max(start, t.start)
            val e = minOf(end, t.end)
            if (s >= e) continue
            val len = t.text.length.coerceAtLeast(1)
            val w = t.box.width
            val part = Box(
                left = t.box.left + w * (s - t.start) / len,
                top = t.box.top,
                right = t.box.left + w * (e - t.start) / len,
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
                    out[out.lastIndex] = OcrWord(glued, prev.box.union(w.box))
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
            replaced[big] = OcrWord(big.text + "." + small.text, big.box.union(small.box))
            removed += small
        }
        if (replaced.isEmpty()) return lines
        return lines.map { line ->
            OcrLine(line.words.filter { it !in removed }.map { replaced[it] ?: it })
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

    /** OCR иногда отдаёт одно и то же дважды — оставляем лучшую находку. */
    private fun dedupe(items: List<Raw>): List<Raw> {
        val sorted = items.sortedByDescending { it.score }
        val out = ArrayList<Raw>(sorted.size)
        for (r in sorted) {
            if (out.none { it.value.compareTo(r.value) == 0 && it.box.iou(r.box) > 0.5f }) out += r
        }
        return out
    }

    private fun similarHeight(a: Box, b: Box): Boolean {
        val big = max(a.height, b.height)
        return big <= 0f || kotlin.math.min(a.height, b.height) / big > 0.75f
    }

    companion object {
        private const val TALL_TEXT = 0.75f
        private const val LONG_RUN_DIGITS = 7
        private const val CURRENCY_GAP = 2
        private const val GLUE_GAP = 0.8f
        private const val THOUSAND_GAP = 0.6f
        private const val SUPERSCRIPT_RATIO = 0.75f
        private const val STRUCK_HEIGHT_RATIO = 0.8f
        private const val STRUCK_DISTANCE = 2.5f

        private val NUMBER = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?![\d]|[.,]\d)""")
        private val THOUSANDS = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?""")
        private val FRACTION = Regex("""[.,]\d{1,2}$""")

        private val OLD_WORD = Regex(
            """(?<![A-Za-z])(?:WAS|OLD|BEFORE|RRP|REG|REGULAR|ORIGINAL)(?![A-Za-z])""",
            RegexOption.IGNORE_CASE,
        )

        private val DATE = Regex("""\d{1,4}[./-]\d{1,2}[./-]\d{1,4}""")
        private val SLASHED = Regex("""\d+\s?/\s?\d+""")
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
