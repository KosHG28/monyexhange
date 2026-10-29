package app.dirhamrub.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Вся арифметика денег и курсов — в этой точности, округление только при показе. */
val MC: MathContext = MathContext.DECIMAL64

private val HUNDRED = BigDecimal(100)

/**
 * Два «чека» карты.
 * Перевод: отправил [rubSent] ₽ → пришло [cardGot] в валюте карты.
 * Покупка: списали [cardPaid] в валюте карты за [aedPaid] AED.
 */
data class Receipt(
    val rubSent: BigDecimal,
    val cardGot: BigDecimal,
    val cardPaid: BigDecimal,
    val aedPaid: BigDecimal,
) {
    val isValid: Boolean
        get() = rubSent.signum() > 0 && cardGot.signum() > 0 && cardPaid.signum() > 0 && aedPaid.signum() > 0

    /** Сколько единиц валюты карты дают за 1 ₽. */
    val cardPerRub: BigDecimal? get() = if (rubSent.signum() > 0) cardGot.divide(rubSent, MC) else null

    /** Сколько единиц валюты карты списывают за 1 AED. */
    val cardPerAed: BigDecimal? get() = if (aedPaid.signum() > 0) cardPaid.divide(aedPaid, MC) else null

    /** Главный курс карты: сколько рублей стоит 1 AED. */
    val rubPerAed: BigDecimal?
        get() {
            if (!isValid) return null
            return cardPerAed!!.divide(cardPerRub!!, MC)
        }
}

enum class RateSource { MANUAL, RECEIPTS }

/** Курс карты: чеки + действующий курс ₽ за дирхам, его источник и дата. */
data class CardRate(
    val card: Card,
    val receipt: Receipt,
    val rubPerAed: BigDecimal,
    val source: RateSource,
    val date: LocalDate,
) {
    /**
     * Валюта карты за 1 AED. Берётся из чека покупки: именно столько спишут с карты.
     * Если чек испорчен, выводится из курса и перевода.
     */
    val cardPerAed: BigDecimal
        get() = receipt.cardPerAed
            ?: receipt.cardPerRub?.let { rubPerAed.multiply(it, MC) }
            ?: BigDecimal.ZERO

    fun withReceipt(newReceipt: Receipt, today: LocalDate): CardRate? {
        val rate = newReceipt.rubPerAed ?: return null
        return copy(receipt = newReceipt, rubPerAed = rate, source = RateSource.RECEIPTS, date = today)
    }

    fun withManualRate(rate: BigDecimal, today: LocalDate): CardRate? {
        if (rate.signum() <= 0) return null
        return copy(rubPerAed = rate, source = RateSource.MANUAL, date = today)
    }
}

object Defaults {
    val DATE: LocalDate = LocalDate.of(2026, 10, 29)

    fun receipt(card: Card): Receipt = when (card) {
        Card.KZT -> Receipt(
            rubSent = BigDecimal("15000"),
            cardGot = BigDecimal("80900"),
            cardPaid = BigDecimal("2757.48"),
            aedPaid = BigDecimal("22"),
        )
        Card.CNY -> Receipt(
            rubSent = BigDecimal("30000"),
            cardGot = BigDecimal("2439.02"),
            cardPaid = BigDecimal("691.65"),
            aedPaid = BigDecimal("379"),
        )
    }

    fun rate(card: Card): CardRate {
        val receipt = receipt(card)
        return CardRate(card, receipt, receipt.rubPerAed!!, RateSource.RECEIPTS, DATE)
    }

    fun rates(): Map<Card, CardRate> = Card.entries.associateWith { rate(it) }
}

/** На сколько процентов [cheaper] дешевле, чем [pricier]. */
fun savingsPercent(cheaper: BigDecimal, pricier: BigDecimal): BigDecimal {
    if (pricier.signum() <= 0) return BigDecimal.ZERO
    return pricier.subtract(cheaper, MC).divide(pricier, MC).multiply(HUNDRED, MC)
}
