package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AtmTest {
    private val day = LocalDate.of(2026, 11, 6)
    private val cbr = OfficialRates(day, mapOf(Currency.AED to BigDecimal("21.97"), Currency.CNY to BigDecimal("11.42")))
    private val rubCard = BankCard("rub", "Рублёвая", Currency.RUB, 0)
    private val yuan = BankCard(
        "cny", "Юани", Currency.CNY, 1,
        transfer = TransferReceipt(BigDecimal("30000"), Currency.RUB, BigDecimal("2439"), day),
    )

    private fun atm(source: BankCard, debited: String) = BankCard(
        "atm", "Наличные AED", Currency.AED, 2, kind = CardKind.CASH,
        transfer = TransferReceipt(BigDecimal(debited), source.currency, BigDecimal("1000"), day, fromCard = source.id),
    )

    private fun quote(cash: BankCard, cards: List<BankCard>) = RateEngine.quote(cash, Currency.AED, Currency.RUB, cbr, cards = cards)!!

    @Test
    fun `сняли с рублёвой карты — курс по чеку снятия`() {
        val cash = atm(rubCard, "22900")
        val q = quote(cash, listOf(rubCard, cash))
        assertEquals(RateBasis.RECEIPTS, q.basis)
        assertEquals(0, BigDecimal("22.9").compareTo(q.homePerUnit))
    }

    @Test
    fun `сняли с карты в юанях — снятие × пополнение карты`() {
        val cash = atm(yuan, "2000")
        val q = quote(cash, listOf(yuan, cash))
        assertEquals(RateBasis.RECEIPTS, q.basis)
        // 2 ¥ за дирхам × (30 000 ₽ / 2 439 ¥).
        val expected = BigDecimal("2").multiply(BigDecimal("30000").divide(BigDecimal("2439"), MC), MC)
        assertEquals(0, expected.compareTo(q.homePerUnit))

        // Карта без чека пополнения — юань по ЦБ: оценка.
        val bare = yuan.copy(transfer = null)
        val e = quote(cash, listOf(bare, cash))
        assertEquals(RateBasis.ESTIMATE, e.basis)
        assertEquals(0, BigDecimal("2").multiply(BigDecimal("11.42"), MC).compareTo(e.homePerUnit))
    }

    @Test
    fun `карту удалили — валюта списания по ЦБ`() {
        val cash = atm(yuan, "2000")
        val q = quote(cash, listOf(cash))
        assertEquals(RateBasis.ESTIMATE, q.basis)
        assertEquals(0, BigDecimal("2").multiply(BigDecimal("11.42"), MC).compareTo(q.homePerUnit))
    }

    @Test
    fun `снятие сохраняется и сравнивается с картой`() {
        val cash = atm(yuan, "2000")
        val wallet = Wallet(cards = listOf(yuan, cash), spend = Currency.AED)
        assertEquals(wallet.cards, WalletCodec.decode(WalletCodec.encode(wallet))!!.cards)
        val all = RateEngine.quoteAll(wallet, cbr)
        assertEquals(listOf("cny", "atm"), all.quotes.map { it.card.id })
        assertEquals(0, quote(cash, wallet.cards).homePerUnit.compareTo(all.of("atm")!!.homePerUnit))
    }
}
