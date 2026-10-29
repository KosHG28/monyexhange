package app.dirhamrub.domain

import java.math.BigDecimal

/** Одна сумма во всех трёх валютах: цены ([source]), рубли и валюта выбранной карты. */
data class Amounts(val source: BigDecimal, val rub: BigDecimal, val card: BigDecimal) {
    fun of(line: Line): BigDecimal = when (line) {
        Line.SOURCE -> source
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

/**
 * Сколько стоит одна единица валюты цен для каждой карты.
 *
 * Дирхамы — по чекам карты (`rubPerAed`, `cardPerAed`).
 * Тенге: для тенговой карты это её собственная валюта — рубли за тенге из чека перевода
 * (`rubSent / cardGot`). Для других карт чека покупки в тенге нет, поэтому курс оценивается
 * через дирхам: тенге → AED по тенговой карте, AED → валюта карты по её чеку.
 */
object Pricing {
    fun rubPerUnit(card: Card, source: Source, rates: Map<Card, CardRate>): BigDecimal {
        val rate = rates.getValue(card)
        return when (source) {
            Source.AED -> rate.rubPerAed
            Source.KZT -> if (card == Card.KZT) {
                rate.receipt.cardPerRub?.takeIf { it.signum() > 0 }?.let { BigDecimal.ONE.divide(it, MC) }
                    ?: divideOrZero(rate.rubPerAed, rate.cardPerAed)
            } else {
                divideOrZero(rate.rubPerAed, kztPerAed(rates))
            }
        }
    }

    fun cardPerUnit(card: Card, source: Source, rates: Map<Card, CardRate>): BigDecimal {
        val rate = rates.getValue(card)
        return when (source) {
            Source.AED -> rate.cardPerAed
            Source.KZT -> if (card == Card.KZT) BigDecimal.ONE else divideOrZero(rate.cardPerAed, kztPerAed(rates))
        }
    }

    private fun kztPerAed(rates: Map<Card, CardRate>): BigDecimal = rates.getValue(Card.KZT).cardPerAed
}

object Converter {
    /** Пересчитать значение строки [line] в валюту цен по выбранной карте. */
    fun toSource(value: BigDecimal, line: Line, card: Card, source: Source, rates: Map<Card, CardRate>): BigDecimal =
        when (line) {
            Line.SOURCE -> value
            Line.RUB -> divideOrZero(value, Pricing.rubPerUnit(card, source, rates))
            Line.CARD -> divideOrZero(value, Pricing.cardPerUnit(card, source, rates))
        }

    fun fromSource(amount: BigDecimal, card: Card, source: Source, rates: Map<Card, CardRate>): Amounts = Amounts(
        source = amount,
        rub = amount.multiply(Pricing.rubPerUnit(card, source, rates), MC),
        card = amount.multiply(Pricing.cardPerUnit(card, source, rates), MC),
    )

    fun amounts(value: BigDecimal, line: Line, card: Card, source: Source, rates: Map<Card, CardRate>): Amounts {
        val amounts = fromSource(toSource(value, line, card, source, rates), card, source, rates)
        // Введённое значение показываем как есть, без круга через деление.
        return when (line) {
            Line.SOURCE -> amounts
            Line.RUB -> amounts.copy(rub = value)
            Line.CARD -> amounts.copy(card = value)
        }
    }

    fun rub(amount: BigDecimal, card: Card, source: Source, rates: Map<Card, CardRate>): BigDecimal =
        amount.multiply(Pricing.rubPerUnit(card, source, rates), MC)

    fun compare(amount: BigDecimal, source: Source, rates: Map<Card, CardRate>): Comparison {
        val kzt = Pricing.rubPerUnit(Card.KZT, source, rates)
        val cny = Pricing.rubPerUnit(Card.CNY, source, rates)
        val rubByCard = mapOf(Card.KZT to amount.multiply(kzt, MC), Card.CNY to amount.multiply(cny, MC))
        val cmp = kzt.compareTo(cny)
        val cheaper = when {
            cmp > 0 -> Card.CNY
            cmp < 0 -> Card.KZT
            else -> null
        }
        val savingsRub = if (cheaper == null) BigDecimal.ZERO
        else rubByCard.getValue(cheaper.other).subtract(rubByCard.getValue(cheaper), MC)
        val percent = if (cheaper == null) BigDecimal.ZERO
        else savingsPercent(minOf(kzt, cny), maxOf(kzt, cny))
        return Comparison(rubByCard, cheaper, savingsRub, percent)
    }
}

internal fun divideOrZero(value: BigDecimal, divisor: BigDecimal): BigDecimal =
    if (divisor.signum() == 0) BigDecimal.ZERO else value.divide(divisor, MC)
