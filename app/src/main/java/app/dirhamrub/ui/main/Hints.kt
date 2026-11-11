package app.dirhamrub.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.StaleReceipt
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.theme.AppTheme

/**
 * Подсказка под плитками одной строкой: «чек устарел · обновить ›» или «≈ оценка · добавить чек ›»
 * (тап открывает редактор с пустым чеком покупки). Если исправлять нечего — ничего не показываем:
 * откуда курс, и так сказано в строке курса между полями.
 */
@Composable
internal fun Basis(state: MainUiState, onAddReceipt: (BankCard) -> Unit, onEditCard: (BankCard) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val card = state.selectedCard
    val stale = basisStale(state)
    if (stale != null && card != null) {
        StaleLine(stale, onOpen = { onEditCard(card) })
        return
    }
    val basis = basisText(state) ?: return
    val style = type.body.copy(fontSize = 14.sp)
    val q = state.selectedQuote
    val cash = card?.takeIf { it.kind == CardKind.CASH && q?.basis == RateBasis.RECEIPTS }
    val exchange = cash?.transfer
    val text = when {
        cash != null && exchange != null && exchange.fromCard != null -> stringResource(
            R.string.basis_atm,
            RuDates.short(exchange.date),
            "${Numbers.money(exchange.got)} ${cash.currency.symbol}",
            "${Numbers.money(exchange.sent)} ${exchange.sentCurrency.symbol}",
        )
        cash != null && exchange != null -> stringResource(
            R.string.basis_exchange,
            RuDates.short(exchange.date),
            "${Numbers.money(exchange.sent)} ${exchange.sentCurrency.symbol}",
            "${Numbers.money(exchange.got)} ${cash.currency.symbol}",
        )
        noOfficialForSpend(state) && (state.homePerUnit == null || q == null) ->
            noRefText(state, R.string.result_no_cbr, R.string.result_no_ref).let { (res, code) -> stringResource(res, code) }
        state.homePerUnit == null || q == null -> stringResource(R.string.result_no_rate)
        else -> basisShort(q)
    }
    val action = basisAction(state) ?: return
    // Наличным нужен не чек покупки, а обмен или курс, по которому купили отданную валюту.
    val missing = cashMissing(state)
    val actionLabel = when (missing) {
        CashMissing.EXCHANGE -> stringResource(R.string.cash_add_exchange)
        CashMissing.BOUGHT -> stringResource(R.string.cash_add_bought, action.transfer?.sentCurrency?.symbol.orEmpty())
        null -> stringResource(R.string.add_receipt)
    }
    val hintA11y = if (missing != null) {
        stringResource(R.string.cash_edit_a11y, action.name)
    } else {
        stringResource(R.string.add_receipt_a11y, state.wallet.spend.nameRu)
    }
    Row(
        modifier = Modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = hintA11y) { if (missing != null) onEditCard(action) else onAddReceipt(action) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (if (basis.approximate) "≈ " else "") + text,
            style = style,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(actionLabel, style = style.copy(fontWeight = FontWeight.Medium), color = colors.accent, maxLines = 1)
    }
}

/** «Чек от 12 окт · ЦБ с тех пор +3,1 % · обновить ›» — тап открывает карту, чтобы обновить чек. */
@Composable
private fun StaleLine(stale: StaleReceipt, onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val style = AppTheme.type.body.copy(fontSize = 14.sp)
    val date = RuDates.short(stale.date)
    val change = signedPercent(stale.cbrChange)
    val a11y = stringResource(R.string.stale_receipt_a11y, date, change)
    Row(
        modifier = Modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.stale_receipt, date, change),
            style = style,
            color = colors.warn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(stringResource(R.string.stale_receipt_action), style = style.copy(fontWeight = FontWeight.Medium), color = colors.accent, maxLines = 1)
    }
}
