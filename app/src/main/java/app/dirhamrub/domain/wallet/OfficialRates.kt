package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Есть ли у ЦБ курс валюты [c]: по известному списку ([Currency.cbr]) или по загруженным курсам [official]
 * (если ЦБ добавил валюту). Отсутствия в загруженных курсах мало, чтобы сказать «у ЦБ нет»: их могла
 * сохранить старая версия с меньшим справочником, а свежие ещё не скачаны.
 */
fun cbrPublishes(c: Currency, official: OfficialRates? = null): Boolean =
    c == Currency.RUB || c.cbr || (official != null && c in official.rubPerUnit && c !in official.derived)

/**
 * Официальные курсы ЦБ РФ: сколько рублей стоит 1 единица валюты (у рубля — 1).
 * Справочные курсы ([withUnionPay]) дополняют их валютами, которых у ЦБ нет: эти валюты — в [derived].
 */
data class OfficialRates(
    val date: LocalDate,
    val rubPerUnit: Map<Currency, BigDecimal>,
    /** Валюты, курс которых не от ЦБ, а по UnionPay к доллару и курсу ЦБ доллара. */
    val derived: Set<Currency> = emptySet(),
    val previousRubPerUnit: Map<Currency, BigDecimal> = emptyMap(),
) {
    /** Курс пары [from] → [to] взят не только у ЦБ, но и у UnionPay — подписывать «UnionPay». */
    fun viaUnionPay(from: Currency, to: Currency): Boolean = from in derived || to in derived

    /**
     * Справочные курсы: курсы ЦБ и валюты, которых у ЦБ нет, — по курсу UnionPay к доллару, умноженному
     * на курс ЦБ доллара (100 LKR = 0,3014 $ × 81,79 ₽). Валюты, которые ЦБ публикует, берутся только у ЦБ —
     * даже если в сохранённых курсах их пока нет.
     */
    fun withUnionPay(unionPay: UnionPayRates?): OfficialRates {
        unionPay ?: return this
        val usd = rubPerUnit[Currency.USD] ?: return this
        val extra = HashMap<Currency, BigDecimal>()
        for (c in Currency.entries) {
            if (c == Currency.RUB || c.cbr || c in rubPerUnit) continue
            val perUsd = unionPay.rate(c, Currency.USD) ?: continue
            extra[c] = perUsd.multiply(usd, MC)
        }
        if (extra.isEmpty()) return this
        return copy(rubPerUnit = rubPerUnit + extra, derived = derived + extra.keys)
    }

    /** Сколько [to] стоит 1 [from] по курсам ЦБ; null, если какой-то валюты нет. */
    fun cross(from: Currency, to: Currency): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val f = rub(from) ?: return null
        val t = rub(to) ?: return null
        return if (t.signum() == 0) null else f.divide(t, MC)
    }

    internal fun withCachedPrevious(cached: OfficialRates?): OfficialRates {
        val previous = when (cached?.date) {
            date -> cached.previousRubPerUnit
            date.minusDays(1) -> cached.rubPerUnit
            else -> emptyMap()
        }
        return if (previous.isEmpty() || previous.all { (currency, _) -> currency in previousRubPerUnit }) {
            this
        } else copy(previousRubPerUnit = previous + previousRubPerUnit)
    }

    /** A UnionPay reference combines its USD quote with the matching day's CBR rates. */
    fun dailyTrend(from: Currency, to: Currency, unionPay: UnionPayRates?, previousUnionPay: UnionPayRates?): BigDecimal? {
        if (from == to) return null
        val reference = withUnionPay(unionPay)
        if (!reference.viaUnionPay(from, to)) {
            val earlier = OfficialRates(date.minusDays(1), previousRubPerUnit)
            return trendPercent(earlier, from, to)
        }
        if (unionPay == null || previousUnionPay?.date != unionPay.date.minusDays(1)) return null
        val cbrBefore = when {
            date <= previousUnionPay.date -> this
            date == unionPay.date -> OfficialRates(previousUnionPay.date, previousRubPerUnit)
            else -> return null
        }
        val earlier = cbrBefore.withUnionPay(previousUnionPay)
        if (!earlier.viaUnionPay(from, to)) return null
        return reference.copy(date = unionPay.date).trendPercent(earlier, from, to)
    }

    private fun rub(c: Currency): BigDecimal? = if (c == Currency.RUB) BigDecimal.ONE else rubPerUnit[c]

    /**
     * Как изменился кросс-курс [from] → [to] по сравнению с более ранними курсами [earlier], в процентах:
     * +0,4 — валюта трат подорожала на 0,4 %. null — нет курса или курсы не раньше этих.
     */
    fun trendPercent(earlier: OfficialRates?, from: Currency, to: Currency): BigDecimal? {
        if (earlier == null || !earlier.date.isBefore(date)) return null
        val now = cross(from, to) ?: return null
        val before = earlier.cross(from, to) ?: return null
        if (before.signum() == 0) return null
        return now.divide(before, MC).subtract(BigDecimal.ONE, MC).multiply(BigDecimal(100), MC)
    }
}

/**
 * Разбор ответа ЦБ РФ.
 * XML (`cbr.ru/scripts/XML_daily.asp`): `<ValCurs Date="29.10.2026">` и блоки
 * `<Valute>` с `CharCode`, `Nominal`, `Value` (запятая в дробной части).
 * JSON-зеркало (`cbr-xml-daily.ru/daily_json.js`): `"Date"` и `"Valute": {"USD": {"Nominal": 1, "Value": 92.1}}`.
 */
object CbrParser {
    private val xmlDate = Regex("""<ValCurs[^>]*\sDate="(\d{2})\.(\d{2})\.(\d{4})"""")
    private val xmlValute = Regex("""<Valute\b[^>]*>(.*?)</Valute>""", RegexOption.DOT_MATCHES_ALL)
    private val jsonDate = Regex(""""Date"\s*:\s*"(\d{4})-(\d{2})-(\d{2})""")
    private val jsonValute = Regex(""""([A-Z]{3})"\s*:\s*\{([^\{\}]*)\}""")
    private val jsonNominal = jsonField("Nominal")
    private val jsonValue = jsonField("Value")
    private val jsonPrevious = jsonField("Previous")

    private fun jsonField(name: String) = Regex(""""$name"\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
    private fun String?.decimal() = this?.replace(',', '.')?.replace(" ", "")?.toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }

    fun parseXml(xml: String): OfficialRates? {
        val d = xmlDate.find(xml) ?: return null
        val date = LocalDate.of(d.groupValues[3].toInt(), d.groupValues[2].toInt(), d.groupValues[1].toInt())
        val rates = HashMap<Currency, BigDecimal>()
        val previous = HashMap<Currency, BigDecimal>()
        for (m in xmlValute.findAll(xml)) {
            val body = m.groupValues[1]
            val code = tag(body, "CharCode") ?: continue
            val currency = Currency.byCode(code) ?: continue
            val nominal = tag(body, "Nominal").decimal() ?: continue
            val value = tag(body, "Value").decimal() ?: continue
            rates[currency] = value.divide(nominal, MC)
            tag(body, "Previous").decimal()?.let { previous[currency] = it.divide(nominal, MC) }
        }
        return if (rates.isEmpty()) null else OfficialRates(date, rates, previousRubPerUnit = previous)
    }

    fun parseJson(json: String): OfficialRates? {
        val d = jsonDate.find(json) ?: return null
        val date = LocalDate.of(d.groupValues[1].toInt(), d.groupValues[2].toInt(), d.groupValues[3].toInt())
        val rates = HashMap<Currency, BigDecimal>()
        val previous = HashMap<Currency, BigDecimal>()
        for (m in jsonValute.findAll(json)) {
            val currency = Currency.byCode(m.groupValues[1]) ?: continue
            val body = m.groupValues[2]
            val nominal = jsonNominal.find(body)?.groupValues?.get(1).decimal() ?: continue
            val value = jsonValue.find(body)?.groupValues?.get(1).decimal() ?: continue
            rates[currency] = value.divide(nominal, MC)
            jsonPrevious.find(body)?.groupValues?.get(1).decimal()?.let { previous[currency] = it.divide(nominal, MC) }
        }
        return if (rates.isEmpty()) null else OfficialRates(date, rates, previousRubPerUnit = previous)
    }

    private fun tag(body: String, name: String): String? =
        Regex("<$name>\\s*([^<]*?)\\s*</$name>").find(body)?.groupValues?.get(1)
}
