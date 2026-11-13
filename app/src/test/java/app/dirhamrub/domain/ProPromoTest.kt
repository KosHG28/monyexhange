package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPromoTest {
    @Test
    fun `общий код включает Pro с любым регистром и пробелами по краям`() {
        for (code in listOf("KIMCHI", "kimchi", "KiMcHi", "  KIMCHI\n")) {
            assertTrue(ProPromo.accepts(code))
            val settings = AppSettings(keyHaptics = false, rounding = Rounding.TENS)
            assertEquals(settings.copy(pro = true), ProPromo.activate(settings, code))
        }
    }

    @Test
    fun `пустой ошибочный и частичный код ничего не меняют`() {
        for (code in listOf("", "   ", "KIMCH", "KIMCHI1", "KIM CHI", "КIMCHI")) {
            assertFalse(ProPromo.accepts(code))
            for (pro in listOf(false, true)) {
                val settings = AppSettings(pro = pro)
                assertEquals(settings, ProPromo.activate(settings, code))
            }
        }
    }

    @Test
    fun `повторная активация сохраняет настройки Pro`() {
        val settings = AppSettings(pro = true, dynamicColor = true, appIcon = AppIcon.GOLD)
        assertEquals(settings, ProPromo.activate(settings, "KIMCHI"))
    }
}
