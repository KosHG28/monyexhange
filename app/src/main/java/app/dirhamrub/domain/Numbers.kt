package app.dirhamrub.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Неразрывный пробел — разделитель разрядов, чтобы суммы не переносились. */
const val NBSP = ' '

/** Разбор ввода и форматирование чисел в стиле ru-RU. */
object Numbers {
    private val HUNDRED = BigDecimal(100)
    private val inputRegex = Regex("""\d*\.?\d*""")

    /**
     * «15 000», «2757,48», «2 757.48» → BigDecimal. Пробелы любого вида игнорируются,
     * запятая равна точке. Пустая строка и мусор → null.
     */
    fun parse(text: String): BigDecimal? {
        val cleaned = buildString(text.length) {
            for (ch in text) {
                when {
                    ch.isWhitespace() || ch == NBSP || ch == ' ' || ch == ' ' -> Unit
                    ch == ',' -> append('.')
                    else -> append(ch)
                }
            }
        }
        if (cleaned.isEmpty() || cleaned == "." || !inputRegex.matches(cleaned)) return null
        return BigDecimal(cleaned)
    }

    /** Деньги: 2 знака, целые суммы без дробной части. 511.276 → «511,28», 8507 → «8 507». */
    fun money(value: BigDecimal): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        return if (rounded.remainder(BigDecimal.ONE).signum() == 0) {
            format(rounded.setScale(0, RoundingMode.HALF_UP))
        } else {
            format(rounded)
        }
    }

    /** Деньги, округлённые до целых: «8 507». */
    fun moneyWhole(value: BigDecimal): String = format(value.setScale(0, RoundingMode.HALF_UP))

    /** Деньги, всегда 2 знака: «5,00». */
    fun money2(value: BigDecimal): String = format(value.setScale(2, RoundingMode.HALF_UP))

    /** Курс: до [maxFraction] знаков без хвостовых нулей. 23.23980 → «23,2398», 12.30001 → «12,3». */
    fun rate(value: BigDecimal, maxFraction: Int = 4): String =
        format(stripZeros(value.setScale(maxFraction, RoundingMode.HALF_UP)))

    /** Шпаргалка: до 100 — 2 знака, дальше целыми. */
    fun cheat(value: BigDecimal): String =
        if (value.abs() < HUNDRED) money2(value) else moneyWhole(value)

    /** Процент с одним знаком: «3,4». */
    fun percent(value: BigDecimal): String = format(stripZeros(value.setScale(1, RoundingMode.HALF_UP)))

    /**
     * Курс перевода в удобную сторону: «1 ₽ = 5,3933 ₸», но «1 ¥ = 12,3 ₽»
     * (если за рубль даётся меньше единицы валюты карты — переворачиваем).
     */
    fun transferRate(cardPerRub: BigDecimal, cardSymbol: String): String =
        if (cardPerRub >= BigDecimal.ONE) {
            "1 ₽ = ${rate(cardPerRub)} $cardSymbol"
        } else {
            "1 $cardSymbol = ${rate(BigDecimal.ONE.divide(cardPerRub, MC))} ₽"
        }

    /** Ввод в процессе набора: «15000,5» → «15 000,5», хвостовая запятая сохраняется. */
    fun groupInput(raw: String): String {
        val comma = raw.indexOf(',')
        val intPart = if (comma >= 0) raw.substring(0, comma) else raw
        val rest = if (comma >= 0) raw.substring(comma) else ""
        return groupDigits(intPart) + rest
    }

    /** Значение для поля ввода: без разделителей разрядов, запятая, без хвостовых нулей. */
    fun plain(value: BigDecimal, maxFraction: Int = 2): String =
        stripZeros(value.setScale(maxFraction, RoundingMode.HALF_UP)).toPlainString().replace('.', ',')

    private fun stripZeros(value: BigDecimal): BigDecimal {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    private fun format(value: BigDecimal): String {
        val plain = value.abs().toPlainString()
        val dot = plain.indexOf('.')
        val intPart = if (dot >= 0) plain.substring(0, dot) else plain
        val fraction = if (dot >= 0) "," + plain.substring(dot + 1) else ""
        val sign = if (value.signum() < 0) "−" else ""
        return sign + groupDigits(intPart) + fraction
    }

    private fun groupDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + digits.length / 3)
        val head = digits.length % 3
        for (i in digits.indices) {
            if (i > 0 && (i - head) % 3 == 0) sb.append(NBSP)
            sb.append(digits[i])
        }
        return sb.toString()
    }
}

/** Даты по-русски без зависимости от локали устройства. */
object RuDates {
    private val genitive = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val short = arrayOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    /** «29 октября» */
    fun long(date: LocalDate): String = "${date.dayOfMonth}$NBSP${genitive[date.monthValue - 1]}"

    /** «29 окт» */
    fun short(date: LocalDate): String = "${date.dayOfMonth}$NBSP${short[date.monthValue - 1]}"
}
