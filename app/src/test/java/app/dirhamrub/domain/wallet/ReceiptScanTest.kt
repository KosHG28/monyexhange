package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReceiptScanTest {
    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertTrue("ожидали $expected, получили $actual", actual != null && BigDecimal(expected).compareTo(actual) == 0)
    }

    private fun purchase(lines: List<String>, card: Currency, spend: Currency = Currency.AED): ScannedReceipt.Purchase =
        ReceiptScanner.guess(lines, card, spend, Currency.RUB) as ScannedReceipt.Purchase

    private fun transfer(lines: List<String>, card: Currency): ScannedReceipt.Transfer =
        ReceiptScanner.guess(lines, card, Currency.AED, Currency.RUB) as ScannedReceipt.Transfer

    @Test
    fun `числа в разных форматах`() {
        assertAmount("3881.00", ReceiptScanner.number("3 881,00"))
        assertAmount("1234.56", ReceiptScanner.number("1,234.56"))
        assertAmount("1234.56", ReceiptScanner.number("1.234,56"))
        assertAmount("304.76", ReceiptScanner.number("304.76"))
        assertAmount("304.76", ReceiptScanner.number("304,76"))
        assertAmount("15000", ReceiptScanner.number("15.000"))
        assertAmount("15000", ReceiptScanner.number("15 000"))
        assertAmount("12345678.9", ReceiptScanner.number("12 345 678,9"))
        assertNull(ReceiptScanner.number("12.3456"))
    }

    @Test
    fun `лишние цифры перед суммой отбрасываются`() {
        // Номер карты и дата слиплись с суммой: берём правильный хвост у знака валюты.
        assertAmount("15000", ReceiptScanner.number("1234 15 000"))
        assertAmount("15000", ReceiptScanner.number("30.10.2026 15 000"))
    }

    @Test
    fun `суммы с валютой до и после числа`() {
        val found = ReceiptScanner.amounts(listOf("Оплата AED 167.00", "-304,76 ¥", "Карта *1234", "15:42"))
        assertEquals(listOf(Currency.AED, Currency.CNY), found.map { it.currency })
        assertAmount("167.00", found[0].amount)
        assertAmount("304.76", found[1].amount)
    }

    @Test
    fun `покупка с тенговой карты, остаток не мешает`() {
        val p = purchase(listOf("Покупка 167,00 AED", "Списано 3 881,00 ₸", "Доступно 120 345,12 ₸"), Currency.KZT)
        assertEquals(Currency.AED, p.spend)
        assertAmount("3881", p.paid)
        assertAmount("167", p.price)
    }

    @Test
    fun `покупка с юаневой карты, коды валют`() {
        val p = purchase(listOf("Оплата 167.00 AED", "-304.76 CNY", "Баланс: 2 134.26 CNY"), Currency.CNY)
        assertEquals(Currency.AED, p.spend)
        assertAmount("304.76", p.paid)
        assertAmount("167", p.price)
    }

    @Test
    fun `покупка в другой валюте трат`() {
        val p = purchase(listOf("THB 1,250.00", "2 921,40 ₸"), Currency.KZT, spend = Currency.AED)
        assertEquals(Currency.THB, p.spend)
        assertAmount("2921.40", p.paid)
        assertAmount("1250", p.price)
    }

    @Test
    fun `пополнение из рублей`() {
        val t = transfer(listOf("Перевод на карту", "15 000 ₽", "Зачислено 80 900 ₸", "Комиссия 0 ₽"), Currency.KZT)
        assertEquals(Currency.RUB, t.sentCurrency)
        assertAmount("15000", t.sent)
        assertAmount("80900", t.got)
    }

    @Test
    fun `итог с комиссией больше суммы без неё`() {
        val t = transfer(listOf("Сумма 15 000 ₽", "Комиссия 150 ₽", "Итого 15 150 ₽", "Зачислено 80 900 ₸"), Currency.KZT)
        assertAmount("15150", t.sent)
    }

    @Test
    fun `знаки, которые латинский OCR читает по-своему`() {
        val t = transfer(listOf("Перевод 30 000 P", "Зачислено 2 439,02 ¥"), Currency.CNY)
        assertAmount("30000", t.sent)
        assertAmount("2439.02", t.got)
        val k = transfer(listOf("15 000 py6.", "80 900 T"), Currency.KZT)
        assertAmount("15000", k.sent)
        assertAmount("80900", k.got)
    }

    @Test
    fun `слова в русском тексте`() {
        val t = transfer(listOf("Вы отправили 15 000 рублей", "получено 80 900 тенге"), Currency.KZT)
        assertAmount("15000", t.sent)
        assertAmount("80900", t.got)
    }

    @Test
    fun `без суммы в валюте карты или второй валюты — ничего`() {
        assertNull(ReceiptScanner.guess(listOf("304,76 ¥"), Currency.CNY, Currency.AED, Currency.RUB))
        assertNull(ReceiptScanner.guess(listOf("167 AED", "15 000 ₽"), Currency.CNY, Currency.AED, Currency.RUB))
        assertNull(ReceiptScanner.guess(emptyList(), Currency.CNY, Currency.AED, Currency.RUB))
    }
}
