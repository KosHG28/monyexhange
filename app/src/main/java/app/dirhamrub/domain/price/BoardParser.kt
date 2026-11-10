package app.dirhamrub.domain.price

import app.dirhamrub.domain.MC
import app.dirhamrub.domain.wallet.Currency
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ln

/**
 * Строка табло обменника: за 1 [currency] дают [buy] местной валюты (курс покупки — по нему меняют вашу),
 * продают за [sell]; [box] — где строка на экране (подсветка).
 */
data class BoardRate(val currency: Currency, val buy: BigDecimal, val sell: BigDecimal?, val box: Box)

/**
 * Табло обменника из OCR: «USD 3.650 3.670», «EUR 3,98 4,05», «JPY 100 2.41 2.49».
 * ML Kit часто режет таблицу на колонки (коды отдельно, цифры отдельно) — поэтому слова всех строк
 * собираются в ряды по вертикали заново. В ряду — код валюты и до двух курсов: меньший — покупка,
 * больший — продажа (обменник покупает дешевле, чем продаёт).
 * Масштаб сверяется с ЦБ ([official] — сколько местной валюты стоит 1 единица по ЦБ): «за 100», «за 1000»
 * или обратная котировка приводятся к «за 1»; курс дальше 30 % от ЦБ — не курс (дата, телефон, мусор).
 */
object BoardParser {
    private val NUMBER = Regex("""^\d{1,6}(?:[.,]\d{1,6})?$""")
    private val UNITS = setOf(BigDecimal.ONE, BigDecimal.TEN, BigDecimal(100), BigDecimal(1000))
    private const val TOLERANCE = 0.30

    fun parse(lines: List<OcrLine>, local: Currency, official: (Currency) -> BigDecimal?): List<BoardRate> {
        val words = lines.flatMap { it.words }
        val result = ArrayList<BoardRate>()
        for (row in rows(words)) {
            val tokens = row.flatMap { w -> w.text.split(' ', '\t').filter { it.isNotBlank() }.map { it to w.box } }
            val currencyIndex = tokens.indexOfFirst { (t, _) -> code(t)?.let { it != local } == true }
            if (currencyIndex < 0) continue
            val currency = code(tokens[currencyIndex].first) ?: continue
            if (result.any { it.currency == currency }) continue
            val numbers = tokens.drop(currencyIndex + 1).mapNotNull { (t, _) -> number(t) }
            if (numbers.isEmpty()) continue
            // «JPY 100 2.41 2.49»: целое 1/10/100/1000 перед курсами — за сколько единиц.
            val unit = numbers.first().takeIf { numbers.size >= 2 && it in UNITS && it.scale() <= 0 }
            val rates = (if (unit != null) numbers.drop(1) else numbers).take(2)
            val cbr = official(currency)
            val scaled = rates.mapNotNull { normalize(if (unit != null) it.divide(unit, MC) else it, cbr) }
            if (scaled.isEmpty()) continue
            val buy = scaled.min()
            val sell = scaled.max().takeIf { scaled.size == 2 && it > buy }
            result += BoardRate(currency, buy, sell, row.map { it.box }.reduce(Box::union))
        }
        return result
    }

    /** Слова в ряды: соседние по вертикали (центр одного внутри высоты другого), в ряду — слева направо. */
    fun rows(words: List<OcrWord>): List<List<OcrWord>> {
        val sorted = words.sortedBy { it.box.centerY }
        val rows = ArrayList<MutableList<OcrWord>>()
        for (w in sorted) {
            val row = rows.lastOrNull()?.takeIf { r ->
                val ref = r.first().box
                abs(w.box.centerY - ref.centerY) < maxOf(ref.height, w.box.height) * 0.5f
            }
            if (row != null) row += w else rows += mutableListOf(w)
        }
        return rows.map { r -> r.sortedBy { it.box.left } }
    }

    private fun code(token: String): Currency? {
        val t = token.trim().trimEnd(':', '.', ',').uppercase()
        if (t.length != 3 || !t.all { it in 'A'..'Z' }) return null
        return Currency.byCode(t)
    }

    private fun number(token: String): BigDecimal? {
        val t = token.trim()
        if (!NUMBER.matches(t)) return null
        return t.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }

    /**
     * Курс «за 1» по ЦБ: из «как есть», «за 10/100/1000» и обратного берём ближайший к ЦБ (в разах),
     * если он в пределах 30 %. Без ЦБ — как есть.
     */
    private fun normalize(value: BigDecimal, cbr: BigDecimal?): BigDecimal? {
        if (cbr == null || cbr.signum() <= 0) return value
        val candidates = listOf(
            value,
            value.divide(BigDecimal.TEN, MC),
            value.divide(BigDecimal(100), MC),
            value.divide(BigDecimal(1000), MC),
            BigDecimal.ONE.divide(value, MC),
        )
        val best = candidates.minBy { abs(ln(it.toDouble() / cbr.toDouble())) }
        val off = abs(ln(best.toDouble() / cbr.toDouble()))
        return best.takeIf { off <= ln(1 + TOLERANCE) }
    }
}
