package app.dirhamrub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainLayoutTest {
    @Test
    fun `готовые виды`() {
        assertEquals(MainLayout.SIMPLE, AppSettings().mainLayout)
        val detailed = AppSettings().withLayout(MainLayout.DETAILED)
        assertEquals(MainLayout.DETAILED, detailed.mainLayout)
        assertEquals(CardsView.ROW, detailed.cardsView)
        assertTrue(detailed.showOfficial && detailed.showChips)
        assertEquals(MainLayout.SIMPLE, detailed.withLayout(MainLayout.SIMPLE).mainLayout)
    }

    @Test
    fun `свой набор и другие настройки не мешают`() {
        val custom = AppSettings().copy(showChips = true)
        assertEquals(MainLayout.CUSTOM, custom.mainLayout)
        assertEquals(custom, custom.withLayout(MainLayout.CUSTOM))
        // Тема или UnionPay на вид калькулятора не влияют.
        assertEquals(MainLayout.SIMPLE, AppSettings(unionPay = false, theme = ThemeMode.DARK).mainLayout)
        assertFalse(AppSettings(theme = ThemeMode.DARK).withLayout(MainLayout.DETAILED).theme != ThemeMode.DARK)
    }
}
