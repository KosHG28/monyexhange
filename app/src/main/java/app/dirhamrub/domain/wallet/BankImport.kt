package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate

enum class BankOperation { PURCHASE, TRANSFER, REFUND, UNKNOWN }

data class BankImportCandidate(
    val operation: BankOperation,
    val counterpart: Money?,
    val onCard: Money?,
    val date: LocalDate?,
    val sourceLines: IntRange,
    val verifiedTotal: Boolean = false,
)

/** Never combine amounts across detected operations or choose a maximum from ambiguous candidates. */
object BankImport {
    private val purchase = Regex("(?iuU)^\\s*(?:покупка|оплата|purchase|payment)\\b")
    private val transfer = Regex("(?iuU)^\\s*(?:пополнение|перевод|обмен|top[ -]?up|transfer|exchange)\\b")
    private val refund = Regex("(?iuU)^\\s*(?:возврат|отмена|refund|reversal)\\b")
    private val total = Regex("(?iuU)^\\s*(?:итого|всего списано|total)\\b")
    private val debit = Regex("(?iuU)^\\s*(?:отправлено|отправили|списано|списали|sent|debited)\\b")
    private val credit = Regex("(?iuU)^\\s*(?:зачислено|получено|пришло|received|credited)\\b")
    private val fee = Regex("(?iuU)^\\s*(?:комиссия|fee)\\b")
    private val datePattern = Regex("""\b(\d{1,2})[./](\d{1,2})[./](\d{4})\b|\b(\d{4})-(\d{2})-(\d{2})\b""")

    private fun operation(text: String): BankOperation = when {
        refund.containsMatchIn(text) -> BankOperation.REFUND
        purchase.containsMatchIn(text) -> BankOperation.PURCHASE
        transfer.containsMatchIn(text) -> BankOperation.TRANSFER
        else -> BankOperation.UNKNOWN
    }

    fun candidates(lines: List<String>, card: Currency): List<BankImportCandidate> {
        val groups = mutableListOf<IntRange>()
        var first = 0
        for (index in lines.indices) {
            if (index > first && operation(lines[index]) != BankOperation.UNKNOWN) {
                groups += first until index
                first = index
            }
        }
        if (first < lines.size) groups += first..lines.lastIndex
        return groups.mapNotNull { range -> candidate(lines, range, card) }
    }

    private fun candidate(lines: List<String>, range: IntRange, card: Currency): BankImportCandidate? {
        val text = range.map(lines::get)
        val rows = text.map { line -> line to ReceiptScanner.amounts(listOf(line)) }
        val relevant = rows.filterNot { fee.containsMatchIn(it.first) }
        val all = relevant.flatMap { it.second }
        if (all.isEmpty()) return null
        fun unique(items: List<Money>): Money? = items.distinctBy { it.currency to it.amount.stripTrailingZeros() }.singleOrNull()
        fun marked(pattern: Regex, currency: (Currency) -> Boolean) =
            unique(relevant.filter { pattern.containsMatchIn(it.first) }.flatMap { it.second }.filter { currency(it.currency) })
        val kind = text.firstNotNullOfOrNull { operation(it).takeUnless { type -> type == BankOperation.UNKNOWN } }
            ?: BankOperation.UNKNOWN
        val exactTotal = marked(total) { it != card }
        val sent = marked(debit) { it != card }
        val sourceFee = sent?.let { base ->
            unique(rows.filter { fee.containsMatchIn(it.first) }.flatMap { it.second }.filter { it.currency == base.currency })
        }
        val other = when (kind) {
            BankOperation.TRANSFER -> exactTotal ?: sent?.let { amount ->
                sourceFee?.let { amount.copy(amount = amount.amount.add(it.amount)) } ?: amount
            }
            else -> null
        } ?: unique(all.filter { it.currency != card })
        val onCard = when (kind) {
            BankOperation.PURCHASE -> marked(debit) { it == card }
            BankOperation.TRANSFER -> marked(credit) { it == card }
            else -> null
        } ?: unique(all.filter { it.currency == card })
        val date = text.firstNotNullOfOrNull { line ->
            val m = datePattern.find(line) ?: return@firstNotNullOfOrNull null
            val parts = m.groupValues
            runCatching {
                if (parts[1].isNotEmpty()) LocalDate.of(parts[3].toInt(), parts[2].toInt(), parts[1].toInt())
                else LocalDate.of(parts[4].toInt(), parts[5].toInt(), parts[6].toInt())
            }.getOrNull()
        }
        return BankImportCandidate(kind, other, onCard, date, range, kind == BankOperation.TRANSFER && (exactTotal != null || sourceFee != null))
    }
}
