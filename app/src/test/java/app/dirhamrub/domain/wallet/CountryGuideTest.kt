package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CountryGuideTest {
    private val sample = """
        # комментарий
        version=2026-11-10
        [AE]
        updated=2026-11-01
        unionpay=partial|Не везде
        mir=no|Нет
        visa=yes
        cash=Доллары|Курс фиксирован
        cash=Евро
        tip=Наличные|На рынках
        unknown=что-то новое
        [XX]
        updated=2026-11-01
        [TR]
        mir=no|без даты — страна пропускается
        [US]
        updated=2026-10-01
        [EC]
        updated=2026-10-01
    """.trimIndent()

    @Test
    fun `разбор файла`() {
        val g = CountryGuideParser.parse(sample)!!
        assertEquals(LocalDate.of(2026, 11, 10), g.version)
        assertEquals(setOf("AE", "US", "EC"), g.countries.keys)
        val ae = g.countries.getValue("AE")
        assertEquals(Acceptance.PARTIAL, ae.networks[CardNetwork.UNIONPAY]?.acceptance)
        assertEquals(Acceptance.NO, ae.networks[CardNetwork.MIR]?.acceptance)
        assertEquals(NetworkNote(Acceptance.YES, ""), ae.networks[CardNetwork.VISA_MC_FOREIGN])
        assertEquals(listOf(GuideItem("Доллары", "Курс фиксирован"), GuideItem("Евро", "")), ae.cash)
        assertEquals(1, ae.tips.size)
    }

    @Test
    fun `без версии файл не принимается`() {
        assertNull(CountryGuideParser.parse("[AE]\nupdated=2026-11-01"))
    }

    @Test
    fun `страна по валюте трат и сети`() {
        val g = CountryGuideParser.parse(sample)!!
        assertEquals("AE", g.forSpend(Currency.AED, null)?.iso)
        // Доллар — у нескольких стран: без сотовой сети не угадываем.
        assertNull(g.forSpend(Currency.USD, null))
        assertEquals("EC", g.forSpend(Currency.USD, "ec")?.iso)
        // Сеть другой страны — по валюте.
        assertEquals("AE", g.forSpend(Currency.AED, "TR")?.iso)
        assertNull(g.forSpend(Currency.KZT, null))
    }

    @Test
    fun `карта в стране`() {
        val ae = CountryGuideParser.parse(sample)!!.countries.getValue("AE")
        fun card(network: CardNetwork? = null, unionPay: Boolean = false, kind: CardKind = CardKind.CARD, currency: Currency = Currency.CNY) =
            BankCard("x", "x", currency, 0, kind = kind, unionPay = unionPay, network = network)
        assertEquals(Acceptance.PARTIAL, ae.verdict(card(unionPay = true), Currency.AED))
        assertEquals(Acceptance.NO, ae.verdict(card(CardNetwork.MIR), Currency.AED))
        assertNull(ae.verdict(card(), Currency.AED))
        assertEquals(Acceptance.YES, ae.verdict(card(kind = CardKind.CASH, currency = Currency.AED), Currency.AED))
        assertEquals(Acceptance.PARTIAL, ae.verdict(card(kind = CardKind.CASH, currency = Currency.USD), Currency.AED))
    }

    @Test
    fun `свежая копия и устаревание`() {
        val a = CountryGuideParser.parse(sample)!!
        val b = a.copy(version = LocalDate.of(2026, 12, 1))
        assertEquals(b, a.newer(b))
        assertEquals(b, b.newer(a))
        assertEquals(a, a.newer(null))
        val ae = a.countries.getValue("AE")
        assertTrue(ae.isStale(LocalDate.of(2027, 3, 1)))
        assertTrue(!ae.isStale(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `встроенный файл разбирается целиком`() {
        // Из сборки Android тесты идут в папке app/, из tools/domain-check — в корне репозитория или в tools/domain-check.
        val file = listOf("src/main/assets/countries.txt", "app/src/main/assets/countries.txt", "../../app/src/main/assets/countries.txt").map(::File).first { it.exists() }
        val g = CountryGuideParser.parse(file.readText())
        assertNotNull(g)
        val sections = file.readLines().count { it.trim().startsWith("[") }
        assertEquals(sections, g!!.countries.size)
        // У каждой страны — все пять платёжных систем: UnionPay РФ и зарубежная, МИР, Visa/MC РФ и зарубежная.
        for (c in g.countries.values) assertEquals(c.iso, CardNetwork.entries.size, c.networks.size)
    }

    @Test
    fun `UnionPay российского и зарубежного банка — отдельно`() {
        val g = CountryGuideParser.parse("version=2026-11-11\n[DE]\nupdated=2026-11-11\nunionpay=no|Не проходят\nunionpay_foreign=partial|Не везде")!!
        val de = g.countries.getValue("DE")
        val ru = BankCard("r", "r", Currency.CNY, 0, unionPay = true)
        val foreign = BankCard("f", "f", Currency.CNY, 0, unionPay = true, network = CardNetwork.UNIONPAY_FOREIGN)
        assertEquals(CardNetwork.UNIONPAY, ru.paymentNetwork)
        assertEquals(CardNetwork.UNIONPAY_FOREIGN, foreign.paymentNetwork)
        assertEquals(Acceptance.NO, de.verdict(ru, Currency.EUR))
        assertEquals(Acceptance.PARTIAL, de.verdict(foreign, Currency.EUR))
        // Зарубежная UnionPay сохраняется как зарубежная и считается по курсу UnionPay.
        val back = WalletCodec.decode(WalletCodec.encode(Wallet(cards = listOf(foreign))))!!.cards.single()
        assertEquals(CardNetwork.UNIONPAY_FOREIGN, back.network)
        assertTrue(back.unionPay)
    }

    @Test
    fun `купюры и ввоз-вывоз`() {
        val g = CountryGuideParser.parse("version=2026-11-11\n[TN]\nupdated=2026-11-11\nnotes=10 · 20 · 50\nexport=Динары вывозить нельзя|Обменяйте остаток\n[MA]\nupdated=2026-11-11")!!
        val tn = g.countries.getValue("TN")
        assertEquals(GuideItem("10 · 20 · 50", ""), tn.notes)
        assertEquals(listOf(GuideItem("Динары вывозить нельзя", "Обменяйте остаток")), tn.export)
        assertNull(g.countries.getValue("MA").notes)
        // Во встроенном файле: у Туниса — запрет на вывоз динаров, у Еврозоны — декларация от 10 000 €.
        val file = listOf("src/main/assets/countries.txt", "app/src/main/assets/countries.txt", "../../app/src/main/assets/countries.txt").map(::File).first { it.exists() }
        val all = CountryGuideParser.parse(file.readText())!!.countries
        assertTrue(all.getValue("TN").export.any { it.title.contains("нельзя") })
        assertTrue(all.getValue("DE").export.any { it.title.contains("10 000 €") })
        assertNotNull(all.getValue("TN").notes)
    }
}
