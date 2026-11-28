package app.dirhamrub.domain.wallet

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CardAppearanceTest {
    private val receipt = TransferReceipt(BigDecimal("8190.25"), Currency.RUB, BigDecimal("100"), LocalDate.of(2026, 11, 15))
    private val card = BankCard("usd", "Travel", Currency.USD, 0, transfer = receipt,
        network = CardNetwork.VISA_MC_FOREIGN, history = listOf(receipt.copy(got = BigDecimal("105"))))

    @Test fun `RGB input accepts six hex digits and rejects alpha or incomplete values`() {
        assertEquals(0x376AC4, CardAppearance.parseHex(" #376ac4 "))
        assertEquals(0, CardAppearance.parseHex("000000"))
        assertEquals(0xFFFFFF, CardAppearance.parseHex("FFFFFF"))
        assertEquals("#00AB0C", CardAppearance.hex(0x00AB0C))
        for (invalid in listOf("", "#FFF", "#FF123456", "12X456", "-12345", "##123456")) assertNull(CardAppearance.parseHex(invalid))
    }

    @Test fun `appearance preserves the latest money data and selection`() {
        val latest = card.withNewTransfer(receipt.copy(sent = BigDecimal("9000")))
        val wallet = Wallet(cards = listOf(latest), selectedId = card.id, spend = Currency.USD)
        val changed = wallet.withAppearance(card.id, 3, 0x376AC4, CardMaterial.CARBON)
        assertEquals(wallet.copy(cards = listOf(latest.copy(style = 3, customColor = 0x376AC4, material = CardMaterial.CARBON))), changed)
        assertEquals(RateEngine.quoteAll(wallet, null).quotes.single().homePerUnit,
            RateEngine.quoteAll(changed, null).quotes.single().homePerUnit)
        assertEquals(latest.copy(material = CardMaterial.CARBON), changed.withAppearance(card.id, 0, null).cards.single())
        assertEquals(latest, changed.withAppearance(card.id, 0, null, CardMaterial.SATIN).cards.single())
    }

    @Test fun `invalid and deleted targets cannot create cards or alter cash`() {
        val wallet = Wallet(cards = listOf(card))
        assertSame(wallet, wallet.withAppearance("missing", 1, 0))
        assertSame(wallet, wallet.withAppearance(card.id, 7, 0))
        assertSame(wallet, wallet.withAppearance(card.id, 1, 0x1000000))
        val cash = wallet.copy(cards = listOf(card.copy(kind = CardKind.CASH)))
        assertSame(cash, cash.withAppearance(card.id, 1, 0))
    }

    @Test fun `custom color persists and malformed or legacy properties use the old palette`() {
        val wallet = Wallet(cards = listOf(card.copy(customColor = 0x00AB0C, material = CardMaterial.HOLOGRAM)), selectedId = card.id)
        assertEquals(wallet, WalletCodec.decode(WalletCodec.encode(wallet)))
        val base = "cards=1\ncard.0.id=usd\ncard.0.name=Travel\ncard.0.currency=USD\ncard.0.style=3\n"
        assertEquals(CardMaterial.HOLOGRAM, WalletCodec.decode(base + "card.0.material=GLASS")!!.cards.single().material)
        for (suffix in listOf("", "card.0.color=oops", "card.0.color=ff123456", "card.0.color=-12345", "card.0.material=UNKNOWN")) {
            val decoded = WalletCodec.decode(base + suffix)!!.cards.single()
            assertNull(decoded.customColor)
            assertEquals(CardMaterial.SATIN, decoded.material)
            assertEquals(3, decoded.style)
            assertEquals(Currency.USD, decoded.currency)
        }
    }
}
