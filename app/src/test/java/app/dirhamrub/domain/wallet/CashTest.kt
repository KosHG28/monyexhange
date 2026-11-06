package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CashTest {
    private val day = LocalDate.of(2026, 11, 6)
    private val cbr = OfficialRates(day, mapOf(Currency.AED to BigDecimal("21.97"), Currency.AMD to BigDecimal("0.227"), Currency.CNY to BigDecimal("11.38")))
    private val cash = BankCard(
        "cash", "Наличные AED", Currency.AED, 2, kind = CardKind.CASH,
        transfer = TransferReceipt(BigDecimal("10000"), Currency.RUB, BigDecimal("440.5"), day),
    )
    private val yuan = BankCard("cny", "Карта в юанях", Currency.CNY, 1)
    private val tenge = BankCard("kzt", "Карта в тенге", Currency.KZT, 0)

    @Test
    fun `наличными платят только в их валюте`() {
        assertNull(RateEngine.quote(cash, Currency.AMD, Currency.RUB, cbr))
        val q = RateEngine.quote(cash, Currency.AED, Currency.RUB, cbr)!!
        // По обмену: 10 000 ₽ → 440,5 AED, без «оценки» и без чека покупки.
        assertEquals(RateBasis.RECEIPTS, q.basis)
        assertEquals(0, BigDecimal("10000").divide(BigDecimal("440.5"), app.dirhamrub.domain.MC).compareTo(q.homePerUnit))
        assertEquals(listOf("cny"), RateEngine.quoteAll(Wallet(cards = listOf(cash, yuan), spend = Currency.AMD), cbr).quotes.map { it.card.id })
    }

    @Test
    fun `выбор наличных не теряется при смене валюты трат`() {
        val wallet = Wallet(cards = listOf(tenge, cash, yuan), spend = Currency.AED, selectedId = "cash")
        assertEquals("cash", wallet.selected?.id)
        val amd = wallet.withSpend(Currency.AMD)
        assertEquals(listOf("kzt", "cny"), amd.usable.map { it.id })
        assertEquals("kzt", amd.selected?.id)
        assertEquals("cash", amd.selectedId)
        assertEquals("cash", amd.withSpend(Currency.AED).selected?.id)
    }

    @Test
    fun `порядок карт из настроек`() {
        val wallet = Wallet(cards = listOf(tenge, cash, yuan))
        assertEquals(listOf("cny", "kzt", "cash"), wallet.reorder(listOf("cny", "kzt", "cash")).cards.map { it.id })
        // Не названные остаются после, неизвестные и повторы пропускаются.
        assertEquals(listOf("cash", "kzt", "cny"), wallet.reorder(listOf("cash", "zzz", "cash")).cards.map { it.id })
    }
}
