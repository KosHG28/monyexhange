package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import app.dirhamrub.domain.NBSP
import app.dirhamrub.domain.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateEngineTest {
    private val day = LocalDate.of(2026, 10, 29)
    private fun bd(s: String) = BigDecimal(s)

    private val tenge = BankCard(
        id = "kzt", name = "Карта в тенге", currency = Currency.KZT, style = 0,
        transfer = TransferReceipt(bd("15000"), Currency.RUB, bd("80900"), day),
        purchases = listOf(PurchaseReceipt(Currency.AED, bd("2757.48"), bd("22"), day)),
    )
    private val yuan = BankCard(
        id = "cny", name = "Карта в юанях", currency = Currency.CNY, style = 1,
        transfer = TransferReceipt(bd("30000"), Currency.RUB, bd("2439.02"), day),
        purchases = listOf(PurchaseReceipt(Currency.AED, bd("691.65"), bd("379"), day)),
    )
    private val official = OfficialRates(
        day,
        mapOf(
            Currency.AED to bd("22.19"),
            Currency.CNY to bd("11.42"),
            Currency.KZT to bd("0.159"),
            Currency.THB to bd("2.5"),
            Currency.USD to bd("81.5"),
        ),
    )

    @Test
    fun `курс по чекам как раньше`() {
        val q = RateEngine.quote(tenge, Currency.AED, Currency.RUB, official)!!
        assertEquals("23,2398", Numbers.rate(q.homePerUnit))
        assertEquals(RateBasis.RECEIPTS, q.basis)
        val y = RateEngine.quote(yuan, Currency.AED, Currency.RUB, null)!!
        assertEquals("22,4467", Numbers.rate(y.homePerUnit))
        assertEquals("1,8249", Numbers.rate(y.cardPerUnit))
    }

    @Test
    fun `сверка с ЦБ`() {
        val q = RateEngine.quote(tenge, Currency.AED, Currency.RUB, official)!!
        // 23,2398 / 22,19 − 1 = 4,73 %
        assertEquals("4,7", Numbers.percent(q.vsOfficial!!))
        assertNull(RateEngine.quote(tenge, Currency.AED, Currency.RUB, null)!!.vsOfficial)
    }

    @Test
    fun `лучшая карта и экономия`() {
        val w = Wallet(cards = listOf(tenge, yuan), spend = Currency.AED)
        val all = RateEngine.quoteAll(w, official)
        assertEquals("cny", all.best!!.card.id)
        assertEquals("3,4", Numbers.percent(all.bestSavingsPercent!!))
        assertEquals("22,19", Numbers.rate(all.official!!, 2))
    }

    @Test
    fun `сравнение выбранной карты с самой дешёвой из остальных`() {
        val all = RateEngine.quoteAll(Wallet(cards = listOf(tenge, yuan), spend = Currency.AED), official)
        val dear = all.compare("kzt")!!
        assertEquals("cny", dear.other.card.id)
        assertFalse(dear.selectedIsBest)
        // (23,2398 − 22,4467) / 23,2398 = 3,4 %; на 100 AED — 79,31 ₽
        assertEquals("3,4", Numbers.percent(dear.percent))
        assertEquals("79,31", Numbers.money(dear.difference(bd("100"))))
        assertTrue(dear.isExact)
        val best = all.compare("cny")!!
        assertEquals("kzt", best.other.card.id)
        assertTrue(best.selectedIsBest)
        assertEquals("3,4", Numbers.percent(best.percent))
        assertEquals("79,31", Numbers.money(best.difference(bd("100"))))
        assertNull(all.compare("nope"))
        assertNull(RateEngine.quoteAll(Wallet(cards = listOf(tenge), spend = Currency.AED), official).compare("kzt"))
    }

    @Test
    fun `сравнение по оценочным курсам не точное`() {
        // Чеков покупки в батах нет: обе карты — курс ЦБ с наценкой.
        val all = RateEngine.quoteAll(Wallet(cards = listOf(tenge, yuan), spend = Currency.THB), official)
        assertFalse(all.compare("kzt")!!.isExact)
    }

    @Test
    fun `итог в тенге — тенговая карта платит ровно по чеку покупки`() {
        val q = RateEngine.quote(tenge, Currency.AED, Currency.KZT, official)!!
        assertEquals("125,34", Numbers.rate(q.homePerUnit))
        assertEquals(RateBasis.RECEIPTS, q.basis)
    }

    @Test
    fun `трата в валюте карты — курс пополнения`() {
        val q = RateEngine.quote(tenge, Currency.KZT, Currency.RUB, official)!!
        assertEquals("0,1854", Numbers.rate(q.homePerUnit))
        assertEquals(0, BigDecimal.ONE.compareTo(q.cardPerUnit))
    }

    @Test
    fun `без чека покупки — курс ЦБ с наценкой карты`() {
        val m = RateEngine.markup(yuan, official)!!
        val q = RateEngine.quote(yuan, Currency.THB, Currency.RUB, official)!!
        assertEquals(RateBasis.ESTIMATE, q.basis)
        val expectedCardPer = official.cross(Currency.THB, Currency.CNY)!!.multiply(BigDecimal.ONE.add(m, MC), MC)
        assertEquals(0, expectedCardPer.compareTo(q.cardPerUnit))
        // Наценка карты к ЦБ та же, что и в дирхамах (если пополнение по курсу ЦБ).
        assertEquals(Numbers.percent(m.multiply(BigDecimal(100))), Numbers.percent(RateEngine.markup(yuan, official)!!.multiply(BigDecimal(100))))
    }

    @Test
    fun `карта без чеков — чистый курс ЦБ`() {
        val usd = BankCard("usd", "Долларовая", Currency.USD, 2)
        val q = RateEngine.quote(usd, Currency.THB, Currency.RUB, official)!!
        assertEquals(RateBasis.OFFICIAL, q.basis)
        assertEquals("2,5", Numbers.rate(q.homePerUnit))
        assertEquals("0", Numbers.percent(q.vsOfficial!!))
        assertNull(RateEngine.quote(usd, Currency.THB, Currency.RUB, null))
    }

    @Test
    fun `пополнение не из итоговой валюты приводится по ЦБ`() {
        val card = BankCard(
            "x", "Карта", Currency.THB, 3,
            transfer = TransferReceipt(bd("100"), Currency.USD, bd("3200"), day),
        )
        val q = RateEngine.quote(card, Currency.THB, Currency.RUB, official)!!
        // 100 $ → 3200 ฿: 1 ฿ = 0,03125 $ = 0,03125 × 81,5 ₽
        assertEquals("2,5469", Numbers.rate(q.homePerUnit))
        assertEquals(RateBasis.ESTIMATE, q.basis)
    }

    @Test
    fun `ручной курс важнее чеков`() {
        val manual = tenge.copy(manual = listOf(ManualRate(Currency.AED, Currency.RUB, bd("24"), day)))
        val q = RateEngine.quote(manual, Currency.AED, Currency.RUB, official)!!
        assertEquals(RateBasis.MANUAL, q.basis)
        assertEquals("24", Numbers.rate(q.homePerUnit))
        // Для другой итоговой валюты ручной курс не действует.
        assertEquals(RateBasis.RECEIPTS, RateEngine.quote(manual, Currency.AED, Currency.KZT, official)!!.basis)
    }

    @Test
    fun `последний чек покупки побеждает`() {
        val card = tenge.copy(
            purchases = tenge.purchases + PurchaseReceipt(Currency.AED, bd("1300"), bd("10"), day.plusDays(2)),
        )
        assertEquals("130", Numbers.rate(RateEngine.quote(card, Currency.AED, Currency.KZT, official)!!.homePerUnit))
    }

    @Test
    fun `кошелёк`() {
        val w = Wallet().upsert(tenge).upsert(yuan)
        // Ручного выбора нет — без известного лучшего первая подходящая.
        assertEquals("kzt", w.pick(null)!!.id)
        assertEquals(2, w.nextStyle())
        val renamed = w.upsert(tenge.copy(name = "Kaspi"))
        assertEquals("Kaspi", renamed.cards.first().name)
        assertEquals(2, renamed.cards.size)
        assertEquals("cny", w.remove("kzt").pick(null)!!.id)
        assertTrue(Wallet().remove("x").cards.isEmpty())
    }

    @Test
    fun `сумма в итоговой валюте`() {
        val q = RateEngine.quote(yuan, Currency.AED, Currency.RUB, official)!!
        assertEquals("8${NBSP}507,31", Numbers.money(BigDecimal("379").multiply(q.homePerUnit, MC)))
    }
}
