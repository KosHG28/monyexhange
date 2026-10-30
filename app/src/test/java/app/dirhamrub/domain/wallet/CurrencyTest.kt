package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyTest {
    @Test
    fun `валюта трат по стране сети`() {
        assertEquals(Currency.KZT, Currency.forCountry("KZ"))
        assertEquals(Currency.KZT, Currency.forCountry("kz"))
        assertEquals(Currency.AED, Currency.forCountry("AE"))
        assertEquals(Currency.EUR, Currency.forCountry("de"))
        // Болгария перешла на евро 1 января 2026.
        assertEquals(Currency.EUR, Currency.forCountry("BG"))
        assertEquals(Currency.USD, Currency.forCountry("US"))
        assertEquals(Currency.VND, Currency.forCountry(" VN "))
    }

    @Test
    fun `страны вне списка и пустой код`() {
        assertNull(Currency.forCountry("RU"))
        assertNull(Currency.forCountry("GB"))
        assertNull(Currency.forCountry(""))
        assertNull(Currency.forCountry(null))
    }

    @Test
    fun `страны подсказки — только валюты трат`() {
        val iso = listOf("AE", "TR", "TH", "US", "FR", "CN", "KZ", "GE", "AM", "VN")
        assertEquals(Currency.SPEND.toSet(), iso.mapNotNull { Currency.forCountry(it) }.toSet())
    }
}
