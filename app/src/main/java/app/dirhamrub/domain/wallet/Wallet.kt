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

/**
 * Карта пользователя (или наличные — [kind]). [style] — номер оформления (градиент).
 * У карты UnionPay ([unionPay]) покупка в любой валюте пересчитывается по курсу UnionPay в валюту
 * расчёта [settlementCurrency], а из неё в валюту карты — по курсу банка.
 */
data class BankCard(
    val id: String,
    val name: String,
    val currency: Currency,
    val style: Int,
    val transfer: TransferReceipt? = null,
    val purchases: List<PurchaseReceipt> = emptyList(),
    val manual: List<ManualRate> = emptyList(),
    val kind: CardKind = CardKind.CARD,
    val unionPay: Boolean = false,
    /** Валюта расчёта UnionPay, если пользователь её выбрал; иначе — [UnionPayRates.defaultSettlement]. */
    val settlement: Currency? = null,
) {
    /** В какую валюту UnionPay пересчитывает покупки по этой карте. */
    val settlementCurrency: Currency get() = settlement ?: UnionPayRates.defaultSettlement(currency)

    /** Можно ли заплатить в [spend]: картой — всегда (банк пересчитает), наличными — только в их валюте. */
    fun canPay(spend: Currency): Boolean = kind != CardKind.CASH || currency == spend

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

    /** Чем можно заплатить в валюте трат: карты и наличные в этой валюте — в порядке пользователя. */
    val usable: List<BankCard> get() = cards.filter { it.canPay(spend) }

    /**
     * Выбранная карта, если ею можно заплатить в валюте трат; иначе — первая подходящая.
     * Выбор не теряется: вернулись к валюте наличных — снова выбраны наличные.
     */
    val selected: BankCard? get() = usable.firstOrNull { it.id == selectedId } ?: usable.firstOrNull()

    /** Карты в порядке [ids] (настройки); не названные — после них, в прежнем порядке. */
    fun reorder(ids: List<String>): Wallet {
        val byId = cards.associateBy { it.id }
        val ordered = ids.distinct().mapNotNull { byId[it] }
        return copy(cards = ordered + cards.filter { it !in ordered })
    }

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
