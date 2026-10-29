package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RateMathTest {
    private val kzt = Defaults.rate(Card.KZT)
    private val cny = Defaults.rate(Card.CNY)

    @Test
    fun `курс тенговой карты из чеков`() {
        assertEquals("23,2398", Numbers.rate(kzt.rubPerAed))
        assertEquals("23,24", Numbers.rate(kzt.rubPerAed, 2))
    }

    @Test
    fun `курс юаневой карты из чеков`() {
        assertEquals("22,4467", Numbers.rate(cny.rubPerAed))
        assertEquals("22,45", Numbers.rate(cny.rubPerAed, 2))
    }

    @Test
    fun `22 AED картой в тенге`() {
        val a = Converter.fromSource(BigDecimal("22"), Card.KZT, Source.AED, Defaults.rates())
        assertEquals("511,28", Numbers.money(a.rub))
        assertEquals("2${NBSP}757,48", Numbers.money(a.card))
    }

    @Test
    fun `379 AED картой в юанях и в тенге`() {
        val a = Converter.fromSource(BigDecimal("379"), Card.CNY, Source.AED, Defaults.rates())
        assertEquals("8${NBSP}507,31", Numbers.money(a.rub))
        assertEquals("691,65", Numbers.money(a.card))
        assertEquals("8${NBSP}807,89", Numbers.money(Converter.rub(BigDecimal("379"), Card.KZT, Source.AED, Defaults.rates())))
    }

    @Test
    fun `юаневая карта дешевле на 3,4 процента`() {
        val c = Converter.compare(BigDecimal("379"), Source.AED, Defaults.rates())
        assertEquals(Card.CNY, c.cheaper)
        assertEquals("3,4", Numbers.percent(c.savingsPercent))
        assertEquals("301", Numbers.moneyWhole(c.savingsRub))
        assertEquals("8${NBSP}507", Numbers.moneyWhole(c.rubByCard.getValue(Card.CNY)))
        assertEquals("8${NBSP}808", Numbers.moneyWhole(c.rubByCard.getValue(Card.KZT)))
    }

    @Test
    fun `курсы перевода в удобную сторону`() {
        assertEquals("1 ¥ = 12,3 ₽", Numbers.transferRate(cny.receipt.cardPerRub!!, "¥"))
        assertEquals("1 ₽ = 5,3933 ₸", Numbers.transferRate(kzt.receipt.cardPerRub!!, "₸"))
        assertEquals("1,8249", Numbers.rate(cny.cardPerAed))
    }

    @Test
    fun `испорченный чек не даёт курса`() {
        val bad = Receipt(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)
        assertNull(bad.rubPerAed)
        assertNull(kzt.withReceipt(bad, Defaults.DATE))
    }

    @Test
    fun `ручной курс меняет источник и дату`() {
        val day = Defaults.DATE.plusDays(3)
        val manual = kzt.withManualRate(BigDecimal("24.1"), day)!!
        assertEquals(RateSource.MANUAL, manual.source)
        assertEquals(day, manual.date)
        assertEquals(0, BigDecimal("24.1").compareTo(manual.rubPerAed))
        assertNull(kzt.withManualRate(BigDecimal.ZERO, day))
    }

    @Test
    fun `курс из новых чеков`() {
        val day = Defaults.DATE.plusDays(1)
        val r = Receipt(BigDecimal("10000"), BigDecimal("1000"), BigDecimal("100"), BigDecimal("50"))
        val rate = cny.withManualRate(BigDecimal("30"), day)!!.withReceipt(r, day)!!
        assertEquals(RateSource.RECEIPTS, rate.source)
        assertEquals("20", Numbers.rate(rate.rubPerAed))
    }

    @Test
    fun `равные курсы — нет более выгодной карты`() {
        val rates = mapOf(Card.KZT to kzt, Card.CNY to cny.copy(rubPerAed = kzt.rubPerAed))
        val c = Converter.compare(BigDecimal("10"), Source.AED, rates)
        assertNull(c.cheaper)
        assertEquals(0, c.savingsRub.signum())
    }
}
