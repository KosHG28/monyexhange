package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate

/** Пополнение карты: отправил [sent] в [sentCurrency] → пришло [got] в валюте карты. */
data class TransferReceipt(
    val sent: BigDecimal,
    val sentCurrency: Currency,
    val got: BigDecimal,
    val date: LocalDate,
) {
    val isValid: Boolean get() = sent.signum() > 0 && got.signum() > 0
}

/** Покупка по карте: списали [paid] в валюте карты за [price] в валюте трат [spend]. */
data class PurchaseReceipt(
    val spend: Currency,
    val paid: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
) {
    val isValid: Boolean get() = paid.signum() > 0 && price.signum() > 0
}

/** Курс, заданный вручную: 1 [spend] = [homePerUnit] в итоговой валюте [home]. */
data class ManualRate(
    val spend: Currency,
    val home: Currency,
    val homePerUnit: BigDecimal,
    val date: LocalDate,
)

/** Чем платят: картой или наличными из обменника. */
enum class CardKind {
    CARD,

    /** Наличные: «чек пополнения» — это обмен (отдал 10 000 ₽ → получил 1 200 AED), покупок по чекам нет. */
    CASH,
}

/** Карта пользователя (или наличные — [kind]). [style] — номер оформления (градиент). */
data class BankCard(
    val id: String,
    val name: String,
    val currency: Currency,
    val style: Int,
    val transfer: TransferReceipt? = null,
    val purchases: List<PurchaseReceipt> = emptyList(),
    val manual: List<ManualRate> = emptyList(),
    val kind: CardKind = CardKind.CARD,
) {
    /** Последний чек покупки в валюте трат [spend]. */
    fun purchaseFor(spend: Currency): PurchaseReceipt? =
        purchases.filter { it.spend == spend && it.isValid }.maxByOrNull { it.date }

    fun manualFor(spend: Currency, home: Currency): ManualRate? =
        manual.firstOrNull { it.spend == spend && it.home == home && it.homePerUnit.signum() > 0 }
}

/**
 * Всё, что пользователь настроил: карты, итоговая валюта, валюта трат, выбранная карта
 * и недавние валюты трат (сначала последняя) — для быстрого выбора среди ~150 валют.
 */
data class Wallet(
    val cards: List<BankCard> = emptyList(),
    val home: Currency = Currency.RUB,
    val spend: Currency = Currency.AED,
    val selectedId: String? = null,
    val recent: List<Currency> = emptyList(),
) {
    /** Новая валюта трат; прежняя и она сама — в начале недавних (не больше [RECENT]). */
    fun withSpend(currency: Currency): Wallet =
        copy(spend = currency, recent = (listOf(currency, spend) + recent).distinct().take(RECENT))

    val selected: BankCard? get() = cards.firstOrNull { it.id == selectedId } ?: cards.firstOrNull()

    fun upsert(card: BankCard): Wallet {
        val exists = cards.any { it.id == card.id }
        val next = if (exists) cards.map { if (it.id == card.id) card else it } else cards + card
        return copy(cards = next, selectedId = selectedId ?: card.id)
    }

    fun remove(id: String): Wallet {
        val next = cards.filterNot { it.id == id }
        return copy(cards = next, selectedId = if (selectedId == id) next.firstOrNull()?.id else selectedId)
    }

    /** Свободный номер оформления для новой карты. */
    fun nextStyle(): Int = (0 until CARD_STYLES).firstOrNull { s -> cards.none { it.style == s } } ?: cards.size % CARD_STYLES

    companion object {
        const val CARD_STYLES = 7
        const val RECENT = 6
    }
}
