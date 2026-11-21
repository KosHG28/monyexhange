package app.dirhamrub.domain.price

import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ReceiptScanner

data class PhotoPrice(val candidate: PriceCandidate, val currency: Currency, val needsReview: Boolean)

/** Parse frozen OCR once. A price with a foreign marker must not be quoted in the active currency. */
fun photoPrices(blocks: List<OcrBlock>, spend: Currency, bounds: Box?): List<PhotoPrice> =
    blocks.flatMap { block ->
        val explicit = block.lines.map { line -> ReceiptScanner.amounts(listOf(line.text)).map { it.currency }.toSet() }
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
