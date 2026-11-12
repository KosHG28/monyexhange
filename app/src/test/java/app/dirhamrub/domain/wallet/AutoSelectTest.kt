package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Автовыбор способа оплаты: пока человек не выбрал сам, считаем лучшим. */
class AutoSelectTest {
    private val tenge = BankCard("kzt", "Карта в тенге", Currency.KZT, 0)
    private val yuan = BankCard("cny", "Карта в юанях", Currency.CNY, 1)
    private val cash = BankCard("cash", "Наличные AED", Currency.AED, 2, kind = CardKind.CASH)
    private val wallet = Wallet(cards = listOf(tenge, yuan, cash), spend = Currency.AED)

    @Test
    fun `без ручного выбора — лучший`() {
        assertEquals("cash", wallet.pick("cash")?.id)
        // Лучший сменился (новый чек) — автовыбор идёт за ним.
        assertEquals("cny", wallet.pick("cny")?.id)
        // Лучший неизвестен (нет курсов) — первый подходящий.
        assertEquals("kzt", wallet.pick(null)?.id)
    }

    @Test
    fun `ручной выбор держится, касание лучшего возвращает автовыбор`() {
        val chosen = wallet.choose("kzt", bestId = "cash")
        assertEquals("kzt", chosen.selectedId)
        assertEquals("kzt", chosen.pick("cash")?.id)
        val back = chosen.choose("cash", bestId = "cash")
        assertNull(back.selectedId)
        assertEquals("cny", back.pick("cny")?.id)
    }

    @Test
    fun `выбранным нельзя заплатить — лучший`() {
        // Наличные дирхамы выбраны, а тратим драмы: ими не заплатить — считаем лучшим из карт.
        val chosen = wallet.choose("cash", bestId = "cny").withSpend(Currency.AMD)
        assertEquals("cash", chosen.selectedId)
        assertEquals("cny", chosen.pick("cny")?.id)
        // Вернулись к дирхамам — снова наличные.
        assertEquals("cash", chosen.withSpend(Currency.AED).pick("cny")?.id)
    }

    @Test
    fun `добавление и удаление карты не меняют выбор`() {
        val chosen = wallet.choose("kzt", bestId = "cash")
        val added = chosen.upsert(BankCard("usd", "Карта в долларах", Currency.USD, 3))
        assertEquals("kzt", added.selectedId)
        assertNull(Wallet().upsert(tenge).selectedId)
        // Удалили выбранную — снова автовыбор; удалили другую — выбор остаётся.
        assertNull(chosen.remove("kzt").selectedId)
        assertEquals("kzt", chosen.remove("cny").selectedId)
    }
}
