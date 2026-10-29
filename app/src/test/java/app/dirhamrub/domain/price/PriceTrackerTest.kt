package app.dirhamrub.domain.price

import org.junit.Assert.assertEquals
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
