package app.dirhamrub.ui.main

import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Wallet
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OnboardingCompletionTest {
    private val old = BankCard("old", "Карта CNY", Currency.CNY, 1)
    private val first = BankCard("new", "Карта USD", Currency.USD, 2)
    private val state = MainUiState(wallet = Wallet(spend = Currency.EUR, home = Currency.RUB,
        place = "GR", cards = listOf(old), selectedId = old.id), active = Line.HOME,
        expression = Expression.of(BigDecimal("42")), onboardingDone = false)

    @Test fun `typed travel price and new card arrive together without replacing trip or existing cards`() {
        val result = state.withCompletedOnboarding(first, BigDecimal("1234.56"))
        assertTrue(result.onboardingDone)
        assertEquals(listOf(old, first), result.wallet.cards)
        assertEquals(first.id, result.wallet.selectedId)
        assertEquals(Currency.EUR, result.wallet.spend)
        assertEquals("GR", result.wallet.place)
        assertEquals(Line.SPEND, result.active)
        assertEquals(0, result.totals.spend.compareTo(BigDecimal("1234.56")))
    }

    @Test fun `skipping repeated onboarding keeps the current calculation and selection`() {
        val result = state.withCompletedOnboarding(null, null)
        assertTrue(result.onboardingDone)
        assertEquals(state.wallet, result.wallet)
        assertEquals(state.active, result.active)
        assertEquals(state.expression, result.expression)
    }

    @Test fun `explicit zero is carried while blank price preserves the existing calculation`() {
        assertEquals(Line.SPEND, state.withCompletedOnboarding(first, BigDecimal.ZERO).active)
        assertEquals(0, state.withCompletedOnboarding(first, BigDecimal.ZERO).totals.spend.signum())
        val blank = state.withCompletedOnboarding(first, null)
        assertEquals(state.active, blank.active)
        assertEquals(state.expression, blank.expression)
        assertEquals(first.id, blank.wallet.selectedId)
    }
}
