package app.dirhamrub.ui.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ManualRate
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.ReceiptScanner
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.components.DashedDivider
import app.dirhamrub.ui.components.NumberField
import app.dirhamrub.ui.components.ReceiptLine
import app.dirhamrub.ui.components.ReceiptSection
import app.dirhamrub.ui.components.ReceiptShape
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.currency.CurrencyPickerSheet
import app.dirhamrub.ui.main.basisLabel
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Чтение скриншота банка: идёт, не нашли, нашли и заполнили чек. */
private sealed interface ScanResult {
    data object Reading : ScanResult
    data object NotFound : ScanResult
    data class Found(val receipt: ScannedReceipt) : ScanResult
}

/** Черновик чека покупки в редакторе: поля — как набрал пользователь. */
private class PurchaseDraft(val key: Int, spend: Currency, paid: String, price: String, val date: LocalDate) {
    var spend by mutableStateOf(spend)
    var paid by mutableStateOf(paid)
    var price by mutableStateOf(price)
}

/**
 * Редактор карты: название, валюта, чек пополнения, чеки покупок в разных валютах трат
 * и ручной курс для текущей пары валют. Итог считается на лету тем же [RateEngine].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorSheet(
    card: BankCard,
    isNew: Boolean,
    spend: Currency,
    home: Currency,
    official: OfficialRates?,
    onSave: (BankCard) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Добавить пустой чек покупки в этой валюте (если его ещё нет). */
    addPurchaseFor: Currency? = null,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    fun close(then: () -> Unit) {
        focus.clearFocus()
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    var name by remember(card.id) { mutableStateOf(card.name) }
    var currency by remember(card.id) { mutableStateOf(card.currency) }
    var kind by remember(card.id) { mutableStateOf(card.kind) }
    val cash = kind == CardKind.CASH
    var sent by remember(card.id) { mutableStateOf(card.transfer?.sent?.let(::field).orEmpty()) }
    var sentCurrency by remember(card.id) { mutableStateOf(card.transfer?.sentCurrency ?: home) }
    var got by remember(card.id) { mutableStateOf(card.transfer?.got?.let(::field).orEmpty()) }
    val purchases = remember(card.id) {
        mutableStateListOf<PurchaseDraft>().apply {
            card.purchases.forEachIndexed { i, p -> add(PurchaseDraft(i, p.spend, field(p.paid), field(p.price), p.date)) }
            // Открыли по подсказке «добавить чек»: сразу пустая строка покупки в валюте трат.
            if (addPurchaseFor != null && card.purchases.none { it.spend == addPurchaseFor }) {
                add(PurchaseDraft(card.purchases.size, addPurchaseFor, "", "", LocalDate.now()))
            }
        }
    }
    var nextKey by remember(card.id) { mutableStateOf(purchases.size) }
    // Свой курс — на ту же единицу, что в итоге и на картах («100 ₸ = …»); единица считается один раз при открытии,
    // чтобы не менялась, пока курс набирают.
    val manualUnit = remember(card.id) {
        val auto = RateEngine.quote(card.withoutManual(spend, home), spend, home, official)?.homePerUnit
        (auto ?: card.manualFor(spend, home)?.homePerUnit)?.let(Numbers::unitFor) ?: BigDecimal.ONE
    }
    var manual by remember(card.id) {
        mutableStateOf(card.manualFor(spend, home)?.homePerUnit?.let { Numbers.plain(it.multiply(manualUnit, MC), 4) }.orEmpty())
    }
    var confirmDelete by remember(card.id) { mutableStateOf(false) }
    var cardPicker by remember(card.id) { mutableStateOf(false) }

    // Чек из скриншота: уведомление или операция в приложении банка → суммы в поля чека.
    val context = LocalContext.current
    var scan by remember(card.id) { mutableStateOf<ScanResult?>(null) }
    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scan = ScanResult.Reading
        scope.launch {
            val lines = readScreenshotLines(context, uri)
            val receipt = lines?.let { ReceiptScanner.guess(it, currency, spend, home) }
            when (receipt) {
                is ScannedReceipt.Purchase -> {
                    val draft = purchases.firstOrNull { it.spend == receipt.spend }
                        ?: PurchaseDraft(nextKey++, receipt.spend, "", "", LocalDate.now()).also { purchases.add(it) }
                    draft.paid = field(receipt.paid)
                    draft.price = field(receipt.price)
                }
                is ScannedReceipt.Transfer -> {
                    sent = field(receipt.sent)
                    sentCurrency = receipt.sentCurrency
                    got = field(receipt.got)
                }
                null -> Unit
            }
            scan = if (receipt == null) ScanResult.NotFound else ScanResult.Found(receipt)
        }
    }

    val defaultName = stringResource(if (cash) R.string.cash_default_name else R.string.card_default_name, currency.code)
    fun build(): BankCard {
        val today = LocalDate.now()
        val sentValue = Numbers.parse(sent)
        val gotValue = Numbers.parse(got)
        val transfer = if (sentValue != null && gotValue != null && sentValue.signum() > 0 && gotValue.signum() > 0) {
            val old = card.transfer
            val unchanged = old != null && old.sent.compareTo(sentValue) == 0 && old.got.compareTo(gotValue) == 0 && old.sentCurrency == sentCurrency
            TransferReceipt(sentValue, sentCurrency, gotValue, if (unchanged) old!!.date else today)
        } else {
            null
        }
        // У наличных покупок по чекам нет: платят ровно ценой в той же валюте.
        val receipts = if (kind == CardKind.CASH) emptyList() else purchases.mapNotNull { d ->
            val paid = Numbers.parse(d.paid) ?: return@mapNotNull null
            val price = Numbers.parse(d.price) ?: return@mapNotNull null
            if (paid.signum() <= 0 || price.signum() <= 0) return@mapNotNull null
            val old = card.purchases.getOrNull(d.key)
            val unchanged = old != null && old.paid.compareTo(paid) == 0 && old.price.compareTo(price) == 0 && old.spend == d.spend
            PurchaseReceipt(d.spend, paid, price, if (unchanged) old!!.date else today)
        }
        val manualValue = Numbers.parse(manual)?.takeIf { it.signum() > 0 }?.divide(manualUnit, MC)
        val oldManual = card.manualFor(spend, home)
        val manualDate = if (oldManual != null && manualValue != null && oldManual.homePerUnit.compareTo(manualValue) == 0) oldManual.date else today
        val otherManual = card.manual.filterNot { it.spend == spend && it.home == home }
        return card.copy(
            name = name.trim().ifEmpty { defaultName },
            currency = currency,
            kind = kind,
            transfer = transfer,
            purchases = receipts,
            manual = otherManual + listOfNotNull(manualValue?.let { ManualRate(spend, home, it, manualDate) }),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bg) {
        // Блоки редактора появляются каскадом.
        val cascade = rememberCascade()
        // «Сохранить» всегда внизу шторки, прокручивается только содержимое над ней.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.cascade(cascade, 0)) {
                Text(
                    text = stringResource(
                        when {
                            cash && isNew -> R.string.editor_title_new_cash
                            cash -> R.string.editor_title_cash
                            isNew -> R.string.editor_title_new
                            else -> R.string.editor_title_edit
                        },
                    ),
                    style = type.title,
                    color = colors.ink,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (!isNew) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(if (cash) R.string.editor_delete_cash else R.string.editor_delete),
                        tint = colors.muted,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { confirmDelete = true }
                            .padding(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // Карта или наличные из обменника: у наличных вместо пополнения — обмен, покупок по чекам нет.
            Box(Modifier.cascade(cascade, 1)) { KindSwitch(kind) { kind = it } }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = { Text(stringResource(R.string.editor_name_hint)) },
                singleLine = true,
                textStyle = type.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .cascade(cascade, 2),
            )
            Spacer(Modifier.height(12.dp))
            ReceiptSection(
                stringResource(if (cash) R.string.editor_currency_cash else R.string.editor_currency),
                Modifier.cascade(cascade, 3),
            )
            // Частые валюты карт чипами, остальные — в окне с поиском.
            CurrencyChips(
                options = (Currency.FREQUENT + currency).distinct(),
                selected = currency,
                onSelect = { currency = it },
                modifier = Modifier.cascade(cascade, 3),
                onMore = { cardPicker = true },
                moreLabel = stringResource(R.string.currency_more),
            )
            Spacer(Modifier.height(16.dp))

            Column(Modifier.cascade(cascade, 4)) {
                ScreenshotButton(
                    result = scan,
                    card = currency,
                    onClick = { pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
            Spacer(Modifier.height(12.dp))
            // Итог на лету: тот же расчёт, что на главном экране.
            val draft = build()
            val q = RateEngine.quote(draft, spend, home, official)
            // Бумажный чек: пополнение и покупки.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cascade(cascade, 5)
                    .clip(ReceiptShape)
                    .background(colors.paper)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
            ) {
                ReceiptSection(stringResource(if (cash) R.string.receipt_exchange else R.string.receipt_transfer))
                val pick = stringResource(R.string.editor_pick_currency)
                val sentLabel = stringResource(if (cash) R.string.receipt_gave else R.string.receipt_sent)
                val gotLabel = stringResource(if (cash) R.string.receipt_received else R.string.receipt_got)
                ReceiptLine(sentLabel, sent, { sent = it }, sentLabel) {
                    CurrencyDropdown(sentCurrency, (Currency.FREQUENT + home + currency + sentCurrency).distinct(), { sentCurrency = it }, pick)
                }
                ReceiptLine(gotLabel, got, { got = it }, gotLabel) {
                    CurrencyMark(currency.symbol)
                }
                // Чек заполнен — курс по нему; пустой — что будет без него.
                val transfer = draft.transfer
                if (transfer != null) {
                    ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.symbol, transfer.sentCurrency.symbol))
                } else {
                    Hint(stringResource(if (cash) R.string.receipt_exchange_hint else R.string.receipt_transfer_hint))
                }
                Spacer(Modifier.height(10.dp))

                if (!cash) {
                    ReceiptSection(stringResource(R.string.receipt_purchases))
                    for (d in purchases) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CurrencyDropdown(d.spend, (listOf(spend, d.spend) + Currency.POPULAR).distinct(), { d.spend = it }, pick, chip = true)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                d.spend.nameRu,
                                style = type.caption,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            val remove = stringResource(R.string.receipt_remove)
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = remove,
                                tint = colors.muted,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { purchases.remove(d) }
                                    .padding(10.dp),
                            )
                        }
                        ReceiptLine(stringResource(R.string.receipt_paid), d.paid, { d.paid = it }, stringResource(R.string.receipt_paid)) {
                            CurrencyMark(currency.symbol)
                        }
                        ReceiptLine(stringResource(R.string.receipt_for), d.price, { d.price = it }, stringResource(R.string.receipt_for)) {
                            CurrencyMark(d.spend.symbol)
                        }
                        val paid = Numbers.parse(d.paid)?.takeIf { it.signum() > 0 }
                        val price = Numbers.parse(d.price)?.takeIf { it.signum() > 0 }
                        if (paid != null && price != null) {
                            ReceiptRate(Numbers.transferRate(paid.divide(price, MC), currency.symbol, d.spend.symbol))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    TextButton(onClick = {
                        val used = purchases.map { it.spend }.toSet()
                        val first = if (spend !in used) spend else Currency.POPULAR.firstOrNull { it !in used } ?: spend
                        purchases.add(PurchaseDraft(nextKey++, first, "", "", LocalDate.now()))
                    }) { Text(stringResource(R.string.receipt_add), style = type.bodyStrong, color = colors.accent) }
                    // Общая подсказка — пока чеков покупок нет; потом — только если курс для валюты трат оценочный.
                    when {
                        draft.purchases.isEmpty() -> Hint(stringResource(R.string.receipt_purchases_hint))
                        q?.basis == RateBasis.ESTIMATE -> Hint(stringResource(R.string.receipt_estimate_hint, spend.symbol))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                DashedDivider()
                Spacer(Modifier.height(10.dp))

                if (q != null) {
                    val unit = Numbers.unitFor(q.homePerUnit)
                    Text(
                        text = stringResource(
                            R.string.editor_result,
                            "${Numbers.moneyWhole(unit)} ${spend.symbol}",
                            Numbers.rate(q.homePerUnit.multiply(unit)),
                            home.symbol,
                        ),
                        style = type.monoStrong.copy(fontSize = 22.sp, lineHeight = 28.sp),
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = basisLabel(q),
                        style = type.mono.copy(fontSize = 13.sp),
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(R.string.editor_no_result),
                        style = type.body,
                        color = colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // Свой курс для текущей пары валют; серым в пустом поле — курс по чекам сейчас.
            val auto = RateEngine.quote(draft.withoutManual(spend, home), spend, home, official)?.homePerUnit
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cascade(cascade, 6)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.editor_manual, "${Numbers.moneyWhole(manualUnit)} ${spend.symbol}"),
                        style = type.monoStrong.copy(fontSize = 15.sp),
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = manual,
                        onValueChange = { manual = it },
                        style = type.monoStrong.copy(fontSize = 17.sp),
                        description = stringResource(R.string.editor_manual_hint),
                        onDone = { focus.clearFocus() },
                        placeholder = auto?.let { Numbers.rate(it.multiply(manualUnit, MC)) },
                        modifier = Modifier.width(MANUAL_FIELD),
                    )
                    Spacer(Modifier.width(8.dp))
                    CurrencyMark(home.symbol)
                }
                Hint(stringResource(R.string.editor_manual_hint))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Button(
                onClick = { val result = build(); close { onSave(result) } },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.save), style = type.bodyStrong.copy(fontSize = 16.sp)) }
        }
    }
    if (cardPicker) {
        CurrencyPickerSheet(
            title = stringResource(if (cash) R.string.editor_currency_cash else R.string.editor_currency),
            selected = currency,
            options = Currency.entries,
            onPick = { currency = it },
            onDismiss = { cardPicker = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = {
                Text(
                    stringResource(if (cash) R.string.editor_delete_cash_confirm else R.string.editor_delete_confirm, card.name),
                    style = type.body,
                    color = colors.ink,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    close(onDelete)
                }) { Text(stringResource(R.string.delete), style = type.bodyStrong, color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), style = type.bodyStrong, color = colors.muted) }
            },
            containerColor = colors.surface,
        )
    }
}

/** «Карта | Наличные» в редакторе. */
@Composable
private fun KindSwitch(kind: CardKind, onChange: (CardKind) -> Unit) {
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
private fun ScreenshotButton(result: ScanResult?, card: Currency, onClick: () -> Unit) {
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
private fun CurrencyMark(symbol: String) {
    Text(symbol, style = AppTheme.type.mono, color = AppTheme.colors.muted, maxLines = 1, softWrap = false)
}

/** «✓ По этому чеку 1 ¥ = 12,3 ₽» — под заполненным чеком. */
@Composable
private fun ReceiptRate(rate: String) {
    Text(
        stringResource(R.string.receipt_rate, rate),
        style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = AppTheme.colors.good,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Карта без своего курса для этой пары — чтобы показать, сколько выходит по чекам. */
private fun BankCard.withoutManual(spend: Currency, home: Currency): BankCard =
    copy(manual = manual.filterNot { it.spend == spend && it.home == home })

@Composable
private fun Hint(text: String) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.muted, modifier = Modifier.padding(top = 2.dp))
}

/** Значение для поля чека: «2 757,48». */
private fun field(value: BigDecimal): String = Numbers.groupInput(Numbers.plain(value))

private const val MAX_NAME = 24

/** Поле своего курса: «18,1724» помещается, подпись слева не переносится. */
private val MANUAL_FIELD = 116.dp
