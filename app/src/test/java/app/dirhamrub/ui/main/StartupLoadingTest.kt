package app.dirhamrub.ui.main

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import app.dirhamrub.data.MemoryPreferences
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CountryGuides
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StartupLoadingTest {
    private val guide = "version=2026-11-14\n[AE]\nupdated=2026-11-14\nmir=no|Нет"

    @Test fun `slow guide does not hold calculator or overwrite early input and saved trip`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val guideScheduler = TestCoroutineScheduler()
        val store = ViewModelStore()
        try {
            val wallet = Wallet(cards = listOf(BankCard("saved", "Моя карта", Currency.USD, 0)),
                spend = Currency.EUR, selectedId = "saved", place = "GR")
            val settings = MemoryPreferences(preferencesOf(
                stringPreferencesKey("wallet") to WalletCodec.encode(wallet),
                stringPreferencesKey("expression") to "12",
                booleanPreferencesKey("selection_auto") to true,
                booleanPreferencesKey("settings_wifi_only") to true,
            ))
            // Hold legacy cleanup while the visible calculator already accepts input.
            val cleanup = CompletableDeferred<Unit>()
            settings.beforeUpdate = { cleanup.await() }
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), MemoryPreferences(), StandardTestDispatcher(testScheduler)),
                bundledGuide = { guide }, unmetered = { false }, guideDispatcher = StandardTestDispatcher(guideScheduler))
            store.put("startup", vm)
            runCurrent()
            assertTrue(vm.state.value.loaded)
            assertNull(vm.state.value.guides)
            assertEquals(wallet, vm.state.value.wallet)
            assertEquals("12", vm.state.value.expression.value.toPlainString())
            vm.press(Key.Digit(3))
            runCurrent()
            cleanup.complete(Unit)
            advanceTimeBy(301)
            runCurrent()
            assertEquals("123", settings.values.value[stringPreferencesKey("expression")])
            val before = vm.state.value
            guideScheduler.runCurrent()
            runCurrent()
            assertNotNull(vm.state.value.guides)
            assertEquals(before.copy(guides = vm.state.value.guides), vm.state.value)
            assertEquals(LocalDate.of(2026, 11, 14), vm.state.value.guides!!.version)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun `unreadable bundled guide falls back to saved copy without blocking startup`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = MemoryPreferences(preferencesOf(
                booleanPreferencesKey("selection_auto") to true,
                booleanPreferencesKey("settings_wifi_only") to true,
            ))
            val rates = MemoryPreferences(preferencesOf(stringPreferencesKey("country_guide") to guide))
            val vm = MainViewModel(WalletRepository(settings, MemoryPreferences(), rates, StandardTestDispatcher(testScheduler)),
                bundledGuide = { throw java.io.IOException("Unreadable asset") },
                unmetered = { false }, guideDispatcher = StandardTestDispatcher(testScheduler))
            store.put("startup", vm)
            runCurrent()
            assertTrue(vm.state.value.loaded)
            assertEquals(LocalDate.of(2026, 11, 14), vm.state.value.guides!!.version)
            assertFalse(vm.state.value.onboardingDone)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun `late old or missing guide cannot downgrade a newer downloaded guide`() {
        val old = CountryGuides(LocalDate.of(2026, 11, 1), emptyMap())
        val fresh = CountryGuides(LocalDate.of(2026, 11, 14), emptyMap())
        val state = MainUiState(loaded = true)
        assertSame(fresh, state.withLoadedGuides(fresh).withLoadedGuides(old).guides)
        assertSame(fresh, state.withLoadedGuides(old).withLoadedGuides(fresh).guides)
        assertSame(fresh, state.withLoadedGuides(fresh).withLoadedGuides(null).guides)
    }
}
