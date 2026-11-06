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
    fun `надпись на упаковке не цена, если в кадре есть ценник с валютой`() {
        // Юбилейное «200 years» на бутылке Evian и ценник «10.00 AED» на полке.
        val bottle = OcrBlock(listOf(line("200", x = 600f, y = 380f, h = 90f), line("YEARS", x = 610f, y = 480f, h = 14f)))
        val tag = OcrBlock(listOf(line("WATER", y = 1000f), line("10.00", "AED", x = 360f, y = 1110f)))
        assertEquals(listOf("10.00"), parser.parse(listOf(bottle, tag)).map { it.value.toPlainString() })
    }

    @Test
    fun `без ценников с валютой крупное целое — догадка`() {
        val c = parser.parse(listOf(OcrBlock(listOf(line("200", y = 380f, h = 90f))))).single()
        assertFalse(c.confident)
        assertTrue(parse(line("AED", "10.00")).single().confident)
        assertTrue(parse(line("49.95")).single().confident)
    }

    @Test
    fun `цена у края кадра отбрасывается`() {
        val bounds = Box(0f, 0f, 923f, 2000f)
        // Ценник уходит за правый край: «18» может быть «18.00» или «18.50».
        val right = OcrBlock(listOf(line("AED", "18", x = 790f, y = 1150f)))
        assertTrue(parser.parse(listOf(right), bounds).isEmpty())
        // И за левый: «50» может быть «150».
        val left = OcrBlock(listOf(line("50", "AED", x = 5f, y = 1150f)))
        assertTrue(parser.parse(listOf(left), bounds).isEmpty())
        // Тот же ценник посередине кадра — цена.
        val middle = OcrBlock(listOf(line("AED", "18", x = 300f, y = 1150f)))
        assertEquals(listOf("18"), parser.parse(listOf(middle), bounds).map { it.value.toPlainString() })
        // Без границ кадра (тесты, старый вызов) — как раньше.
        assertEquals(1, parser.parse(listOf(right)).size)
    }

    @Test
    fun `два прочтения одного места — одна находка`() {
        val a = OcrBlock(listOf(line("AED", "200")))
        val b = OcrBlock(listOf(line("AED", "20")))
        assertEquals(1, parser.parse(listOf(a, b)).size)
    }

    @Test
    fun `рамка для вписывания — цена вместе с валютой`() {
        val l = line("Цена", "17", "990", "TF")
        val c = PriceParser(PriceCurrency.KZT).parse(listOf(OcrBlock(listOf(l)))).single()
        val tf = l.words.last().box
        // Цифры — без «TF», рамка для вписывания — до правого края «TF».
        assertTrue(c.box.right < tf.left)
        assertEquals(tf.right, c.extent.right, 0.01f)
        assertEquals(c.box.left, c.extent.left, 0.01f)
        // Валюта перед числом: «AED 45.00» — рамка начинается с «AED».
        val a = line("AED", "45.00")
        val p = parse(a).single()
        assertEquals(a.words.first().box.left, p.extent.left, 0.01f)
        // Без валюты рамка совпадает с ценой.
        val bare = parse(line("49.95")).single()
        assertEquals(bare.box, bare.extent)
    }

    @Test
    fun `наклон строки переходит в цену`() {
        val tilted = line("AED", "45.00").copy(angle = 12f)
        assertEquals(12f, parse(tilted).single().angle, 0.01f)
        // Склейка копеек верхним индексом не теряет наклон.
        val big = w("79", 10f, y = 100f, h = 60f)
        val sup = w("95", big.box.right + 3f, y = 102f, h = 26f)
        assertEquals(-8f, parse(OcrLine(listOf(big, sup), angle = -8f)).single().angle, 0.01f)
    }

    @Test
    fun `ширина наклонённой строки по рамке и высоте букв`() {
        // Строка 200 × 40 под 35°: рамка вдоль осей шириной 200·cos35° + 40·sin35° ≈ 186,77.
        assertEquals(200f, orientedWidth(186.77f, 40f, 35f)!!, 0.5f)
        assertEquals(200f, orientedWidth(186.77f, 40f, -35f)!!, 0.5f)
        // Под 65° (ценник почти поперёк) — всё ещё считается.
        val w65 = 200f * kotlin.math.cos(Math.toRadians(65.0)).toFloat() + 40f * kotlin.math.sin(Math.toRadians(65.0)).toFloat()
        assertEquals(200f, orientedWidth(w65, 40f, 65f)!!, 0.5f)
        // Почти вертикальная строка — не вписываем.
        assertEquals(null, orientedWidth(60f, 40f, 80f))
        assertEquals(120f, orientedWidth(120f, 30f, 0f)!!, 0.01f)
    }

    @Test
    fun `высота цифр берётся у слов, а не у рамки`() {
        // Наклонная строка: рамка «179» высотой 90, а сами цифры — 40.
        val l = OcrLine(listOf(OcrWord("179", Box(100f, 100f, 200f, 190f), height = 40f), OcrWord("AED", Box(200f, 60f, 300f, 150f), height = 40f)), angle = -35f)
        val c = parse(l).single()
        assertEquals(40f, c.height, 0.01f)
        assertEquals(-35f, c.angle, 0.01f)
        // Без наклона высота — как у рамки.
        assertEquals(40f, parse(line("AED", "45.00")).single().height, 0.01f)
    }

    @Test
    fun `артикул через несколько слешей не цена`() {
        // Ценник Zara: «ART.: 9598/202/251» — «251» не цена, хотя набрано тем же кеглем.
        val art = OcrBlock(listOf(line("ART.:", "9598/202/251", y = 300f, h = 60f)))
        assertTrue(parser.parse(listOf(art)).isEmpty())
        assertTrue(PriceParser(PriceCurrency.KZT).parse(listOf(art)).isEmpty())
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
        // Прописные шире цифр, точка уже: «AED» = 3,9 из 8,4 единиц ширины.
        assertEquals(74.29f, box.left, 0.01f)
        assertEquals(160f, box.right, 0.01f)
        // «179.00DHS»: цифры — 5,5 из 9,4 единиц, рамка не заходит на «D» (по числу символов было бы 627).
        val zara = parse(OcrLine(listOf(OcrWord("179.00DHS", Box(0f, 0f, 940f, 80f))))).single()
        assertEquals(0f, zara.box.left, 0.01f)
        assertEquals(550f, zara.box.right, 0.01f)
        assertEquals(940f, zara.extent.right, 0.01f)
    }

    @Test
    fun `наклон строки — по центрам слов, углы OCR проверяются рамкой`() {
        // Ровная строка из двух слов: углы OCR повёрнуты на 90° (или 180°) — всё равно 0.
        val level = listOf(Box(0f, 100f, 180f, 160f), Box(200f, 100f, 300f, 160f))
        assertEquals(0f, lineAngle(level, 0f, 300f), 0.01f)
        assertEquals(0f, lineAngle(level, -300f, 0f), 0.01f)
        // Наклон 20° по центрам слов (рамки вдоль осей — у наклонных слов выше букв).
        val tilted = listOf(Box(0f, 0f, 100f, 70f), Box(170f, 62f, 270f, 132f))
        assertEquals(20f, lineAngle(tilted, 1f, 0f), 0.5f)
        // Одно слово: угол по углам OCR, 180° — та же горизонталь, наклон −8° — как есть.
        val word = listOf(Box(0f, 0f, 300f, 80f))
        assertEquals(0f, lineAngle(word, -300f, -2f), 0.01f)
        assertEquals(-8f, lineAngle(word, 297f, -41.7f), 0.1f)
        // Почти вертикальный угол у низкой широкой рамки — неверен: 0.
        assertEquals(0f, lineAngle(word, 2f, 300f), 0.01f)
        // Мелкий наклон — 0, чтобы сумма не дрожала.
        assertEquals(0f, lineAngle(word, 300f, 5f), 0.01f)
    }

    @Test
    fun `высота букв по углам OCR — только правдоподобная`() {
        val box = Box(0f, 0f, 200f, 60f)
        assertEquals(40f, glyphHeight(40f, box), 0.01f)
        assertEquals(60f, glyphHeight(null, box), 0.01f)
        // Длиннее рамки (углы перепутаны — это ширина слова) — высота рамки.
        assertEquals(60f, glyphHeight(200f, box), 0.01f)
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
        // Кириллическое «ТГ» латинский OCR читает как «TF» или «Tr» — это тоже тенге.
        assertTrue(kzt.parse(listOf(OcrBlock(listOf(line("17", "990", "TF"))))).single().hasCurrency)
        assertTrue(kzt.parse(listOf(OcrBlock(listOf(line("17", "990", "Tr"))))).single().hasCurrency)
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

    @Test
    fun `ценники в валютах из справочника`() {
        fun v(code: String, vararg words: String) = vals(PriceCurrency.of(code), line(*words))
        // Индонезия: точка — разделитель тысяч.
        assertEquals(listOf("150000"), v("IDR", "Rp", "150.000"))
        assertEquals(listOf("150000"), v("IDR", "Rp150.000"))
        // Индия: индийские разряды и «Rs.».
        assertEquals(listOf("150000"), v("INR", "₹1,50,000"))
        assertEquals(listOf("499"), v("INR", "Rs.", "499"))
        // Кувейт: три знака после запятой.
        assertEquals(listOf("1.250"), v("KWD", "KD", "1.250"))
        assertEquals(listOf("1.250"), v("KWD", "1,250", "KWD"))
        // Япония: без копеек, разряды запятой.
        assertEquals(listOf("1280"), v("JPY", "¥1,280"))
        // Малайзия, Марокко, Британия, Шри-Ланка.
        assertEquals(listOf("12.90"), v("MYR", "RM", "12.90"))
        assertEquals(listOf("120"), v("MAD", "120", "DH"))
        assertEquals(listOf("4.50"), v("GBP", "£4.50"))
        assertEquals(listOf("2500.00"), v("LKR", "Rs", "2,500.00"))
        // Неизвестный код — правила дирхама, как раньше.
        assertEquals(PriceCurrency.AED, PriceCurrency.of("XXX"))
    }

    @Test
    fun `диапазон цен и длина кода — от размера валюты`() {
        val idr = PriceCurrency.of("IDR")
        assertEquals(0, BigDecimal("1000").compareTo(idr.min))
        assertTrue(idr.longRunDigits >= 10)
        // Слишком мелкое для рупии число — не цена.
        assertTrue(vals(idr, line("Rp", "15")).isEmpty())
        val kwd = PriceCurrency.of("KWD")
        assertEquals(3, kwd.decimals)
    }
}
