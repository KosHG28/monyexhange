package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ExchangerTest {
    private fun d(s: String) = BigDecimal(s)
    private fun ex(id: String, buy: String, sell: String? = null, give: Currency = Currency.USD) =
        Exchanger(id, id, give, Currency.AED, d(buy), sell?.let(::d), at = id.hashCode().toLong())
    private fun r2(v: BigDecimal?) = v?.setScale(2, RoundingMode.HALF_UP)

    @Test
    fun `разница покупки и продажи`() {
        assertEquals(d("0.55"), r2(ex("a", "3.65", "3.67").spreadPercent))
        assertFalse(ex("a", "3.65", "3.67").expensive)
        assertTrue(ex("b", "3.40", "3.85").expensive)
        assertNull(ex("c", "3.65").spreadPercent)
        // Перепутанные колонки — не считаем.
        assertNull(ex("d", "3.67", "3.65").spreadPercent)
    }

    @Test
    fun `курс местной валюты через почём купили доллары`() {
        // Доллары по 92,10 ₽, за доллар 3,65 AED → 1 AED = 25,23 ₽.
        assertEquals(d("25.23"), r2(ex("a", "3.65").homePerLocal(d("92.10"))))
        assertNull(ex("a", "0").homePerLocal(d("92.10")))
    }

    @Test
    fun `рейтинг и разница`() {
        val list = listOf(ex("airport", "3.40", "3.85"), ex("metro", "3.65", "3.67"), ex("mall", "3.61"), ex("eur", "3.9", give = Currency.EUR))
        assertEquals(listOf("metro", "mall", "airport"), ExchangeMath.ranked(list, Currency.USD, Currency.AED).map { it.id })
        assertEquals(d("125.00"), r2(ExchangeMath.extraLocal(list[1], list[0], d("500"))))
        assertEquals(d("12.38"), r2(ExchangeMath.percentVs(d("25.23"), d("22.45"))))
    }

    @Test
    fun `наличные после обмена`() {
        val template = BankCard("new", "", Currency.RUB, 2)
        val day = LocalDate.of(2026, 11, 10)
        val cash = ExchangeMath.cashAfter(ex("metro", "3.65"), d("500"), d("92.10"), Currency.RUB, day, null, template)
        assertEquals(CardKind.CASH, cash.kind)
        assertEquals(Currency.AED, cash.currency)
        assertEquals(d("1825.00"), r2(cash.transfer!!.got))
        assertEquals(d("92.10") to Currency.RUB, cash.transfer!!.bought)
        // Отдавали рубли — «почём купили» не нужно.
        val rub = ExchangeMath.cashAfter(ex("r", "0.041", give = Currency.RUB), d("10000"), d("1"), Currency.RUB, day, cash, template)
        assertEquals(cash.id, rub.id)
        assertNull(rub.transfer!!.bought)
    }

    @Test
    fun `сохранение`() {
        val list = listOf(ex("a", "3.65", "3.67"), ex("b", "3.61"))
        val back = ExchangerCodec.decode(ExchangerCodec.encode(list))
        assertEquals(list.toSet(), back.toSet())
        assertEquals(emptyList<Exchanger>(), ExchangerCodec.decode(null))
        assertEquals(emptyList<Exchanger>(), ExchangerCodec.decode("мусор"))
    }
}
