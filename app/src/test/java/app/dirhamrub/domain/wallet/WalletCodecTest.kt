package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val w = WalletCodec.decode("cards=2\ncard.0.id=a\ncard.0.currency=ZZZ\ncard.1.id=b\ncard.1.currency=USD\nspend=ZZZ\nrecent=IDR,ZZZ,,AED\nselected=a")!!
        assertEquals(listOf("b"), w.cards.map { it.id })
        // Неизвестная валюта трат — дирхамы; в недавних неизвестные коды пропускаются.
        assertEquals(Currency.AED, w.spend)
        assertEquals(listOf(Currency.IDR, Currency.AED), w.recent)
        // Тратить можно в любой валюте, и в рублях тоже (поездка по России).
        assertEquals(Currency.RUB, WalletCodec.decode("spend=RUB")!!.spend)
        assertEquals("b", w.selectedId)
        assertNull(WalletCodec.decodeRates("date=oops"))
    }

    @Test
    fun `курсы со старым справочником валют показываются, но скачиваются заново`() {
        val rates = OfficialRates(day, mapOf(Currency.USD to BigDecimal("81.5"), Currency.MDL to BigDecimal("4.56")))
        assertTrue(WalletCodec.isCurrentTable(WalletCodec.encodeRates(rates)))
        assertTrue(WalletCodec.isCurrentTable(WalletCodec.encodeHistory(mapOf(day to rates))))
        assertEquals(mapOf(day to rates), WalletCodec.decodeHistory(WalletCodec.encodeHistory(mapOf(day to rates))))

        // Так курсы сохраняла 1.10: без отпечатка, только 14 валют — нет ни лея, ни сомони.
        val old = "date=2026-11-06\nrub.USD=81.7947\nrub.KZT=0.180598\n"
        assertEquals(OfficialRates(LocalDate.of(2026, 11, 6), mapOf(Currency.USD to BigDecimal("81.7947"), Currency.KZT to BigDecimal("0.180598"))), WalletCodec.decodeRates(old))
        assertFalse(WalletCodec.isCurrentTable(old))
        assertFalse(WalletCodec.isCurrentTable(null))
        assertFalse(WalletCodec.isCurrentTable(""))

        val table110 = WalletCodec.tableOf("RUB AED TRY THB USD EUR CNY KZT GEL AMD VND BYN UZS KGS".split(' '))
        assertNotEquals(table110, WalletCodec.RATES_TABLE)
        assertFalse(WalletCodec.isCurrentTable("table=$table110\n$old"))
    }

    @Test
    fun `платёжная система карты сохраняется`() {
        val card = BankCard(id = "k", name = "Карта в тенге", currency = Currency.KZT, style = 0, network = CardNetwork.VISA_MC_FOREIGN)
        val back = WalletCodec.decode(WalletCodec.encode(Wallet(cards = listOf(card))))!!.cards.single()
        assertEquals(CardNetwork.VISA_MC_FOREIGN, back.network)
        val old = WalletCodec.decode(WalletCodec.encode(Wallet(cards = listOf(card.copy(network = null)))))!!.cards.single()
        assertEquals(null, old.network)
    }
}
