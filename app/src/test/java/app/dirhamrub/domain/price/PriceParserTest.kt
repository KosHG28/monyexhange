package app.dirhamrub.domain.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PriceParserTest {
    private val parser = PriceParser()

    /** Слово высотой [h] начиная с [x]; ширина пропорциональна длине текста. */
    private fun w(text: String, x: Float, y: Float = 100f, h: Float = 40f) =
        OcrWord(text, Box(x, y, x + text.length * h * 0.6f, y + h))

    /** Строка из слов, идущих подряд с пробелом в полвысоты. */
    private fun line(vararg texts: String, x: Float = 10f, y: Float = 100f, h: Float = 40f): OcrLine {
        var cx = x
        return OcrLine(texts.map { t -> w(t, cx, y, h).also { cx = it.box.right + h * 0.5f } })
    }

    private fun parse(vararg lines: OcrLine) = parser.parse(listOf(OcrBlock(lines.toList())))
    private fun values(vararg lines: OcrLine) = parse(*lines).map { it.value.toPlainString() }

    @Test
    fun `простые форматы с валютой до и после`() {
        assertEquals(listOf("1299.00"), values(line("AED", "1,299.00")))
        assertEquals(listOf("49.95"), values(line("49.95", "AED")))
        assertEquals(listOf("379"), values(line("Dhs", "379")))
        assertEquals(listOf("12.50"), values(line("DHS12.50")))
        assertEquals(listOf("7"), values(line("7", "DH")))
        assertEquals(listOf("25"), values(line("25", "Dirham")))
        assertEquals(listOf("18.75"), values(line("18.75", "د.إ")))
        assertEquals(listOf("9.5"), values(line("AED", "9,5")))
    }

    @Test
    fun `валюта повышает скоринг`() {
        val c = parse(line("AED", "49.95")).single()
        assertTrue(c.hasCurrency)
        val plain = parse(line("49.95")).single()
        assertFalse(plain.hasCurrency)
        assertTrue(c.score > plain.score)
    }

    @Test
    fun `склейка разорванных частей`() {
        val l = OcrLine(listOf(w("79", 10f), w(".95", 10f + 2 * 24f + 4f)))
        assertEquals(listOf("79.95"), values(l))
        val l2 = OcrLine(listOf(w("1,299", 10f), w(".00", 10f + 5 * 24f + 4f)))
        assertEquals(listOf("1299.00"), values(l2))
        val l3 = OcrLine(listOf(w("AED", 0f), w("1", 80f), w("299", 80f + 24f + 8f)))
        assertEquals(listOf("1299"), values(l3))
    }

    @Test
    fun `копейки верхним индексом`() {
        val big = w("79", 10f, y = 100f, h = 60f)
        val sup = w("95", big.box.right + 3f, y = 102f, h = 26f)
        assertEquals(listOf("79.95"), values(OcrLine(listOf(big, sup))))
        // Индекс распознан отдельной строкой того же блока.
        assertEquals(listOf("79.95"), values(OcrLine(listOf(big)), OcrLine(listOf(sup))))
    }

    @Test
    fun `телефоны отбрасываются`() {
        assertTrue(values(line("+971", "4", "123", "4567")).isEmpty())
        assertTrue(values(line("Tel:", "04", "3456789")).isEmpty())
        assertTrue(values(line("050", "123", "4567")).isEmpty())
        assertTrue(values(line("0501234567")).isEmpty())
    }

    @Test
    fun `штрихкоды и артикулы отбрасываются`() {
        assertTrue(values(line("6291041500213")).isEmpty())
        assertTrue(values(line("SKU", "12345678")).isEmpty())
        assertTrue(values(line("Item", "No.", "4521")).isEmpty())
    }

    @Test
    fun `даты и время отбрасываются`() {
        assertTrue(values(line("29.10.2026")).isEmpty())
        assertTrue(values(line("EXP", "12/26")).isEmpty())
        assertTrue(values(line("10:30")).isEmpty())
        assertTrue(values(line("Open", "9.30am")).isEmpty())
    }

    @Test
    fun `проценты и единицы отбрасываются`() {
        assertTrue(values(line("50%", "OFF")).isEmpty())
        assertTrue(values(line("500ml")).isEmpty())
        assertTrue(values(line("1.5", "L")).isEmpty())
        assertTrue(values(line("250g")).isEmpty())
        assertTrue(values(line("2", "kg")).isEmpty())
        assertTrue(values(line("30", "cm")).isEmpty())
        assertTrue(values(line("12", "pcs")).isEmpty())
        assertTrue(values(line("x2")).isEmpty())
        assertTrue(values(line("2x")).isEmpty())
    }

    @Test
    fun `цена рядом с объёмом находится`() {
        assertEquals(listOf("12.50"), values(line("Milk", "1L", "AED", "12.50")))
    }

    @Test
    fun `диапазон цен`() {
        assertTrue(values(line("AED", "0.25")).isEmpty())
        assertTrue(values(line("AED", "150,000")).isEmpty())
        assertEquals(listOf("100000"), values(line("AED", "100,000")))
    }

    @Test
    fun `голое мелкое целое без валюты не цена`() {
        val title = line("Aisle", "12", h = 20f)
        val price = line("AED", "45.00", y = 200f, h = 60f)
        val found = parser.parse(listOf(OcrBlock(listOf(title)), OcrBlock(listOf(price))))
        assertEquals(listOf("45.00"), found.map { it.value.toPlainString() })
    }

    @Test
    fun `крупное число без валюты считается ценой`() {
        val small = line("Fresh", "Bread", h = 20f)
        val big = line("379", y = 200f, h = 80f)
        val found = parser.parse(listOf(OcrBlock(listOf(small)), OcrBlock(listOf(big))))
        assertEquals(listOf("379"), found.map { it.value.toPlainString() })
    }

    @Test
    fun `крупная цена выше по скорингу`() {
        val small = line("AED", "12.00", h = 20f)
        val big = line("AED", "45.00", y = 300f, h = 70f)
        val found = parser.parse(listOf(OcrBlock(listOf(small)), OcrBlock(listOf(big))))
        assertEquals(BigDecimal("45.00"), found.first().value)
    }

    @Test
    fun `WAS помечает старую цену`() {
        val found = parse(line("WAS", "AED", "59.00"), line("NOW", "AED", "39.00", y = 160f))
        val old = found.first { it.value.compareTo(BigDecimal("59")) == 0 }
        val now = found.first { it.value.compareTo(BigDecimal("39")) == 0 }
        assertTrue(old.isOld)
        assertFalse(now.isOld)
    }

    @Test
    fun `WAS строкой выше`() {
        val found = parse(line("Was"), line("AED", "59.00", y = 150f))
        assertTrue(found.single().isOld)
    }

    @Test
    fun `мелкая и бо́льшая цена возле крупной — зачёркнутая старая`() {
        val struck = line("AED", "79.95", y = 100f, h = 24f)
        val now = line("AED", "49.95", y = 135f, h = 64f)
        val found = parse(struck, now)
        assertTrue(found.first { it.value.compareTo(BigDecimal("79.95")) == 0 }.isOld)
        assertFalse(found.first { it.value.compareTo(BigDecimal("49.95")) == 0 }.isOld)
    }

    @Test
    fun `рамка цены внутри слова с валютой`() {
        val l = OcrLine(listOf(OcrWord("AED12.50", Box(0f, 0f, 160f, 40f))))
        val box = parse(l).single().box
        assertEquals(60f, box.left, 0.01f)
        assertEquals(160f, box.right, 0.01f)
    }

    @Test
    fun `дубликаты схлопываются`() {
        val a = OcrBlock(listOf(line("AED", "10.00")))
        assertEquals(1, parser.parse(listOf(a, a)).size)
    }

    @Test
    fun `несколько цен на кадре`() {
        val found = parser.parse(
            listOf(
                OcrBlock(listOf(line("Apples", "AED", "8.50"))),
                OcrBlock(listOf(line("Pears", "AED", "11.25", y = 300f))),
            ),
        )
        assertEquals(setOf("8.50", "11.25"), found.map { it.value.toPlainString() }.toSet())
    }

    @Test
    fun `цены в тенге`() {
        val kzt = PriceParser(PriceCurrency.KZT)
        fun v(vararg l: OcrLine) = kzt.parse(listOf(OcrBlock(l.toList()))).map { it.value.toPlainString() }
        assertEquals(listOf("1299"), v(line("1", "299", "₸")))
        assertEquals(listOf("4590"), v(line("4590", "KZT")))
        assertEquals(listOf("850"), v(line("850", "T")))
        assertEquals(listOf("12500"), v(line("12", "500", "тг")))
        // Дирхамы в режиме тенге не считаются ценой с валютой, а 5 ₸ — меньше диапазона.
        assertFalse(kzt.parse(listOf(OcrBlock(listOf(line("AED", "45.00"))))).single().hasCurrency)
        assertTrue(v(line("5", "₸")).isEmpty())
    }

    private fun vals(currency: PriceCurrency, vararg l: OcrLine) =
        PriceParser(currency).parse(listOf(OcrBlock(l.toList()))).map { it.value.toPlainString() }

    @Test
    fun `доллары и евро`() {
        assertEquals(listOf("12.99"), vals(PriceCurrency.USD, line("$12.99")))
        assertEquals(listOf("5"), vals(PriceCurrency.USD, line("US$", "5")))
        assertEquals(listOf("12.99"), vals(PriceCurrency.EUR, line("12,99", "€")))
        assertEquals(listOf("1299.90"), vals(PriceCurrency.EUR, line("1.299,90", "EUR")))
    }

    @Test
    fun `лира, бат, лари, драм, юань`() {
        assertEquals(listOf("1299.90"), vals(PriceCurrency.TRY, line("1.299,90", "TL")))
        assertEquals(listOf("89.90"), vals(PriceCurrency.TRY, line("₺89,90")))
        assertEquals(listOf("250"), vals(PriceCurrency.THB, line("250", "THB")))
        assertEquals(listOf("4.50"), vals(PriceCurrency.GEL, line("4.50", "₾")))
        assertEquals(listOf("3500"), vals(PriceCurrency.AMD, line("3", "500", "AMD")))
        assertEquals(listOf("38"), vals(PriceCurrency.CNY, line("¥38")))
    }

    @Test
    fun `донг с точками между разрядами`() {
        assertEquals(listOf("45000"), vals(PriceCurrency.VND, line("45.000đ")))
        assertEquals(listOf("1250000"), vals(PriceCurrency.VND, line("1.250.000", "VND")))
        // Меньше 1000 донгов — не цена.
        assertTrue(vals(PriceCurrency.VND, line("500", "VND")).isEmpty())
    }

    @Test
    fun `валюта по коду`() {
        assertEquals(PriceCurrency.VND, PriceCurrency.of("VND"))
        assertEquals(PriceCurrency.AED, PriceCurrency.of("XXX"))
    }
}
