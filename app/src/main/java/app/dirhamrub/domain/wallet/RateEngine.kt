package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import java.math.BigDecimal

/** Откуда взят курс карты — чтобы честно подписать его в интерфейсе. */
enum class RateBasis {
    /** Задан вручную. */
    MANUAL,

    /** Оба шага — пополнение и покупка — посчитаны по чекам (или валюты совпадают). */
    RECEIPTS,

    /** Покупки в этой валюте не было: курс ЦБ плюс наценка карты, измеренная по её чекам. */
    ESTIMATE,

    /** Чеков нет: чистый курс ЦБ. */
    OFFICIAL,
}

/**
 * Цена 1 единицы валюты трат при оплате картой.
 * [homePerUnit] — в итоговой валюте, [cardPerUnit] — сколько спишут с карты;
 * [vsOfficial] — разница с курсом ЦБ в процентах (+3,1 — дороже ЦБ на 3,1 %).
 */
data class CardQuote(
    val card: BankCard,
    val homePerUnit: BigDecimal,
    val cardPerUnit: BigDecimal,
    val basis: RateBasis,
    val vsOfficial: BigDecimal?,
)

/** Выбранная карта [selected] против самой дешёвой из остальных [other]. */
data class CardComparison(val selected: CardQuote, val other: CardQuote) {
    /** Выбранная не дороже остальных. */
    val selectedIsBest: Boolean get() = selected.homePerUnit <= other.homePerUnit

    /** Насколько выгодная из двух дешевле, в процентах от цены дорогой. */
    val percent: BigDecimal
        get() {
            val high = selected.homePerUnit.max(other.homePerUnit)
            if (high.signum() == 0) return BigDecimal.ZERO
            return selected.homePerUnit.subtract(other.homePerUnit, MC).abs().divide(high, MC).multiply(HUNDRED, MC)
        }

    /** Оба курса измерены по чекам или заданы вручную — разница не оценочная. */
    val isExact: Boolean get() = selected.basis.isExact && other.basis.isExact

    /** Разница в итоговой валюте на покупку [amount] в валюте трат (≥ 0). */
    fun difference(amount: BigDecimal): BigDecimal =
        amount.multiply(selected.homePerUnit.subtract(other.homePerUnit, MC).abs(), MC)
}

private val RateBasis.isExact: Boolean get() = this == RateBasis.RECEIPTS || this == RateBasis.MANUAL

/** Результат сравнения карт на одной сумме. */
data class WalletQuote(
    val quotes: List<CardQuote>,
    /** Официальный курс: 1 единица валюты трат в итоговой валюте. */
    val official: BigDecimal?,
) {
    val best: CardQuote? get() = quotes.minByOrNull { it.homePerUnit }

    fun of(id: String?): CardQuote? = quotes.firstOrNull { it.card.id == id }

    /** Выбранная карта против самой дешёвой из остальных; null, если карта одна или не выбрана. */
    fun compare(selectedId: String?): CardComparison? {
        val selected = of(selectedId) ?: return null
        val other = quotes.filter { it.card.id != selected.card.id }.minByOrNull { it.homePerUnit } ?: return null
        return CardComparison(selected, other)
    }

    /** Экономия лучшей карты против следующей по цене, в процентах; null, если карта одна. */
    val bestSavingsPercent: BigDecimal?
        get() {
            val sorted = quotes.map { it.homePerUnit }.sorted()
            if (sorted.size < 2 || sorted[1].signum() == 0) return null
            return sorted[1].subtract(sorted[0], MC).divide(sorted[1], MC).multiply(HUNDRED, MC)
        }
}

private val HUNDRED = BigDecimal(100)

/**
 * Считает реальный курс каждой карты.
 *
 * 1 единица валюты трат в итоговой валюте = (валюта карты за 1 единицу трат) × (итоговая валюта за 1 единицу карты).
 * - Итоговая за единицу карты: 1, если валюты совпадают; иначе по чеку пополнения
 *   (отправленная валюта приводится к итоговой по курсу ЦБ); без чека — по курсу ЦБ.
 * - Валюта карты за единицу трат: 1, если совпадают; иначе по последнему чеку покупки в этой валюте;
 *   без такого чека — курс ЦБ с наценкой карты (средняя наценка её чеков покупок к курсу ЦБ).
 */
object RateEngine {

    fun quote(card: BankCard, spend: Currency, home: Currency, official: OfficialRates?): CardQuote? {
        val manual = card.manualFor(spend, home)
        val homePerCard = homePerCard(card, home, official)
        val (cardPerSpend, spendBasis) = cardPerSpend(card, spend, official) ?: (null to RateBasis.OFFICIAL)
        val officialRate = official?.cross(spend, home)

        if (manual != null) {
            val cardPer = cardPerSpend
                ?: homePerCard?.first?.takeIf { it.signum() > 0 }?.let { manual.homePerUnit.divide(it, MC) }
                ?: BigDecimal.ZERO
            return CardQuote(card, manual.homePerUnit, cardPer, RateBasis.MANUAL, percent(manual.homePerUnit, officialRate))
        }
        val (homePer, homeBasis) = homePerCard ?: return null
        cardPerSpend ?: return null
        val value = cardPerSpend.multiply(homePer, MC)
        val basis = when {
            spendBasis == RateBasis.RECEIPTS && homeBasis == RateBasis.RECEIPTS -> RateBasis.RECEIPTS
            spendBasis == RateBasis.OFFICIAL && homeBasis == RateBasis.OFFICIAL -> RateBasis.OFFICIAL
            else -> RateBasis.ESTIMATE
        }
        return CardQuote(card, value, cardPerSpend, basis, percent(value, officialRate))
    }

    fun quoteAll(wallet: Wallet, official: OfficialRates?): WalletQuote = WalletQuote(
        quotes = wallet.cards.mapNotNull { quote(it, wallet.spend, wallet.home, official) },
        official = official?.cross(wallet.spend, wallet.home),
    )

    /** Наценка карты к курсу ЦБ по её чекам покупок (0,031 — на 3,1 % дороже); null без данных. */
    fun markup(card: BankCard, official: OfficialRates?): BigDecimal? {
        official ?: return null
        val ratios = card.purchases.filter { it.isValid && it.spend != card.currency }.mapNotNull { p ->
            val ref = official.cross(p.spend, card.currency) ?: return@mapNotNull null
            if (ref.signum() == 0) null else p.paid.divide(p.price, MC).divide(ref, MC).subtract(BigDecimal.ONE, MC)
        }
        if (ratios.isEmpty()) return null
        return ratios.fold(BigDecimal.ZERO) { acc, r -> acc.add(r, MC) }.divide(BigDecimal(ratios.size), MC)
    }

    private fun homePerCard(card: BankCard, home: Currency, official: OfficialRates?): Pair<BigDecimal, RateBasis>? {
        if (card.currency == home) return BigDecimal.ONE to RateBasis.RECEIPTS
        val t = card.transfer?.takeIf { it.isValid }
        if (t != null) {
            val sentPerCard = t.sent.divide(t.got, MC)
            if (t.sentCurrency == home) return sentPerCard to RateBasis.RECEIPTS
            val toHome = official?.cross(t.sentCurrency, home) ?: return null
            return sentPerCard.multiply(toHome, MC) to RateBasis.ESTIMATE
        }
        val o = official?.cross(card.currency, home) ?: return null
        return o to RateBasis.OFFICIAL
    }

    private fun cardPerSpend(card: BankCard, spend: Currency, official: OfficialRates?): Pair<BigDecimal, RateBasis>? {
        if (card.currency == spend) return BigDecimal.ONE to RateBasis.RECEIPTS
        val p = card.purchaseFor(spend)
        if (p != null) return p.paid.divide(p.price, MC) to RateBasis.RECEIPTS
        val o = official?.cross(spend, card.currency) ?: return null
        val m = markup(card, official) ?: return o to RateBasis.OFFICIAL
        return o.multiply(BigDecimal.ONE.add(m, MC), MC) to RateBasis.ESTIMATE
    }

    private fun percent(value: BigDecimal, official: BigDecimal?): BigDecimal? {
        if (official == null || official.signum() == 0) return null
        return value.divide(official, MC).subtract(BigDecimal.ONE, MC).multiply(HUNDRED, MC)
    }
}
