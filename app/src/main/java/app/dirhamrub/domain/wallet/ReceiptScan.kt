package app.dirhamrub.domain.wallet

import java.math.BigDecimal

/** Сумма с валютой, найденная в тексте. */
data class Money(val amount: BigDecimal, val currency: Currency)

/** Что удалось понять из скриншота банка: чек покупки или пополнения для карты. */
sealed interface ScannedReceipt {
    /** Покупка: с карты списали [paid] (в валюте карты) за [price] в валюте трат [spend]. */
    data class Purchase(val spend: Currency, val paid: BigDecimal, val price: BigDecimal) : ScannedReceipt

    /** Пополнение: отправили [sent] в [sentCurrency], на карту пришло [got]. */
    data class Transfer(val sent: BigDecimal, val sentCurrency: Currency, val got: BigDecimal) : ScannedReceipt
}

/**
 * Разбор текста со скриншота банка (уведомление, выписка, история операций): суммы с валютами
 * и догадка, какой это чек. Текст приходит из OCR построчно; строки с остатком, кэшбэком и т. п. пропускаются.
 */
object ReceiptScanner {

    /**
     * Обозначения валют рядом с суммой: знаки, коды, русские слова и то, во что их превращает
     * латинский OCR («₽» → «P», «руб» → «py6», «₸» → «T»).
     */
    private val MARKERS: List<Pair<Currency, String>> = listOf(
        Currency.RUB to "₽|RUB|RUR|(?iu:руб\\p{L}*|р\\.|р(?!\\p{L})|py6\\p{L}*|pyб\\p{L}*)|P(?!\\p{L})|Р(?!\\p{L})",
        Currency.CNY to "¥|CNY|RMB|元|(?iu:юан\\p{L}*)",
        Currency.KZT to "₸|KZT|(?iu:тенге|тг(?!\\p{L}))|T(?!\\p{L})",
        Currency.AED to "AED|DHS?(?!\\p{L})|(?iu:дирхам\\p{L}*)",
        Currency.USD to "\\$|USD|(?iu:долл\\p{L}*)",
        Currency.EUR to "€|EUR|(?iu:евро)",
        Currency.TRY to "₺|TRY|TL(?!\\p{L})|(?iu:лир\\p{L}*)",
        Currency.THB to "฿|THB|(?iu:бат\\p{L}*)",
        Currency.GEL to "₾|GEL|(?iu:лари)",
        Currency.AMD to "֏|AMD|(?iu:драм\\p{L}*)",
        Currency.VND to "₫|VND|(?iu:донг\\p{L}*)",
        Currency.BYN to "BYN|Br(?!\\p{L})",
        Currency.UZS to "UZS|(?iu:сум(?!\\p{L}))",
        Currency.KGS to "KGS|(?iu:сом(?!\\p{L}))",
    ) + Currency.entries.filter { it.ordinal >= Currency.KGS.ordinal + 1 }.map { c ->
        // Остальные валюты — по коду и по знаку, если он не совпадает с кодом («IDR», «Rp», «₹»).
        // Одиночные буквы («R», «P») не берём: их легко принять за часть текста.
        val sign = c.symbol.takeIf { it != c.code && (it.length > 1 || !it[0].isLetter()) }
            ?.let { "|" + Regex.escape(it) + "(?!\\p{L})" }.orEmpty()
        c to "(?<!\\p{L})${c.code}(?!\\p{L})$sign"
    }
    private val AFTER = MARKERS.map { (c, p) -> c to Regex("^[\\s\\u00A0\\u202F]{0,2}(?:$p)") }
    private val BEFORE = MARKERS.map { (c, p) -> c to Regex("(?:$p)[\\s\\u00A0\\u202F]{0,2}[-−+]?[\\s\\u00A0\\u202F]?$") }

    /** Число с разрядами через пробел, точку или запятую и копейками: «3 881,00», «1,234.56», «304.76». */
    private val NUMBER = Regex("\\d[\\d\\u00A0\\u202F\\u2009 .,']*\\d|\\d")

    /** Строки не про саму операцию. */
    private val SKIP = Regex(
        "(?iu)остат|баланс|доступн|лимит|кэшб|кешб|cashback|бонус|balance|available|limit",
    )

    /** Все суммы с распознанной валютой — в порядке появления. */
    fun amounts(lines: List<String>): List<Money> = lines
        .filterNot { SKIP.containsMatchIn(it) }
        .flatMap { line -> NUMBER.findAll(line).mapNotNull { m -> money(line, m) }.toList() }

    /**
     * Догадка по суммам: нужна сумма в валюте карты [card] и сумма в другой валюте.
     * Порядок: покупка в текущей валюте трат → пополнение из итоговой валюты → покупка в другой валюте трат →
     * пополнение из любой другой. Если сумм одной валюты несколько, берётся наибольшая
     * (итог с комиссией больше суммы без неё).
     */
    fun guess(lines: List<String>, card: Currency, spend: Currency, home: Currency): ScannedReceipt? {
        val found = amounts(lines)
        val byCurrency = found.groupBy { it.currency }.mapValues { (_, list) -> list.maxOf { it.amount } }
        val onCard = byCurrency[card] ?: return null
        val others = found.map { it.currency }.distinct().filter { it != card }
        fun purchase(c: Currency) = ScannedReceipt.Purchase(c, onCard, byCurrency.getValue(c))
        fun transfer(c: Currency) = ScannedReceipt.Transfer(byCurrency.getValue(c), c, onCard)
        return when {
            spend in others && spend != home -> purchase(spend)
            home in others -> transfer(home)
            else -> {
                // Платят в поездке любой валютой, кроме рубля: рубли — это пополнение.
                val other = others.firstOrNull() ?: return null
                if (other != Currency.RUB) purchase(other) else transfer(other)
            }
        }
    }

    private fun money(line: String, m: MatchResult): Money? {
        val after = line.substring(m.range.last + 1)
        val before = line.substring(0, m.range.first)
        // Знак после числа надёжнее («304,76 ¥»), но бывает и до («AED 167.00», «$12»).
        val afterCurrency = AFTER.firstOrNull { (_, r) -> r.containsMatchIn(after) }?.first
        val currency = afterCurrency ?: BEFORE.firstOrNull { (_, r) -> r.containsMatchIn(before) }?.first ?: return null
        val amount = number(m.value, markerAfter = afterCurrency != null) ?: return null
        return if (amount.signum() > 0) Money(amount, currency) else null
    }

    /**
     * «3 881,00» → 3881.00, «1,234.56» → 1234.56, «304.76» → 304.76, «15.000» → 15000.
     * Если разряды через пробел не складываются в одно число («*1234 15 000 ₸»), берётся самый длинный
     * правильный кусок у знака валюты: после числа — хвост («15 000»), до числа — начало.
     */
    internal fun number(raw: String, markerAfter: Boolean = true): BigDecimal? {
        val groups = raw.trim().split(Regex("[\\s\\u00A0\\u202F\\u2009']+")).filter { it.isNotEmpty() }
        if (groups.isEmpty()) return null
        val runs = if (markerAfter) groups.indices.map { groups.subList(it, groups.size) } else groups.indices.reversed().map { groups.subList(0, it + 1) }
        val run = runs.firstOrNull { isGrouped(it) } ?: return null
        return decimal(run.joinToString(""))
    }

    /** Одна группа или «1–3 цифры, дальше группы по три цифры» (у последней могут быть копейки). */
    private fun isGrouped(run: List<String>): Boolean =
        run.size == 1 || run.first().all { it.isDigit() } && run.first().length <= 3 &&
            run.drop(1).all { g -> g.takeWhile { it.isDigit() }.length == 3 }

    private fun decimal(text: String): BigDecimal? {
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        val plain = when {
            lastDot >= 0 && lastComma >= 0 -> {
                // Есть и точка, и запятая: последняя — десятичная, другая — разряды.
                val decimalSep = if (lastDot > lastComma) '.' else ','
                val thousands = if (decimalSep == '.') ',' else '.'
                text.replace(thousands.toString(), "").replace(decimalSep, '.')
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val parts = text.split(sep)
                when {
                    // Один разделитель и 1–2 цифры после — копейки.
                    parts.size == 2 && parts[1].length in 1..2 -> parts[0] + "." + parts[1]
                    // Группы по три — разряды.
                    parts.drop(1).all { it.length == 3 } && parts[0].length in 1..3 -> parts.joinToString("")
                    else -> return null
                }
            }
            else -> text
        }
        if (plain.isEmpty() || !plain.all { it.isDigit() || it == '.' }) return null
        return plain.toBigDecimalOrNull()
    }
}
