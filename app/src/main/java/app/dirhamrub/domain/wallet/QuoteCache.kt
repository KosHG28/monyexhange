package app.dirhamrub.domain.wallet

import java.util.concurrent.atomic.AtomicInteger

/** Bounded per-state cache. Expensive work never holds a monitor needed by the UI thread. */
class QuoteCache {
    private data class Reference(val official: OfficialRates?, val up: UnionPayRates?, val result: OfficialRates?)
    private data class Entry(val wallet: Wallet, val reference: OfficialRates?, val up: UnionPayRates?, val result: WalletQuote)
    @Volatile private var references = emptyArray<Reference>()
    @Volatile private var entries = emptyArray<Entry>()
    private val computed = AtomicInteger()
    internal val calculations: Int get() = computed.get()

    fun reference(official: OfficialRates?, up: UnionPayRates?): OfficialRates? {
        val current = references
        var i = 0
        while (i < current.size) {
            val entry = current[i++]
            if (entry.official === official && entry.up === up) return entry.result
        }
        val result = official?.withUnionPay(up)
        // Concurrent misses may evict a useful entry, but can never return rates for another key.
        references = arrayOf(Reference(official, up, result), *references.take(3).toTypedArray())
        return result
    }

    fun quote(wallet: Wallet, reference: OfficialRates?, up: UnionPayRates?): WalletQuote {
        val current = entries
        var i = 0
        while (i < current.size) {
            val old = current[i++]
            if (old.reference !== reference || old.up !== up || old.wallet.spend != wallet.spend || old.wallet.home != wallet.home) continue
            if (old.wallet === wallet || old.wallet.cards === wallet.cards) return old.result
            if (old.wallet.cards.size == wallet.cards.size && old.wallet.cards.indices.all { index ->
                val before = old.wallet.cards[index]
                val after = wallet.cards[index]
                before === after || before.copy(name = after.name, style = after.style, network = after.network) == after
            }) {
                val rebound = old.result.copy(quotes = old.result.quotes.map { q ->
                    q.copy(card = wallet.cards.first { it.id == q.card.id })
                })
                store(Entry(wallet, reference, up, rebound))
                return rebound
            }
        }
        computed.incrementAndGet()
        return RateEngine.quoteAll(wallet, reference, up).also { store(Entry(wallet, reference, up, it)) }
    }

    private fun store(value: Entry) {
        // Keep old/new market snapshots for input concurrent with refresh; release removed cards.
        entries = arrayOf(value, *entries.filter { it.wallet.cards === value.wallet.cards }.take(3).toTypedArray())
    }
}
