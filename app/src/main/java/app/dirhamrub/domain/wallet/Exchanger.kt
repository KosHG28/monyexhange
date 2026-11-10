package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import java.math.BigDecimal
import java.util.Properties

/**
 * Обменник: за 1 [give] (что вы несёте — доллары, рубли) дают [buy] местной валюты [local] — это его курс
 * покупки вашей валюты. [sell] — курс продажи (необязателен): по разнице видно, честный ли обменник.
 * [at] — когда записали (мс).
 */
data class Exchanger(
    val id: String,
    val name: String,
    val give: Currency,
    val local: Currency,
    val buy: BigDecimal,
    val sell: BigDecimal? = null,
    val at: Long = 0L,
) {
    /**
     * Разница покупки и продажи к середине, в процентах: честные обменники — 0,5–1,5 %, дорогие — больше 3 %.
     * null — продажа не указана или курсы перепутаны (продажа ниже покупки).
     */
    val spreadPercent: BigDecimal?
        get() {
            val s = sell?.takeIf { it > buy } ?: return null
            val mid = s.add(buy).divide(BigDecimal(2), MC)
            return s.subtract(buy).multiply(BigDecimal(100), MC).divide(mid, MC)
        }

    /** Разница покупки и продажи слишком большая — дорогой обменник. */
    val expensive: Boolean get() = spreadPercent?.let { it > EXPENSIVE_SPREAD } == true

    /** Сколько итоговой валюты стоит 1 единица местной, если 1 [give] обошёлся в [homePerGive]. */
    fun homePerLocal(homePerGive: BigDecimal): BigDecimal? =
        if (buy.signum() > 0 && homePerGive.signum() > 0) homePerGive.divide(buy, MC) else null

    companion object {
        val EXPENSIVE_SPREAD: BigDecimal = BigDecimal(3)
    }
}

/** Сравнение обменников между собой и с картой. */
object ExchangeMath {
    /** Обменники пары [give] → [local], лучший (больше всего дают) — первым. */
    fun ranked(all: List<Exchanger>, give: Currency, local: Currency): List<Exchanger> =
        all.filter { it.give == give && it.local == local && it.buy.signum() > 0 }.sortedByDescending { it.buy }

    /** На сколько процентов [cost] дороже [base] (отрицательное — дешевле). */
    fun percentVs(cost: BigDecimal, base: BigDecimal): BigDecimal? =
        if (base.signum() > 0) cost.subtract(base).multiply(BigDecimal(100), MC).divide(base, MC) else null

    /** Сколько местной валюты больше даст [better] вместо [worse] за [amount] вашей. */
    fun extraLocal(better: Exchanger, worse: Exchanger, amount: BigDecimal): BigDecimal =
        better.buy.subtract(worse.buy).multiply(amount, MC)

    /**
     * Наличные после обмена [amount] в [ex]: карта-«наличные» в местной валюте с чеком обмена
     * (отдал [amount] [Exchanger.give], получил по курсу обменника) и курсом, по которому купили отданную валюту.
     * [base] — уже заведённые наличные в этой валюте (обновляем их), иначе [template] — новая пустая карта.
     */
    fun cashAfter(
        ex: Exchanger,
        amount: BigDecimal,
        homePerGive: BigDecimal?,
        home: Currency,
        date: java.time.LocalDate,
        base: BankCard?,
        template: BankCard,
    ): BankCard {
        val bought = homePerGive?.takeIf { ex.give != home && it.signum() > 0 }
        val transfer = TransferReceipt(amount, ex.give, amount.multiply(ex.buy, MC), date, bought, bought?.let { home })
        // Уже были наличные — прежний обмен уходит в историю (курс можно считать в среднем).
        val card = base ?: template.copy(kind = CardKind.CASH, currency = ex.local)
        return card.copy(kind = CardKind.CASH, currency = ex.local).withNewTransfer(transfer)
    }
}

/** Хранение обменников (Properties, как у кошелька). */
object ExchangerCodec {
    /** Больше не храним: старые вытесняются новыми. */
    const val MAX = 30

    fun encode(list: List<Exchanger>): String {
        val p = Properties()
        val kept = list.sortedByDescending { it.at }.take(MAX)
        p["count"] = kept.size.toString()
        kept.forEachIndexed { i, e ->
            val k = "ex.$i."
            p[k + "id"] = e.id
            p[k + "name"] = e.name
            p[k + "give"] = e.give.code
            p[k + "local"] = e.local.code
            p[k + "buy"] = e.buy.toPlainString()
            e.sell?.let { p[k + "sell"] = it.toPlainString() }
            p[k + "at"] = e.at.toString()
        }
        return WalletCodec.store(p)
    }

    fun decode(text: String?): List<Exchanger> {
        val p = text?.let(WalletCodec::load) ?: return emptyList()
        val count = p.getProperty("count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val k = "ex.$i."
            Exchanger(
                id = p.getProperty(k + "id") ?: return@mapNotNull null,
                name = p.getProperty(k + "name").orEmpty(),
                give = Currency.byCode(p.getProperty(k + "give")) ?: return@mapNotNull null,
                local = Currency.byCode(p.getProperty(k + "local")) ?: return@mapNotNull null,
                buy = p.getProperty(k + "buy")?.toBigDecimalOrNull() ?: return@mapNotNull null,
                sell = p.getProperty(k + "sell")?.toBigDecimalOrNull(),
                at = p.getProperty(k + "at")?.toLongOrNull() ?: 0L,
            )
        }
    }
}
