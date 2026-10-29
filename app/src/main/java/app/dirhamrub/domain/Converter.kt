package app.dirhamrub.domain

import java.math.BigDecimal

/** Одна сумма во всех трёх валютах выбранной карты. */
data class Amounts(val aed: BigDecimal, val rub: BigDecimal, val card: BigDecimal) {
    fun of(line: Line): BigDecimal = when (line) {
        Line.AED -> aed
        Line.RUB -> rub
        Line.CARD -> card
    }
}

/** Цена одной и той же покупки в рублях по обеим картам. */
data class Comparison(
    val rubByCard: Map<Card, BigDecimal>,
    /** Более выгодная карта; null, если курсы равны. */
    val cheaper: Card?,
    /** Экономия в рублях на этой сумме. */
    val savingsRub: BigDecimal,
    /** На сколько процентов выгоднее (не зависит от суммы). */
    val savingsPercent: BigDecimal,
)

object Converter {
    /** Пересчитать значение строки [line] в дирхамы по курсу карты. */
    fun toAed(value: BigDecimal, line: Line, rate: CardRate): BigDecimal = when (line) {
        Line.AED -> value
        Line.RUB -> divideOrZero(value, rate.rubPerAed)
        Line.CARD -> divideOrZero(value, rate.cardPerAed)
    }

    fun fromAed(aed: BigDecimal, rate: CardRate): Amounts = Amounts(
        aed = aed,
        rub = aed.multiply(rate.rubPerAed, MC),
        card = aed.multiply(rate.cardPerAed, MC),
    )

    fun amounts(value: BigDecimal, line: Line, rate: CardRate): Amounts {
        val amounts = fromAed(toAed(value, line, rate), rate)
        // Введённое значение показываем как есть, без круга через деление.
        return when (line) {
            Line.AED -> amounts
            Line.RUB -> amounts.copy(rub = value)
            Line.CARD -> amounts.copy(card = value)
        }
    }

    fun rub(aed: BigDecimal, rate: CardRate): BigDecimal = aed.multiply(rate.rubPerAed, MC)

    fun compare(aed: BigDecimal, rates: Map<Card, CardRate>): Comparison {
        val kzt = rates.getValue(Card.KZT).rubPerAed
        val cny = rates.getValue(Card.CNY).rubPerAed
        val rubByCard = rates.mapValues { (_, rate) -> rub(aed, rate) }
        val cmp = kzt.compareTo(cny)
        val cheaper = when {
            cmp > 0 -> Card.CNY
            cmp < 0 -> Card.KZT
            else -> null
        }
        val savingsRub = if (cheaper == null) BigDecimal.ZERO
        else rubByCard.getValue(cheaper.other).subtract(rubByCard.getValue(cheaper), MC)
        val percent = if (cheaper == null) BigDecimal.ZERO
        else savingsPercent(rates.getValue(cheaper).rubPerAed, rates.getValue(cheaper.other).rubPerAed)
        return Comparison(rubByCard, cheaper, savingsRub, percent)
    }

    private fun divideOrZero(value: BigDecimal, divisor: BigDecimal): BigDecimal =
        if (divisor.signum() == 0) BigDecimal.ZERO else value.divide(divisor, MC)
}
