package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CurrencyTest {
    @Test
    fun `валюта трат по стране сети`() {
        assertEquals(Currency.KZT, Currency.forCountry("KZ"))
        assertEquals(Currency.KZT, Currency.forCountry("kz"))
        assertEquals(Currency.AED, Currency.forCountry("AE"))
        assertEquals(Currency.EUR, Currency.forCountry("de"))
        // Болгария перешла на евро 1 января 2026.
        assertEquals(Currency.EUR, Currency.forCountry("BG"))
        assertEquals(Currency.USD, Currency.forCountry("US"))
        assertEquals(Currency.VND, Currency.forCountry(" VN "))
        assertEquals(Currency.IDR, Currency.forCountry("ID"))
        assertEquals(Currency.GBP, Currency.forCountry("GB"))
        assertEquals(Currency.MVR, Currency.forCountry("MV"))
        // В Панаме туристы платят долларами.
        assertEquals(Currency.USD, Currency.forCountry("PA"))
    }

    @Test
    fun `дома и без кода — без подсказки`() {
        assertNull(Currency.forCountry("RU"))
        assertNull(Currency.forCountry(""))
        assertNull(Currency.forCountry(null))
        assertNull(Currency.forCountry("ZZ"))
    }

    @Test
    fun `частые страны — частые валюты трат`() {
        val iso = listOf("AE", "TR", "TH", "US", "FR", "CN", "KZ", "GE", "AM", "VN")
        assertEquals(Currency.POPULAR.toSet(), iso.mapNotNull { Currency.forCountry(it) }.toSet())
    }

    @Test
    fun `справочник — коды по ISO, знаки однозначные`() {
        assertTrue(Currency.entries.size > 140)
        assertTrue(Currency.entries.all { it.code.length == 3 && it.code.all(Char::isUpperCase) })
        assertEquals(Currency.IDR, Currency.byCode("IDR"))
        assertNull(Currency.byCode("XXX"))
        // У валют с общим знаком («$», «Rs», «kr») — код, у остальных знак свой.
        val bySymbol = Currency.entries.groupBy { it.symbol }
        assertTrue(bySymbol.filterValues { it.size > 1 }.isEmpty())
        assertEquals("Rp", Currency.IDR.symbol)
        assertEquals("₹", Currency.INR.symbol)
        assertEquals("£", Currency.GBP.symbol)
        assertEquals("JPY", Currency.JPY.symbol)
        // Нынешние 14 валют — как были.
        assertEquals("AED", Currency.AED.symbol)
        assertEquals("Дирхамы", Currency.AED.shortRu)
    }

    @Test
    fun `знаки после запятой, размер и курс ЦБ`() {
        assertEquals(0, Currency.JPY.digits)
        assertEquals(3, Currency.KWD.digits)
        assertEquals(4, Currency.IDR.magnitude)
        assertEquals(0, Currency.EUR.magnitude)
        assertTrue(Currency.INR.cbr)
        assertFalse(Currency.LKR.cbr)
        assertEquals(NumberStyle.DOT, Currency.IDR.style)
        assertEquals(NumberStyle.INDIAN, Currency.INR.style)
    }

    @Test
    fun `пометка курса ЦБ совпадает с перечнем ЦБ на 06-11-2026`() {
        // XML_daily.asp на 06.11.2026: 54 позиции — эти 53 валюты и СДР (XDR, не наличные).
        val cbr = ("AUD AZN DZD GBP AMD BHD BYN BOB BRL HUF VND HKD GEL DKK AED USD EUR EGP INR IDR IRR KZT CAD QAR " +
            "KGS CNY CUP MDL MNT NGN NZD NOK OMR PLN SAR RON SGD TJS THB BDT TRY TMT UZS UAH CZK SEK CHF ETB RSD ZAR " +
            "KRW JPY MMK").split(' ').toSet()
        assertEquals(53, cbr.size)
        assertEquals(cbr, Currency.entries.filter { it.cbr }.map { it.code }.toSet())
    }

    @Test
    fun `знак не повторяет код другими буквами`() {
        // «ТМТ» кириллицей в списке выглядел как «TMT · TMT».
        val lookalike = mapOf('А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X')
        for (c in Currency.entries) {
            val latin = c.symbol.uppercase().map { lookalike[it] ?: it }.joinToString("")
            assertTrue("${c.code}: ${c.symbol}", latin != c.code || c.symbol == c.code)
        }
        assertEquals("TMT", Currency.TMT.symbol)
    }

    @Test
    fun `у каждой страны есть валюта из справочника и часть света`() {
        assertTrue(Countries.all.size > 200)
        assertEquals(Countries.all.size, Countries.all.map { it.iso }.toSet().size)
        assertEquals("🇮🇩", Countries.byIso("ID")?.flag)
        assertEquals(Region.NEAR, Countries.byIso("KZ")?.region)
        assertEquals(Region.MIDDLE_EAST, Countries.byIso("AE")?.region)
        assertEquals(Region.EUROPE, Countries.byIso("CY")?.region)
    }

    @Test
    fun `поиск страны по названию, курорту, валюте, коду и знаку`() {
        fun iso(q: String) = CurrencySearch.countries(q).map { it.iso }
        assertEquals("ID", iso("Индонезия").first())
        assertTrue("ID" in iso("бали"))
        assertTrue("AE" in iso("дубай"))
        assertTrue("TH" in iso("Пхукет"))
        // «руп» — все страны с рупиями, сначала совпадения по названию страны (их нет).
        val rupee = iso("руп")
        assertTrue(rupee.containsAll(listOf("IN", "ID", "LK", "NP", "PK", "MU", "SC")))
        assertTrue(iso("AED").contains("AE"))
        assertTrue(iso("₹").contains("IN"))
        assertTrue(iso("сша").contains("US"))
        assertTrue(iso("Соединенные").contains("US"))
        // ё и е — одно и то же.
        assertEquals(iso("Соединённые"), iso("Соединенные"))
        assertTrue(iso("   ").isEmpty())
    }

    @Test
    fun `поиск валюты — по названию, коду и стране`() {
        val home = Currency.entries.filter { it == Currency.RUB || it.cbr }
        assertEquals(listOf(Currency.INR), CurrencySearch.currencies("индийская", home))
        assertTrue(Currency.IDR in CurrencySearch.currencies("индонезия", home))
        assertTrue(CurrencySearch.currencies("шри-ланк", home).isEmpty())
        assertEquals(home, CurrencySearch.currencies("", home))
    }

    @Test
    fun `быстрые суммы и шпаргалка от размера валюты`() {
        fun list(vararg v: Long) = v.map { BigDecimal(it) }
        assertEquals(list(5, 10, 22, 50, 100, 200, 500), QuickSums.chips(Currency.AED))
        assertEquals(list(10_000, 20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000), QuickSums.chips(Currency.IDR))
        assertEquals(list(100, 200, 500, 1000, 2000, 5000, 10_000), QuickSums.chips(Currency.LKR))
        assertEquals(list(1, 2, 5, 10, 20, 50, 100), QuickSums.chips(Currency.KWD))
        assertEquals(9, QuickSums.cheat(Currency.MXN).size)
        assertEquals(BigDecimal(10), QuickSums.cheat(Currency.MXN).first())
    }

    @Test
    fun `недавние валюты трат`() {
        var w = Wallet(spend = Currency.AED)
        w = w.withSpend(Currency.IDR)
        assertEquals(listOf(Currency.IDR, Currency.AED), w.recent)
        w = w.withSpend(Currency.AED)
        assertEquals(listOf(Currency.AED, Currency.IDR), w.recent)
        repeat(10) { w = w.withSpend(Currency.entries[20 + it]) }
        assertEquals(Wallet.RECENT, w.recent.size)
        val back = WalletCodec.decode(WalletCodec.encode(w))!!
        assertEquals(w.recent, back.recent)
        assertEquals(w.spend, back.spend)
    }
}
