package app.dirhamrub.domain.wallet

/**
 * Валюты приложения: итоговая (домашняя), валюты карт и валюты трат.
 * [symbol] — для сумм («3 120 ₽»), [flag] — эмодзи флага в списках.
 */
enum class Currency(val symbol: String, val nameRu: String, val flag: String) {
    RUB("₽", "Российский рубль", "🇷🇺"),
    AED("AED", "Дирхам ОАЭ", "🇦🇪"),
    TRY("₺", "Турецкая лира", "🇹🇷"),
    THB("฿", "Тайский бат", "🇹🇭"),
    USD("$", "Доллар США", "🇺🇸"),
    EUR("€", "Евро", "🇪🇺"),
    CNY("¥", "Китайский юань", "🇨🇳"),
    KZT("₸", "Казахстанский тенге", "🇰🇿"),
    GEL("₾", "Грузинский лари", "🇬🇪"),
    AMD("֏", "Армянский драм", "🇦🇲"),
    VND("₫", "Вьетнамский донг", "🇻🇳"),
    BYN("Br", "Белорусский рубль", "🇧🇾"),
    UZS("сум", "Узбекский сум", "🇺🇿"),
    KGS("сом", "Киргизский сом", "🇰🇬");

    val code: String get() = name

    companion object {
        /** Валюты, в которых тратят в поездке (выбор в шапке). */
        val SPEND: List<Currency> = listOf(AED, TRY, THB, USD, EUR, CNY, KZT, GEL, AMD, VND)

        fun byCode(code: String?): Currency? = entries.firstOrNull { it.name == code }
    }
}
