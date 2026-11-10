package app.dirhamrub.domain.price

import app.dirhamrub.domain.wallet.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class BoardParserTest {
    private fun d(s: String) = BigDecimal(s)
    private fun word(text: String, x: Float, y: Float) = OcrWord(text, Box(x, y, x + text.length * 12f, y + 30f))

    /** Строки как в ML Kit: каждая — отдельный ряд табло. */
    private fun line(y: Float, vararg tokens: String): OcrLine {
        var x = 10f
        return OcrLine(tokens.map { t -> word(t, x, y).also { x += t.length * 12f + 40f } })
    }

    private val cbrAed: (Currency) -> BigDecimal? = { c ->
        when (c) {
            Currency.USD -> d("3.6725")
            Currency.EUR -> d("3.98")
            Currency.RUB -> d("0.0455")
            Currency.JPY -> d("0.0245")
            else -> null
        }
    }

    private fun r(v: BigDecimal?) = v?.setScale(4, RoundingMode.HALF_UP)

    @Test
    fun `обычное табло`() {
        val rates = BoardParser.parse(
            listOf(line(0f, "BUY", "SELL"), line(50f, "USD", "3.650", "3.670"), line(100f, "EUR", "3,98", "4,05"), line(150f, "GBP", "4.61")),
            Currency.AED,
            cbrAed,
        )
        val usd = rates.first { it.currency == Currency.USD }
        assertEquals(d("3.650"), usd.buy)
        assertEquals(d("3.670"), usd.sell)
        assertEquals(d("3.98"), rates.first { it.currency == Currency.EUR }.buy)
        // У фунта нет курса ЦБ — берём как есть.
        assertEquals(d("4.61"), rates.first { it.currency == Currency.GBP }.buy)
    }

    @Test
    fun `колонки распознаны отдельными строками`() {
        // ML Kit отдал коды одним блоком, курсы — другим: ряды собираются по вертикали.
        val codes = OcrLine(listOf(word("USD", 10f, 50f)))
        val codes2 = OcrLine(listOf(word("EUR", 10f, 100f)))
        val nums = OcrLine(listOf(word("3.650", 200f, 52f), word("3.670", 320f, 52f)))
        val nums2 = OcrLine(listOf(word("3.980", 200f, 98f), word("4.050", 320f, 98f)))
        val rates = BoardParser.parse(listOf(codes, codes2, nums, nums2), Currency.AED, cbrAed)
        assertEquals(d("3.650"), rates.first { it.currency == Currency.USD }.buy)
        assertEquals(d("4.050"), rates.first { it.currency == Currency.EUR }.sell)
    }

    @Test
    fun `покупка — меньшее, даже если колонки в другом порядке`() {
        val usd = BoardParser.parse(listOf(line(0f, "USD", "3.670", "3.650")), Currency.AED, cbrAed).single()
        assertEquals(d("3.650"), usd.buy)
        assertEquals(d("3.670"), usd.sell)
    }

    @Test
    fun `за 100 и за 1000`() {
        // Рубли за 100: 4,10 и 4,70 AED.
        val rub = BoardParser.parse(listOf(line(0f, "RUB", "4.10", "4.70")), Currency.AED, cbrAed).single()
        assertEquals(d("0.0410"), r(rub.buy))
        // Иены с явной единицей «100».
        val jpy = BoardParser.parse(listOf(line(0f, "JPY", "100", "2.41", "2.49")), Currency.AED, cbrAed).single()
        assertEquals(d("0.0241"), r(jpy.buy))
    }

    @Test
    fun `обратная котировка`() {
        // Табло пишет, сколько долларов за дирхам: 0.2740 → 3,65 за доллар.
        val usd = BoardParser.parse(listOf(line(0f, "USD", "0.2740")), Currency.AED, cbrAed).single()
        assertEquals(d("3.6496"), r(usd.buy))
        assertNull(usd.sell)
    }

    @Test
    fun `мусор не курс`() {
        val rates = BoardParser.parse(
            listOf(line(0f, "USD", "2026"), line(50f, "AED", "1.00"), line(100f, "OPEN", "24", "HOURS"), line(150f, "TEL", "800")),
            Currency.AED,
            cbrAed,
        )
        assertTrue(rates.isEmpty())
    }
}
