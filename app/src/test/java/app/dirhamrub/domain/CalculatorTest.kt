package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorTest {
    private fun type(vararg keys: Key, start: Expression = Expression()): Expression =
        keys.fold(start) { e, k -> e.press(k) }

    private fun digits(s: String): Array<Key> = s.map {
        when (it) {
            ',' -> Key.Comma
            '+' -> Key.Plus
            '*' -> Key.Times
            '=' -> Key.Equals
            else -> Key.Digit(it - '0')
        }
    }.toTypedArray()

    @Test
    fun `сумма нескольких покупок`() {
        val e = type(*digits("22+15,5+8"))
        assertEquals("22 + 15,5 + 8", e.display())
        assertEquals(0, BigDecimal("45.5").compareTo(e.value))
        assertTrue(e.isCompound)
    }

    @Test
    fun `умножение раньше сложения`() {
        val e = type(*digits("3*22+15,5"))
        assertEquals("3 × 22 + 15,5", e.display())
        assertEquals(0, BigDecimal("81.5").compareTo(e.value))
        assertEquals(0, BigDecimal("47").compareTo(type(*digits("2+3*15")).value))
        assertEquals(0, BigDecimal("30").compareTo(type(*digits("2*3*5")).value))
    }

    @Test
    fun `недописанный множитель не обнуляет сумму`() {
        val e = type(*digits("3*"))
        assertEquals("3 × ", e.display())
        assertEquals(0, BigDecimal("3").compareTo(e.value))
        assertEquals(0, BigDecimal("15").compareTo(type(*digits("10+5*")).value))
    }

    @Test
    fun `знак после знака заменяет его`() {
        assertEquals("3 × ", type(*digits("3+*")).display())
        assertEquals("3 + ", type(*digits("3*+")).display())
        assertEquals("", type(*digits("*")).display())
    }

    @Test
    fun `стирание знака умножения`() {
        val e = type(*digits("3*2"))
        assertEquals("3 × ", e.press(Key.Backspace).display())
        val back = e.press(Key.Backspace).press(Key.Backspace)
        assertEquals("3", back.display())
        assertEquals("3 + 1", back.press(Key.Plus).press(Key.Digit(1)).display())
    }

    @Test
    fun `чип после умножения становится множителем`() {
        val e = type(*digits("3*")).putAmount(BigDecimal("22"))
        assertEquals("3 × 22", e.display())
        assertEquals(0, BigDecimal("66").compareTo(e.value))
    }

    @Test
    fun `умножение сохраняется и читается`() {
        val e = type(*digits("3*22+15,5"))
        assertEquals("3*22+15,5", e.serialize())
        assertEquals(e, Expression.parse(e.serialize()))
        // Сохранения прежних версий — только сложение.
        assertEquals("22 + 15,5", Expression.parse("22+15,5").display())
        assertEquals(0, BigDecimal("37.5").compareTo(Expression.parse("22+15,5").value))
    }

    @Test
    fun `равно сворачивает выражение`() {
        val e = type(*digits("22+15,5+8="))
        assertFalse(e.isCompound)
        assertEquals("45,5", e.display())
        assertTrue(e.fresh)
        // Следующая цифра начинает новое число.
        assertEquals("7", e.press(Key.Digit(7)).display())
        // А «+» продолжает сумму.
        assertEquals("45,5 + ", e.press(Key.Plus).display())
    }

    @Test
    fun `не больше двух знаков после запятой и одной запятой`() {
        assertEquals("1,25", type(*digits("1,256")).display())
        assertEquals("1,2", type(*digits("1,,2")).display())
        assertEquals("0,5", type(*digits(",5")).display())
    }

    @Test
    fun `ведущий ноль заменяется`() {
        assertEquals("5", type(*digits("05")).display())
        assertEquals("0", type(*digits("00")).display())
    }

    @Test
    fun `разряды при наборе`() {
        assertEquals("15${NBSP}000", type(*digits("15000")).display())
        assertEquals("999${NBSP}999${NBSP}999", type(*digits("9999999999")).display())
    }

    @Test
    fun `двойной плюс игнорируется`() {
        assertEquals("22 + ", type(*digits("22++")).display())
        assertEquals("", type(*digits("+")).display())
    }

    @Test
    fun `стирание`() {
        val e = type(*digits("22+1"))
        val e1 = e.press(Key.Backspace)
        assertEquals("22 + ", e1.display())
        val e2 = e1.press(Key.Backspace)
        assertEquals("22", e2.display())
        assertEquals("2", e2.press(Key.Backspace).display())
        assertEquals("", e.press(Key.Clear).display())
        assertEquals("", Expression().press(Key.Backspace).display())
    }

    @Test
    fun `чип подставляется в пустое слагаемое`() {
        val e = type(*digits("22+")).putAmount(BigDecimal("50"))
        assertEquals("22 + 50", e.display())
        val replaced = type(*digits("22")).putAmount(BigDecimal("50"))
        assertEquals("50", replaced.display())
        assertTrue(replaced.fresh)
    }

    @Test
    fun `сохранение и восстановление`() {
        val e = type(*digits("22+15,5+"))
        val restored = Expression.parse(e.serialize())
        assertEquals(e.terms, restored.terms)
        assertEquals("", Expression.parse("мусор").display())
        assertEquals("", Expression.parse(null).display())
        assertEquals("", Expression.parse("1,2,3").display())
    }

    @Test
    fun `значение из числа`() {
        assertEquals("8507,31", Expression.of(BigDecimal("8507.3125")).display().replace(NBSP.toString(), ""))
        assertTrue(Expression.of(BigDecimal.ZERO).isEmpty)
    }
}
