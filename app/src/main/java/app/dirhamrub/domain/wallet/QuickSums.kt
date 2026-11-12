package app.dirhamrub.domain.wallet

import java.math.BigDecimal

/**
 * Быстрые суммы в валюте трат (пример суммы в знакомстве и демо). У самых частых валют — подобраны вручную
 * (у дирхама 22 — проезд в метро), у остальных — «круглые» суммы от размера валюты:
 * у рупии ([Currency.magnitude] = 4) 10K…1M, у песо (1) 10…1000.
 */
object QuickSums {
    fun chips(c: Currency): List<BigDecimal> = when (c) {
        Currency.AED -> listOf(5, 10, 22, 50, 100, 200, 500)
        Currency.TRY, Currency.THB -> listOf(50, 100, 200, 500, 1000, 2000, 5000)
        Currency.CNY -> listOf(10, 20, 50, 100, 200, 500, 1000)
        Currency.KZT, Currency.AMD -> listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
        Currency.VND -> listOf(10000, 20000, 50000, 100000, 200000, 500000, 1000000)
        Currency.USD, Currency.EUR, Currency.GEL -> listOf(1, 5, 10, 20, 50, 100, 200)
        else -> null
    }?.map { BigDecimal(it) } ?: scaled(c, CHIPS)

    /** Ряд 1-2-5, умноженный на 10^[Currency.magnitude] (примерно столько единиц за доллар). */
    private fun scaled(c: Currency, base: List<Int>): List<BigDecimal> {
        val unit = BigDecimal.TEN.pow(c.magnitude)
        return base.map { BigDecimal(it).multiply(unit) }
    }

    private val CHIPS = listOf(1, 2, 5, 10, 20, 50, 100)
}
