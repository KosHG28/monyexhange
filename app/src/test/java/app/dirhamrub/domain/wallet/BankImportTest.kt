package app.dirhamrub.domain.wallet

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BankImportTest {
    @Test fun `separates purchases and never pairs maxima across operations`() {
        val result = BankImport.candidates(listOf(
            "Покупка 167 AED", "Списано 3 881 KZT", "Покупка 80 AED", "Списано 5 800 KZT"
        ), Currency.KZT)
        assertEquals(2, result.size)
        assertEquals(BigDecimal("167"), result[0].counterpart!!.amount)
        assertEquals(BigDecimal("3881"), result[0].onCard!!.amount)
        assertEquals(BigDecimal("80"), result[1].counterpart!!.amount)
        assertEquals(BigDecimal("5800"), result[1].onCard!!.amount)
        val ambiguous = BankImport.candidates(listOf("167 AED", "3881 KZT", "80 AED", "5800 KZT"), Currency.KZT).single()
        assertNull(ambiguous.counterpart)
        assertNull(ambiguous.onCard)
    }

    @Test fun `preserves currency precision and identifies refunds`() {
        val results = BankImport.candidates(listOf(
            "Покупка 1.250 KWD", "Списано 4.10 USD", "21.11.2026",
            "Возврат 1.250 KWD", "Зачислено 4.10 USD"
        ), Currency.USD)
        assertEquals(BankOperation.PURCHASE, results.first().operation)
        assertEquals(BigDecimal("1.250"), results.first().counterpart?.amount)
        assertEquals(LocalDate.of(2026, 11, 21), results.first().date)
        assertEquals(BankOperation.REFUND, results.last().operation)
    }

    @Test fun `explicit total already includes fee while a separate debit fee is added once`() {
        val separate = listOf("Пополнение", "Отправлено 15 000 RUB", "Комиссия 150 RUB", "Зачислено 80 900 KZT")
        for (lines in listOf(separate, separate + "Итого 15 150 RUB")) {
            val result = BankImport.candidates(lines, Currency.KZT).single()
            assertEquals(BigDecimal("15150"), result.counterpart?.amount)
            assertTrue(result.verifiedTotal)
        }
    }

    @Test fun `home currency payment stays a purchase`() {
        val result = BankImport.candidates(listOf("Покупка 1 000 RUB", "Списано 12 USD"), Currency.USD).single()
        assertEquals(BankOperation.PURCHASE, result.operation)
        assertEquals(Currency.RUB, result.counterpart?.currency)
    }
}
