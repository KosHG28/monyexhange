package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Пополнение карты (у наличных — обмен): отправил [sent] в [sentCurrency] → пришло [got] в валюте карты.
 * Если отдавали не итоговую валюту (100 $ в драмы), [boughtAt] — почём её купили: 1 [sentCurrency] =
 * [boughtAt] в [boughtIn] (обычно рубли); без него — по курсу ЦБ.
 * У наличных из банкомата [fromCard] — карта, с которой сняли: списали [sent] в её валюте за [got] на руки
 * (комиссии банкомата и банка — внутри), а в итоговую валюту — по курсу этой карты.
 */
data class TransferReceipt(
    val sent: BigDecimal,
    val sentCurrency: Currency,
    val got: BigDecimal,
    val date: LocalDate,
    val boughtAt: BigDecimal? = null,
    val boughtIn: Currency? = null,
    val fromCard: String? = null,
) {
    /** Почём купили отправленную валюту, если это известно: (курс, в какой валюте). */
    val bought: Pair<BigDecimal, Currency>?
        get() {
            val rate = boughtAt?.takeIf { it.signum() > 0 } ?: return null
            val c = boughtIn?.takeIf { it != sentCurrency } ?: return null
            return rate to c
        }

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

/**
 * Платёжная система карты — для справочника «чем платить в стране». Visa и Mastercard российских банков за границей
 * не работают — их нет (прежние сохранённые карты читаются без платёжной системы).
 */
enum class CardNetwork {
    /** UnionPay российского банка (АТБ, Россельхозбанк…): за границей её отклоняют чаще. */
    UNIONPAY,

    /** UnionPay зарубежного банка (Казахстан, Киргизия, Китай…). */
    UNIONPAY_FOREIGN,
    MIR,


    /** Visa или Mastercard зарубежного банка (Казахстан, Киргизия, Армения…). */
    VISA_MC_FOREIGN,
}

/** Какой курс пополнения считать, если пополнений несколько. */
enum class TransferMode {
    /** По последнему пополнению — ближе всего к тому, что банк берёт сейчас. */
    LAST,

    /** В среднем по пополнениям: все отправленные / все пришедшие — во что обошлись деньги на карте. */
    AVERAGE,
}

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
    /** Платёжная система, если пользователь её указал (у UnionPay-карты — UnionPay и без этого). */
    val network: CardNetwork? = null,
    /** Прежние пополнения (без последнего — он в [transfer]), новые — первыми. */
    val history: List<TransferReceipt> = emptyList(),
    /** Курс по последнему пополнению или в среднем по всем. */
    val transferMode: TransferMode = TransferMode.LAST,
) {
    /**
     * Пополнение, по которому считается курс: последнее, а в режиме «в среднем» — сводное по всем
     * пополнениям из той же валюты (и с той же карты у банкомата): суммы складываются, «почём купили» —
     * среднее, взвешенное по отправленному (если известно у всех). Дата — последнего.
     */
    val effectiveTransfer: TransferReceipt?
        get() {
            val last = transfer?.takeIf { it.isValid } ?: return transfer
            if (transferMode != TransferMode.AVERAGE) return last
            val same = listOf(last) + history.filter {
                it.isValid && it.sentCurrency == last.sentCurrency && it.fromCard == last.fromCard
            }
            if (same.size < 2) return last
            val sent = same.fold(java.math.BigDecimal.ZERO) { acc, t -> acc.add(t.sent) }
            val got = same.fold(java.math.BigDecimal.ZERO) { acc, t -> acc.add(t.got) }
            val boughtIn = last.bought?.second
            val bought = if (boughtIn != null && same.all { it.bought?.second == boughtIn }) {
                same.fold(java.math.BigDecimal.ZERO) { acc, t -> acc.add(t.sent.multiply(t.bought!!.first)) }
                    .divide(sent, app.dirhamrub.domain.MC)
            } else {
                null
            }
            return last.copy(sent = sent, got = got, boughtAt = bought, boughtIn = bought?.let { boughtIn })
        }

    /** Записать новое пополнение: прежнее уходит в историю (не больше [HISTORY_MAX]). */
    fun withNewTransfer(next: TransferReceipt): BankCard =
        copy(transfer = next, history = (listOfNotNull(transfer) + history).take(HISTORY_MAX))

    /** Платёжная система для справочника: указанная, а у карты UnionPay без указанной — UnionPay российского банка. */
    val paymentNetwork: CardNetwork? get() = network ?: CardNetwork.UNIONPAY.takeIf { unionPay }

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

/** Сколько прежних пополнений храним. */
const val HISTORY_MAX = 20

/**
 * Всё, что пользователь настроил: карты, итоговая валюта, валюта трат, выбранная вручную карта (null — автовыбор)
 * и недавние валюты трат (сначала последняя) — для быстрого выбора среди ~150 валют.
 */
data class Wallet(
    val cards: List<BankCard> = emptyList(),
    val home: Currency = Currency.RUB,
    val spend: Currency = Currency.AED,
    val selectedId: String? = null,
    val recent: List<Currency> = emptyList(),
    /** Страна, выбранная в списке стран (ISO), — для шапки, если валютой платят в нескольких странах (евро). */
    val place: String? = null,
) {
    /**
     * Новая валюта трат; прежняя и она сама — в начале недавних (не больше [RECENT]). [place] — страна, если её
     * выбрали в списке стран (та же валюта, другая страна — только она и меняется).
     */
    fun withSpend(currency: Currency, place: String? = null): Wallet =
        if (currency == spend) copy(place = place ?: this.place)
        else copy(spend = currency, place = place, recent = (listOf(currency, spend) + recent).distinct().take(RECENT))

    /** Чем можно заплатить в валюте трат: карты и наличные в этой валюте — в порядке пользователя. */
    val usable: List<BankCard> get() = cards.filter { it.canPay(spend) }

    /**
     * Чем считаем: выбранный вручную способ, если им можно заплатить в валюте трат; иначе — лучший
     * ([bestId], самый дешёвый — его знает расчёт курсов), иначе — первый подходящий.
     * [selectedId] == null — «автовыбор»: считаем лучшим. Ручной выбор не теряется: вернулись
     * к валюте наличных — снова выбраны наличные.
     */
    fun pick(bestId: String?): BankCard? =
        usable.firstOrNull { it.id == selectedId } ?: usable.firstOrNull { it.id == bestId } ?: usable.firstOrNull()

    /** Выбрали способ касанием: лучший — снова автовыбор (дальше считаем лучшим, каким бы он ни стал). */
    fun choose(id: String, bestId: String?): Wallet = copy(selectedId = id.takeIf { it != bestId })

    /** Карты в порядке [ids] (настройки); не названные — после них, в прежнем порядке. */
    fun reorder(ids: List<String>): Wallet {
        val byId = cards.associateBy { it.id }
        val ordered = ids.distinct().mapNotNull { byId[it] }
        return copy(cards = ordered + cards.filter { it !in ordered })
    }

    fun upsert(card: BankCard): Wallet {
        val exists = cards.any { it.id == card.id }
        val next = if (exists) cards.map { if (it.id == card.id) card else it } else cards + card
        return copy(cards = next)
    }

    fun remove(id: String): Wallet {
        val next = cards.filterNot { it.id == id }
        return copy(cards = next, selectedId = selectedId?.takeIf { it != id })
    }

    /** Свободный номер оформления для новой карты. */
    fun nextStyle(): Int = (0 until CARD_STYLES).firstOrNull { s -> cards.none { it.style == s } } ?: cards.size % CARD_STYLES

    companion object {
        const val CARD_STYLES = 7
        const val RECENT = 6
    }
}
