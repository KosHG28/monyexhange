package app.dirhamrub.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFlightTest {
    @Test
    fun `the calculator and wallet keep independent origins for the same payment`() {
        val registry = CardFlight()
        val calculator = CardFlightKey("payment", CardFlightSource.CALCULATOR)
        val wallet = CardFlightKey("payment", CardFlightSource.WALLET)
        val thumbnail = Rect(28f, 250f, 74f, 280f)
        val walletRow = Rect(14f, 160f, 346f, 244f)

        registry.place(calculator, thumbnail)
        registry.place(wallet, walletRow)
        assertEquals(thumbnail, registry.originOf(calculator))
        assertEquals(walletRow, registry.originOf(wallet))

        registry.place(wallet, null)
        assertNull(registry.originOf(wallet))
        assertEquals(thumbnail, registry.originOf(calculator))
    }
}
