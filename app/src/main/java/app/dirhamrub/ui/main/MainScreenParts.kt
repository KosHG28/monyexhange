package app.dirhamrub.ui.main

import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardComparison
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.StaleReceipt
import app.dirhamrub.domain.wallet.cbrPublishes

/* Общие для частей главного экрана размеры и расчёты. */

internal val CARDS_TOP = 6.dp
internal val CARDS_HEIGHT = 108.dp

/** Строка выбранной карты на калькуляторе. */
internal val PICKER_HEIGHT = 60.dp
internal val ROW = 48.dp
internal val TEXT_ROW = 20.dp
internal const val HERO_SP = 60
internal const val HERO_COMPACT_SP = 46

/** Подпись под суммой нужна только в особых случаях: курса нет, оценка, курс ЦБ, ручной курс. */
internal fun basisText(state: MainUiState): RateBasis? {
    if (state.homePerUnit == null) return RateBasis.OFFICIAL
    val q = state.selectedQuote ?: return null
    // Наличные по обмену — подпись «по обмену …», чтобы было видно, откуда курс.
    if (q.card.kind == CardKind.CASH && q.basis == RateBasis.RECEIPTS && q.card.transfer != null) return RateBasis.RECEIPTS
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
    // У наличных чеков покупок нет: платят ровно ценой. Не хватать может обмена или курса отданной валюты.
    if (q.card.kind == CardKind.CASH) return q.card.takeIf { cashMissing(state) != null }
    return q.card.takeIf { q.basis == RateBasis.ESTIMATE || q.basis == RateBasis.OFFICIAL || q.basis == RateBasis.UNIONPAY }
}

/** Чего не хватает наличным для точного курса: обмена или того, почём купили отданную в обменнике валюту. */
internal enum class CashMissing { EXCHANGE, BOUGHT }

internal fun cashMissing(state: MainUiState): CashMissing? {
    val q = state.selectedQuote ?: return null
    if (q.card.kind != CardKind.CASH || q.basis == RateBasis.RECEIPTS || q.basis == RateBasis.MANUAL) return null
    val t = q.card.transfer?.takeIf { it.isValid } ?: return CashMissing.EXCHANGE
    // Из банкомата — курс по карте, с которой сняли: «почём купили» тут ни при чём.
    if (t.fromCard != null) return null
    return CashMissing.BOUGHT.takeIf { t.sentCurrency != state.wallet.home && t.bought == null }
}

/**
 * Курсы ЦБ загружены, но курса валюты трат нет ни у ЦБ, ни у UnionPay (справочный):
 * считаем только по чекам и своему курсу.
 */
internal fun noOfficialForSpend(state: MainUiState): Boolean {
    val rates = state.reference ?: return false
    fun known(c: Currency) = cbrPublishes(c, rates) || c in rates.derived
    return !known(state.wallet.spend) || !known(state.wallet.home)
}

/** Сообщение «нет курса» называет UnionPay, только если его курсы есть (иначе — как раньше, только ЦБ). */
internal fun noRefText(state: MainUiState, cbrOnly: Int, both: Int): Pair<Int, String> =
    (if (state.activeUnionPay == null) cbrOnly else both) to state.wallet.spend.code

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
