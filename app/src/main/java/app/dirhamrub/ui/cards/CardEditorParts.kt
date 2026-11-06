package app.dirhamrub.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.NumberField
import app.dirhamrub.ui.components.ReceiptLine
import app.dirhamrub.ui.components.ReceiptSection
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

/** Чтение скриншота банка: идёт, не нашли, нашли и заполнили чек. */
internal sealed interface ScanResult {
    data object Reading : ScanResult
    data object NotFound : ScanResult
    data class Found(val receipt: ScannedReceipt) : ScanResult
}

/** «Карта | Наличные» в редакторе. */
@Composable
internal fun KindSwitch(kind: CardKind, onChange: (CardKind) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(4.dp),
    ) {
        for (option in CardKind.entries) {
            val selected = option == kind
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.accentSoft else colors.surface)
                    .selectable(selected = selected, role = Role.Tab) { onChange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (option == CardKind.CASH) R.string.kind_cash else R.string.kind_card),
                    style = type.bodyStrong,
                    color = if (selected) colors.accent else colors.muted,
                )
            }
        }
    }
}

/** «Заполнить из скриншота банка» и что получилось. */
@Composable
internal fun ScreenshotButton(result: ScanResult?, card: Currency, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, enabled = result != ScanResult.Reading, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_screenshot), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.scan_button), style = type.bodyStrong, color = colors.ink)
            Text(stringResource(R.string.scan_hint), style = type.caption, color = colors.muted)
        }
    }
    val message = when (result) {
        null -> null
        ScanResult.Reading -> stringResource(R.string.scan_reading)
        ScanResult.NotFound -> stringResource(R.string.scan_not_found, card.symbol)
        is ScanResult.Found -> when (val r = result.receipt) {
            is ScannedReceipt.Purchase -> stringResource(
                R.string.scan_purchase,
                "${Numbers.money(r.paid)} ${card.symbol}",
                "${Numbers.money(r.price)} ${r.spend.symbol}",
            )
            is ScannedReceipt.Transfer -> stringResource(
                R.string.scan_transfer,
                "${Numbers.money(r.sent)} ${r.sentCurrency.symbol}",
                "${Numbers.money(r.got)} ${card.symbol}",
            )
        }
    }
    if (message != null) {
        Text(
            text = message,
            style = type.caption.copy(fontSize = 13.sp),
            color = when (result) {
                ScanResult.NotFound -> colors.accent
                is ScanResult.Found -> colors.good
                else -> colors.muted
            },
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun CurrencyMark(symbol: String) {
    Text(symbol, style = AppTheme.type.mono, color = AppTheme.colors.muted, maxLines = 1, softWrap = false)
}

/** «✓ По этому чеку 1 ¥ = 12,3 ₽» — под заполненным чеком. */
@Composable
internal fun ReceiptRate(rate: String, whole: Boolean = false) {
    Text(
        // [whole] — готовая строка («✓ Итог: …»), иначе «✓ По этому чеку …».
        if (whole) rate else stringResource(R.string.receipt_rate, rate),
        style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = AppTheme.colors.good,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.muted, modifier = Modifier.padding(top = 2.dp))
}

/** Свой курс для текущей пары валют на единицу [unit]; серым в пустом поле — курс по чекам сейчас ([auto]). */
@Composable
internal fun ManualRatePanel(
    unit: BigDecimal,
    spend: Currency,
    home: Currency,
    value: String,
    onValueChange: (String) -> Unit,
    auto: BigDecimal?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_manual, "${Numbers.moneyWhole(unit)} ${spend.symbol}"),
                style = type.monoStrong.copy(fontSize = 15.sp),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = value,
                onValueChange = onValueChange,
                style = type.monoStrong.copy(fontSize = 17.sp),
                description = stringResource(R.string.editor_manual_hint),
                onDone = onDone,
                placeholder = auto?.let { Numbers.rate(it.multiply(unit, MC)) },
                modifier = Modifier.width(MANUAL_FIELD),
            )
            Spacer(Modifier.width(8.dp))
            CurrencyMark(home.symbol)
        }
        Hint(stringResource(R.string.editor_manual_hint))
    }
}

private val MANUAL_FIELD = 116.dp

/**
 * «Карта UnionPay» и валюта расчёта: UnionPay пересчитывает покупку в любой валюте по своему курсу
 * в валюту расчёта (обычно юань), банк — из неё в валюту карты.
 */
@Composable
internal fun UnionPaySection(
    enabled: Boolean,
    settlement: Currency,
    cardCurrency: Currency,
    onEnabled: (Boolean) -> Unit,
    onSettlement: (Currency) -> Unit,
    onMoreSettlements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabled),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_unionpay), style = type.bodyStrong, color = colors.ink)
                Text(stringResource(R.string.editor_unionpay_hint), style = type.caption, color = colors.muted)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.bg,
                    uncheckedBorderColor = colors.line,
                ),
            )
        }
        if (enabled) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.editor_settlement), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(6.dp))
            CurrencyChips(
                options = (UnionPayRates.BASES.take(SETTLEMENT_CHIPS) + settlement).distinct(),
                selected = settlement,
                onSelect = onSettlement,
                onMore = onMoreSettlements,
                moreLabel = stringResource(R.string.currency_more),
            )
            if (settlement != cardCurrency) {
                Spacer(Modifier.height(4.dp))
                Hint(stringResource(R.string.editor_settlement_hint, settlement.code, cardCurrency.code))
            }
        }
    }
}

/** Частые валюты расчёта чипами: юань, доллар, евро, гонконгский доллар; остальные — в списке. */
private const val SETTLEMENT_CHIPS = 4

/**
 * «Почём купили доллары»: отдавали в обменнике (или переводили на карту) не итоговую валюту — её курс
 * покупки делает итог точным. Пусто — по курсу ЦБ (серым в поле). Ниже — итог: единица карты в итоговой.
 */
@Composable
internal fun BoughtPlate(
    sentCurrency: Currency,
    home: Currency,
    card: Currency,
    unit: BigDecimal,
    value: String,
    onValueChange: (String) -> Unit,
    official: BigDecimal?,
    transfer: TransferReceipt?,
) {
    Spacer(Modifier.height(10.dp))
    ReceiptSection(stringResource(R.string.receipt_bought_title, sentCurrency.shortRu.lowercase()))
    val label = stringResource(R.string.receipt_bought, "${Numbers.moneyWhole(unit)} ${sentCurrency.symbol}")
    ReceiptLine(label, value, onValueChange, label, last = true, placeholder = official?.let { Numbers.rate(it.multiply(unit, MC)) }) {
        CurrencyMark(home.symbol)
    }
    val bought = Numbers.parse(value)?.takeIf { it.signum() > 0 }?.divide(unit, MC) ?: official
    if (transfer != null && bought != null) {
        val homePerCard = transfer.sent.divide(transfer.got, MC).multiply(bought, MC)
        val u = Numbers.unitFor(homePerCard)
        ReceiptRate(
            stringResource(
                R.string.receipt_bought_total,
                "${Numbers.moneyWhole(u)} ${card.symbol}",
                "${Numbers.rate(homePerCard.multiply(u, MC))} ${home.symbol}",
            ),
            whole = true,
        )
    }
    Hint(stringResource(if (value.isBlank()) R.string.receipt_bought_hint_empty else R.string.receipt_bought_hint))
}
