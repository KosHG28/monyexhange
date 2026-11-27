package app.dirhamrub.domain.price

import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ReceiptScanner
import app.dirhamrub.domain.wallet.sign
import java.util.Locale

data class PhotoPrice(val candidate: PriceCandidate, val currency: Currency, val needsReview: Boolean)

private val priceMarkers = buildMap<String, Set<Currency>> {
    fun add(mark: String, currency: Currency) {
        val key = mark.uppercase(Locale.ROOT)
        put(key, get(key).orEmpty() + currency)
    }
    for (currency in Currency.entries) {
        add(currency.code, currency)
        for (mark in listOf(currency.symbol, currency.sign)) {
            if (mark.any { !it.isLetter() }) add(mark, currency)
        }
    }
    for (mark in listOf("¥", "￥")) {
        add(mark, Currency.CNY)
        add(mark, Currency.JPY)
    }
    add("円", Currency.JPY)
}

private val markerPattern = Regex(
    "(?iu)(?<!\\p{L})(?:" +
        priceMarkers.keys.sortedByDescending { it.length }.joinToString("|", transform = Regex::escape) +
        ")(?!\\p{L})",
)

private fun priceCurrencies(line: String, spend: Currency): Set<Currency> {
    val matches = markerPattern.findAll(line).mapNotNull { match ->
        val before = line.substring(0, match.range.first).trimEnd()
        val after = line.substring(match.range.last + 1).trimStart()
        if (before.lastOrNull()?.isDigit() != true && after.firstOrNull()?.isDigit() != true) null
        else priceMarkers[match.value.uppercase(Locale.ROOT)]
    }.toList()
    val explicit = matches.mapNotNull { it.singleOrNull() }.toSet()
    return matches.flatMap { options ->
        val currency = options.singleOrNull() ?: options.intersect(explicit).singleOrNull() ?: spend.takeIf { it in options }
        if (currency == null) options else setOf(currency)
    }.toSet()
}

/** The live tracker prices all of its candidates in [spend], so foreign amounts go through photo review. */
fun livePriceBlocks(blocks: List<OcrBlock>, spend: Currency): List<OcrBlock> =
    blocks.flatMap { block ->
        val segments = mutableListOf<OcrBlock>()
        val lines = mutableListOf<OcrLine>()
        for (line in block.lines) {
            if (priceCurrencies(line.text, spend).any { it != spend }) {
                if (lines.isNotEmpty()) segments.add(OcrBlock(lines.toList()))
                lines.clear()
            } else {
                lines.add(line)
            }
        }
        if (lines.isNotEmpty()) segments.add(OcrBlock(lines))
        segments
    }

/** Parse frozen OCR once. A price with a foreign marker must not be quoted in the active currency. */
fun photoPrices(blocks: List<OcrBlock>, spend: Currency, bounds: Box?): List<PhotoPrice> =
    blocks.flatMap { block ->
        val explicit = block.lines.map { line ->
            priceCurrencies(line.text, spend).ifEmpty {
                ReceiptScanner.amounts(listOf(line.text)).map { it.currency }.toSet()
            }
        }
        if (explicit.any { currencies -> currencies.any { it != spend } }) {
            block.lines.flatMapIndexed { index, line ->
                val currencies = explicit[index]
                val printed = currencies.singleOrNull() ?: spend
                PriceParser(PriceCurrency.of(printed.code)).parse(listOf(OcrBlock(listOf(line))), bounds).map {
                    PhotoPrice(it, printed, printed != spend || currencies.size != 1 || !it.hasCurrency)
                }
            }
        } else {
            PriceParser(PriceCurrency.of(spend.code)).parse(listOf(block), bounds).map {
                PhotoPrice(it, spend, !it.hasCurrency)
            }
        }
    }.sortedByDescending { it.candidate.score }
