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
}
