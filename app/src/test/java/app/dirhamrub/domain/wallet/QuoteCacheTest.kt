package app.dirhamrub.domain.wallet

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.lang.management.ManagementFactory

class QuoteCacheTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val rates = OfficialRates(day, mapOf(Currency.USD to BigDecimal("80"), Currency.EUR to BigDecimal("90")))
    private val card = BankCard("usd", "USD", Currency.USD, 0)
    private val wallet = Wallet(cards = listOf(card), spend = Currency.EUR)

    @Test fun `selection place and appearance reuse math but rebind current metadata`() {
        val cache = QuoteCache()
        val first = cache.quote(wallet, rates, null)
        assertSame(first, cache.quote(wallet.copy(selectedId = card.id, place = "GR"), rates, null))
        val edited = wallet.copy(cards = listOf(card.copy(name = "New", style = 3, network = CardNetwork.MIR, customColor = 0x376AC4)))
        val rebound = cache.quote(edited, rates, null)
        assertEquals(RateEngine.quoteAll(edited, rates), rebound)
        assertEquals("New", rebound.quotes.single().card.name)
        assertEquals(1, cache.calculations)
    }

    @Test fun `all financial fields and source changes invalidate and match uncached calculation`() {
        val transfer = TransferReceipt(BigDecimal("80"), Currency.RUB, BigDecimal.ONE, day)
        val changes = listOf(
            card.copy(currency = Currency.EUR), card.copy(kind = CardKind.CASH), card.copy(unionPay = true),
            card.copy(settlement = Currency.EUR), card.copy(transfer = transfer),
            card.copy(history = listOf(transfer)), card.copy(transferMode = TransferMode.AVERAGE),
            card.copy(purchases = listOf(PurchaseReceipt(Currency.EUR, BigDecimal("1.1"), BigDecimal.ONE, day))),
            card.copy(manual = listOf(ManualRate(Currency.EUR, Currency.RUB, BigDecimal("91"), day))),
        )
        for (change in changes) {
            val cache = QuoteCache()
            cache.quote(wallet, rates, null)
            val next = wallet.copy(cards = listOf(change))
            assertEquals(RateEngine.quoteAll(next, rates), cache.quote(next, rates, null))
            assertEquals(2, cache.calculations)
        }
        val cache = QuoteCache()
        cache.quote(wallet, rates, null)
        val newRates = rates.copy(date = day.plusDays(1), rubPerUnit = rates.rubPerUnit + (Currency.EUR to BigDecimal("95")))
        assertEquals(RateEngine.quoteAll(wallet, newRates), cache.quote(wallet, newRates, null))
        assertEquals(RateEngine.quoteAll(wallet.copy(home = Currency.USD), newRates), cache.quote(wallet.copy(home = Currency.USD), newRates, null))
        assertEquals(3, cache.calculations)
    }

    @Test fun `cash from ATM updates when source card changes and removed cards are not returned`() {
        val source = card.copy(transfer = TransferReceipt(BigDecimal("800"), Currency.RUB, BigDecimal("10"), day))
        val cash = BankCard("cash", "cash", Currency.EUR, 1, kind = CardKind.CASH,
            transfer = TransferReceipt(BigDecimal("10"), Currency.USD, BigDecimal("8"), day, fromCard = source.id))
        val cache = QuoteCache()
        val first = Wallet(cards = listOf(source, cash), spend = Currency.EUR)
        cache.quote(first, rates, null)
        val next = first.copy(cards = listOf(source.copy(transfer = source.transfer!!.copy(sent = BigDecimal("900"))), cash))
        assertEquals(RateEngine.quoteAll(next, rates), cache.quote(next, rates, null))
        assertNotNull(cache.quote(next, rates, null).of(cash.id))
        assertNull(cache.quote(next.copy(cards = emptyList()), rates, null).of(cash.id))
    }

    @Test fun `allocation probe for selection-only updates records bounded cache benefit`() {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        val variants = List(300) { wallet.copy(selectedId = if (it % 2 == 0) card.id else null) }
        val cache = QuoteCache()
        repeat(100) { RateEngine.quoteAll(wallet, rates); cache.quote(wallet, rates, null) }
        val id = Thread.currentThread().id
        var sink: WalletQuote? = null
        val a = bean.getThreadAllocatedBytes(id)
        for (value in variants) sink = RateEngine.quoteAll(value, rates)
        val old = bean.getThreadAllocatedBytes(id) - a
        assertNotNull(sink)
        val b = bean.getThreadAllocatedBytes(id)
        for (value in variants) sink = cache.quote(value, rates, null)
        val current = bean.getThreadAllocatedBytes(id) - b
        assertNotNull(sink)
        assertEquals(1, cache.calculations)
        println("ALLOCATION_PROBE selection=300 uncachedBytes=$old cachedBytes=$current JVM-only")
    }
}
