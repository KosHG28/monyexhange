package app.dirhamrub.ui.main

import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import app.dirhamrub.data.*
import app.dirhamrub.domain.*
import app.dirhamrub.domain.wallet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MarketRefreshTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val oldRates = OfficialRates(day, mapOf(Currency.EUR to BigDecimal("90"), Currency.USD to BigDecimal("80")))
    private val card = BankCard("rub", "RUB", Currency.RUB, 0)
    private val wallet = Wallet(cards = listOf(card), spend = Currency.EUR, selectedId = card.id, place = "GR")
    private class Sources : RateSources {
        var calls = 0
        var unionPayCalls = 0
        var fail = false
        val result = CompletableDeferred<CbrClient.Fetched?>()
        override suspend fun official(): CbrClient.Fetched? { calls++; if (fail) throw java.io.IOException("offline"); return result.await() }
        override suspend fun onDate(date: LocalDate): OfficialRates? = null
        override suspend fun unionPay(): UnionPayRates? {
            unionPayCalls++
            return null
        }
        override suspend fun guide(): Pair<String, CountryGuides>? = null
    }
    private fun preferences() = MemoryPreferences(preferencesOf(
        stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
        booleanPreferencesKey("selection_auto") to true,
        booleanPreferencesKey("settings_unionpay") to false,
        stringPreferencesKey("settings_rates_update") to RatesUpdate.MANUAL.name,
    ))

    @Test fun `background refresh retries against latest input trip and manual card selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val calculate = TestCoroutineScheduler()
        val source = Sources()
        try {
            val repo = WalletRepository(preferences(), MemoryPreferences(), MemoryPreferences(preferencesOf(
                stringPreferencesKey("official_rates") to WalletCodec.encodeRates(oldRates))), StandardTestDispatcher(testScheduler))
            val vm = MainViewModel(repo, guideDispatcher = StandardTestDispatcher(testScheduler),
                calculationDispatcher = StandardTestDispatcher(calculate), sources = source)
            store.put("vm", vm); runCurrent()
            vm.refreshRates(); runCurrent()
            assertEquals(1, source.unionPayCalls)
            vm.press(Key.Digit(1)); vm.press(Key.Digit(2)); runCurrent()
            val fresh = oldRates.copy(rubPerUnit = oldRates.rubPerUnit + (Currency.USD to BigDecimal("85")))
            source.result.complete(CbrClient.Fetched(fresh, "test")); runCurrent()
            assertTrue(vm.state.value.ratesLoading)
            vm.setSpend(Currency.USD, "US")
            vm.press(Key.Digit(6)); runCurrent()
            repeat(5) { calculate.runCurrent(); runCurrent() }
            val result = vm.state.value
            assertSame(fresh, result.official)
            assertEquals(Currency.USD, result.wallet.spend)
            assertEquals("US", result.wallet.place)
            assertEquals(card.id, result.selectedCard!!.id)
            assertEquals("6", result.expression.value.toPlainString())
            assertEquals(0, BigDecimal("510").compareTo(result.totals.home))
            assertFalse(result.ratesLoading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `navigation and settings do not emit per digit and pause saves latest queued input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = preferences()
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), MemoryPreferences(), StandardTestDispatcher(testScheduler)),
                guideDispatcher = StandardTestDispatcher(testScheduler), sources = Sources())
            store.put("vm", vm); runCurrent()
            val navigation = mutableListOf<MainUiState>()
            val themes = mutableListOf<AppSettings>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.navigation.toList(navigation) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.settings.toList(themes) }
            navigation.clear(); themes.clear()
            vm.flush() // Queued before the following input, should snapshot when it actually runs.
            vm.press(Key.Digit(9)); runCurrent()
            assertTrue(navigation.isEmpty()); assertTrue(themes.isEmpty())
            assertEquals("9", settings.values.value[stringPreferencesKey("expression")])
            vm.setTheme(ThemeMode.DARK); runCurrent()
            assertEquals(1, navigation.size); assertEquals(1, themes.size)
            assertEquals("9", navigation.single().expression.value.toPlainString())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `failed refresh clears the retry gate and preserves the previous calculation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val source = Sources().apply { fail = true }
        try {
            val vm = MainViewModel(WalletRepository(preferences(), MemoryPreferences(), MemoryPreferences(), StandardTestDispatcher(testScheduler)),
                guideDispatcher = StandardTestDispatcher(testScheduler), sources = source)
            store.put("vm", vm); runCurrent()
            vm.press(Key.Digit(4)); vm.refreshRates(); runCurrent()
            assertTrue(vm.state.value.ratesFailed); assertFalse(vm.state.value.ratesLoading)
            vm.refreshRates(); runCurrent()
            assertEquals(2, source.calls)
            assertEquals("4", vm.state.value.expression.value.toPlainString())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
