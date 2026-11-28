package app.dirhamrub.domain.wallet

/**
 * Поиск в выборе валюты: по названию страны (и другому её названию), городу, острову или курорту,
 * названию валюты, коду и знаку. «руп» — все рупии, «бали» — Индонезия, «AED» — ОАЭ.
 */
object CurrencySearch {
    /** Курорты, города и острова → страна: по ним ищут чаще, чем по названию страны. */
    private val PLACES: Map<String, String> = mapOf(
        "бали" to "ID", "ломбок" to "ID", "джакарта" to "ID", "ява" to "ID",
        "пхукет" to "TH", "паттайя" to "TH", "самуи" to "TH", "бангкок" to "TH", "краби" to "TH", "чиангмай" to "TH",
        "дубай" to "AE", "абу-даби" to "AE", "шарджа" to "AE", "рас-эль-хайма" to "AE", "фуджейра" to "AE",
        "шарм-эль-шейх" to "EG", "хургада" to "EG", "каир" to "EG", "марса-алам" to "EG",
        "анталья" to "TR", "стамбул" to "TR", "аланья" to "TR", "кемер" to "TR", "бодрум" to "TR", "каппадокия" to "TR",
        "гоа" to "IN", "дели" to "IN", "мумбаи" to "IN", "керала" to "IN",
        "нячанг" to "VN", "фукуок" to "VN", "ханой" to "VN", "хошимин" to "VN", "дананг" to "VN", "фантьет" to "VN",
        "тбилиси" to "GE", "батуми" to "GE", "ереван" to "AM", "баку" to "AZ",
        "алматы" to "KZ", "астана" to "KZ", "ташкент" to "UZ", "самарканд" to "UZ", "бухара" to "UZ", "бишкек" to "KG",
        "иссык-куль" to "KG", "минск" to "BY",
        "пекин" to "CN", "шанхай" to "CN", "хайнань" to "CN", "санья" to "CN", "гуанчжоу" to "CN", "харбин" to "CN",
        "сеул" to "KR", "пусан" to "KR", "токио" to "JP", "киото" to "JP", "осака" to "JP",
        "мале" to "MV", "коломбо" to "LK", "катманду" to "NP",
        "куала-лумпур" to "MY", "лангкави" to "MY", "пенанг" to "MY", "манила" to "PH", "боракай" to "PH", "себу" to "PH",
        "канкун" to "MX", "гавана" to "CU", "варадеро" to "CU", "пунта-кана" to "DO", "занзибар" to "TZ",
        "тенерифе" to "ES", "барселона" to "ES", "мадрид" to "ES", "париж" to "FR", "ницца" to "FR", "рим" to "IT",
        "милан" to "IT", "прага" to "CZ", "будапешт" to "HU", "белград" to "RS", "будва" to "ME", "тиват" to "ME",
        "лондон" to "GB", "нью-йорк" to "US", "майами" to "US", "доха" to "QA", "маскат" to "OM",
        "эр-рияд" to "SA", "тель-авив" to "IL", "иерусалим" to "IL", "эйлат" to "IL", "марракеш" to "MA", "агадир" to "MA",
        "кипр" to "CY", "лимасол" to "CY", "пафос" to "CY", "крит" to "GR", "родос" to "GR", "афины" to "GR",
    )

    private data class CurrencyTerms(val words: List<String>, val code: String, val symbol: String) {
        fun matches(query: String): Boolean = words.any { it.startsWith(query) } || code.startsWith(query) || symbol == query
    }

    private data class CountryTerms(val country: Country, val name: String, val words: List<String>, val alias: List<String>)

    private val currencyTerms by lazy {
        Currency.entries.map { CurrencyTerms(words(it.nameRu), it.code.lowercase(), normalize(it.symbol)) }
    }

    private val countryTerms by lazy {
        Countries.all.sortedBy { it.nameRu }.map { CountryTerms(it, normalize(it.nameRu), words(it.nameRu), words(it.alias)) }
    }

    /** Страны, подходящие под запрос; пустой запрос — ничего. Сначала совпадение по названию страны. */
    fun countries(query: String): List<Country> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return findCountries(q)
    }

    private fun findCountries(q: String): List<Country> {
        val places = HashSet<String>()
        for ((place, iso) in PLACES) {
            if (place.startsWith(q) || q.length >= 4 && place.contains(q)) places += iso
        }
        val names = ArrayList<Country>()
        val aliases = ArrayList<Country>()
        val currencies = ArrayList<Country>()
        for (row in countryTerms) {
            when {
                row.name.startsWith(q) || row.words.any { it.startsWith(q) } -> names += row.country
                row.country.iso in places || row.alias.any { it.startsWith(q) } -> aliases += row.country
                currencyTerms[row.country.currency.ordinal].matches(q) -> currencies += row.country
            }
        }
        names.addAll(aliases)
        names.addAll(currencies)
        return names
    }

    /** Валюты из [among], подходящие под запрос: по названию, коду, знаку или стране, где ими платят. */
    fun currencies(query: String, among: List<Currency>): List<Currency> {
        val q = normalize(query)
        if (q.isEmpty()) return among
        val byCountry = findCountries(q).mapTo(HashSet()) { it.currency }
        val explicit = ArrayList<Currency>()
        val implicit = ArrayList<Currency>()
        for (currency in among) {
            if (currencyTerms[currency.ordinal].matches(q)) explicit += currency
            else if (currency in byCountry) implicit += currency
        }
        explicit.addAll(implicit)
        return explicit
    }

    private fun words(s: String): List<String> = normalize(s).split(' ', '-', ',', '(', ')').filter { it.isNotEmpty() }

    private fun normalize(s: String): String = s.trim().lowercase().replace('ё', 'е')
}
