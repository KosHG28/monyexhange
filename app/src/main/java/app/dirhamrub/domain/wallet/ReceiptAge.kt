package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Чек от [date] устарел: курс ЦБ той же пары с тех пор изменился на [cbrChange] процентов. */
data class StaleReceipt(val date: LocalDate, val cbrChange: BigDecimal)

/**
 * Устаревший чек: курс карты считается по последнему чеку, но если чеку больше [MIN_DAYS] дней,
 * а курс ЦБ той же пары валют с тех пор сдвинулся на [MIN_CHANGE] % и больше, посчитанный курс
 * уже неточен — стоит добавить свежий чек.
 */
object ReceiptAge {
    const val MIN_DAYS = 14L
    val MIN_CHANGE = BigDecimal(2)

    /** Чек и пара валют, курс ЦБ которой он фактически зафиксировал. */
    private class Use(val date: LocalDate, val from: Currency, val to: Currency)

    private fun uses(card: BankCard, spend: Currency, home: Currency): List<Use> {
        if (card.manualFor(spend, home) != null) return emptyList()
        return listOfNotNull(
            card.purchaseFor(spend)?.takeIf { card.currency != spend }?.let { Use(it.date, spend, card.currency) },
            card.effectiveTransfer?.takeIf { it.isValid && card.currency != home }?.let { Use(it.date, card.currency, it.sentCurrency) },
        )
    }

    /** Даты чеков старше [MIN_DAYS], для которых нужны курсы ЦБ (их качают заранее). */
    fun datesToCheck(card: BankCard, spend: Currency, home: Currency, today: LocalDate): Set<LocalDate> =
        uses(card, spend, home).map { it.date }.filter { ChronoUnit.DAYS.between(it, today) >= MIN_DAYS }.toSet()

    /**
     * Самый устаревший по курсу чек карты для пары [spend] → [home] или null.
     * [history] — курсы ЦБ на даты чеков; без них проверить нельзя.
     */
    fun check(
        card: BankCard,
        spend: Currency,
        home: Currency,
        today: LocalDate,
        now: OfficialRates?,
        history: Map<LocalDate, OfficialRates>,
    ): StaleReceipt? {
        now ?: return null
        return uses(card, spend, home)
            .filter { ChronoUnit.DAYS.between(it.date, today) >= MIN_DAYS }
            .mapNotNull { u ->
                val then = history[u.date] ?: return@mapNotNull null
                val change = now.trendPercent(then, u.from, u.to) ?: return@mapNotNull null
                if (change.abs() >= MIN_CHANGE) StaleReceipt(u.date, change) else null
            }
            .maxByOrNull { it.cbrChange.abs() }
    }
}
