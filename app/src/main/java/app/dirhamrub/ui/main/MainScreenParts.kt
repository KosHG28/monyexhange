package app.dirhamrub.ui.main

import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardComparison
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.StaleReceipt

/* Общие для частей главного экрана размеры и расчёты. */

internal val CARDS_TOP = 6.dp
internal val CARDS_HEIGHT = 108.dp
internal val ROW = 48.dp
internal val TEXT_ROW = 20.dp
internal const val HERO_SP = 60
internal const val HERO_COMPACT_SP = 46

/** Подпись под суммой нужна только в особых случаях: курса нет, оценка, курс ЦБ, ручной курс. */
internal fun basisText(state: MainUiState): RateBasis? {
    if (state.homePerUnit == null) return RateBasis.OFFICIAL
    val q = state.selectedQuote ?: return null
    return q.basis.takeIf { it != RateBasis.RECEIPTS }
}

/** Устаревший чек выбранной карты — показываем вместо подписи «оценка». */
internal fun basisStale(state: MainUiState): StaleReceipt? = state.staleReceipt

/**
 * Карта, которой не хватает чека покупки в валюте трат: курс — оценка или чистый ЦБ,
 * а у валюты без курса ЦБ — курса нет совсем, пока нет чека.
 */
internal fun basisAction(state: MainUiState): BankCard? {
    val q = state.selectedQuote ?: return state.selectedCard?.takeIf { noOfficialForSpend(state) }
    return q.card.takeIf { q.basis == RateBasis.ESTIMATE || q.basis == RateBasis.OFFICIAL }
}

/** Курсы ЦБ загружены, но валюты трат среди них нет: считаем только по чекам и своему курсу. */
internal fun noOfficialForSpend(state: MainUiState): Boolean {
    val rates = state.official ?: return false
    return rates.cross(state.wallet.spend, state.wallet.home) == null
}

/** Сравнение с самой дешёвой из остальных карт, если разница заметна. */
internal fun comparison(state: MainUiState): CardComparison? =
    state.quote.compare(state.selectedCard?.id)?.takeIf { Numbers.percent(it.percent) != "0" }

internal fun valueText(state: MainUiState, line: Line): String =
    if (state.active == line) state.activeText else state.totals.of(line)?.let(Numbers::money) ?: "—"

internal fun Line.currency(state: MainUiState): Currency? = when (this) {
    Line.SPEND -> state.wallet.spend
    Line.HOME -> state.wallet.home
    Line.CARD -> state.selectedCard?.currency
}

/** «Дирхамы», «Рубли» или название карты — для подписей и TalkBack. */
internal fun lineName(state: MainUiState, line: Line): String =
    if (line == Line.CARD) state.selectedCard?.name.orEmpty() else (line.currency(state) ?: state.wallet.spend).shortRu
