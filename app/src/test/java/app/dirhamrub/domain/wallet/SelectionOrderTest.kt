package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SelectionOrderTest {
    @Test fun `latest valid receipt keeps first on tied dates and skips invalid values`() {
        val day = LocalDate.of(2026, 11, 14)
        val first = PurchaseReceipt(Currency.EUR, BigDecimal("11"), BigDecimal.TEN, day)
        val tie = first.copy(paid = BigDecimal("12"))
        val card = BankCard("a", "a", Currency.USD, 0, purchases = listOf(
            first.copy(date = day.minusDays(1)), first, tie,
            first.copy(date = day.plusDays(1), paid = BigDecimal.ZERO),
            first.copy(date = day.plusDays(1), spend = Currency.AED),
        ))
        assertSame(first, card.purchaseFor(Currency.EUR))
    }
    @Test fun `comparison tie keeps wallet order and ignores the selected card`() {
        val cards = List(3) { BankCard(it.toString(), it.toString(), Currency.RUB, 0) }
        val quotes = cards.map { CardQuote(it, BigDecimal.ONE, BigDecimal.ONE, RateBasis.OFFICIAL, null) }
        val wallet = WalletQuote(quotes, BigDecimal.ONE)
        assertEquals(cards[0], wallet.best!!.card)
        assertEquals(cards[0], wallet.compare(cards[2].id)!!.other.card)
        assertNull(WalletQuote(quotes.take(1), BigDecimal.ONE).compare(cards[0].id))
    }
}
