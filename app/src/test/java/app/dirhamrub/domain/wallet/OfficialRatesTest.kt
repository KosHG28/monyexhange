package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OfficialRatesTest {
    private val xml = """<?xml version="1.0" encoding="windows-1251"?>
<ValCurs Date="29.10.2026" name="Foreign Currency Market">
<Valute ID="R01235"><NumCode>840</NumCode><CharCode>USD</CharCode><Nominal>1</Nominal><Name>Доллар США</Name><Value>81,5000</Value><VunitRate>81,5</VunitRate></Valute>
<Valute ID="R01335"><NumCode>398</NumCode><CharCode>KZT</CharCode><Nominal>100</Nominal><Name>Тенге</Name><Value>15,9000</Value><VunitRate>0,159</VunitRate></Valute>
<Valute ID="R01230"><NumCode>784</NumCode><CharCode>AED</CharCode><Nominal>1</Nominal><Name>Дирхам ОАЭ</Name><Value>22,1900</Value><VunitRate>22,19</VunitRate></Valute>
<Valute ID="R01150"><NumCode>704</NumCode><CharCode>VND</CharCode><Nominal>10000</Nominal><Name>Донгов</Name><Value>31,2000</Value><VunitRate>0,00312</VunitRate></Valute>
<Valute ID="R01999"><NumCode>999</NumCode><CharCode>XXX</CharCode><Nominal>1</Nominal><Name>Неизвестная</Name><Value>1,0</Value></Valute>
</ValCurs>"""

    private val json = """{
    "Date": "2026-10-29T11:30:00+03:00",
    "Valute": {
        "USD": {"ID": "R01235", "NumCode": "840", "CharCode": "USD", "Nominal": 1, "Name": "Доллар США", "Value": 81.5, "Previous": 81.2},
        "AMD": {"ID": "R01060", "NumCode": "051", "CharCode": "AMD", "Nominal": 100, "Name": "Армянских драмов", "Value": 21.0, "Previous": 21.1}
    }
}"""

    @Test
    fun `разбор XML ЦБ`() {
        val r = CbrParser.parseXml(xml)!!
        assertEquals(LocalDate.of(2026, 10, 29), r.date)
        assertEquals("81,5", Numbers.rate(r.rubPerUnit.getValue(Currency.USD)))
        assertEquals("0,159", Numbers.rate(r.rubPerUnit.getValue(Currency.KZT)))
        assertEquals("0,0031", Numbers.rate(r.rubPerUnit.getValue(Currency.VND)))
        assertEquals(4, r.rubPerUnit.size)
    }

    @Test
    fun `разбор JSON-зеркала`() {
        val r = CbrParser.parseJson(json)!!
        assertEquals(LocalDate.of(2026, 10, 29), r.date)
        assertEquals("0,21", Numbers.rate(r.rubPerUnit.getValue(Currency.AMD)))
        assertEquals("81,5", Numbers.rate(r.rubPerUnit.getValue(Currency.USD)))
    }

    @Test
    fun `кросс-курс`() {
        val r = CbrParser.parseXml(xml)!!
        // 1 AED в тенге: 22,19 / 0,159
        assertEquals("139,5597", Numbers.rate(r.cross(Currency.AED, Currency.KZT)!!))
        assertEquals("1", Numbers.rate(r.cross(Currency.RUB, Currency.RUB)!!))
        assertEquals("22,19", Numbers.rate(r.cross(Currency.AED, Currency.RUB)!!))
        assertNull(r.cross(Currency.THB, Currency.RUB))
    }

    @Test
    fun `мусор не разбирается`() {
        assertNull(CbrParser.parseXml("<html>error</html>"))
        assertNull(CbrParser.parseJson("{}"))
    }
}
