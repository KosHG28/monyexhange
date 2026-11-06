package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptAgeTest {
    private fun bd(s: String) = BigDecimal(s)
    private val today = LocalDate.of(2026, 11, 4)
    private val old = LocalDate.of(2026, 10, 12)
    private val now = OfficialRates(today, mapOf(Currency.AED to bd("21.97"), Currency.CNY to bd("11.42")))
    private val then = OfficialRates(old, mapOf(Currency.AED to bd("21.30"), Currency.CNY to bd("11.40")))

    private fun yuan(purchaseDate: LocalDate, transferDate: LocalDate = today) = BankCard(
        id = "cny", name = "Карта в юанях", currency = Currency.CNY, style = 1,
        transfer = TransferReceipt(bd("30000"), Currency.RUB, bd("2439.02"), transferDate),
        purchases = listOf(PurchaseReceipt(Currency.AED, bd("691.65"), bd("379"), purchaseDate)),
    )

    @Test
    fun `старый чек покупки и сдвиг ЦБ больше 2 процентов`() {
        val stale = ReceiptAge.check(yuan(old), Currency.AED, Currency.RUB, today, now, mapOf(old to then))!!
        assertEquals(old, stale.date)
        // AED в юанях: (21,97/11,42) / (21,30/11,40) − 1 = +2,96 %
        assertEquals("3", Numbers.percent(stale.cbrChange))
    }

    @Test
    fun `свежий чек, малый сдвиг или нет курса на дату — не устарел`() {
        val recent = today.minusDays(10)
        assertNull(ReceiptAge.check(yuan(recent), Currency.AED, Currency.RUB, today, now, mapOf(recent to then)))
        val calm = OfficialRates(old, mapOf(Currency.AED to bd("21.80"), Currency.CNY to bd("11.40")))
        assertNull(ReceiptAge.check(yuan(old), Currency.AED, Currency.RUB, today, now, mapOf(old to calm)))
        assertNull(ReceiptAge.check(yuan(old), Currency.AED, Currency.RUB, today, now, emptyMap()))
        assertNull(ReceiptAge.check(yuan(old), Currency.AED, Currency.RUB, today, null, mapOf(old to then)))
    }

    @Test
    fun `старый чек пополнения`() {
        // Пополнение юанями за рубли месяц назад, юань с тех пор подорожал на 3 %.
        val cnyThen = OfficialRates(old, mapOf(Currency.AED to bd("21.97"), Currency.CNY to bd("11.08")))
        val card = yuan(purchaseDate = today, transferDate = old)
        val stale = ReceiptAge.check(card, Currency.AED, Currency.RUB, today, now, mapOf(old to cnyThen))!!
        assertEquals("3,1", Numbers.percent(stale.cbrChange))
    }

    @Test
    fun `какие даты нужны для проверки`() {
        assertEquals(setOf(old), ReceiptAge.datesToCheck(yuan(old), Currency.AED, Currency.RUB, today))
        assertEquals(emptySet<LocalDate>(), ReceiptAge.datesToCheck(yuan(today.minusDays(3)), Currency.AED, Currency.RUB, today))
        // Ручной курс — чеки не используются.
        val manual = yuan(old).copy(manual = listOf(ManualRate(Currency.AED, Currency.RUB, bd("22"), old)))
        assertEquals(emptySet<LocalDate>(), ReceiptAge.datesToCheck(manual, Currency.AED, Currency.RUB, today))
    }
}
