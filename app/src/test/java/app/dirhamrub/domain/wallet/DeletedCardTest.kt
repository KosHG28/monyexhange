package app.dirhamrub.domain.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletedCardTest {
    private val a = BankCard("a", "Карта A", Currency.KZT, 0)
    private val b = BankCard("b", "Карта B", Currency.CNY, 1)
    private val c = BankCard("c", "Карта C", Currency.RUB, 2)
    private val wallet = Wallet(cards = listOf(a, b, c), selectedId = "b")

    @Test
    fun `удалили и вернули — на прежнем месте и снова выбрана`() {
        val deleted = wallet.deleted("b")!!
        assertEquals(DeletedCard(b, 1, true), deleted)
        val removed = wallet.remove("b")
        assertEquals(listOf(a, c), removed.cards)
        assertNull(removed.selectedId)
        val restored = removed.restore(deleted)
        assertEquals(listOf(a, b, c), restored.cards)
        assertEquals("b", restored.selectedId)
    }

    @Test
    fun `вернуть невыбранную — выбор не трогаем, место — в пределах списка`() {
        val deleted = wallet.deleted("c")!!
        val removed = wallet.remove("c").remove("a")
        val restored = removed.restore(deleted)
        assertEquals(listOf(b, c), restored.cards)
        assertEquals("b", restored.selectedId)
        // Уже на месте — второй раз не добавляем.
        assertEquals(restored, restored.restore(deleted))
        assertNull(wallet.deleted("нет"))
    }
}
