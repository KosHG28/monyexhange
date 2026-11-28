package app.dirhamrub.data

import androidx.datastore.preferences.core.stringPreferencesKey
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.wallet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WalletWriteOrderTest {
    @Test fun `serialized background encoding cannot let an older pause overwrite newer wallet and input`() = runTest {
        val encoding = testScheduler
        val settings = MemoryPreferences()
        val repo = WalletRepository(settings, MemoryPreferences(), MemoryPreferences(), StandardTestDispatcher(encoding))
        val old = Wallet(cards = listOf(BankCard("old", "old", Currency.USD, 0)))
        val fresh = old.copy(cards = emptyList(), spend = Currency.EUR, place = "GR")
        val pause = async { repo.saveSession(old, Line.SPEND, Expression.of(BigDecimal.ONE)) }
        val edit = async { repo.saveWallet(fresh) }
        val input = async { repo.saveInput(Line.HOME, Expression.of(BigDecimal("123.45"))) }
        runCurrent()
        repeat(5) { encoding.runCurrent(); runCurrent() }
        pause.await(); edit.await(); input.await()
        assertEquals(fresh, WalletCodec.decode(settings.values.value[stringPreferencesKey("wallet")]))
        assertEquals("123,45", settings.values.value[stringPreferencesKey("expression")])
        assertEquals(Line.HOME.name, settings.values.value[stringPreferencesKey("active_line")])
    }

    @Test fun `widget refresh follows the committed write even when it takes longer than a timer`() = runTest {
        val card = BankCard("a", "before", Currency.USD, 0)
        val wallet = Wallet(cards = listOf(card))
        val settings = MemoryPreferences(androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
            androidx.datastore.preferences.core.booleanPreferencesKey("settings_pro") to true,
        ))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = WalletRepository(settings, MemoryPreferences(), MemoryPreferences(), dispatcher)
        val observed = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.widgetUpdates.collect { observed += repo.loadWidget()!!.wallet.cards.single().name }
        }
        runCurrent()
        assertEquals(listOf("before"), observed)
        val release = CompletableDeferred<Unit>()
        settings.beforeUpdate = { release.await() }
        val save = launch { repo.saveWallet(wallet.upsert(card.copy(name = "after"))) }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("before"), observed)
        release.complete(Unit)
        save.join()
        runCurrent()
        assertEquals(listOf("before", "after"), observed)
    }
}
