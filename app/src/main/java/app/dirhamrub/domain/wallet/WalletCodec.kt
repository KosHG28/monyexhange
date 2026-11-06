package app.dirhamrub.domain.wallet

import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Properties

/**
 * Сохранение кошелька и курсов ЦБ в текст (формат java.util.Properties):
 * без сторонних библиотек, одинаково на Android и на JVM в тестах.
 * Числа — строками BigDecimal, даты — ISO. Испорченные записи пропускаются.
 */
object WalletCodec {

    fun encode(wallet: Wallet): String {
        val p = Properties()
        p["home"] = wallet.home.code
        p["spend"] = wallet.spend.code
        wallet.selectedId?.let { p["selected"] = it }
        p["cards"] = wallet.cards.size.toString()
        wallet.cards.forEachIndexed { i, c ->
            val k = "card.$i."
            p[k + "id"] = c.id
            p[k + "name"] = c.name
            p[k + "currency"] = c.currency.code
            p[k + "style"] = c.style.toString()
            p[k + "kind"] = c.kind.name
            c.transfer?.let { t ->
                p[k + "transfer.sent"] = t.sent.toPlainString()
                p[k + "transfer.sentCurrency"] = t.sentCurrency.code
                p[k + "transfer.got"] = t.got.toPlainString()
                p[k + "transfer.date"] = t.date.toString()
            }
            p[k + "purchases"] = c.purchases.size.toString()
            c.purchases.forEachIndexed { j, r ->
                val pk = k + "purchase.$j."
                p[pk + "spend"] = r.spend.code
                p[pk + "paid"] = r.paid.toPlainString()
                p[pk + "price"] = r.price.toPlainString()
                p[pk + "date"] = r.date.toString()
            }
            p[k + "manual"] = c.manual.size.toString()
            c.manual.forEachIndexed { j, m ->
                val mk = k + "manual.$j."
                p[mk + "spend"] = m.spend.code
                p[mk + "home"] = m.home.code
                p[mk + "rate"] = m.homePerUnit.toPlainString()
                p[mk + "date"] = m.date.toString()
            }
        }
        return store(p)
    }

    fun decode(text: String?): Wallet? {
        if (text.isNullOrBlank()) return null
        val p = load(text) ?: return null
        val cards = (0 until (p.int("cards") ?: 0)).mapNotNull { i -> card(p, "card.$i.") }
        return Wallet(
            cards = cards,
            home = Currency.byCode(p.getProperty("home")) ?: Currency.RUB,
            spend = Currency.byCode(p.getProperty("spend"))?.takeIf { it in Currency.SPEND } ?: Currency.AED,
            selectedId = p.getProperty("selected")?.takeIf { id -> cards.any { it.id == id } } ?: cards.firstOrNull()?.id,
        )
    }

    fun encodeRates(rates: OfficialRates): String {
        val p = Properties()
        p["date"] = rates.date.toString()
        for ((c, v) in rates.rubPerUnit) p["rub." + c.code] = v.toPlainString()
        return store(p)
    }

    fun decodeRates(text: String?): OfficialRates? {
        if (text.isNullOrBlank()) return null
        val p = load(text) ?: return null
        val date = p.date("date") ?: return null
        val map = HashMap<Currency, BigDecimal>()
        for (name in p.stringPropertyNames()) {
            if (!name.startsWith("rub.")) continue
            val c = Currency.byCode(name.removePrefix("rub.")) ?: continue
            p.decimal(name)?.let { map[c] = it }
        }
        return if (map.isEmpty()) null else OfficialRates(date, map)
    }

    private fun card(p: Properties, k: String): BankCard? {
        val id = p.getProperty(k + "id") ?: return null
        val currency = Currency.byCode(p.getProperty(k + "currency")) ?: return null
        val transfer = run {
            val sent = p.decimal(k + "transfer.sent") ?: return@run null
            val got = p.decimal(k + "transfer.got") ?: return@run null
            TransferReceipt(
                sent = sent,
                sentCurrency = Currency.byCode(p.getProperty(k + "transfer.sentCurrency")) ?: Currency.RUB,
                got = got,
                date = p.date(k + "transfer.date") ?: LocalDate.now(),
            )
        }
        val purchases = (0 until (p.int(k + "purchases") ?: 0)).mapNotNull { j ->
            val pk = k + "purchase.$j."
            PurchaseReceipt(
                spend = Currency.byCode(p.getProperty(pk + "spend")) ?: return@mapNotNull null,
                paid = p.decimal(pk + "paid") ?: return@mapNotNull null,
                price = p.decimal(pk + "price") ?: return@mapNotNull null,
                date = p.date(pk + "date") ?: LocalDate.now(),
            )
        }
        val manual = (0 until (p.int(k + "manual") ?: 0)).mapNotNull { j ->
            val mk = k + "manual.$j."
            ManualRate(
                spend = Currency.byCode(p.getProperty(mk + "spend")) ?: return@mapNotNull null,
                home = Currency.byCode(p.getProperty(mk + "home")) ?: return@mapNotNull null,
                homePerUnit = p.decimal(mk + "rate") ?: return@mapNotNull null,
                date = p.date(mk + "date") ?: LocalDate.now(),
            )
        }
        return BankCard(
            id = id,
            name = p.getProperty(k + "name").orEmpty(),
            currency = currency,
            style = p.int(k + "style") ?: 0,
            transfer = transfer,
            purchases = purchases,
            manual = manual,
            kind = CardKind.entries.firstOrNull { it.name == p.getProperty(k + "kind") } ?: CardKind.CARD,
        )
    }

    /** Курсы ЦБ на даты чеков: ключ — дата чека, значение — курсы, действовавшие на неё. */
    fun encodeHistory(history: Map<LocalDate, OfficialRates>): String {
        val p = Properties()
        for ((day, rates) in history) {
            p["$day.date"] = rates.date.toString()
            for ((c, v) in rates.rubPerUnit) p["$day.rub.${c.code}"] = v.toPlainString()
        }
        return store(p)
    }

    fun decodeHistory(text: String?): Map<LocalDate, OfficialRates> {
        if (text.isNullOrEmpty()) return emptyMap()
        val p = load(text) ?: return emptyMap()
        val out = HashMap<LocalDate, OfficialRates>()
        for (name in p.stringPropertyNames()) {
            if (!name.endsWith(".date")) continue
            val key = name.removeSuffix(".date")
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val date = p.date(name) ?: continue
            val rates = p.stringPropertyNames().filter { it.startsWith("$key.rub.") }.mapNotNull { n ->
                val c = Currency.byCode(n.removePrefix("$key.rub.")) ?: return@mapNotNull null
                val v = p.decimal(n) ?: return@mapNotNull null
                c to v
            }.toMap()
            if (rates.isNotEmpty()) out[day] = OfficialRates(date, rates)
        }
        return out
    }

    private fun store(p: Properties): String = StringWriter().also { p.store(it, null) }.toString()

    private fun load(text: String): Properties? =
        runCatching { Properties().apply { load(StringReader(text)) } }.getOrNull()

    private fun Properties.int(key: String): Int? = getProperty(key)?.toIntOrNull()
    private fun Properties.decimal(key: String): BigDecimal? = getProperty(key)?.toBigDecimalOrNull()
    private fun Properties.date(key: String): LocalDate? =
        getProperty(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
