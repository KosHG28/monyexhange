package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Курсы платёжной системы UnionPay на дату [date]: сколько единиц валюты расчёта стоит 1 единица
 * валюты покупки. UnionPay публикует их по будним дням (в выходные действует пятничный курс)
 * для ~160 валют покупки и 15 валют расчёта ([BASES]). Комиссии банка-эмитента в курс не входят.
 */
data class UnionPayRates(
    val date: LocalDate,
    /** Валюта расчёта → (валюта покупки → единиц валюты расчёта за 1 единицу покупки). */
    val rates: Map<Currency, Map<Currency, BigDecimal>>,
) {
    /** Сколько [base] стоит 1 [trans] по курсу UnionPay; null, если такой пары нет. */
    fun rate(trans: Currency, base: Currency): BigDecimal? =
        if (trans == base) BigDecimal.ONE else rates[base]?.get(trans)?.takeIf { it.signum() > 0 }

    companion object {
        /** Валюты расчёта UnionPay: сначала частые у карт российских банков. */
        val BASES: List<Currency> = listOf(
            Currency.CNY, Currency.USD, Currency.EUR, Currency.HKD, Currency.GBP, Currency.JPY, Currency.SGD,
            Currency.THB, Currency.AUD, Currency.CAD, Currency.NZD, Currency.HUF, Currency.MNT, Currency.MOP, Currency.VND,
        )

        /** Валюта расчёта по умолчанию для карты в [card]: она сама, если UnionPay в ней рассчитывается, иначе юань. */
        fun defaultSettlement(card: Currency): Currency = if (card in BASES) card else Currency.CNY
    }
}

/**
 * Разбор файла курсов UnionPay (`m.unionpayintl.com/jfimg/ГГГГММДД.json`, его загружает их страница курсов):
 * `{"exchangeRateJson":[{"transCur":"AED","baseCur":"CNY","rateData":1.826},…],"curDate":"2026-11-06"}`.
 * Валюты, которых нет в справочнике, пропускаются.
 */
object UnionPayParser {
    private val date = Regex(""""curDate"\s*:\s*"(\d{4})-(\d{2})-(\d{2})"""")
    // Фигурные скобки экранированы везде: регулярные выражения Android (ICU) строже JVM — `\{[^{}]*}` там не компилируется.
    private val item = Regex("""\{[^\{\}]*\}""")
    private val trans = Regex(""""transCur"\s*:\s*"([A-Z]{3})"""")
    private val base = Regex(""""baseCur"\s*:\s*"([A-Z]{3})"""")
    private val rate = Regex(""""rateData"\s*:\s*"?([0-9.]+(?:[eE][-+]?\d+)?)""")

    fun parse(json: String): UnionPayRates? {
        val d = date.find(json) ?: return null
        val day = runCatching {
            LocalDate.of(d.groupValues[1].toInt(), d.groupValues[2].toInt(), d.groupValues[3].toInt())
        }.getOrNull() ?: return null
        val out = HashMap<Currency, HashMap<Currency, BigDecimal>>()
        for (m in item.findAll(json)) {
            val body = m.value
            val t = trans.find(body)?.groupValues?.get(1)?.let(Currency::byCode) ?: continue
            val b = base.find(body)?.groupValues?.get(1)?.let(Currency::byCode) ?: continue
            val r = rate.find(body)?.groupValues?.get(1)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: continue
            if (t != b) out.getOrPut(b) { HashMap() }[t] = r
        }
        return if (out.isEmpty()) null else UnionPayRates(day, out)
    }
}
