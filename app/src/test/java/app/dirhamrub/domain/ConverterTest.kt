package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ConverterTest {
    private val rates = Defaults.rates()

    @Test
    fun `ввод в рублях`() {
        val a = Converter.amounts(BigDecimal("8507.31"), Line.RUB, Card.CNY, Source.AED, rates)
        assertEquals("379", Numbers.money(a.source))
        assertEquals("691,65", Numbers.money(a.card))
        assertEquals("8${NBSP}507,31", Numbers.money(a.rub))
    }

    @Test
    fun `ввод в валюте карты`() {
        val a = Converter.amounts(BigDecimal("2757.48"), Line.CARD, Card.KZT, Source.AED, rates)
        assertEquals("22", Numbers.money(a.source))
        assertEquals("511,28", Numbers.money(a.rub))
        assertEquals("2${NBSP}757,48", Numbers.money(a.card))
    }

    @Test
    fun `смена карты при активной строке карты сохраняет дирхамы`() {
        // 691,65 ¥ = 379 AED; при переходе на тенге сумма в AED не меняется.
        val aed = Converter.toSource(BigDecimal("691.65"), Line.CARD, Card.CNY, Source.AED, rates)
        val inTenge = Converter.fromSource(aed, Card.KZT, Source.AED, rates)
        assertEquals("379", Numbers.money(inTenge.source))
        assertEquals("8${NBSP}807,89", Numbers.money(inTenge.rub))
    }

    @Test
    fun `значение по строке`() {
        val a = Converter.fromSource(BigDecimal("22"), Card.KZT, Source.AED, rates)
        assertEquals(a.source, a.of(Line.SOURCE))
        assertEquals(a.rub, a.of(Line.RUB))
        assertEquals(a.card, a.of(Line.CARD))
    }

    @Test
    fun `нулевой курс не падает`() {
        val broken = rates + (Card.KZT to rates.getValue(Card.KZT).copy(rubPerAed = BigDecimal.ZERO))
        assertEquals(0, Converter.toSource(BigDecimal.TEN, Line.RUB, Card.KZT, Source.AED, broken).signum())
    }

    @Test
    fun `цены в тенге картой в тенге — по чеку перевода`() {
        // 1 ₽ = 5,3933 ₸ → 1000 ₸ = 185,41 ₽, списание — те же 1000 ₸.
        val a = Converter.fromSource(BigDecimal("1000"), Card.KZT, Source.KZT, rates)
        assertEquals("185,41", Numbers.money(a.rub))
        assertEquals("1${NBSP}000", Numbers.money(a.card))
    }

    @Test
    fun `цены в тенге картой в юанях — через дирхам`() {
        // 1000 ₸ = 1000 / 125,34 AED по тенговой карте; дальше по юаневой: × 22,4467 ₽ и × 1,8249 ¥.
        val a = Converter.fromSource(BigDecimal("1000"), Card.CNY, Source.KZT, rates)
        assertEquals("179,09", Numbers.money(a.rub))
        assertEquals("14,56", Numbers.money(a.card))
    }

    @Test
    fun `в тенге юаневая карта тоже дешевле на 3,4 процента`() {
        val c = Converter.compare(BigDecimal("10000"), Source.KZT, rates)
        assertEquals(Card.CNY, c.cheaper)
        assertEquals("3,4", Numbers.percent(c.savingsPercent))
        assertEquals("1${NBSP}854", Numbers.moneyWhole(c.rubByCard.getValue(Card.KZT)))
    }

    @Test
    fun `ввод в рублях при ценах в тенге`() {
        val a = Converter.amounts(BigDecimal("185.41"), Line.RUB, Card.KZT, Source.KZT, rates)
        assertEquals("1${NBSP}000", Numbers.moneyWhole(a.source))
    }
}
