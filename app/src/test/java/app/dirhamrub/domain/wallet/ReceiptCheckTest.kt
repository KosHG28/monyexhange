package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class ReceiptCheckTest {
    private fun d(s: String) = BigDecimal(s)

    @Test
    fun `обычная наценка банка не подозрительна`() {
        // 8 509 ₽ за 379 AED = 22,45 при ЦБ 21,97: +2,2 %.
        assertNull(ReceiptCheck.suspicious(d("8509"), d("379"), d("21.97")))
        assertEquals(d("2.2"), ReceiptCheck.deviationPercent(d("8509"), d("379"), d("21.97"))!!.setScale(1, RoundingMode.HALF_UP))
    }

    @Test
    fun `лишний ноль в списанной сумме`() {
        val dev = ReceiptCheck.suspicious(d("85090"), d("379"), d("21.97"))
        assertNotNull(dev)
        assertEquals(1, dev!!.signum())
    }

    @Test
    fun `потерянный ноль даёт отрицательное отклонение`() {
        val dev = ReceiptCheck.suspicious(d("850.9"), d("379"), d("21.97"))
        assertNotNull(dev)
        assertEquals(-1, dev!!.signum())
    }

    @Test
    fun `граница 15 процентов`() {
        assertNull(ReceiptCheck.suspicious(d("115"), d("1"), d("100")))
        assertNotNull(ReceiptCheck.suspicious(d("115.01"), d("1"), d("100")))
        assertNull(ReceiptCheck.suspicious(d("85"), d("1"), d("100")))
        assertNotNull(ReceiptCheck.suspicious(d("84.99"), d("1"), d("100")))
    }

    @Test
    fun `без курса ЦБ или с пустыми суммами не проверяем`() {
        assertNull(ReceiptCheck.suspicious(d("100"), d("1"), null))
        assertNull(ReceiptCheck.suspicious(d("0"), d("1"), d("100")))
        assertNull(ReceiptCheck.suspicious(d("100"), d("0"), d("100")))
        assertNull(ReceiptCheck.suspicious(d("100"), d("1"), d("0")))
    }
}
