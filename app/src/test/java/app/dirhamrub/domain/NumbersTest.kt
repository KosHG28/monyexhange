package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NumbersTest {
    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `разбор ввода`() {
        assertEquals(0, bd("15000").compareTo(Numbers.parse("15 000")))
        assertEquals(0, bd("2757.48").compareTo(Numbers.parse("2757,48")))
        assertEquals(0, bd("2757.48").compareTo(Numbers.parse("2 757.48")))
        assertEquals(0, bd("2757.48").compareTo(Numbers.parse("2${NBSP}757,48")))
        assertEquals(0, bd("2757.48").compareTo(Numbers.parse("2 757,48")))
        assertEquals(0, bd("0.5").compareTo(Numbers.parse(",5")))
        assertEquals(0, bd("15").compareTo(Numbers.parse("15,")))
    }

    @Test
    fun `мусор не разбирается`() {
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse("   "))
        assertNull(Numbers.parse(","))
        assertNull(Numbers.parse("1,2,3"))
        assertNull(Numbers.parse("abc"))
        assertNull(Numbers.parse("-5"))
    }

    @Test
    fun `деньги`() {
        assertEquals("511,28", Numbers.money(bd("511.2756")))
        assertEquals("8${NBSP}507", Numbers.money(bd("8507.001")))
        assertEquals("22,50", Numbers.money(bd("22.5")))
        assertEquals("0", Numbers.money(BigDecimal.ZERO))
        assertEquals("1${NBSP}000${NBSP}000", Numbers.money(bd("1000000")))
        assertEquals("0,01", Numbers.money(bd("0.005")))
    }

    @Test
    fun `курсы до 4 знаков`() {
        assertEquals("23,2398", Numbers.rate(bd("23.23980")))
        assertEquals("12,3", Numbers.rate(bd("12.30001")))
        assertEquals("5", Numbers.rate(bd("5.00001")))
        assertEquals("1${NBSP}234,5", Numbers.rate(bd("1234.5")))
    }

    @Test
    fun `шпаргалка`() {
        assertEquals("23,24", Numbers.cheat(bd("23.2398")))
        assertEquals("5,00", Numbers.cheat(bd("5")))
        assertEquals("99,99", Numbers.cheat(bd("99.994")))
        assertEquals("116", Numbers.cheat(bd("116.199")))
        assertEquals("23${NBSP}240", Numbers.cheat(bd("23239.8")))
    }

    @Test
    fun `курс для сравнения — всегда с копейками`() {
        assertEquals("18,00", Numbers.rateShort(bd("18.0004")))
        assertEquals("18,17", Numbers.rateShort(bd("18.1724")))
        assertEquals("0,93", Numbers.rateShort(bd("0.9349")))
        assertEquals("999,99", Numbers.rateShort(bd("999.991")))
        assertEquals("12${NBSP}600", Numbers.rateShort(bd("12600.4")))
    }

    @Test
    fun `разница между картами`() {
        assertEquals("0,54", Numbers.saving(bd("0.5372")))
        assertEquals("5", Numbers.saving(bd("5.001")))
        assertEquals("9,99", Numbers.saving(bd("9.994")))
        assertEquals("11", Numbers.saving(bd("11.08")))
        assertEquals("1${NBSP}132", Numbers.saving(bd("1131.6")))
    }

    @Test
    fun `проценты`() {
        assertEquals("3,4", Numbers.percent(bd("3.4127")))
        assertEquals("10", Numbers.percent(bd("9.96")))
    }

    @Test
    fun `ввод с разрядами`() {
        assertEquals("15${NBSP}000,5", Numbers.groupInput("15000,5"))
        assertEquals("15,", Numbers.groupInput("15,"))
        assertEquals("", Numbers.groupInput(""))
        assertEquals("123", Numbers.groupInput("123"))
        assertEquals("1${NBSP}234", Numbers.groupInput("1234"))
    }

    @Test
    fun `значение для поля ввода`() {
        assertEquals("23,2398", Numbers.plain(bd("23.23980"), 4))
        assertEquals("15000", Numbers.plain(bd("15000.00")))
        assertEquals("8507,31", Numbers.plain(bd("8507.3125")))
    }

    @Test
    fun `даты`() {
        assertEquals("29${NBSP}октября", RuDates.long(LocalDate.of(2026, 10, 29)))
        assertEquals("1${NBSP}мая", RuDates.long(LocalDate.of(2026, 5, 1)))
        assertEquals("29${NBSP}окт", RuDates.short(LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun `курс в удобную сторону и единица для подписи`() {
        assertEquals("1 ₽ = 5,3933 ₸", Numbers.transferRate(bd("5.393333"), "₸"))
        assertEquals("1 ¥ = 12,3 ₽", Numbers.transferRate(bd("0.0813007"), "¥"))
        assertEquals("1 $ = 2,7 ₾", Numbers.transferRate(bd("2.7"), "₾", "$"))
        assertEquals(BigDecimal.ONE, Numbers.unitFor(bd("22.45")))
        assertEquals(BigDecimal(100), Numbers.unitFor(bd("0.1854")))
        assertEquals(BigDecimal(10000), Numbers.unitFor(bd("0.00312")))
        assertEquals(BigDecimal.ONE, Numbers.unitFor(bd("2.5")))
        assertEquals(BigDecimal(1_000_000), Numbers.unitFor(BigDecimal.ZERO))
    }

    @Test
    fun `округление итога`() {
        val v = BigDecimal("3706.06")
        assertEquals("3\u00A0706,06".replace("\u00A0", Numbers.money(BigDecimal(1000)).substring(1, 2)), Numbers.total(v, Rounding.NONE))
        assertEquals(Numbers.moneyWhole(BigDecimal(3706)), Numbers.total(v, Rounding.WHOLE))
        assertEquals(Numbers.moneyWhole(BigDecimal(3710)), Numbers.total(v, Rounding.TENS))
        assertEquals(Numbers.moneyWhole(BigDecimal(3700)), Numbers.total(BigDecimal("3704.99"), Rounding.TENS))
        assertEquals(Numbers.moneyWhole(BigDecimal(3710)), Numbers.total(BigDecimal("3705"), Rounding.TENS))
        // Меньше 10 — с копейками при любом округлении, иначе «0 ₽» ничего не скажет.
        assertEquals("5,40", Numbers.total(BigDecimal("5.4"), Rounding.TENS))
        assertEquals("67", Numbers.total(BigDecimal("67"), Rounding.NONE))
    }
}
