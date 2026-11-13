package app.dirhamrub.ui.main

import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.wallet.BankCard
import java.math.BigDecimal

/** Atomic handoff: preserve the chosen country and old wallet, select the new card, carry the price. */
internal fun MainUiState.withCompletedOnboarding(card: BankCard?, spendAmount: BigDecimal?): MainUiState = copy(
    wallet = card?.let { wallet.upsert(it).copy(selectedId = it.id) } ?: wallet,
    active = if (spendAmount != null) Line.SPEND else active,
    expression = spendAmount?.let(Expression::of) ?: expression,
    onboardingDone = true,
)
