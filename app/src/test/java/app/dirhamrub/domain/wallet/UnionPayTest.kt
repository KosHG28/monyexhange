package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UnionPayTest {
    private val day = LocalDate.of(2026, 11, 6)

    // Кусок настоящего файла m.unionpayintl.com/jfimg/20261106.json (плюс пара строк для проверки разбора).
    private val json = """{"exchangeRateJson":[{"transCur":"AED","baseCur":"AUD","rateData":0.39526384},""" +
        """{"transCur":"ARS","baseCur":"AUD","rateData":9.7716E-4},{"transCur":"AUD","baseCur":"AUD","rateData":1},""" +
        """{"transCur":"LKR","baseCur":"CNY","rateData":0.02015521},{"transCur":"LKR","baseCur":"USD","rateData":0.003014},""" +
        """{"transCur":"MVR","baseCur":"USD","rateData":0.06487769},{"transCur":"MDL","baseCur":"USD","rateData":0.0572},""" +
        """{"transCur":"THB","baseCur":"CNY","rateData":0.20016733},{"transCur":"ZZZ","baseCur":"USD","rateData":5}],""" +
        """"curDate":"2026-11-06"}"""

    private val up = UnionPayParser.parse(json)!!

    /** Курсы ЦБ на 06.11.2026 (доллар, юань, бат); LKR и MVR у ЦБ нет. */
    private val cbr = OfficialRates(
        day,
        mapOf(Currency.USD to BigDecimal("81.7947"), Currency.CNY to BigDecimal("11.38"), Currency.THB to BigDecimal("2.45")),
    )

    @Test
    fun `разбор файла UnionPay`() {
        assertEquals(day, up.date)
        assertEquals(BigDecimal("0.02015521"), up.rate(Currency.LKR, Currency.CNY))
        assertEquals(BigDecimal("0.003014"), up.rate(Currency.LKR, Currency.USD))
        assertEquals(0, BigDecimal("0.00097716").compareTo(up.rate(Currency.ARS, Currency.AUD)))
        assertEquals(BigDecimal.ONE, up.rate(Currency.AUD, Currency.AUD))
        assertNull(up.rate(Currency.LKR, Currency.EUR))
        assertNull(UnionPayParser.parse("""{"exchangeRateJson":[]}"""))
        assertNull(UnionPayParser.parse("<html>Данные обновляются</html>"))
    }

    @Test
    fun `валюта расчёта по умолчанию`() {
        assertEquals(Currency.CNY, UnionPayRates.defaultSettlement(Currency.RUB))
        assertEquals(Currency.CNY, UnionPayRates.defaultSettlement(Currency.KZT))
        assertEquals(Currency.USD, UnionPayRates.defaultSettlement(Currency.USD))
        assertEquals(Currency.CNY, BankCard("c", "UnionPay", Currency.RUB, 0, unionPay = true).settlementCurrency)
        assertEquals(Currency.USD, BankCard("c", "UnionPay", Currency.RUB, 0, unionPay = true, settlement = Currency.USD).settlementCurrency)
    }

    @Test
    fun `справочный курс для валют без ЦБ`() {
        val ref = cbr.withUnionPay(up)
        // 1 LKR = 0,003014 $ × 81,7947 ₽ = 0,24653 ₽.
        assertEquals(0, BigDecimal("0.003014").multiply(BigDecimal("81.7947"), MC).compareTo(ref.rubPerUnit[Currency.LKR]))
        assertEquals(setOf(Currency.LKR, Currency.MVR), ref.derived)
        assertTrue(ref.viaUnionPay(Currency.LKR, Currency.RUB))
        assertFalse(ref.viaUnionPay(Currency.CNY, Currency.RUB))
        // Лей ЦБ публикует: даже без него в курсах его не берём у UnionPay.
        assertNull(ref.rubPerUnit[Currency.MDL])
        assertFalse(cbrPublishes(Currency.LKR, ref))
        assertTrue(cbrPublishes(Currency.CNY, ref))
        assertEquals(cbr, cbr.withUnionPay(null))
        val noUsd = OfficialRates(day, mapOf(Currency.CNY to BigDecimal("11.38")))
        assertEquals(noUsd, noUsd.withUnionPay(up))
    }

    @Test
    fun `карта в юанях UnionPay — курс UnionPay без чека`() {
        val card = BankCard(
            "cny", "Юани", Currency.CNY, 0, unionPay = true,
            transfer = TransferReceipt(BigDecimal("10000"), Currency.RUB, BigDecimal("870"), day),
        )
        val q = RateEngine.quote(card, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!
        assertEquals(RateBasis.UNIONPAY, q.basis)
        assertEquals(BigDecimal("0.02015521"), q.cardPerUnit)
        val expected = BigDecimal("0.02015521").multiply(BigDecimal("10000").divide(BigDecimal("870"), MC), MC)
        assertEquals(0, expected.compareTo(q.homePerUnit))
        assertTrue(q.basis.approximate)
    }

    @Test
    fun `рублёвая карта UnionPay — курс банка из чека в юанях`() {
        val withReceipt = BankCard(
            "rub", "Рубли", Currency.RUB, 0, unionPay = true,
            purchases = listOf(PurchaseReceipt(Currency.CNY, BigDecimal("1180"), BigDecimal("100"), day)),
        )
        val q = RateEngine.quote(withReceipt, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!
        assertEquals(RateBasis.UNIONPAY, q.basis)
        assertEquals(0, BigDecimal("0.02015521").multiply(BigDecimal("11.8"), MC).compareTo(q.homePerUnit))

        // Без чека в юанях — оценка: юань по ЦБ с наценкой карты (чеков нет — без наценки).
        val bare = withReceipt.copy(purchases = emptyList())
        val e = RateEngine.quote(bare, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!
        assertEquals(RateBasis.ESTIMATE, e.basis)
        assertEquals(0, BigDecimal("0.02015521").multiply(BigDecimal("11.38"), MC).compareTo(e.homePerUnit))

        // Чек в валюте трат точнее всего.
        val lkr = withReceipt.copy(purchases = withReceipt.purchases + PurchaseReceipt(Currency.LKR, BigDecimal("620"), BigDecimal("2500"), day))
        assertEquals(RateBasis.RECEIPTS, RateEngine.quote(lkr, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!.basis)

        // Покупка в самой валюте расчёта — как у обычной карты.
        assertEquals(RateBasis.RECEIPTS, RateEngine.quote(withReceipt, Currency.CNY, Currency.RUB, cbr, up)!!.basis)
        assertEquals(
            RateEngine.quote(bare.copy(unionPay = false), Currency.CNY, Currency.RUB, cbr, up),
            RateEngine.quote(bare, Currency.CNY, Currency.RUB, cbr, up)?.let { it.copy(card = it.card.copy(unionPay = false)) },
        )
    }

    @Test
    fun `без курсов UnionPay и у обычной карты — справочный курс`() {
        val card = BankCard("rub", "Рубли", Currency.RUB, 0, unionPay = true)
        // Курсов UnionPay нет — у ЦБ тоже нет LKR: курса нет.
        assertNull(RateEngine.quote(card, Currency.LKR, Currency.RUB, cbr, null))
        // Обычная рублёвая карта без чеков: справочный курс UnionPay × ЦБ — оценка, как с курсом ЦБ.
        val plain = BankCard("p", "Обычная", Currency.RUB, 0)
        val q = RateEngine.quote(plain, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!
        assertEquals(RateEngine.quote(plain, Currency.CNY, Currency.RUB, cbr)!!.basis, q.basis)
        assertEquals(cbr.withUnionPay(up).rubPerUnit[Currency.LKR], q.homePerUnit)
        assertTrue(q.viaUnionPay)
        val wallet = Wallet(cards = listOf(plain), spend = Currency.LKR)
        assertTrue(RateEngine.quoteAll(wallet, cbr.withUnionPay(up), up).viaUnionPay)
        assertFalse(RateEngine.quoteAll(wallet.copy(spend = Currency.CNY), cbr.withUnionPay(up), up).viaUnionPay)
        // Наличные UnionPay не бывают.
        val cash = BankCard("cash", "Наличные", Currency.CNY, 0, kind = CardKind.CASH, unionPay = true)
        assertNotNull(RateEngine.quote(cash, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up))
        assertEquals(RateBasis.OFFICIAL, RateEngine.quote(cash, Currency.LKR, Currency.RUB, cbr.withUnionPay(up), up)!!.basis)
    }

    @Test
    fun `карта UnionPay и курсы UnionPay сохраняются`() {
        val card = BankCard("rub", "Рубли", Currency.RUB, 0, unionPay = true, settlement = Currency.USD)
        val wallet = Wallet(cards = listOf(card, BankCard("p", "Обычная", Currency.KZT, 1)))
        assertEquals(wallet.cards, WalletCodec.decode(WalletCodec.encode(wallet))!!.cards)

        val text = WalletCodec.encodeUnionPay(up)
        assertTrue(WalletCodec.isCurrentTable(text))
        assertEquals(up, WalletCodec.decodeUnionPay(text))
        assertNull(WalletCodec.decodeUnionPay(null))
    }
}
