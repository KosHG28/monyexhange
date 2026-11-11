package app.dirhamrub.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Вся арифметика денег и курсов — в этой точности, округление только при показе. */
val MC: MathContext = MathContext.DECIMAL64

/** Неразрывный пробел — разделитель разрядов, чтобы суммы не переносились. */
const val NBSP = ' '

/** Разбор ввода и форматирование чисел в стиле ru-RU. */
object Numbers {
    private val TEN = BigDecimal.TEN
    private val HUNDRED = BigDecimal(100)
    private val THOUSAND = BigDecimal(1000)
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

    /**
     * Итог по настройке округления: без округления — с копейками («3 706,06»); до целых — целыми,
     * а меньше 10 — с копейками («3 706», «5,40»); до десятков — до ближайших 10 («3 710»), меньше 10 — как до целых.
     */
    fun total(value: BigDecimal, rounding: Rounding): String = when {
        rounding == Rounding.NONE -> money(value)
        value.abs() < TEN -> money(value)
        rounding == Rounding.TENS -> moneyWhole(value.divide(TEN).setScale(0, RoundingMode.HALF_UP).multiply(TEN))
        else -> moneyWhole(value)
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

    /**
     * Курс для сравнения карт и ЦБ: всегда 2 знака, чтобы «18,00» стояло рядом с «18,54»,
     * а не «18»; от 1000 — целыми («12 600»).
     */
    fun rateShort(value: BigDecimal): String =
        if (value.abs() < THOUSAND) money2(value) else moneyWhole(value)

    /** Разница между картами: меньше 10 — с копейками («0,54», «5»), дальше — целыми («11», «132»). */
    fun saving(value: BigDecimal): String =
        if (value.abs() < TEN) money(value) else moneyWhole(value)

    /** Процент с одним знаком: «3,4». */
    fun percent(value: BigDecimal): String = format(stripZeros(value.setScale(1, RoundingMode.HALF_UP)))

    /**
     * Курс в удобную сторону: «1 ₽ = 5,3933 ₸», но «1 ¥ = 12,3 ₽»
     * (если за единицу [base] даётся меньше единицы [quote] — переворачиваем).
     */
    fun transferRate(quotePerBase: BigDecimal, quoteSymbol: String, baseSymbol: String = "₽"): String =
        if (quotePerBase >= BigDecimal.ONE) {
            "1 $baseSymbol = ${rate(quotePerBase)} $quoteSymbol"
        } else {
            "1 $quoteSymbol = ${rate(BigDecimal.ONE.divide(quotePerBase, MC))} $baseSymbol"
        }

    /**
     * Сколько единиц валюты брать для подписи курса, чтобы число не было слишком мелким:
     * «1 AED = 22,45 ₽», но «100 ₸ = 18,54 ₽» и «10 000 ₫ = 31,2 ₽».
     */
    fun unitFor(perUnit: BigDecimal): BigDecimal {
        if (perUnit >= BigDecimal.ONE) return BigDecimal.ONE
        var unit = BigDecimal.TEN
        while (perUnit.multiply(unit, MC) < BigDecimal.TEN && unit < MAX_UNIT) unit = unit.multiply(BigDecimal.TEN)
        return unit
    }

    private val MAX_UNIT = BigDecimal(1_000_000)

    /**
     * Набранное в поле суммы: только цифры и одна запятая (точка — тоже запятая), без пробелов —
     * разряды поле показывает само. «15 000.5» → «15000,5», «,5» → «0,5».
     */
    fun cleanInput(text: String): String {
        val out = StringBuilder()
        var comma = false
        for (ch in text) {
            when {
                ch.isDigit() -> out.append(ch)
                (ch == ',' || ch == '.') && !comma -> {
                    comma = true
                    out.append(if (out.isEmpty()) "0," else ",")
                }
            }
        }
        return out.toString()
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
