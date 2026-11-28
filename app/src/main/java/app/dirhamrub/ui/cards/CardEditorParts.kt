package app.dirhamrub.ui.cards

import app.dirhamrub.ui.components.ScreenAction
import app.dirhamrub.ui.components.ScreenActionHeight
import app.dirhamrub.ui.components.ScreenActionRadius

import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.ui.theme.LocalReduceMotion
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import app.dirhamrub.ui.components.ButtonGroup
import app.dirhamrub.ui.components.SlidingSelection
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.dirhamrub.R
import app.dirhamrub.ui.components.ContactlessIcon
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.EmvBrand
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferMode
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.ui.components.NumberField
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

/** Чтение скриншота банка: идёт, не нашли, нашли и заполнили чек. */
internal sealed interface ScanResult {
    data object Reading : ScanResult
    data object NotFound : ScanResult
    data object ReadError : ScanResult
    data class Found(val receipt: ScannedReceipt) : ScanResult
}

internal const val EDITOR_KIND_MOTION_MS = 270

/** «Карта | Наличные» в редакторе: группа кнопок Expressive. */
@Composable
internal fun KindSwitch(kind: CardKind, onChange: (CardKind) -> Unit) {
    val options = CardKind.entries
    ButtonGroup(
        options = options.map { stringResource(if (it == CardKind.CASH) R.string.kind_cash else R.string.kind_card) },
        selected = options.indexOf(kind),
        onSelect = { onChange(options[it]) },
        role = Role.Tab,
        indicatorAnimation = tween(EDITOR_KIND_MOTION_MS, easing = FastOutSlowInEasing),
    )
}

/** Чипы платёжных систем по рядам: российская и зарубежная — рядом. */
private val NETWORK_ROWS = listOf(
    listOf(CardNetwork.UNIONPAY, CardNetwork.UNIONPAY_FOREIGN),
    listOf(CardNetwork.VISA_MC_FOREIGN, CardNetwork.MIR),
)

/**
 * Платёжная система карты: UnionPay РФ / зарубежная, Visa/MC зарубежная, МИР; повторное касание снимает выбор. Нужна справочнику «Чем платить»
 * (примут ли карту в стране); UnionPay включает и пересчёт покупок по курсу UnionPay.
 */
@Composable
internal fun NetworkChips(network: CardNetwork?, onChange: (CardNetwork?) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val selectedIndex = NETWORK_ROWS.flatten().indexOf(network)
    SlidingSelection(selectedIndex, colors.accent, modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((rowIndex, row) in NETWORK_ROWS.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((columnIndex, option) in row.withIndex()) {
                        val positionIndex = rowIndex * 2 + columnIndex
                        val selected = option == network
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .then(position(positionIndex))
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(50))
                                .selectable(selected = selected, role = Role.RadioButton) { onChange(if (selected) null else option) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(
                                    when (option) {
                                        CardNetwork.UNIONPAY -> R.string.network_short_unionpay_ru
                                        CardNetwork.UNIONPAY_FOREIGN -> R.string.network_short_unionpay_foreign
                                        CardNetwork.MIR -> R.string.network_mir
                                        CardNetwork.VISA_MC_FOREIGN -> R.string.network_short_visa_foreign
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                style = type.bodyStrong.copy(fontSize = 13.5.sp),
                                color = if (covers(positionIndex)) colors.onAccent else colors.ink,
                                textAlign = TextAlign.Center,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
        ButtonGroup(
            options = TransferMode.entries.map {
                stringResource(if (it == TransferMode.LAST) R.string.transfer_mode_last else R.string.transfer_mode_average)
            },
            selected = mode.ordinal,
            onSelect = { onMode(TransferMode.entries[it]) },
            minHeight = 44.dp,
            textStyle = type.bodyStrong.copy(fontSize = 13.5.sp),
            trackColor = colors.surface,
            indicatorColor = colors.accentSoft,
            indicatorText = colors.accent,
            showCheck = false,
        )
        if (average != null) {
            ReceiptRate(
                stringResource(
                    R.string.transfer_average,
                    Numbers.transferRate(average.got.divide(average.sent, MC), cardSymbol, average.sentCurrency.receiptMark),
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
                    RuDates.short(t.date) + " · " + Numbers.money(t.sent) + " " + t.sentCurrency.receiptMark + " → " +
                        Numbers.money(t.got) + " " + cardSymbol + " · " +
                        Numbers.transferRate(t.got.divide(t.sent, MC), cardSymbol, t.sentCurrency.receiptMark),
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

/** «Заполнить из скриншота банка» и что получилось. Без [onClick] — только показ (знакомство). */
@Composable
internal fun ScreenshotButton(result: ScanResult?, card: Currency, onClick: (() -> Unit)?) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    // Главная кнопка блока: избавляет от ручного ввода сумм ниже — бледно-синяя подложка, значок сканера.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentSoft)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, enabled = result != ScanResult.Reading, onClick = onClick) else Modifier,
            )
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
        ScanResult.ReadError -> stringResource(R.string.scan_read_error)
        ScanResult.NotFound -> stringResource(R.string.scan_not_found, card.mark)
        is ScanResult.Found -> when (val r = result.receipt) {
            is ScannedReceipt.Purchase -> stringResource(
                R.string.scan_purchase,
                "${Numbers.groupInput(Numbers.exactInput(r.paid))} ${card.mark}",
                "${Numbers.groupInput(Numbers.exactInput(r.price))} ${r.spend.mark}",
            )
            is ScannedReceipt.Transfer -> stringResource(
                R.string.scan_transfer,
                "${Numbers.groupInput(Numbers.exactInput(r.sent))} ${r.sentCurrency.mark}",
                "${Numbers.groupInput(Numbers.exactInput(r.got))} ${card.mark}",
            )
        }
    }
    if (message != null) {
        Text(
            text = message,
            style = type.caption.copy(fontSize = 13.sp),
            color = when (result) {
                ScanResult.NotFound, ScanResult.ReadError -> colors.danger
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

/**
 * Приложить карту: строка со значком «бесконтактно» и подсказкой; ниже — что прочитали (зелёным) или что не вышло
 * (красным). Само чтение идёт всё время, пока открыта настройка карты ([NfcCardReader]).
 */
@Composable
internal fun NfcHint(read: NfcRead?, enabled: Boolean, animate: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val result = read.takeIf { enabled }
    val found = result is NfcRead.Found
    var visible by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val pulse = if (!reduce && animate && visible && resumed && enabled && !found) {
        rememberInfiniteTransition(label = "nfcWaiting").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "nfcWave",
        )
    } else null
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = found, modifier = Modifier.size(24.dp),
            animationSpec = if (reduce) snap() else tween(180), label = "nfcIcon") { success ->
            Box(contentAlignment = Alignment.Center) {
                if (success) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp), tint = colors.good)
                } else {
                    ContactlessIcon(
                        tint = if (enabled) MaterialTheme.colorScheme.primary else colors.muted,
                        modifier = Modifier.graphicsLayer {
                            val breathing = pulse?.value ?: 0f
                            scaleX = 1f + 0.10f * breathing
                            scaleY = scaleX
                            alpha = 1f - 0.24f * breathing
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (enabled) R.string.nfc_title else R.string.nfc_disabled), style = type.bodyStrong, color = colors.ink)
            if (enabled) {
                Text(stringResource(R.string.nfc_sub), style = type.caption, color = colors.muted)
            } else {
                TextButton(onClick = { openNfcSettings(context) }) {
                    Text(stringResource(R.string.nfc_enable), style = type.bodyStrong, color = colors.accent)
                }
            }
        }
    }
    val message = when (result) {
        null -> null
        NfcRead.Failed -> stringResource(R.string.nfc_failed)
        is NfcRead.Found -> {
            val brand = stringResource(
                when (result.info.brand) {
                    EmvBrand.VISA -> R.string.nfc_brand_visa
                    EmvBrand.MASTERCARD -> R.string.nfc_brand_mastercard
                    EmvBrand.UNIONPAY -> R.string.nfc_brand_unionpay
                    EmvBrand.MIR -> R.string.nfc_brand_mir
                    EmvBrand.OTHER -> R.string.nfc_brand_other
                },
            )
            result.info.currency?.let { stringResource(R.string.nfc_found, brand, it.code) }
                ?: stringResource(R.string.nfc_found_no_currency, brand)
        }
    }
    if (message != null) {
        Text(
            message,
            style = type.caption.copy(fontSize = 13.sp),
            color = if (result is NfcRead.Found) colors.good else colors.danger,
            modifier = Modifier
                .padding(start = 2.dp, top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Пока идёт запись, кнопка показывает загрузку. Галочка появляется после подтверждения записи.
 */
@Composable
internal fun SaveButton(saving: Boolean, saved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (saving && !saved) {
        ScreenAction(stringResource(R.string.editor_saving), onClick, Modifier.fillMaxWidth(), loading = true)
        return
    }
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val shrink by animateFloatAsState(if (saved) 1f else 0f, if (reduceMotion) snap() else Springs.spatial(), label = "save")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val full = maxWidth
        val k = shrink.coerceIn(0f, 1.05f)
        val width = full - (full - ScreenActionHeight) * k
        val label = stringResource(R.string.save)
        val done = stringResource(R.string.saved_a11y)
        Box(
            Modifier
                .width(width.coerceAtLeast(ScreenActionHeight))
                .heightIn(min = ScreenActionHeight)
                .clip(RoundedCornerShape(ScreenActionRadius + (ScreenActionHeight / 2 - ScreenActionRadius) * k.coerceAtMost(1f)))
                .background(colors.accent)
                .clickable(enabled = enabled && !saved, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = if (saved) done else label
                    if (saved) liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            if (shrink < 0.5f) {
                Text(label, style = AppTheme.type.bodyStrong.copy(fontSize = 17.sp), color = colors.onAccent, maxLines = 1)
            } else {
                Text("✓", style = AppTheme.type.bodyStrong.copy(fontSize = 22.sp), color = colors.onAccent)
            }
        }
    }
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
    ButtonGroup(
        options = listOf(stringResource(R.string.receipt_source_exchange), stringResource(R.string.receipt_source_atm)),
        selected = if (atm) 1 else 0,
        onSelect = { onChange(it == 1) },
        modifier = Modifier.padding(bottom = 6.dp),
        role = Role.Tab,
    )
}

/** Карта, с которой сняли в банкомате: в строке — «¥ ▾», в списке — названия карт. */
@Composable
internal fun CardDropdown(currency: Currency, cards: List<BankCard>, onSelect: (BankCard) -> Unit, description: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(20))
                .clickable(enabled = cards.isNotEmpty(), role = Role.DropdownList, onClickLabel = description) { open = true }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                currency.receiptMark + if (cards.isNotEmpty()) " ▾" else "",
                style = AppTheme.type.mono, color = AppTheme.colors.accent, maxLines = 1, softWrap = false,
            )
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
