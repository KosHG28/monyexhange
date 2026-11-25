package app.dirhamrub.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcCardNameTest {
    @Test fun `an untouched template follows the detected currency`() {
        assertEquals("Карта RUB", nameAfterNfcRead("Карта CNY", false, "Карта CNY", "Карта RUB"))
    }

    @Test fun `custom names are preserved even when they match the old template`() {
        assertEquals("Карта CNY", nameAfterNfcRead("Карта CNY", true, "Карта CNY", "Карта RUB"))
        assertEquals("Для поездок", nameAfterNfcRead("Для поездок", false, "Карта CNY", "Карта RUB"))
    }

    @Test fun `a new card can keep its reactive default placeholder`() {
        assertEquals("", nameAfterNfcRead("", false, "Карта CNY", "Карта RUB"))
    }
}
