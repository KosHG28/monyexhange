package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class TransferHistoryTest {
    private fun d(s: String) = BigDecimal(s)
    private val first = TransferReceipt(d("10000"), Currency.RUB, d("1000"), LocalDate.of(2026, 11, 1))
    private val second = TransferReceipt(d("10000"), Currency.RUB, d("900"), LocalDate.of(2026, 11, 8))
    private val card = BankCard("c", "Карта в юанях", Currency.CNY, 1).withNewTransfer(first).withNewTransfer(second)

    private fun rate(c: BankCard) = RateEngine.quote(c, Currency.CNY, Currency.RUB, null, null)!!.homePerUnit.setScale(2, RoundingMode.HALF_UP)

    @Test
    fun `новое пополнение уходит в историю прежнее`() {
        assertEquals(second, card.transfer)
        assertEquals(listOf(first), card.history)
    }

    @Test
    fun `последний и средний курс`() {
        // 10 000 ₽ → 900 ¥: 11,11 ₽ за юань; в среднем 20 000 / 1 900 = 10,53.
        assertEquals(d("11.11"), rate(card))
        assertEquals(d("10.53"), rate(card.copy(transferMode = TransferMode.AVERAGE)))
        assertEquals(LocalDate.of(2026, 11, 8), card.copy(transferMode = TransferMode.AVERAGE).effectiveTransfer!!.date)
    }

    @Test
    fun `в среднем — только пополнения из той же валюты`() {
        val usd = TransferReceipt(d("100"), Currency.USD, d("700"), LocalDate.of(2026, 10, 1))
        val c = card.copy(history = card.history + usd, transferMode = TransferMode.AVERAGE)
        assertEquals(d("20000"), c.effectiveTransfer!!.sent)
        assertEquals(d("1900"), c.effectiveTransfer!!.got)
    }

    @Test
    fun `почём купили — взвешенное среднее`() {
        val a = TransferReceipt(d("100"), Currency.USD, d("367"), LocalDate.of(2026, 11, 1), d("80"), Currency.RUB)
        val b = TransferReceipt(d("300"), Currency.USD, d("1101"), LocalDate.of(2026, 11, 5), d("92"), Currency.RUB)
        val cash = BankCard("x", "Наличные AED", Currency.AED, 3, kind = CardKind.CASH, transferMode = TransferMode.AVERAGE)
            .withNewTransfer(a).withNewTransfer(b)
        val t = cash.effectiveTransfer!!
        assertEquals(d("400"), t.sent)
        // (100·80 + 300·92) / 400 = 89.
        assertEquals(d("89.00"), t.bought!!.first.setScale(2, RoundingMode.HALF_UP))
        // У одного неизвестно, почём купили — среднего «почём» нет.
        val mixed = cash.copy(history = listOf(a.copy(boughtAt = null, boughtIn = null)))
        assertNull(mixed.effectiveTransfer!!.bought)
    }

    @Test
    fun `история сохраняется`() {
        val c = card.copy(transferMode = TransferMode.AVERAGE)
        val back = WalletCodec.decode(WalletCodec.encode(Wallet(cards = listOf(c))))!!.cards.single()
        assertEquals(c.history, back.history)
        assertEquals(TransferMode.AVERAGE, back.transferMode)
        assertEquals(c.transfer, back.transfer)
    }

    @Test
    fun `история не растёт бесконечно`() {
        var c = BankCard("c", "", Currency.CNY, 1)
        repeat(HISTORY_MAX + 5) { i -> c = c.withNewTransfer(first.copy(got = d((1000 + i).toString()))) }
        assertEquals(HISTORY_MAX, c.history.size)
    }
}
