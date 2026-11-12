package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate

/** Чем платят — последний шаг знакомства. */
enum class PayWay {
    /** Карта в итоговой валюте (обычно рублёвая): банк сам пересчитывает в валюту трат. */
    HOME_CARD,

    /** Карта в другой валюте: в мастере — чек покупки (цена и сколько списали в валюте карты); пополнение — потом. */
    CURRENCY_CARD,

    /** Наличные валюты трат из обменника. */
    CASH,

    /** Пока ничего — считаем по курсу ЦБ. */
    NOTHING,
}

/**
 * Первая карта из мастера знакомства: только поля нужного способа, суммы — как набрал пользователь
 * (null — не заполнено). Незаполненный чек — просто карта без чека: курс по ЦБ с пометкой «≈».
 */
object FirstCard {
    /**
     * [template] — пустая карта с id и оформлением; [first]/[second] — суммы чека способа:
     * карта (в итоговой или другой валюте) — цена в валюте трат / сколько списали в валюте карты;
     * наличные — сколько отдали ([sentCurrency]) / сколько получили.
     * [bought] — почём купили отданную валюту, если она не итоговая (1 единица в итоговой).
     */
    fun build(
        way: PayWay,
        template: BankCard,
        home: Currency,
        spend: Currency,
        cardCurrency: Currency,
        sentCurrency: Currency,
        first: BigDecimal?,
        second: BigDecimal?,
        bought: BigDecimal?,
        date: LocalDate,
    ): BankCard? {
        val a = first?.takeIf { it.signum() > 0 }
        val b = second?.takeIf { it.signum() > 0 }
        return when (way) {
            PayWay.NOTHING -> null
            PayWay.HOME_CARD -> template.copy(
                currency = home,
                kind = CardKind.CARD,
                purchases = if (a != null && b != null && spend != home) listOf(PurchaseReceipt(spend, b, a, date)) else emptyList(),
            )
            PayWay.CURRENCY_CARD -> template.copy(
                currency = cardCurrency,
                kind = CardKind.CARD,
                purchases = if (a != null && b != null && spend != cardCurrency) listOf(PurchaseReceipt(spend, b, a, date)) else emptyList(),
            )
            PayWay.CASH -> {
                val boughtAt = bought?.takeIf { it.signum() > 0 && sentCurrency != home }
                template.copy(
                    currency = spend,
                    kind = CardKind.CASH,
                    transfer = if (a != null && b != null && sentCurrency != spend) {
                        TransferReceipt(a, sentCurrency, b, date, boughtAt, boughtAt?.let { home })
                    } else {
                        null
                    },
                )
            }
        }
    }
}
