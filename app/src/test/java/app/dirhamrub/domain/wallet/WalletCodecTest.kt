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
