package app.dirhamrub.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WalletRepositoryStartupTest {
    @Test fun `load leaves the caller thread and restores wallet and exact expression`() = runBlocking {
        val wallet = Wallet(cards = listOf(BankCard("saved", "Карта USD", Currency.USD, 0)),
            spend = Currency.EUR, selectedId = "saved", place = "GR")
        val settings = MemoryPreferences(preferencesOf(
            stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
            stringPreferencesKey("expression") to "123456789012,34",
            booleanPreferencesKey("settings_pure_black") to true,
        ))
        val rates = MemoryPreferences()
        val unionPay = MemoryPreferences()
        val caller = Thread.currentThread()
        val loaded = WalletRepository(settings, unionPay, rates).load()
        assertEquals(wallet, loaded.wallet)
        assertEquals("123456789012.34", loaded.expression.value.toPlainString())
        assertTrue(loaded.settings.pureBlack)
        for (store in listOf(settings, rates, unionPay)) {
            assertEquals(1, store.readThreads.size)
            assertNotSame(caller, store.readThreads.single())
        }
    }

    @Test fun `a leftover legacy table cannot replace newer rates during migration`() = runBlocking {
        val day = LocalDate.of(2026, 11, 28)
        val current = OfficialRates(day, mapOf(Currency.EUR to BigDecimal("100")))
        val legacy = current.copy(date = day.minusDays(4), rubPerUnit = mapOf(Currency.EUR to BigDecimal("90")))
        val table = stringPreferencesKey("official_rates")
        val fetched = longPreferencesKey("official_fetched_at")
        val preferences = MemoryPreferences(preferencesOf(table to WalletCodec.encodeRates(legacy), fetched to 10L))
        val rates = MemoryPreferences(preferencesOf(table to WalletCodec.encodeRates(current), fetched to 20L))
        val repository = WalletRepository(preferences, MemoryPreferences(), rates)
        assertEquals(current, repository.load().official)
        assertEquals(20L, repository.load().officialFetchedAt)
        assertNull(preferences.values.value[table])
        Unit
    }
}
