#!/usr/bin/env python3
"""
Генератор справочника валют и стран для «Курс по чеку».

Источник — открытые данные Unicode CLDR (npm-пакеты cldr-core, cldr-numbers-full, cldr-localenames-full):
русские названия валют и стран, знаки валют, число знаков после запятой, какая валюта где ходит,
разделитель тысяч в стране. Сами данные в репозиторий не кладутся — только результат.

Запуск (из корня репозитория):
    npm pack cldr-core cldr-numbers-full cldr-localenames-full   # в отдельной папке, затем распаковать
    python3 tools/currencies/gen.py <папка, где лежат распакованные cldr-*/package>

Пишет app/src/main/java/app/dirhamrub/domain/wallet/Currency.kt и Countries.kt — их руками не правят.
"""
import json
import math
import os
import sys

ROOT = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/java/app/dirhamrub/domain/wallet")


def load(pkg, path):
    with open(os.path.join(ROOT, pkg, "package", path), encoding="utf-8") as f:
        return json.load(f)


ru_cur = load("cldr-numbers-full", "main/ru/currencies.json")["main"]["ru"]["numbers"]["currencies"]
cur_data = load("cldr-core", "supplemental/currencyData.json")["supplemental"]["currencyData"]
territories = load("cldr-localenames-full", "main/ru/territories.json")["main"]["ru"]["localeDisplayNames"]["territories"]
containment = load("cldr-core", "supplemental/territoryContainment.json")["supplemental"]["territoryContainment"]
likely = load("cldr-core", "supplemental/likelySubtags.json")["supplemental"]["likelySubtags"]
info = load("cldr-core", "supplemental/territoryInfo.json")["supplemental"]["territoryInfo"]

# Нынешние 14 валют — как были в приложении (знаки и подписи подобраны вручную).
KEEP = {
    "RUB": ("₽", "Российский рубль", "Рубли", "🇷🇺"),
    "AED": ("AED", "Дирхам ОАЭ", "Дирхамы", "🇦🇪"),
    "TRY": ("₺", "Турецкая лира", "Лиры", "🇹🇷"),
    "THB": ("฿", "Тайский бат", "Баты", "🇹🇭"),
    "USD": ("$", "Доллар США", "Доллары", "🇺🇸"),
    "EUR": ("€", "Евро", "Евро", "🇪🇺"),
    "CNY": ("¥", "Китайский юань", "Юани", "🇨🇳"),
    "KZT": ("₸", "Казахстанский тенге", "Тенге", "🇰🇿"),
    "GEL": ("₾", "Грузинский лари", "Лари", "🇬🇪"),
    "AMD": ("֏", "Армянский драм", "Драмы", "🇦🇲"),
    "VND": ("₫", "Вьетнамский донг", "Донги", "🇻🇳"),
    "BYN": ("Br", "Белорусский рубль", "Бел. рубли", "🇧🇾"),
    "UZS": ("сум", "Узбекский сум", "Сумы", "🇺🇿"),
    "KGS": ("сом", "Киргизский сом", "Сомы", "🇰🇬"),
}

# Валюты, которые публикует ЦБ РФ: сверено с XML_daily.asp на 06.11.2026 — 54 позиции, это 53 валюты
# ниже и СДР (XDR, не наличные). Точный список приложение берёт из ответа ЦБ; этот нужен только
# до первой загрузки курсов (пометка «без ЦБ», выбор итоговой валюты).
CBR = set("""AUD AZN DZD GBP AMD BHD BYN BOB BRL HUF VND HKD GEL DKK AED USD EUR EGP INR IDR IRR KZT CAD QAR
KGS CNY CUP MDL MNT NGN NZD NOK OMR PLN SAR RON SGD TJS THB BDT TRY TMT UZS UAH CZK SEK CHF RSD ZAR ETB KRW
JPY MMK""".split())

# Примерно сколько единиц валюты за 1 доллар — только порядок величины: быстрые суммы, шпаргалка,
# правдоподобный диапазон цен на ценниках. Точность не важна, важна степень десяти.
PER_USD = {
    "AED": 3.67, "AFN": 70, "ALL": 92, "AMD": 390, "ANG": 1.79, "AOA": 910, "ARS": 1300, "AUD": 1.5, "AWG": 1.79,
    "AZN": 1.7, "BAM": 1.7, "BBD": 2, "BDT": 120, "BHD": 0.38, "BIF": 2900, "BMD": 1, "BND": 1.3, "BOB": 6.9,
    "BRL": 5.5, "BSD": 1, "BTN": 85, "BWP": 13.5, "BYN": 3.2, "BZD": 2, "CAD": 1.38, "CDF": 2800, "CHF": 0.8,
    "CLP": 950, "CNY": 7.2, "COP": 4000, "CRC": 510, "CUP": 24, "CVE": 95, "CZK": 22, "DJF": 178, "DKK": 6.5,
    "DOP": 60, "DZD": 132, "EGP": 49, "ERN": 15, "ETB": 130, "EUR": 0.88, "FJD": 2.25, "FKP": 0.75, "GBP": 0.75,
    "GEL": 2.7, "GHS": 12, "GIP": 0.75, "GMD": 72, "GNF": 8600, "GTQ": 7.7, "GYD": 210, "HKD": 7.8, "HNL": 26,
    "HTG": 131, "HUF": 360, "IDR": 16300, "ILS": 3.5, "INR": 87, "IQD": 1310, "IRR": 42000, "ISK": 125, "JMD": 160,
    "JOD": 0.71, "JPY": 150, "KES": 129, "KGS": 87, "KHR": 4000, "KMF": 430, "KPW": 900, "KRW": 1400, "KWD": 0.31,
    "KYD": 0.83, "KZT": 520, "LAK": 21600, "LBP": 89500, "LKR": 300, "LRD": 200, "LSL": 18, "LYD": 5.4, "MAD": 9,
    "MDL": 17, "MGA": 4500, "MKD": 54, "MMK": 2100, "MNT": 3600, "MOP": 8, "MRU": 40, "MUR": 46, "MVR": 15.4,
    "MWK": 1735, "MXN": 18.5, "MYR": 4.2, "MZN": 64, "NAD": 18, "NGN": 1530, "NIO": 36.7, "NOK": 10, "NPR": 140,
    "NZD": 1.7, "OMR": 0.385, "PAB": 1, "PEN": 3.5, "PGK": 4.1, "PHP": 57, "PKR": 282, "PLN": 3.7, "PYG": 7500,
    "QAR": 3.64, "RON": 4.4, "RSD": 103, "RUB": 80, "RWF": 1450, "SAR": 3.75, "SBD": 8.3, "SCR": 14.5, "SDG": 600,
    "SEK": 9.6, "SGD": 1.29, "SHP": 0.75, "SLE": 23, "SOS": 571, "SRD": 38, "SSP": 4500, "STN": 21.5, "SYP": 13000,
    "SZL": 18, "THB": 32.5, "TJS": 9.4, "TMT": 3.5, "TND": 2.95, "TOP": 2.4, "TRY": 41, "TTD": 6.8, "TWD": 30,
    "TZS": 2500, "UAH": 41, "UGX": 3600, "USD": 1, "UYU": 40, "UZS": 12500, "VES": 140, "VND": 26300, "VUV": 120,
    "WST": 2.75, "XAF": 575, "XCD": 2.7, "XOF": 575, "XPF": 105, "YER": 240, "ZAR": 18, "ZMW": 23, "ZWG": 27,
}

# Страна → валюта, если в стране ходят две: туристы платят этой.
PRIMARY = {"PA": "USD", "ZW": "USD", "BT": "BTN", "LS": "LSL", "NA": "NAD", "SZ": "SZL", "CU": "CUP", "SV": "USD",
           "TL": "USD", "EC": "USD", "KH": "KHR", "LR": "LRD", "HT": "HTG", "PS": "ILS"}
# Не показывать как страну трат: код без страны, Антарктида и т. п.
SKIP_REGIONS = {"EU", "EZ", "UN", "ZZ", "QO", "AQ", "BV", "HM", "IO", "TF", "UM", "CP", "DG", "EA", "IC", "TA", "AC", "CQ", "XA", "XB", "XK"}

# Названия, под которыми страну знают туристы (в поиске находятся и официальные).
NAMES = {"US": "США", "KR": "Южная Корея", "KP": "Северная Корея", "CG": "Конго", "CD": "ДР Конго"}

NEAR = {"RU", "BY", "UA", "MD", "AM", "AZ", "GE", "KZ", "KG", "TJ", "TM", "UZ"}


def members(code):
    """Все страны (двухбуквенные коды) внутри региона CLDR."""
    out = []
    for c in containment.get(code, {}).get("_contains", []):
        if len(c) == 2 and c.isalpha():
            out.append(c)
        else:
            out += members(c)
    return out


CONT = {}
for code, region in [("002", "AFRICA"), ("019", "AMERICA"), ("142", "ASIA"), ("150", "EUROPE"), ("009", "OCEANIA")]:
    for t in members(code):
        CONT[t] = region
for t in members("145"):
    CONT[t] = "MIDDLE_EAST"
CONT["CY"] = "EUROPE"
for t in NEAR:
    CONT[t] = "NEAR"


def current(region):
    """Нынешние законные валюты страны в порядке CLDR."""
    out = []
    for item in cur_data["region"].get(region, []):
        (code, meta), = item.items()
        if "_to" in meta or meta.get("_tender") == "false":
            continue
        out.append(code)
    return out


countries = []
used = set()
for region in sorted(cur_data["region"]):
    if len(region) != 2 or not region.isalpha() or region in SKIP_REGIONS or region not in CONT:
        continue
    codes = current(region)
    if not codes:
        continue
    code = PRIMARY.get(region, codes[0])
    if code not in PER_USD:
        continue
    full = territories.get(region)
    short = territories.get(region + "-alt-short")
    if not full:
        continue
    # Короткое имя — если полное со скобками или длинное («Гонконг (САР)» → «Гонконг»); другое — для поиска.
    name = NAMES.get(region) or (short if short and ("(" in full or len(full) > 24) else full)
    alias = short if name == full else full
    countries.append((region, name, code, CONT[region], alias if alias and alias != name else ""))
    used.add(code)
used |= set(KEEP)


def flag(region):
    return "".join(chr(0x1F1E6 + ord(ch) - ord("A")) for ch in region)


def population(region):
    try:
        return float(info.get(region, {}).get("_population", "0"))
    except ValueError:
        return 0


def main_region(code):
    if code == "EUR":
        return "EU"
    users = [r for r, _, c, _, _ in countries if c == code]
    if code[:2] in users:
        return code[:2]
    return max(users, key=population) if users else None


def style(code):
    """Как в стране пишут числа: 1,234.56 (COMMA), 1.234,56 (DOT), 1 234,56 (SPACE), 1,23,456 (INDIAN)."""
    region = main_region(code)
    if not region or region == "EU":
        return "DOT"
    tag = likely.get("und-" + region, "")
    lang = tag.split("-")[0]
    for loc in (lang + "-" + region, lang):
        p = os.path.join(ROOT, "cldr-numbers-full", "package", "main", loc, "numbers.json")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                n = json.load(f)["main"][loc]["numbers"]
            sym = n.get("symbols-numberSystem-latn", {})
            pattern = n.get("decimalFormats-numberSystem-latn", {}).get("standard", "")
            if "#,##,##0" in pattern:
                return "INDIAN"
            g = sym.get("group", ",")
            if g == ".":
                return "DOT"
            if g in (" ", " ", " ", "’", "'"):
                return "SPACE"
            return "COMMA"
    return "COMMA"


def cap(s):
    return s[:1].upper() + s[1:]


LOOKALIKE = str.maketrans("АВЕКМНОРСТХ", "ABEKMHOPCTX")
rows = []
for code in sorted(used, key=lambda c: (c not in KEEP, list(KEEP).index(c) if c in KEEP else 0, c)):
    meta = ru_cur.get(code, {})
    if code in KEEP:
        symbol, name, short, fl = KEEP[code]
    else:
        name = cap(meta.get("displayName", code))
        short = name
        symbol = meta.get("symbol") if meta.get("symbol") and meta.get("symbol") != code else meta.get("symbol-alt-narrow", code)
        symbol = (symbol or code).replace(" ", " ")
        # «ТМТ» кириллицей выглядит как код TMT — показываем код, иначе в списке «TMT · ТМТ».
        if symbol.upper().translate(LOOKALIKE) == code:
            symbol = code
        r = main_region(code)
        fl = flag(r) if r else "🌍"
    digits = int(cur_data["fractions"].get(code, cur_data["fractions"]["DEFAULT"])["_digits"])
    mag = max(0, int(math.floor(math.log10(PER_USD[code] * 1.0001))))
    rows.append([code, symbol, name, short, fl, digits, mag, code in CBR, style(code)])

# Знак должен однозначно указывать на валюту: совпадающие знаки новых валют («Rs», «$», «kr») — кодом,
# кроме главной валюты знака.
OWNERS = {"£": "GBP", "₩": "KRW"}
seen = {}
for r in rows:
    seen.setdefault(r[1], []).append(r[0])
for r in rows:
    if r[0] in KEEP or OWNERS.get(r[1]) == r[0]:
        continue
    if len(seen[r[1]]) > 1 or r[1] in {k[0] for k in KEEP.values()}:
        r[1] = r[0]


def kt(s):
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")


with open(os.path.join(OUT, "Currency.kt"), "w", encoding="utf-8") as f:
    f.write("""package app.dirhamrub.domain.wallet

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
""")
    for i, (code, symbol, name, short, fl, digits, mag, cbr, st) in enumerate(rows):
        end = ";" if i == len(rows) - 1 else ","
        f.write(f'    {code}("{kt(symbol)}", "{kt(name)}", "{kt(short)}", "{fl}", {digits}, {mag}, {str(cbr).lower()}, NumberStyle.{st}){end}\n')
    f.write("""
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
""")

with open(os.path.join(OUT, "Countries.kt"), "w", encoding="utf-8") as f:
    f.write("""package app.dirhamrub.domain.wallet

// Сгенерировано tools/currencies/gen.py из данных Unicode CLDR — руками не править.

/** Часть света для списка стран в выборе валюты — в порядке показа. */
enum class Region(val title: String) {
    NEAR("Ближнее зарубежье"),
    MIDDLE_EAST("Ближний Восток"),
    ASIA("Азия"),
    EUROPE("Европа"),
    AMERICA("Америка"),
    AFRICA("Африка"),
    OCEANIA("Океания"),
}

/**
 * Страна: код ISO 3166-1, русское название, валюта, которой там платят, часть света и другое название
 * для поиска («США» — «Соединенные Штаты»).
 */
data class Country(val iso: String, val nameRu: String, val currency: Currency, val region: Region, val alias: String = "") {
    val flag: String get() = iso.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

object Countries {
    val all: List<Country> = listOf(
""")
    for region, name, code, cont, alias in sorted(countries, key=lambda x: x[1]):
        extra = f', "{kt(alias)}"' if alias else ""
        f.write(f'        Country("{region}", "{kt(name)}", Currency.{code}, Region.{cont}{extra}),\n')
    f.write("""    )

    private val byIso = all.associateBy { it.iso }

    fun byIso(iso: String): Country? = byIso[iso]
}
""")
print(len(rows), "валют,", len(countries), "стран")
