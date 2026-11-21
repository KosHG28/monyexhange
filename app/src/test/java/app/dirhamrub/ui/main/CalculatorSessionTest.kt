package app.dirhamrub.ui.main

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import app.dirhamrub.data.*
import app.dirhamrub.domain.*
import app.dirhamrub.domain.wallet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorSessionTest {
    private object Offline : RateSources {
        override suspend fun official(): CbrClient.Fetched? = null
        override suspend fun onDate(date: LocalDate): OfficialRates? = null
        override suspend fun unionPay(): UnionPayRates? = null
        override suspend fun guide(): Pair<String, CountryGuides>? = null
    }

    @Test fun returningFromCurrencyWithoutRateRestoresTheAmount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val wallet = Wallet(cards = emptyList(), spend = Currency.AED, home = Currency.RUB)
            val rates = OfficialRates(LocalDate.of(2026, 11, 21), mapOf(Currency.AED to BigDecimal("22")))
            val settings = MemoryPreferences(preferencesOf(
                stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
                stringPreferencesKey("settings_rates_update") to RatesUpdate.MANUAL.name,
                stringPreferencesKey("expression") to "250",
            ))
            val market = MemoryPreferences(preferencesOf(stringPreferencesKey("official_rates") to WalletCodec.encodeRates(rates)))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), market, dispatcher), guideDispatcher = dispatcher,
                calculationDispatcher = dispatcher, sources = Offline)
            store.put("vm", vm)
            runCurrent()
            assertEquals("250", vm.state.value.expression.serialize())
            assertNotNull(vm.state.value.homePerUnit)
            vm.setSpend(Currency.VND, "VN")
            assertNull(vm.state.value.homePerUnit)
            assertTrue(vm.state.value.expression.isEmpty)
            vm.flush()
            runCurrent()
            val restored = WalletRepository(settings, MemoryPreferences(), market, dispatcher).load()
            assertEquals("250", restored.pendingConversion!!.expression.serialize())
            vm.setSpend(Currency.AED, "AE")
            assertEquals("250", vm.state.value.expression.serialize())
            assertNull(vm.state.value.pendingConversion)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun togglingInputCurrencyRetainsSmallAmount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val wallet = Wallet(cards = emptyList(), spend = Currency.VND, home = Currency.RUB)
            val rates = OfficialRates(LocalDate.of(2026, 11, 21), mapOf(Currency.VND to BigDecimal("0.0032")))
            val settings = MemoryPreferences(preferencesOf(
                stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
                stringPreferencesKey("settings_rates_update") to RatesUpdate.MANUAL.name,
                stringPreferencesKey("expression") to "1",
            ))
            val market = MemoryPreferences(preferencesOf(stringPreferencesKey("official_rates") to WalletCodec.encodeRates(rates)))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), market, dispatcher), guideDispatcher = dispatcher,
                calculationDispatcher = dispatcher, sources = Offline)
            store.put("vm", vm)
            runCurrent()
            assertEquals("0.0032", vm.state.value.totals.home!!.toPlainString())
            vm.setActive(Line.HOME)
            assertEquals(BigDecimal("0.0032"), vm.state.value.expression.value)
            vm.setActive(Line.SPEND)
            assertEquals(BigDecimal.ONE, vm.state.value.totals.spend)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun finishedResultRestoresAsFreshButOldSessionsRemainEditable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MemoryPreferences(preferencesOf(stringPreferencesKey("expression") to "12"))
        val repo = WalletRepository(settings, MemoryPreferences(), MemoryPreferences(), dispatcher)
        assertEquals("123", repo.load().expression.press(Key.Digit(3)).serialize())
        repo.saveInput(Line.SPEND, Expression.of(BigDecimal(12)))
        assertEquals("3", repo.load().expression.press(Key.Digit(3)).serialize())
    }
}
