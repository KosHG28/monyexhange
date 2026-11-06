package app.dirhamrub.domain.wallet

// Сгенерировано tools/currencies/gen.py из данных Unicode CLDR — руками не править.

/**
 * Валюты приложения (около 160 — все нынешние законные валюты стран): итоговая, валюты карт и трат.
 * [symbol] — для сумм («3 120 ₽»; у валют с общим знаком — код), [shortRu] — подпись строки («Дирхамы»),
 * [flag] — эмодзи флага главной страны валюты, [digits] — знаков после запятой (иена — 0, динар Кувейта — 3),
 * [magnitude] — сколько единиц примерно за доллар, степенью десяти (рупия — 4: десятки тысяч),
 * [cbr] — есть ли курс у ЦБ РФ (по известному списку; точный — в ответе ЦБ), [style] — как в стране пишут числа.
 */
enum class Currency(
    val symbol: String,
    val nameRu: String,
    val shortRu: String,
    val flag: String,
    val digits: Int,
    val magnitude: Int,
    val cbr: Boolean,
    val style: NumberStyle,
) {
    RUB("₽", "Российский рубль", "Рубли", "🇷🇺", 2, 1, false, NumberStyle.SPACE),
    AED("AED", "Дирхам ОАЭ", "Дирхамы", "🇦🇪", 2, 0, true, NumberStyle.COMMA),
    TRY("₺", "Турецкая лира", "Лиры", "🇹🇷", 2, 1, true, NumberStyle.DOT),
    THB("฿", "Тайский бат", "Баты", "🇹🇭", 2, 1, true, NumberStyle.COMMA),
    USD("\$", "Доллар США", "Доллары", "🇺🇸", 2, 0, true, NumberStyle.COMMA),
    EUR("€", "Евро", "Евро", "🇪🇺", 2, 0, true, NumberStyle.DOT),
    CNY("¥", "Китайский юань", "Юани", "🇨🇳", 2, 0, true, NumberStyle.COMMA),
    KZT("₸", "Казахстанский тенге", "Тенге", "🇰🇿", 2, 2, true, NumberStyle.SPACE),
    GEL("₾", "Грузинский лари", "Лари", "🇬🇪", 2, 0, true, NumberStyle.SPACE),
    AMD("֏", "Армянский драм", "Драмы", "🇦🇲", 2, 2, true, NumberStyle.SPACE),
    VND("₫", "Вьетнамский донг", "Донги", "🇻🇳", 0, 4, true, NumberStyle.DOT),
    BYN("Br", "Белорусский рубль", "Бел. рубли", "🇧🇾", 2, 0, true, NumberStyle.SPACE),
    UZS("сум", "Узбекский сум", "Сумы", "🇺🇿", 2, 4, true, NumberStyle.SPACE),
    KGS("сом", "Киргизский сом", "Сомы", "🇰🇬", 2, 1, true, NumberStyle.SPACE),
    AFN("؋", "Афгани", "Афгани", "🇦🇫", 0, 1, false, NumberStyle.COMMA),
    ALL("ALL", "Албанский лек", "Албанский лек", "🇦🇱", 0, 1, false, NumberStyle.SPACE),
    AOA("Kz", "Ангольская кванза", "Ангольская кванза", "🇦🇴", 2, 2, false, NumberStyle.SPACE),
    ARS("ARS", "Аргентинский песо", "Аргентинский песо", "🇦🇷", 2, 3, false, NumberStyle.DOT),
    AUD("A\$", "Австралийский доллар", "Австралийский доллар", "🇦🇺", 2, 0, true, NumberStyle.COMMA),
    AWG("AWG", "Арубанский флорин", "Арубанский флорин", "🇦🇼", 2, 0, false, NumberStyle.DOT),
    AZN("₼", "Азербайджанский манат", "Азербайджанский манат", "🇦🇿", 2, 0, true, NumberStyle.DOT),
    BAM("KM", "Конвертируемая марка Боснии и Герцеговины", "Конвертируемая марка Боснии и Герцеговины", "🇧🇦", 2, 0, false, NumberStyle.DOT),
    BBD("BBD", "Барбадосский доллар", "Барбадосский доллар", "🇧🇧", 2, 0, false, NumberStyle.COMMA),
    BDT("৳", "Бангладешская така", "Бангладешская така", "🇧🇩", 2, 2, true, NumberStyle.INDIAN),
    BHD("BHD", "Бахрейнский динар", "Бахрейнский динар", "🇧🇭", 3, 0, true, NumberStyle.COMMA),
    BIF("BIF", "Бурундийский франк", "Бурундийский франк", "🇧🇮", 0, 3, false, NumberStyle.DOT),
    BMD("BMD", "Бермудский доллар", "Бермудский доллар", "🇧🇲", 2, 0, false, NumberStyle.COMMA),
    BND("BND", "Брунейский доллар", "Брунейский доллар", "🇧🇳", 2, 0, false, NumberStyle.DOT),
    BOB("Bs", "Боливийский боливиано", "Боливийский боливиано", "🇧🇴", 2, 0, true, NumberStyle.DOT),
    BRL("R\$", "Бразильский реал", "Бразильский реал", "🇧🇷", 2, 0, true, NumberStyle.DOT),
    BSD("BSD", "Багамский доллар", "Багамский доллар", "🇧🇸", 2, 0, false, NumberStyle.COMMA),
    BTN("BTN", "Бутанский нгултрум", "Бутанский нгултрум", "🇧🇹", 2, 1, false, NumberStyle.INDIAN),
    BWP("P", "Ботсванская пула", "Ботсванская пула", "🇧🇼", 2, 1, false, NumberStyle.COMMA),
    BZD("BZD", "Белизский доллар", "Белизский доллар", "🇧🇿", 2, 0, false, NumberStyle.COMMA),
    CAD("CA\$", "Канадский доллар", "Канадский доллар", "🇨🇦", 2, 0, true, NumberStyle.COMMA),
    CDF("CDF", "Конголезский франк", "Конголезский франк", "🇨🇩", 2, 3, false, NumberStyle.SPACE),
    CHF("CHF", "Швейцарский франк", "Швейцарский франк", "🇨🇭", 2, 0, true, NumberStyle.SPACE),
    CLP("CLP", "Чилийский песо", "Чилийский песо", "🇨🇱", 0, 2, false, NumberStyle.DOT),
    COP("COP", "Колумбийский песо", "Колумбийский песо", "🇨🇴", 0, 3, false, NumberStyle.DOT),
    CRC("₡", "Костариканский колон", "Костариканский колон", "🇨🇷", 2, 2, false, NumberStyle.SPACE),
    CUP("CUP", "Кубинский песо", "Кубинский песо", "🇨🇺", 2, 1, true, NumberStyle.COMMA),
    CVE("CVE", "Эскудо Кабо-Верде", "Эскудо Кабо-Верде", "🇨🇻", 2, 1, false, NumberStyle.SPACE),
    CZK("Kč", "Чешская крона", "Чешская крона", "🇨🇿", 2, 1, true, NumberStyle.SPACE),
    DJF("DJF", "Франк Джибути", "Франк Джибути", "🇩🇯", 0, 2, false, NumberStyle.SPACE),
    DKK("DKK", "Датская крона", "Датская крона", "🇩🇰", 2, 0, true, NumberStyle.DOT),
    DOP("DOP", "Доминиканский песо", "Доминиканский песо", "🇩🇴", 2, 1, false, NumberStyle.COMMA),
    DZD("DZD", "Алжирский динар", "Алжирский динар", "🇩🇿", 2, 2, true, NumberStyle.DOT),
    EGP("E£", "Египетский фунт", "Египетский фунт", "🇪🇬", 2, 1, true, NumberStyle.COMMA),
    ERN("ERN", "Эритрейская накфа", "Эритрейская накфа", "🇪🇷", 2, 1, false, NumberStyle.COMMA),
    ETB("ETB", "Эфиопский быр", "Эфиопский быр", "🇪🇹", 2, 2, true, NumberStyle.COMMA),
    FJD("FJD", "Доллар Фиджи", "Доллар Фиджи", "🇫🇯", 2, 0, false, NumberStyle.COMMA),
    FKP("FKP", "Фунт Фолклендских островов", "Фунт Фолклендских островов", "🇫🇰", 2, 0, false, NumberStyle.COMMA),
    GBP("£", "Британский фунт стерлингов", "Британский фунт стерлингов", "🇬🇧", 2, 0, true, NumberStyle.COMMA),
    GHS("GH₵", "Ганский седи", "Ганский седи", "🇬🇭", 2, 1, false, NumberStyle.COMMA),
    GIP("GIP", "Гибралтарский фунт", "Гибралтарский фунт", "🇬🇮", 2, 0, false, NumberStyle.COMMA),
    GMD("GMD", "Гамбийский даласи", "Гамбийский даласи", "🇬🇲", 2, 1, false, NumberStyle.COMMA),
    GNF("FG", "Гвинейский франк", "Гвинейский франк", "🇬🇳", 0, 3, false, NumberStyle.SPACE),
    GTQ("Q", "Гватемальский кетсаль", "Гватемальский кетсаль", "🇬🇹", 2, 0, false, NumberStyle.COMMA),
    GYD("GYD", "Гайанский доллар", "Гайанский доллар", "🇬🇾", 2, 2, false, NumberStyle.COMMA),
    HKD("HK\$", "Гонконгский доллар", "Гонконгский доллар", "🇭🇰", 2, 0, true, NumberStyle.COMMA),
    HNL("HNL", "Гондурасская лемпира", "Гондурасская лемпира", "🇭🇳", 2, 1, false, NumberStyle.COMMA),
    HTG("HTG", "Гаитянский гурд", "Гаитянский гурд", "🇭🇹", 2, 2, false, NumberStyle.SPACE),
    HUF("Ft", "Венгерский форинт", "Венгерский форинт", "🇭🇺", 0, 2, true, NumberStyle.SPACE),
    IDR("Rp", "Индонезийская рупия", "Индонезийская рупия", "🇮🇩", 0, 4, true, NumberStyle.DOT),
    ILS("₪", "Новый израильский шекель", "Новый израильский шекель", "🇮🇱", 2, 0, false, NumberStyle.COMMA),
    INR("₹", "Индийская рупия", "Индийская рупия", "🇮🇳", 2, 1, true, NumberStyle.INDIAN),
    IQD("IQD", "Иракский динар", "Иракский динар", "🇮🇶", 0, 3, false, NumberStyle.COMMA),
    IRR("IRR", "Иранский риал", "Иранский риал", "🇮🇷", 0, 4, true, NumberStyle.COMMA),
    ISK("ISK", "Исландская крона", "Исландская крона", "🇮🇸", 0, 2, false, NumberStyle.DOT),
    JMD("JMD", "Ямайский доллар", "Ямайский доллар", "🇯🇲", 2, 2, false, NumberStyle.COMMA),
    JOD("JOD", "Иорданский динар", "Иорданский динар", "🇯🇴", 3, 0, false, NumberStyle.COMMA),
    JPY("JPY", "Японская иена", "Японская иена", "🇯🇵", 0, 2, true, NumberStyle.COMMA),
    KES("KES", "Кенийский шиллинг", "Кенийский шиллинг", "🇰🇪", 2, 2, false, NumberStyle.COMMA),
    KHR("៛", "Камбоджийский риель", "Камбоджийский риель", "🇰🇭", 2, 3, false, NumberStyle.COMMA),
    KMF("CF", "Коморский франк", "Коморский франк", "🇰🇲", 0, 2, false, NumberStyle.COMMA),
    KPW("KPW", "Северокорейская вона", "Северокорейская вона", "🇰🇵", 0, 2, false, NumberStyle.COMMA),
    KRW("₩", "Южнокорейская вона", "Южнокорейская вона", "🇰🇷", 0, 3, true, NumberStyle.COMMA),
    KWD("KWD", "Кувейтский динар", "Кувейтский динар", "🇰🇼", 3, 0, false, NumberStyle.COMMA),
    KYD("KYD", "Доллар Островов Кайман", "Доллар Островов Кайман", "🇰🇾", 2, 0, false, NumberStyle.COMMA),
    LAK("₭", "Лаосский кип", "Лаосский кип", "🇱🇦", 0, 4, false, NumberStyle.DOT),
    LBP("L£", "Ливанский фунт", "Ливанский фунт", "🇱🇧", 0, 4, false, NumberStyle.DOT),
    LKR("LKR", "Шри-ланкийская рупия", "Шри-ланкийская рупия", "🇱🇰", 2, 2, false, NumberStyle.COMMA),
    LRD("LRD", "Либерийский доллар", "Либерийский доллар", "🇱🇷", 2, 2, false, NumberStyle.COMMA),
    LSL("LSL", "Лоти", "Лоти", "🇱🇸", 2, 1, false, NumberStyle.COMMA),
    LYD("LYD", "Ливийский динар", "Ливийский динар", "🇱🇾", 3, 0, false, NumberStyle.DOT),
    MAD("MAD", "Марокканский дирхам", "Марокканский дирхам", "🇲🇦", 2, 0, false, NumberStyle.DOT),
    MDL("MDL", "Молдавский лей", "Молдавский лей", "🇲🇩", 2, 1, true, NumberStyle.DOT),
    MGA("Ar", "Малагасийский ариари", "Малагасийский ариари", "🇲🇬", 0, 3, false, NumberStyle.COMMA),
    MKD("MKD", "Македонский денар", "Македонский денар", "🇲🇰", 2, 1, false, NumberStyle.DOT),
    MMK("K", "Мьянманский кьят", "Мьянманский кьят", "🇲🇲", 0, 3, true, NumberStyle.COMMA),
    MNT("₮", "Монгольский тугрик", "Монгольский тугрик", "🇲🇳", 2, 3, true, NumberStyle.COMMA),
    MOP("MOP", "Патака Макао", "Патака Макао", "🇲🇴", 2, 0, false, NumberStyle.COMMA),
    MRU("MRU", "Мавританская угия", "Мавританская угия", "🇲🇷", 2, 1, false, NumberStyle.DOT),
    MUR("MUR", "Маврикийская рупия", "Маврикийская рупия", "🇲🇺", 2, 1, false, NumberStyle.SPACE),
    MVR("MVR", "Мальдивская руфия", "Мальдивская руфия", "🇲🇻", 2, 1, false, NumberStyle.COMMA),
    MWK("MWK", "Малавийская квача", "Малавийская квача", "🇲🇼", 2, 3, false, NumberStyle.COMMA),
    MXN("MX\$", "Мексиканский песо", "Мексиканский песо", "🇲🇽", 2, 1, false, NumberStyle.COMMA),
    MYR("RM", "Малайзийский ринггит", "Малайзийский ринггит", "🇲🇾", 2, 0, false, NumberStyle.COMMA),
    MZN("MZN", "Мозамбикский метикал", "Мозамбикский метикал", "🇲🇿", 2, 1, false, NumberStyle.SPACE),
    NAD("NAD", "Доллар Намибии", "Доллар Намибии", "🇳🇦", 2, 1, false, NumberStyle.SPACE),
    NGN("₦", "Нигерийская найра", "Нигерийская найра", "🇳🇬", 2, 3, true, NumberStyle.COMMA),
    NIO("C\$", "Никарагуанская кордоба", "Никарагуанская кордоба", "🇳🇮", 2, 1, false, NumberStyle.COMMA),
    NOK("NOK", "Норвежская крона", "Норвежская крона", "🇳🇴", 2, 1, true, NumberStyle.SPACE),
    NPR("NPR", "Непальская рупия", "Непальская рупия", "🇳🇵", 2, 2, false, NumberStyle.INDIAN),
    NZD("NZ\$", "Новозеландский доллар", "Новозеландский доллар", "🇳🇿", 2, 0, true, NumberStyle.COMMA),
    OMR("OMR", "Оманский риал", "Оманский риал", "🇴🇲", 3, 0, true, NumberStyle.COMMA),
    PEN("PEN", "Перуанский соль", "Перуанский соль", "🇵🇪", 2, 0, false, NumberStyle.COMMA),
    PGK("PGK", "Кина Папуа – Новой Гвинеи", "Кина Папуа – Новой Гвинеи", "🇵🇬", 2, 0, false, NumberStyle.COMMA),
    PHP("₱", "Филиппинский песо", "Филиппинский песо", "🇵🇭", 2, 1, false, NumberStyle.COMMA),
    PKR("PKR", "Пакистанская рупия", "Пакистанская рупия", "🇵🇰", 0, 2, false, NumberStyle.COMMA),
    PLN("zł", "Польский злотый", "Польский злотый", "🇵🇱", 2, 0, true, NumberStyle.SPACE),
    PYG("₲", "Парагвайский гуарани", "Парагвайский гуарани", "🇵🇾", 0, 3, false, NumberStyle.COMMA),
    QAR("QAR", "Катарский риал", "Катарский риал", "🇶🇦", 2, 0, true, NumberStyle.COMMA),
    RON("RON", "Румынский лей", "Румынский лей", "🇷🇴", 2, 0, true, NumberStyle.DOT),
    RSD("RSD", "Сербский динар", "Сербский динар", "🇷🇸", 0, 2, true, NumberStyle.DOT),
    RWF("RF", "Франк Руанды", "Франк Руанды", "🇷🇼", 0, 3, false, NumberStyle.DOT),
    SAR("SAR", "Саудовский риял", "Саудовский риял", "🇸🇦", 2, 0, true, NumberStyle.COMMA),
    SBD("SBD", "Доллар Соломоновых Островов", "Доллар Соломоновых Островов", "🇸🇧", 2, 0, false, NumberStyle.COMMA),
    SCR("SCR", "Сейшельская рупия", "Сейшельская рупия", "🇸🇨", 2, 1, false, NumberStyle.SPACE),
    SDG("SDG", "Суданский фунт", "Суданский фунт", "🇸🇩", 2, 2, false, NumberStyle.COMMA),
    SEK("SEK", "Шведская крона", "Шведская крона", "🇸🇪", 2, 0, true, NumberStyle.SPACE),
    SGD("SGD", "Сингапурский доллар", "Сингапурский доллар", "🇸🇬", 2, 0, true, NumberStyle.COMMA),
    SHP("SHP", "Фунт острова Святой Елены", "Фунт острова Святой Елены", "🇸🇭", 2, 0, false, NumberStyle.COMMA),
    SLE("SLE", "Леоне", "Леоне", "🇸🇱", 2, 1, false, NumberStyle.COMMA),
    SOS("SOS", "Сомалийский шиллинг", "Сомалийский шиллинг", "🇸🇴", 0, 2, false, NumberStyle.COMMA),
    SRD("SRD", "Суринамский доллар", "Суринамский доллар", "🇸🇷", 2, 1, false, NumberStyle.DOT),
    SSP("SSP", "Южносуданский фунт", "Южносуданский фунт", "🇸🇸", 2, 3, false, NumberStyle.COMMA),
    STN("Db", "Добра Сан-Томе и Принсипи", "Добра Сан-Томе и Принсипи", "🇸🇹", 2, 1, false, NumberStyle.SPACE),
    SYP("SYP", "Сирийский фунт", "Сирийский фунт", "🇸🇾", 0, 4, false, NumberStyle.COMMA),
    SZL("SZL", "Свазилендский лилангени", "Свазилендский лилангени", "🇸🇿", 2, 1, false, NumberStyle.COMMA),
    TJS("TJS", "Таджикский сомони", "Таджикский сомони", "🇹🇯", 2, 0, true, NumberStyle.SPACE),
    TMT("TMT", "Новый туркменский манат", "Новый туркменский манат", "🇹🇲", 2, 0, true, NumberStyle.SPACE),
    TND("TND", "Тунисский динар", "Тунисский динар", "🇹🇳", 3, 0, false, NumberStyle.DOT),
    TOP("T\$", "Тонганская паанга", "Тонганская паанга", "🇹🇴", 2, 0, false, NumberStyle.COMMA),
    TTD("TTD", "Доллар Тринидада и Тобаго", "Доллар Тринидада и Тобаго", "🇹🇹", 2, 0, false, NumberStyle.COMMA),
    TWD("NT\$", "Новый тайваньский доллар", "Новый тайваньский доллар", "🇹🇼", 2, 1, false, NumberStyle.COMMA),
    TZS("TZS", "Танзанийский шиллинг", "Танзанийский шиллинг", "🇹🇿", 2, 3, false, NumberStyle.COMMA),
    UAH("₴", "Украинская гривна", "Украинская гривна", "🇺🇦", 2, 1, true, NumberStyle.SPACE),
    UGX("UGX", "Угандийский шиллинг", "Угандийский шиллинг", "🇺🇬", 0, 3, false, NumberStyle.COMMA),
    UYU("UYU", "Уругвайский песо", "Уругвайский песо", "🇺🇾", 2, 1, false, NumberStyle.DOT),
    VES("VES", "Венесуэльский боливар", "Венесуэльский боливар", "🇻🇪", 2, 2, false, NumberStyle.DOT),
    VUV("VUV", "Вату Вануату", "Вату Вануату", "🇻🇺", 0, 2, false, NumberStyle.COMMA),
    WST("WST", "Самоанская тала", "Самоанская тала", "🇼🇸", 2, 0, false, NumberStyle.COMMA),
    XAF("FCFA", "Франк КФА BEAC", "Франк КФА BEAC", "🇨🇲", 0, 2, false, NumberStyle.SPACE),
    XCD("EC\$", "Восточно-карибский доллар", "Восточно-карибский доллар", "🇱🇨", 2, 0, false, NumberStyle.COMMA),
    XOF("F CFA", "Франк КФА ВСЕАО", "Франк КФА ВСЕАО", "🇨🇮", 0, 2, false, NumberStyle.SPACE),
    XPF("CFPF", "Французский тихоокеанский франк", "Французский тихоокеанский франк", "🇳🇨", 0, 2, false, NumberStyle.SPACE),
    YER("YER", "Йеменский риал", "Йеменский риал", "🇾🇪", 0, 2, false, NumberStyle.COMMA),
    ZAR("R", "Южноафриканский рэнд", "Южноафриканский рэнд", "🇿🇦", 2, 1, true, NumberStyle.COMMA),
    ZMW("ZK", "Замбийская квача", "Замбийская квача", "🇿🇲", 2, 1, false, NumberStyle.COMMA);

    val code: String get() = name

    companion object {
        private val byCode = entries.associateBy { it.name }

        /** Валюты, в которых тратят в поездке: любые. */
        val SPEND: List<Currency> get() = entries

        /** Самые частые валюты трат — плитками в знакомстве. */
        val POPULAR: List<Currency> = listOf(AED, TRY, THB, USD, EUR, CNY, KZT, GEL, AMD, VND)

        /** Частые валюты карт и пополнений — чипами в редакторе. */
        val FREQUENT: List<Currency> = listOf(RUB, USD, EUR, CNY, KZT, AED, TRY, GEL, AMD, BYN, UZS, KGS)

        fun byCode(code: String?): Currency? = code?.let { byCode[it] }

        /**
         * Валюта трат по коду страны ISO 3166-1 (страна сотовой сети): «KZ» → тенге, «ID» → рупия.
         * null — страна неизвестна или это Россия (дома подсказка «вы в другой стране» не нужна).
         */
        fun forCountry(iso: String?): Currency? {
            val c = iso?.trim()?.uppercase()
            if (c.isNullOrEmpty() || c == "RU") return null
            return Countries.byIso(c)?.currency
        }
    }
}

/** Как в стране пишут числа на ценниках. */
enum class NumberStyle {
    /** 1,234.56 */
    COMMA,

    /** 1.234,56 */
    DOT,

    /** 1 234,56 */
    SPACE,

    /** 1,23,456.78 — индийские разряды */
    INDIAN,
}
