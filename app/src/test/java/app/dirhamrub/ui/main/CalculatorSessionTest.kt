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
import java.io.IOException
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
    @Test fun addingManualRateRecoversPendingAmountWithoutRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val wallet = Wallet(spend = Currency.AED)
            val date = LocalDate.of(2026, 11, 27)
            val rates = OfficialRates(date, mapOf(Currency.AED to BigDecimal("22")))
            val settings = MemoryPreferences(preferencesOf(
                stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
                stringPreferencesKey("settings_rates_update") to RatesUpdate.MANUAL.name,
                stringPreferencesKey("expression") to "250",
            ))
            val market = MemoryPreferences(preferencesOf(stringPreferencesKey("official_rates") to WalletCodec.encodeRates(rates)))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), market, dispatcher),
                guideDispatcher = dispatcher, calculationDispatcher = dispatcher, sources = Offline)
            store.put("vm", vm)
            runCurrent()
            vm.setSpend(Currency.VND, "VN")
            assertNotNull(vm.state.value.pendingConversion)
            vm.saveCard(BankCard("manual", "manual", Currency.RUB, 0,
                manual = listOf(ManualRate(Currency.VND, Currency.RUB, BigDecimal("0.005"), date))))
            assertEquals(0, BigDecimal("1100000").compareTo(vm.state.value.totals.spend))
            assertNull(vm.state.value.pendingConversion)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun sameCurrencyNeedsNeitherCardsNorDownloadedRates() {
        val state = MainUiState(wallet = Wallet(spend = Currency.RUB), expression = Expression.of(BigDecimal("250")))
        assertEquals(BigDecimal.ONE, state.homePerUnit)
        assertEquals(BigDecimal("250"), state.totals.home)
    }

    @Test fun `reverse input keeps the displayed total when changing card and input direction`() = runTest {
        val date = LocalDate.now()
        fun card(id: String, price: String) = BankCard(id, id, Currency.RUB, 0,
            manual = listOf(ManualRate(Currency.AED, Currency.RUB, BigDecimal(price), date)))
        withWallet(Wallet(cards = listOf(card("A", "22"), card("B", "25")))) { vm, _ ->
            vm.putAmount(BigDecimal("100"))
            vm.setActive(Line.HOME)
            vm.selectCard("B")
            assertEquals(0, BigDecimal("88").compareTo(vm.state.value.totals.spend))
            vm.setActive(Line.SPEND)
            assertEquals(0, BigDecimal("88").compareTo(vm.state.value.totals.spend))
            assertEquals(0, BigDecimal("2200").compareTo(vm.state.value.totals.home))
        }
    }

    @Test fun `input waits for a missing rate instead of becoming a zero price`() = runTest {
        val date = LocalDate.now()
        val card = BankCard("a", "a", Currency.RUB, 0, manual = listOf(
            ManualRate(Currency.LKR, Currency.RUB, BigDecimal("0.30"), date),
            ManualRate(Currency.LKR, Currency.EUR, BigDecimal("0.003"), date),
        ))
        val noRate = BankCard("b", "b", Currency.USD, 0)
        withWallet(Wallet(cards = listOf(card, noRate), spend = Currency.LKR),
            OfficialRates(date, mapOf(Currency.EUR to BigDecimal("100")))) { vm, _ ->
            vm.putAmount(BigDecimal("3000"))
            vm.setActive(Line.HOME)
            listOf(1, 0, 0, 0).forEach { vm.press(Key.Digit(it)) }
            vm.selectCard("b")
            assertNull(vm.state.value.displaySpend)
            vm.setHome(Currency.EUR)
            assertEquals(BigDecimal("1000"), vm.state.value.pendingConversion!!.expression.value)
            vm.selectCard("a")
            assertNull(vm.state.value.pendingConversion)
            assertEquals("10", Numbers.total(vm.state.value.totals.home!!, Rounding.NONE))
        }
    }

    @Test fun `failed card write leaves the stored and displayed wallet available for retry`() = runTest {
        val card = BankCard("a", "a", Currency.RUB, 0)
        withWallet(Wallet(cards = listOf(card))) { vm, settings ->
            settings.beforeUpdate = { throw IOException("disk full") }
            assertFalse(vm.saveCard(card.copy(name = "renamed")))
            assertEquals(card, vm.state.value.wallet.cards.single())
            assertFalse(vm.deleteCard(card.id))
            assertEquals(card, vm.state.value.wallet.cards.single())
            settings.beforeUpdate = {}
            assertTrue(vm.saveCard(card.copy(name = "renamed")))
            assertEquals("renamed", vm.state.value.wallet.cards.single().name)
        }
    }

    private suspend fun TestScope.withWallet(
        wallet: Wallet,
        rates: OfficialRates? = null,
        block: suspend (MainViewModel, MemoryPreferences) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val settings = MemoryPreferences(preferencesOf(
                stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
                stringPreferencesKey("settings_rates_update") to RatesUpdate.MANUAL.name,
            ))
            val market = if (rates == null) MemoryPreferences() else MemoryPreferences(preferencesOf(
                stringPreferencesKey("official_rates") to WalletCodec.encodeRates(rates),
            ))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), market, dispatcher),
                guideDispatcher = dispatcher, calculationDispatcher = dispatcher, sources = Offline)
            store.put("vm", vm)
            runCurrent()
            block(vm, settings)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

}
