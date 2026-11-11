package app.dirhamrub.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferMode
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.NumberField
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

/**
 * Платёжная система карты: 2×2 чипа, повторное касание снимает выбор. Нужна справочнику «Чем платить»
 * (примут ли карту в стране); UnionPay включает и пересчёт покупок по курсу UnionPay.
 */
@Composable
internal fun NetworkChips(network: CardNetwork?, onChange: (CardNetwork?) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in CardNetwork.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in row) {
                    val selected = option == network
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.accent else colors.surface)
                            .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(50))
                            .selectable(selected = selected, role = Role.RadioButton) { onChange(if (selected) null else option) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    CardNetwork.UNIONPAY -> R.string.network_unionpay
                                    CardNetwork.MIR -> R.string.network_mir
                                    CardNetwork.VISA_MC_RU -> R.string.network_short_visa_ru
                                    CardNetwork.VISA_MC_FOREIGN -> R.string.network_short_visa_foreign
                                },
                            ),
                            style = type.bodyStrong.copy(fontSize = 13.5.sp),
                            color = if (selected) colors.onAccent else colors.ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Прежние пополнения: «по последнему / в среднем» (если пополнений больше одного), список с датами и курсами
 * (удалить — «×»), средний курс и «+ Новое пополнение» — заполненное уходит в историю, поля очищаются.
 */
@Composable
internal fun TransferHistory(
    history: List<TransferReceipt>,
    mode: TransferMode,
    cardSymbol: String,
    average: TransferReceipt?,
    canAddNew: Boolean,
    /** «+ Новое пополнение» у карты, «+ Новый обмен» у наличных. */
    newLabel: String,
    onMode: (TransferMode) -> Unit,
    onRemove: (TransferReceipt) -> Unit,
    onNew: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    if (history.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(3.dp)
                .selectableGroup(),
        ) {
            for (option in TransferMode.entries) {
                val selected = option == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accentSoft else colors.surface)
                        .selectable(selected = selected, role = Role.RadioButton) { onMode(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (option == TransferMode.LAST) R.string.transfer_mode_last else R.string.transfer_mode_average),
                        style = type.bodyStrong.copy(fontSize = 13.5.sp),
                        color = if (selected) colors.accent else colors.muted,
                    )
                }
            }
        }
        if (average != null) {
            ReceiptRate(
                stringResource(
                    R.string.transfer_average,
                    Numbers.transferRate(average.got.divide(average.sent, MC), cardSymbol, average.sentCurrency.mark),
                    history.size + 1,
                ),
                whole = true,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.transfer_history), style = type.label, color = colors.muted)
        for (t in history) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    RuDates.short(t.date) + " · " + Numbers.money(t.sent) + " " + t.sentCurrency.mark + " → " +
                        Numbers.money(t.got) + " " + cardSymbol + " · " +
                        Numbers.transferRate(t.got.divide(t.sent, MC), cardSymbol, t.sentCurrency.mark),
                    style = type.caption,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                val remove = stringResource(R.string.transfer_remove)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = remove) { onRemove(t) }
                        .semantics { contentDescription = remove },
                    contentAlignment = Alignment.Center,
                ) { Text("×", style = type.bodyStrong, color = colors.muted) }
            }
        }
    }
    if (canAddNew) AddAction(newLabel, onNew)
}

/** «Заполнить из скриншота банка» и что получилось. */
@Composable
internal fun ScreenshotButton(result: ScanResult?, card: Currency, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    // Главная кнопка блока: избавляет от ручного ввода сумм ниже — бледно-синяя подложка, значок сканера.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentSoft)
            .clickable(role = Role.Button, enabled = result != ScanResult.Reading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_scan), contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.scan_button), style = type.bodyStrong, color = colors.accent)
            Text(stringResource(R.string.scan_hint), style = type.caption, color = colors.muted)
        }
    }
    val message = when (result) {
        null -> null
        ScanResult.Reading -> stringResource(R.string.scan_reading)
        ScanResult.NotFound -> stringResource(R.string.scan_not_found, card.mark)
        is ScanResult.Found -> when (val r = result.receipt) {
            is ScannedReceipt.Purchase -> stringResource(
                R.string.scan_purchase,
                "${Numbers.money(r.paid)} ${card.mark}",
                "${Numbers.money(r.price)} ${r.spend.mark}",
            )
            is ScannedReceipt.Transfer -> stringResource(
                R.string.scan_transfer,
                "${Numbers.money(r.sent)} ${r.sentCurrency.mark}",
                "${Numbers.money(r.got)} ${card.mark}",
            )
        }
    }
    if (message != null) {
        Text(
            text = message,
            style = type.caption.copy(fontSize = 13.sp),
            color = when (result) {
                ScanResult.NotFound -> colors.danger
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
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

@Composable
internal fun Hint(text: String) {
    // Подсказка под полем — один стиль во всём приложении: 12 sp, серым, 4 dp сверху.
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.muted, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
}

/**
 * Свой курс: по умолчанию выключен — на экране только итог по чекам. Включили — одно крупное поле
 * «1 000 ₩ = [62,50 ₽]» (₽ — серый знак внутри поля: итоговая валюта не выбирается).
 */
@Composable
internal fun ManualRatePanel(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    unit: BigDecimal,
    spend: Currency,
    home: Currency,
    value: String,
    onValueChange: (String) -> Unit,
    auto: BigDecimal?,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.line),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.editor_manual_toggle), style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            Hint(stringResource(if (on) R.string.editor_manual_on_hint else R.string.editor_manual_off_hint))
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = on,
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
    if (on) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_manual_unit, "${Numbers.moneyWhole(unit)} ${spend.mark}"),
                style = type.body.copy(fontSize = 17.sp),
                color = colors.muted,
            )
            Spacer(Modifier.width(10.dp))
            AmountField(
                label = null,
                value = value,
                onValueChange = onValueChange,
                description = stringResource(R.string.editor_manual_toggle),
                placeholder = auto?.let { Numbers.rate(it.multiply(unit, MC)) },
                trailing = { FieldSuffix(home.mark) },
                big = true,
                modifier = Modifier.weight(1f),
            )
        }
        Hint(stringResource(R.string.editor_manual_home_hint, home.shortRu))
    }
}

/**
 * Валюта расчёта UnionPay (блок появляется, только если выбрана система UnionPay): UnionPay пересчитывает
 * покупку в любой валюте по своему курсу в валюту расчёта (обычно юань), банк — из неё в валюту карты.
 */
@Composable
internal fun UnionPaySection(
    settlement: Currency,
    cardCurrency: Currency,
    onSettlement: (Currency) -> Unit,
    onMoreSettlements: () -> Unit,
) {
    FieldLabel(stringResource(R.string.editor_settlement))
    CurrencyChips(
        options = (UnionPayRates.BASES.take(SETTLEMENT_CHIPS) + settlement).distinct(),
        selected = settlement,
        onSelect = onSettlement,
        onMore = onMoreSettlements,
        moreLabel = stringResource(R.string.currency_more),
    )
    Hint(
        if (settlement != cardCurrency) {
            stringResource(R.string.editor_settlement_hint, settlement.code, cardCurrency.code)
        } else {
            stringResource(R.string.editor_unionpay_hint)
        },
    )
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
    SubTitle(stringResource(R.string.receipt_bought_title, sentCurrency.shortRu.lowercase()))
    val label = stringResource(R.string.receipt_bought, "${Numbers.moneyWhole(unit)} ${sentCurrency.mark}")
    AmountField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        description = label,
        placeholder = official?.let { Numbers.rate(it.multiply(unit, MC)) },
        trailing = { FieldSuffix(home.mark) },
    )
    // Итог здесь не повторяем — он один, в плашке ниже.
    Hint(stringResource(if (value.isBlank()) R.string.receipt_bought_hint_empty else R.string.receipt_bought_hint))
}

/** Откуда наличные: «Обменник | Банкомат». */
@Composable
internal fun CashSourceSwitch(atm: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        for ((value, label) in listOf(false to R.string.receipt_source_exchange, true to R.string.receipt_source_atm)) {
            val on = value == atm
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) colors.accentSoft else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = if (on) colors.accent else colors.muted,
                )
            }
        }
    }
}

/** Карта, с которой сняли в банкомате: в строке — «¥ ▾», в списке — названия карт. */
@Composable
internal fun CardDropdown(selected: BankCard?, cards: List<BankCard>, onSelect: (BankCard) -> Unit, description: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(20))
                .clickable(role = Role.DropdownList, onClickLabel = description) { open = true }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("${selected?.currency?.mark ?: "?"} ▾", style = AppTheme.type.mono, color = AppTheme.colors.accent, maxLines = 1, softWrap = false)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in cards) {
                DropdownMenuItem(
                    text = { Text("${c.name} · ${c.currency.code}", style = AppTheme.type.body) },
                    onClick = {
                        open = false
                        onSelect(c)
                    },
                )
            }
        }
    }
}
