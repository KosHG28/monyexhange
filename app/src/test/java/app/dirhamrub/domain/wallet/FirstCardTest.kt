package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FirstCardTest {
    private val day = LocalDate.of(2026, 11, 6)
    private val template = BankCard("new", "", Currency.AED, 3)
    private val cbr = OfficialRates(day, mapOf(Currency.AED to BigDecimal("21.97"), Currency.CNY to BigDecimal("11.42"), Currency.USD to BigDecimal("81.79")))

    private fun build(way: PayWay, a: String?, b: String?, card: Currency = Currency.CNY, sent: Currency = Currency.RUB, bought: String? = null) =
        FirstCard.build(way, template, Currency.RUB, Currency.AED, card, sent, a?.let(::BigDecimal), b?.let(::BigDecimal), bought?.let(::BigDecimal), day)

    @Test
    fun `рублёвая карта — один чек покупки`() {
        val card = build(PayWay.HOME_CARD, "379", "8508.55")!!
        assertEquals(Currency.RUB, card.currency)
        val q = RateEngine.quote(card, Currency.AED, Currency.RUB, cbr)!!
        assertEquals(RateBasis.RECEIPTS, q.basis)
        assertEquals(0, BigDecimal("22.45").compareTo(q.homePerUnit))
        // Без чека — курс ЦБ, но карта есть.
        val bare = build(PayWay.HOME_CARD, null, null)!!
        assertTrue(bare.purchases.isEmpty())
        assertEquals(0, BigDecimal("21.97").compareTo(RateEngine.quote(bare, Currency.AED, Currency.RUB, cbr)!!.homePerUnit))
    }

    @Test
    fun `карта в другой валюте — чек пополнения`() {
        val card = build(PayWay.CURRENCY_CARD, "30000", "2439")!!
        assertEquals(Currency.CNY, card.currency)
        assertEquals(TransferReceipt(BigDecimal("30000"), Currency.RUB, BigDecimal("2439"), day), card.transfer)
        assertNull(build(PayWay.CURRENCY_CARD, "30000", null)!!.transfer)
    }

    @Test
    fun `наличные — обмен, в том числе из долларов`() {
        val rub = build(PayWay.CASH, "10000", "440.5")!!
        assertEquals(CardKind.CASH, rub.kind)
        assertEquals(Currency.AED, rub.currency)
        assertEquals(0, BigDecimal("10000").divide(BigDecimal("440.5"), MC).compareTo(RateEngine.quote(rub, Currency.AED, Currency.RUB, cbr)!!.homePerUnit))
        val usd = build(PayWay.CASH, "300", "1101.75", sent = Currency.USD, bought = "81.5")!!
        assertEquals(Currency.RUB, usd.transfer!!.boughtIn)
        assertEquals(RateBasis.RECEIPTS, RateEngine.quote(usd, Currency.AED, Currency.RUB, cbr)!!.basis)
    }

    @Test
    fun `пока ничего — без карты`() {
        assertNull(build(PayWay.NOTHING, "1", "2"))
    }
}
