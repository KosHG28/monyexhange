package app.dirhamrub.domain.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PriceTrackerTest {
    private fun cand(value: String, x: Float, y: Float = 100f, score: Float = 3f) =
        PriceCandidate(BigDecimal(value), Box(x, y, x + 100f, y + 40f), score, hasCurrency = true, isOld = false)

    @Test
    fun `показ после двух попаданий из четырёх`() {
        val t = PriceTracker()
        assertTrue(t.update(listOf(cand("10", 0f)), 0).isEmpty())
        assertTrue(t.update(emptyList(), 33).isEmpty())
        assertEquals(1, t.update(listOf(cand("10", 4f)), 66).size)
    }

    @Test
    fun `два числа на одном месте — остаётся стабильное`() {
        val t = PriceTracker()
        // OCR читает на одном месте то «200», то «20»; «200» — чаще.
        t.update(listOf(cand("200", 0f)), 0)
        t.update(listOf(cand("20", 2f)), 33)
        t.update(listOf(cand("200", 0f)), 66)
        val shown = t.update(listOf(cand("20", 2f), cand("200", 1f)), 100)
        assertEquals(listOf("200"), shown.map { it.value.toPlainString() })
    }

    @Test
    fun `догадка остаётся догадкой`() {
        val t = PriceTracker()
        val guess = cand("200", 0f).copy(hasCurrency = false, confident = false)
        t.update(listOf(guess), 0)
        assertFalse(t.update(listOf(guess), 33).single().confident)
        val sure = cand("15", 400f)
        t.update(listOf(sure), 66)
        assertTrue(t.update(listOf(sure, guess), 100).first { it.value.compareTo(BigDecimal("15")) == 0 }.confident)
    }

    @Test
    fun `при уверенной цене догадки скрыты`() {
        val t = PriceTracker()
        val guess = cand("251", 0f, y = 600f).copy(hasCurrency = false, confident = false)
        val price = cand("17990", 0f, y = 1100f)
        // Догадка успела набрать попадания в кадрах, где OCR не видел валюту у цены.
        t.update(listOf(guess), 0)
        assertEquals(listOf("251"), t.update(listOf(guess), 33).map { it.value.toPlainString() })
        t.update(listOf(guess, price), 66)
        assertEquals(listOf("17990"), t.update(listOf(guess, price), 100).map { it.value.toPlainString() })
    }

    @Test
    fun `рамка с валютой сглаживается вместе с ценой`() {
        val t = PriceTracker()
        val c = cand("17990", 0f).let { it.copy(extent = Box(it.box.left, it.box.top, it.box.right + 40f, it.box.bottom)) }
        t.update(listOf(c), 0)
        val moved = c.copy(box = Box(20f, 100f, 120f, 140f), extent = Box(20f, 100f, 160f, 140f))
        val shown = t.update(listOf(moved), 33).single()
        // Рамка цены сдвинулась на 0,35 × 20 = 7, хвост с валютой остался на 40 правее.
        assertEquals(7f, shown.box.left, 0.01f)
        assertEquals(shown.box.right + 40f, shown.extent.right, 0.01f)
    }

    @Test
    fun `одиночная вспышка не показывается`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        repeat(4) { i -> assertTrue(t.update(emptyList(), 33L * (i + 1)).isEmpty()) }
        // Спустя окно старая находка забыта: снова нужно два попадания.
        assertTrue(t.update(listOf(cand("10", 0f)), 200).isEmpty())
    }

    @Test
    fun `сглаживание позиции`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        val shown = t.update(listOf(cand("10", 20f)), 33).single()
        assertEquals(7f, shown.box.left, 0.01f)
    }

    @Test
    fun `удержание и плавное исчезновение`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        t.update(listOf(cand("10", 0f)), 33)
        assertEquals(1f, t.update(emptyList(), 200).single().alpha, 0.001f)
        val fading = t.update(emptyList(), 333).single().alpha
        assertTrue(fading in 0.01f..0.99f)
        assertTrue(t.update(emptyList(), 500).isEmpty())
    }

    @Test
    fun `разные значения — разные метки`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        assertTrue(t.update(listOf(cand("11", 0f)), 33).isEmpty())
    }

    @Test
    fun `далёкая находка с тем же значением — новая метка`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        t.update(listOf(cand("10", 0f)), 33)
        val labels = t.update(listOf(cand("10", 0f), cand("10", 900f, 900f)), 66)
        assertEquals(1, labels.size)
        assertEquals(2, t.update(listOf(cand("10", 0f), cand("10", 900f, 900f)), 99).size)
    }

    @Test
    fun `не больше шести лучших`() {
        val t = PriceTracker()
        val many = (1..9).map { i -> cand("$i", 0f, i * 100f, score = i.toFloat()) }
        t.update(many, 0)
        val shown = t.update(many, 33)
        assertEquals(6, shown.size)
        assertEquals(BigDecimal("9"), shown.first().value)
        assertEquals(setOf(4, 5, 6, 7, 8, 9), shown.map { it.value.toInt() }.toSet())
    }

    @Test
    fun `идентификатор метки стабилен`() {
        val t = PriceTracker()
        t.update(listOf(cand("10", 0f)), 0)
        val id = t.update(listOf(cand("10", 2f)), 33).single().id
        assertEquals(id, t.update(listOf(cand("10", 4f)), 66).single().id)
    }
}
