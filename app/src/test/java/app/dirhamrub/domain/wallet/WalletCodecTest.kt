package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class WalletCodecTest {
    private val day = LocalDate.of(2026, 10, 29)

    @Test
    fun `кошелёк сохраняется и читается без потерь`() {
        val wallet = Wallet(
            cards = listOf(
                BankCard(
                    id = "kzt", name = "Карта в тенге = Kaspi #1", currency = Currency.KZT, style = 0,
                    transfer = TransferReceipt(BigDecimal("15000"), Currency.RUB, BigDecimal("80900"), day),
                    purchases = listOf(
                        PurchaseReceipt(Currency.AED, BigDecimal("2757.48"), BigDecimal("22"), day),
                        PurchaseReceipt(Currency.THB, BigDecimal("1500.5"), BigDecimal("100"), day.plusDays(1)),
                    ),
                    manual = listOf(ManualRate(Currency.AED, Currency.RUB, BigDecimal("23.5"), day)),
                ),
                BankCard(id = "usd", name = "", currency = Currency.USD, style = 4),
            ),
            home = Currency.KZT,
            spend = Currency.THB,
            selectedId = "usd",
        )
        assertEquals(wallet, WalletCodec.decode(WalletCodec.encode(wallet)))
    }

    @Test
    fun `наличные сохраняются как наличные, старые карты — карты`() {
        val cash = BankCard(
            id = "cash", name = "Наличные AED", currency = Currency.AED, style = 3, kind = CardKind.CASH,
            transfer = TransferReceipt(BigDecimal("10000"), Currency.RUB, BigDecimal("450"), LocalDate.of(2026, 11, 1)),
        )
        val back = WalletCodec.decode(WalletCodec.encode(Wallet(cards = listOf(cash))))!!
        assertEquals(cash, back.cards.single())
        // Сохранения до 1.6 — без поля kind: читаются как карты.
        val legacy = WalletCodec.encode(Wallet(cards = listOf(cash.copy(kind = CardKind.CARD)))).lines().filterNot { it.contains("kind") }.joinToString("\n")
        assertEquals(CardKind.CARD, WalletCodec.decode(legacy)!!.cards.single().kind)
    }

    @Test
    fun `курсы ЦБ на даты чеков сохраняются`() {
        val history = mapOf(
            LocalDate.of(2026, 10, 12) to OfficialRates(LocalDate.of(2026, 10, 11), mapOf(Currency.AED to BigDecimal("21.30"))),
            LocalDate.of(2026, 9, 30) to OfficialRates(LocalDate.of(2026, 9, 30), mapOf(Currency.CNY to BigDecimal("11.10"), Currency.KZT to BigDecimal("0.17"))),
        )
        assertEquals(history, WalletCodec.decodeHistory(WalletCodec.encodeHistory(history)))
        assertEquals(emptyMap<LocalDate, OfficialRates>(), WalletCodec.decodeHistory(null))
    }

    @Test
    fun `курсы ЦБ сохраняются`() {
        val rates = OfficialRates(day, mapOf(Currency.USD to BigDecimal("81.5"), Currency.VND to BigDecimal("0.00312")))
        assertEquals(rates, WalletCodec.decodeRates(WalletCodec.encodeRates(rates)))
    }

    @Test
    fun `испорченное и пустое`() {
        assertNull(WalletCodec.decode(null))
        assertNull(WalletCodec.decode(""))
        val w = WalletCodec.decode("cards=2\ncard.0.id=a\ncard.0.currency=ZZZ\ncard.1.id=b\ncard.1.currency=USD\nspend=RUB\nselected=a")!!
        assertEquals(listOf("b"), w.cards.map { it.id })
        assertEquals(Currency.AED, w.spend)
        assertEquals("b", w.selectedId)
        assertNull(WalletCodec.decodeRates("date=oops"))
    }
}
