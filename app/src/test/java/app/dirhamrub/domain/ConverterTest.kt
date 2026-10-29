package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ConverterTest {
    private val kzt = Defaults.rate(Card.KZT)
    private val cny = Defaults.rate(Card.CNY)

    @Test
    fun `ввод в рублях`() {
        val a = Converter.amounts(BigDecimal("8507.31"), Line.RUB, cny)
        assertEquals("379", Numbers.money(a.aed))
        assertEquals("691,65", Numbers.money(a.card))
        assertEquals("8${NBSP}507,31", Numbers.money(a.rub))
    }

    @Test
    fun `ввод в валюте карты`() {
        val a = Converter.amounts(BigDecimal("2757.48"), Line.CARD, kzt)
        assertEquals("22", Numbers.money(a.aed))
        assertEquals("511,28", Numbers.money(a.rub))
        assertEquals("2${NBSP}757,48", Numbers.money(a.card))
    }

    @Test
    fun `смена карты при активной строке карты сохраняет дирхамы`() {
        // 691,65 ¥ = 379 AED; при переходе на тенге сумма в AED не меняется.
        val aed = Converter.toAed(BigDecimal("691.65"), Line.CARD, cny)
        val inTenge = Converter.fromAed(aed, kzt)
        assertEquals("379", Numbers.money(inTenge.aed))
        assertEquals("8${NBSP}807,89", Numbers.money(inTenge.rub))
    }

    @Test
    fun `значение по строке`() {
        val a = Converter.fromAed(BigDecimal("22"), kzt)
        assertEquals(a.aed, a.of(Line.AED))
        assertEquals(a.rub, a.of(Line.RUB))
        assertEquals(a.card, a.of(Line.CARD))
    }

    @Test
    fun `нулевой курс не падает`() {
        val broken = kzt.copy(rubPerAed = BigDecimal.ZERO)
        assertEquals(0, Converter.toAed(BigDecimal.TEN, Line.RUB, broken).signum())
    }
}
