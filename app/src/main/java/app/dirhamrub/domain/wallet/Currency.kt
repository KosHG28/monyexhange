package app.dirhamrub.domain.wallet

/**
 * Валюты приложения: итоговая (домашняя), валюты карт и валюты трат.
 * [symbol] — для сумм («3 120 ₽»), [shortRu] — подпись строки («Дирхамы»), [flag] — эмодзи флага.
 */
enum class Currency(val symbol: String, val nameRu: String, val shortRu: String, val flag: String) {
    RUB("₽", "Российский рубль", "Рубли", "🇷🇺"),
    AED("AED", "Дирхам ОАЭ", "Дирхамы", "🇦🇪"),
    TRY("₺", "Турецкая лира", "Лиры", "🇹🇷"),
    THB("฿", "Тайский бат", "Баты", "🇹🇭"),
    USD("$", "Доллар США", "Доллары", "🇺🇸"),
    EUR("€", "Евро", "Евро", "🇪🇺"),
    CNY("¥", "Китайский юань", "Юани", "🇨🇳"),
    KZT("₸", "Казахстанский тенге", "Тенге", "🇰🇿"),
    GEL("₾", "Грузинский лари", "Лари", "🇬🇪"),
    AMD("֏", "Армянский драм", "Драмы", "🇦🇲"),
    VND("₫", "Вьетнамский донг", "Донги", "🇻🇳"),
    BYN("Br", "Белорусский рубль", "Бел. рубли", "🇧🇾"),
    UZS("сум", "Узбекский сум", "Сумы", "🇺🇿"),
    KGS("сом", "Киргизский сом", "Сомы", "🇰🇬");

    val code: String get() = name

    companion object {
        /** Валюты, в которых тратят в поездке (выбор в шапке). */
        val SPEND: List<Currency> = listOf(AED, TRY, THB, USD, EUR, CNY, KZT, GEL, AMD, VND)

        fun byCode(code: String?): Currency? = entries.firstOrNull { it.name == code }
    }
}
