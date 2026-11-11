package app.dirhamrub.domain.wallet

import java.time.LocalDate

/** Принимают ли платёжную систему в стране. */
enum class Acceptance { YES, PARTIAL, NO }

/** Строка справочника: заголовок и пояснение. */
data class GuideItem(val title: String, val text: String)

/** Платёжная система в стране: принимают ли и что важно знать. */
data class NetworkNote(val acceptance: Acceptance, val text: String)

/**
 * «Чем платить» в стране [iso]: платёжные системы, наличные, банкоматы, обменники, советы.
 * [updated] — когда сведения обновлялись: они меняются (решения банков, санкции), поэтому дата видна всегда.
 */
data class CountryGuide(
    val iso: String,
    val updated: LocalDate,
    val networks: Map<CardNetwork, NetworkNote>,
    val cash: List<GuideItem> = emptyList(),
    val atm: List<GuideItem> = emptyList(),
    val exchange: List<GuideItem> = emptyList(),
    val tips: List<GuideItem> = emptyList(),
    /** Ходовые купюры местной валюты: номиналы («20 · 50 · 100 · 200») и пояснение, если нужно. */
    val notes: GuideItem? = null,
    /** Ввоз и вывоз денег: местную валюту вывозить нельзя, крупные суммы декларируют. */
    val export: List<GuideItem> = emptyList(),
) {
    /** Давно не обновлялись — показываем пометку. */
    fun isStale(today: LocalDate): Boolean = updated.plusDays(STALE_DAYS).isBefore(today)

    /**
     * Можно ли заплатить картой (или наличными) [card] в стране с валютой [local]: наличные — если они
     * в местной валюте или в долларах/евро (их меняют), карта — по её платёжной системе.
     * null — неизвестно (платёжная система не указана или о ней нет сведений).
     */
    fun verdict(card: BankCard, local: Currency): Acceptance? = when {
        card.kind == CardKind.CASH -> when (card.currency) {
            local -> Acceptance.YES
            Currency.USD, Currency.EUR -> Acceptance.PARTIAL
            else -> null
        }
        else -> card.paymentNetwork?.let { networks[it]?.acceptance }
    }

    companion object {
        const val STALE_DAYS = 90L
    }
}

/** Все страны справочника и версия файла (дата): из двух копий берём более свежую. */
data class CountryGuides(val version: LocalDate, val countries: Map<String, CountryGuide>) {
    /**
     * Справочник для валюты трат [spend]: страна сотовой сети [networkIso], если в ней платят этой валютой,
     * иначе — единственная страна справочника с этой валютой (у доллара и евро их много — тогда ничего).
     */
    fun forSpend(spend: Currency, networkIso: String?): CountryGuide? {
        val iso = networkIso?.uppercase()
        iso?.let { countries[it] }?.takeIf { Countries.byIso(it.iso)?.currency == spend }?.let { return it }
        return countries.values.filter { Countries.byIso(it.iso)?.currency == spend }.singleOrNull()
    }

    /** Более свежая из двух копий (встроенной и скачанной). */
    fun newer(other: CountryGuides?): CountryGuides = if (other != null && other.version.isAfter(version)) other else this
}

/**
 * Разбор файла справочника (`assets/countries.txt`, та же копия скачивается из репозитория):
 * ```
 * version=2026-11-10
 * [AE]
 * updated=2026-11-10
 * unionpay=partial|Принимают во многих магазинах…
 * mir=no|Не принимают
 * cash=Лучше везти доллары|Курс к дирхаму фиксирован…
 * ```
 * Платёжные системы — `unionpay`, `mir`, `visa_ru`, `visa`: «yes|partial|no» и пояснение.
 * Строки разделов — `cash`, `atm`, `exchange`, `tip`: «заголовок|пояснение», ключ можно повторять.
 * Неизвестные ключи и строки с ошибками пропускаются: файл правят руками, старое приложение не должно падать.
 */
object CountryGuideParser {
    fun parse(text: String): CountryGuides? {
        var version: LocalDate? = null
        val result = LinkedHashMap<String, CountryGuide>()
        var current: Builder? = null
        fun flush() {
            current?.build()?.let { result[it.iso] = it }
            current = null
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                flush()
                val iso = line.substring(1, line.length - 1).trim().uppercase()
                current = if (iso.length == 2 && Countries.byIso(iso) != null) Builder(iso) else null
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().lowercase()
            val value = line.substring(eq + 1).trim()
            val b = current
            if (b == null) {
                if (key == "version") version = date(value)
                continue
            }
            b.put(key, value)
        }
        flush()
        val v = version ?: return null
        return CountryGuides(v, result)
    }

    private fun date(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    private fun pair(value: String): Pair<String, String>? {
        val bar = value.indexOf('|')
        val first = (if (bar < 0) value else value.substring(0, bar)).trim()
        val second = if (bar < 0) "" else value.substring(bar + 1).trim()
        return if (first.isEmpty()) null else first to second
    }

    private class Builder(val iso: String) {
        var updated: LocalDate? = null
        val networks = LinkedHashMap<CardNetwork, NetworkNote>()
        val cash = ArrayList<GuideItem>()
        val atm = ArrayList<GuideItem>()
        val exchange = ArrayList<GuideItem>()
        val tips = ArrayList<GuideItem>()
        val export = ArrayList<GuideItem>()
        var notes: GuideItem? = null

        fun put(key: String, value: String) {
            when (key) {
                "updated" -> updated = date(value)
                "unionpay", "unionpay_foreign", "mir", "visa_ru", "visa" -> {
                    val (status, text) = pair(value) ?: return
                    val acceptance = when (status.lowercase()) {
                        "yes" -> Acceptance.YES
                        "partial" -> Acceptance.PARTIAL
                        "no" -> Acceptance.NO
                        else -> return
                    }
                    val network = when (key) {
                        "unionpay" -> CardNetwork.UNIONPAY
                        "unionpay_foreign" -> CardNetwork.UNIONPAY_FOREIGN
                        "mir" -> CardNetwork.MIR
                        "visa_ru" -> CardNetwork.VISA_MC_RU
                        else -> CardNetwork.VISA_MC_FOREIGN
                    }
                    networks[network] = NetworkNote(acceptance, text)
                }
                "notes" -> notes = pair(value)?.let { (title, text) -> GuideItem(title, text) }
                "cash", "atm", "exchange", "tip", "export" -> {
                    val (title, text) = pair(value) ?: return
                    val item = GuideItem(title, text)
                    when (key) {
                        "cash" -> cash += item
                        "atm" -> atm += item
                        "exchange" -> exchange += item
                        "export" -> export += item
                        else -> tips += item
                    }
                }
            }
        }

        fun build(): CountryGuide? {
            val u = updated ?: return null
            return CountryGuide(iso, u, networks.toMap(), cash.toList(), atm.toList(), exchange.toList(), tips.toList(), notes, export.toList())
        }
    }
}
