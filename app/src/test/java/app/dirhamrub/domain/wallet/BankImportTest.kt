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
    @Test fun `confirmed debit with fee is never increased again`() {
        for (label in listOf("Списано с учётом комиссии", "Списано")) {
            val receipt = BankImport.candidates(
                listOf("Пополнение", "$label 10 100 RUB", "Комиссия 100 RUB", "Зачислено 100 USD"),
                Currency.USD,
            ).single()
            assertEquals(BigDecimal("10100"), receipt.counterpart?.amount)
        }
    }

    @Test fun `a declined or pending operation cannot be applied as a receipt`() {
        for (status in listOf("Операция отклонена", "В обработке", "Payment declined")) {
            val receipt = BankImport.candidates(
                listOf("Оплата", status, "Списано 75 USD", "5000 JPY"), Currency.USD,
            ).last()
            assertTrue(receipt.operation.blocked)
        }
        val statusBelowAmounts = BankImport.candidates(
            listOf("Payment 5000 JPY", "Debited 75 USD", "Payment declined"), Currency.USD,
        ).single()
        assertTrue(statusBelowAmounts.operation.blocked)
    }

    @Test fun `recognizes failure wording and associates repeated amounts with their operation`() {
        val purchase = listOf("Покупка 5 000 JPY", "Списано 75 USD")
        for (status in listOf("Оплата не проведена", "Операция не совершена", "Отказано", "Покупка отклонена 5 000 JPY")) {
            assertEquals(BankOperation.UNSUCCESSFUL, BankImport.candidates(purchase + status, Currency.USD).single().operation)
        }
        val different = BankImport.candidates(purchase + "Покупка отклонена 7 000 JPY", Currency.USD)
        assertEquals(listOf(BankOperation.PURCHASE, BankOperation.UNSUCCESSFUL), different.map { it.operation })
    }
}
