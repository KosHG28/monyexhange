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
        val archiveRequests = mutableListOf<LocalDate>()
        var archive: Map<LocalDate, OfficialRates> = emptyMap()
        override suspend fun onDate(date: LocalDate): OfficialRates? {
            archiveRequests.add(date)
            return archive[date]
        }
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
    @Test fun `refresh from an older mirror keeps the newer downloaded table`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val source = Sources()
        try {
            val market = MemoryPreferences(preferencesOf(
                stringPreferencesKey("official_rates") to WalletCodec.encodeRates(oldRates),
                longPreferencesKey("official_fetched_at") to 123L,
            ))
            val repo = WalletRepository(preferences(), MemoryPreferences(), market, dispatcher)
            val vm = MainViewModel(repo, guideDispatcher = dispatcher, calculationDispatcher = dispatcher, sources = source)
            store.put("vm", vm)
            runCurrent()
            val older = oldRates.copy(date = oldRates.date.minusDays(2))
            source.result.complete(CbrClient.Fetched(older, CbrClient.SOURCE_MIRROR))
            vm.refreshRates()
            runCurrent()
            assertEquals(oldRates.date, vm.state.value.official?.date)
            assertEquals(oldRates.date, repo.load().official?.date)
            assertEquals(123L, vm.state.value.officialFetchedAt)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `missing historic tables never prevent loading another receipt date`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val days = (0L..5L).map { LocalDate.now().minusDays(40 + it) }
            val available = days.last()
            val source = Sources().apply { archive = mapOf(available to oldRates.copy(date = available)) }
            val cards = days.mapIndexed { i, date ->
                BankCard("card-$i", "card-$i", Currency.EUR, i,
                    transfer = TransferReceipt(BigDecimal("1000"), Currency.RUB, BigDecimal.TEN, date))
            }
            val settings = preferences().also { it.updateData { current ->
                current.toMutablePreferences().apply { this[stringPreferencesKey("wallet")] = WalletCodec.encode(wallet.copy(cards = cards)) }
            } }
            val market = MemoryPreferences(preferencesOf(stringPreferencesKey("official_rates") to WalletCodec.encodeRates(oldRates)))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), market, dispatcher),
                guideDispatcher = dispatcher, calculationDispatcher = dispatcher, sources = source)
            store.put("vm", vm)
            runCurrent()
            source.result.complete(null)
            vm.refreshRates()
            runCurrent()
            assertTrue(source.archiveRequests.containsAll(days))
            assertNotNull(vm.state.value.ratesHistory[available])
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

}
