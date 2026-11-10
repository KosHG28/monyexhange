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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.components.DashedDivider
import app.dirhamrub.ui.components.ReceiptLine
import app.dirhamrub.ui.components.ReceiptSection
import app.dirhamrub.ui.components.ReceiptShape
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.currency.CurrencyPickerSheet
import app.dirhamrub.ui.main.basisLabel
import app.dirhamrub.ui.main.BankCardView
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch

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
    /** Курсы UnionPay — для карт UnionPay. */
    unionPay: UnionPayRates? = null,
    /** Все карты кошелька: наличные из банкомата снимают с одной из них. */
    cards: List<BankCard> = emptyList(),
    /** Добавить пустой чек покупки в этой валюте (если его ещё нет). */
    addPurchaseFor: Currency? = null,
    /** Сумма с главного экрана в валюте трат — для карточки-предпросмотра (нет суммы — за 100 единиц). */
    amount: BigDecimal = BigDecimal.ZERO,
) {
    val previewAmount = amount.takeIf { it.signum() > 0 } ?: PREVIEW_AMOUNT
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
    var unionPayCard by remember(card.id) { mutableStateOf(card.unionPay) }
    var settlement by remember(card.id) { mutableStateOf(card.settlement) }
    var settlementPicker by remember(card.id) { mutableStateOf(false) }
    val cash = kind == CardKind.CASH
    var sent by remember(card.id) { mutableStateOf(card.transfer?.sent?.let(::field).orEmpty()) }
    var sentCurrency by remember(card.id) { mutableStateOf(card.transfer?.sentCurrency ?: home) }
    var got by remember(card.id) { mutableStateOf(card.transfer?.got?.let(::field).orEmpty()) }
    // Отдавали не итоговую валюту (100 $ в драмы) — почём её купили, на единицу как у курса ЦБ: «1 $ = 82 ₽».
    fun boughtUnitFor(c: Currency): BigDecimal =
        Numbers.unitFor(official?.cross(c, home) ?: card.transfer?.bought?.first ?: BigDecimal.ONE)
    // Наличные из банкомата: с какой карты сняли (курс — по ней).
    var atm by remember(card.id) { mutableStateOf(card.transfer?.fromCard != null) }
    var fromCard by remember(card.id) { mutableStateOf(card.transfer?.fromCard) }
    val atmSources = cards.filter { it.kind == CardKind.CARD && it.id != card.id }
    val atmSource = atmSources.firstOrNull { it.id == fromCard }
    var boughtAt by remember(card.id) {
        mutableStateOf(
            card.transfer?.let { t ->
                t.bought?.takeIf { it.second == home }?.first?.let { Numbers.plain(it.multiply(boughtUnitFor(t.sentCurrency), MC), 4) }
            }.orEmpty(),
        )
    }
    val boughtUnit = boughtUnitFor(sentCurrency)
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
        val auto = RateEngine.quote(card.withoutManual(spend, home), spend, home, official, unionPay, cards)?.homePerUnit
        (auto ?: card.manualFor(spend, home)?.homePerUnit)?.let(Numbers::unitFor) ?: BigDecimal.ONE
    }
    var manual by remember(card.id) {
        mutableStateOf(card.manualFor(spend, home)?.homePerUnit?.let { Numbers.plain(it.multiply(manualUnit, MC), 4) }.orEmpty())
    }
    var confirmDelete by remember(card.id) { mutableStateOf(false) }
    // Нажали «Сохранить», а в чеке заполнена только одна сумма из двух — подсвечиваем вторую, шторку не закрываем.
    var showErrors by remember(card.id) { mutableStateOf(false) }
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
                    if (sentCurrency != receipt.sentCurrency) boughtAt = ""
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
            val fromAtm = atmSource?.takeIf { kind == CardKind.CASH && atm }
            val bought = Numbers.parse(boughtAt)?.takeIf { it.signum() > 0 && sentCurrency != home && fromAtm == null }?.divide(boughtUnit, MC)
            TransferReceipt(
                sentValue,
                fromAtm?.currency ?: sentCurrency,
                gotValue,
                if (unchanged) old!!.date else today,
                bought,
                bought?.let { home },
                fromCard = fromAtm?.id,
            )
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
            unionPay = unionPayCard && kind == CardKind.CARD,
            settlement = settlement,
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
            // Черновик из полей и его курс — для предпросмотра сверху и для чека ниже.
            val draft = build()
            val q = RateEngine.quote(draft, spend, home, official, unionPay, cards)
            // Живая карточка: как карта будет выглядеть на главном экране с тем, что уже введено.
            Box(
                Modifier
                    .fillMaxWidth()
                    .cascade(cascade, 1)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                BankCardView(
                    card = draft,
                    quote = q,
                    spend = spend,
                    home = home,
                    sum = q?.let { Numbers.moneyWhole(previewAmount.multiply(it.homePerUnit, MC)) },
                    selected = true,
                    best = null,
                    onClick = {},
                    onLongClick = {},
                    width = PREVIEW_WIDTH,
                    height = PREVIEW_HEIGHT,
                    needsReceipt = official?.cross(spend, home) == null,
                    flipOnChange = false,
                )
            }
            Spacer(Modifier.height(8.dp))
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
            // Четыре валюты чипами: та, что была у карты, валюта трат, валюты других карт и итоговая
            // (добираются частыми); остальные — в окне с поиском. Список не прыгает при выборе.
            val chipOptions = remember(card.id) {
                (listOf(card.currency, spend) + cards.filter { it.id != card.id }.map { it.currency } + home + Currency.FREQUENT)
                    .distinct()
                    .take(EDITOR_CHIPS)
            }
            CurrencyChips(
                options = (chipOptions + currency).distinct(),
                selected = currency,
                onSelect = { currency = it },
                modifier = Modifier.cascade(cascade, 3),
                onMore = { cardPicker = true },
                moreLabel = stringResource(R.string.currency_more_count, Currency.entries.size),
            )
            if (!cash) {
                Spacer(Modifier.height(12.dp))
                UnionPaySection(
                    enabled = unionPayCard,
                    settlement = settlement ?: UnionPayRates.defaultSettlement(currency),
                    cardCurrency = currency,
                    onEnabled = { unionPayCard = it },
                    onSettlement = { settlement = it },
                    onMoreSettlements = { settlementPicker = true },
                    modifier = Modifier.cascade(cascade, 3),
                )
            }
            Spacer(Modifier.height(16.dp))

            // Скриншот банка — только у карт: обмен наличных в приложении банка не виден.
            if (!cash) {
                Column(Modifier.cascade(cascade, 4)) {
                    ScreenshotButton(
                        result = scan,
                        card = currency,
                        onClick = { pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            // Итог на лету: тот же расчёт, что на главном экране.
            // Бумажный чек: пополнение и покупки.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cascade(cascade, 5)
                    .clip(ReceiptShape)
                    .background(colors.paper)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 28.dp),
            ) {
                val pick = stringResource(R.string.editor_pick_currency)
                // Наличные — из обменника или из банкомата (снятие с одной из карт).
                if (cash) {
                    CashSourceSwitch(atm) { on ->
                        atm = on
                        if (on) {
                            val first = atmSource ?: atmSources.firstOrNull()
                            fromCard = first?.id
                            first?.let { sentCurrency = it.currency }
                            boughtAt = ""
                        } else {
                            fromCard = null
                            sentCurrency = home
                        }
                    }
                }
                if (cash && atm) {
                    ReceiptSection(stringResource(R.string.receipt_atm))
                    if (atmSources.isEmpty()) {
                        Hint(stringResource(R.string.receipt_atm_no_cards))
                    } else {
                        val gotAtm = stringResource(R.string.receipt_atm_got)
                        val paidAtm = stringResource(R.string.receipt_atm_paid)
                        ReceiptLine(gotAtm, got, { got = it }, gotAtm, error = missing(showErrors, got, sent)) { CurrencyMark(currency.symbol) }
                        ReceiptLine(paidAtm, sent, { sent = it }, paidAtm, last = true, error = missing(showErrors, sent, got)) {
                            CardDropdown(
                                atmSource,
                                atmSources,
                                {
                                    fromCard = it.id
                                    sentCurrency = it.currency
                                },
                                stringResource(R.string.editor_pick_card),
                            )
                        }
                        val transfer = draft.transfer
                        if (transfer != null) {
                            ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.symbol, transfer.sentCurrency.symbol))
                            ReceiptTypo(transfer.sent, transfer.got, official?.cross(currency, transfer.sentCurrency))
                            // Итог в итоговой валюте — по курсу карты, с которой сняли.
                            RateEngine.cardToHome(draft, home, official, cards)?.first?.let { perCash ->
                                val u = Numbers.unitFor(perCash)
                                ReceiptRate(
                                    stringResource(
                                        R.string.receipt_bought_total,
                                        "${Numbers.moneyWhole(u)} ${currency.symbol}",
                                        "${Numbers.rate(perCash.multiply(u, MC))} ${home.symbol}",
                                    ),
                                    whole = true,
                                )
                            }
                        }
                        Hint(stringResource(R.string.receipt_atm_hint))
                    }
                } else {
                    ReceiptSection(stringResource(if (cash) R.string.receipt_exchange else R.string.receipt_transfer))
                    val sentLabel = stringResource(if (cash) R.string.receipt_gave else R.string.receipt_sent)
                    val gotLabel = stringResource(if (cash) R.string.receipt_received else R.string.receipt_got)
                    ReceiptLine(sentLabel, sent, { sent = it }, sentLabel, error = missing(showErrors, sent, got)) {
                        // В обменник несут рубли, доллары, евро или юани; карту пополняют и из других валют.
                        val sentOptions = (if (cash) listOf(home) + EXCHANGE_FROM else Currency.FREQUENT + home)
                            .filter { it != currency } + sentCurrency
                        CurrencyDropdown(
                            sentCurrency,
                            sentOptions.distinct(),
                            {
                                if (it != sentCurrency) boughtAt = ""
                                sentCurrency = it
                            },
                            pick,
                        )
                    }
                    ReceiptLine(gotLabel, got, { got = it }, gotLabel, error = missing(showErrors, got, sent)) {
                        CurrencyMark(currency.symbol)
                    }
                    // Чек заполнен — курс по нему; пустой — что будет без него.
                    val transfer = draft.transfer
                    if (transfer != null) {
                        ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.symbol, transfer.sentCurrency.symbol))
                        ReceiptTypo(transfer.sent, transfer.got, official?.cross(currency, transfer.sentCurrency))
                    } else {
                        Hint(stringResource(if (cash) R.string.receipt_exchange_hint else R.string.receipt_transfer_hint))
                    }
                    if (sentCurrency != home) {
                        BoughtPlate(
                            sentCurrency = sentCurrency,
                            home = home,
                            card = currency,
                            unit = boughtUnit,
                            value = boughtAt,
                            onValueChange = { boughtAt = it },
                            official = official?.cross(sentCurrency, home),
                            transfer = transfer,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (!cash) {
                    PurchaseReceipts(
                        purchases = purchases,
                        showErrors = showErrors,
                        official = official,
                        cardCurrency = currency,
                        spend = spend,
                        pick = pick,
                        // Общая подсказка — пока чеков покупок нет; потом — только если курс для валюты трат оценочный.
                        hint = when {
                            q?.basis == RateBasis.UNIONPAY ->
                                stringResource(R.string.receipt_unionpay_hint, spend.symbol, draft.settlementCurrency.code)
                            draft.purchases.isEmpty() -> stringResource(R.string.receipt_purchases_hint)
                            q != null && q.basis == RateBasis.ESTIMATE && q.viaUnionPay ->
                                stringResource(R.string.receipt_estimate_hint_unionpay, spend.symbol)
                            q?.basis == RateBasis.ESTIMATE -> stringResource(R.string.receipt_estimate_hint, spend.symbol)
                            else -> null
                        },
                        onAdd = {
                            val used = purchases.map { it.spend }.toSet()
                            val first = if (spend !in used) spend else Currency.POPULAR.firstOrNull { it !in used } ?: spend
                            purchases.add(PurchaseDraft(nextKey++, first, "", "", LocalDate.now()))
                        },
                    )
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
            val auto = RateEngine.quote(draft.withoutManual(spend, home), spend, home, official, unionPay, cards)?.homePerUnit
            ManualRatePanel(
                unit = manualUnit,
                spend = spend,
                home = home,
                value = manual,
                onValueChange = { manual = it },
                auto = auto,
                onDone = { focus.clearFocus() },
                modifier = Modifier.cascade(cascade, 6),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Button(
                onClick = {
                    if (halfFilled(sent, got) || (!cash && purchases.any { halfFilled(it.paid, it.price) })) {
                        showErrors = true
                    } else {
                        val result = build()
                        close { onSave(result) }
                    }
                },
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
    if (settlementPicker) {
        CurrencyPickerSheet(
            title = stringResource(R.string.editor_settlement),
            selected = settlement ?: UnionPayRates.defaultSettlement(currency),
            options = UnionPayRates.BASES,
            onPick = { settlement = it },
            onDismiss = { settlementPicker = false },
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

/** Карта без своего курса для этой пары — чтобы показать, сколько выходит по чекам. */
private fun BankCard.withoutManual(spend: Currency, home: Currency): BankCard =
    copy(manual = manual.filterNot { it.spend == spend && it.home == home })

/** Значение для поля чека: «2 757,48». */
private fun field(value: BigDecimal): String = Numbers.groupInput(Numbers.plain(value))

private const val MAX_NAME = 24

/** Что несут в обменник за местные наличные (кроме итоговой валюты — она есть всегда). */
private val EXCHANGE_FROM = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

/** Сколько валют показывать чипами в редакторе карты. */
private const val EDITOR_CHIPS = 4

/** Карточка-предпросмотр в редакторе. */
private val PREVIEW_WIDTH = 190.dp
private val PREVIEW_HEIGHT = 100.dp
private val PREVIEW_AMOUNT = BigDecimal(100)
