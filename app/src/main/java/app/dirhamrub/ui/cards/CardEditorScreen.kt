package app.dirhamrub.ui.cards

import app.dirhamrub.data.ReceiptShots
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.SideEffect
import app.dirhamrub.ui.components.CardMorph
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import app.dirhamrub.ui.theme.LocalReduceMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import app.dirhamrub.domain.RuDates
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.FullScreenScope
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.finishCascadeOnTouch
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Rounding
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.HISTORY_MAX
import app.dirhamrub.domain.wallet.ManualRate
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.ReceiptScanner
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferMode
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.currency.CurrencyPickerScreen
import app.dirhamrub.ui.main.BankCardView
import app.dirhamrub.ui.main.basisLabel
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
fun CardEditorScreen(
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
    /** Округление итогов из настроек — сумма на карточке-предпросмотре такая же, как на главном. */
    rounding: Rounding = Rounding.WHOLE,
    /** Перелёт с главного: мини-карта строки вырастает в карточку-предпросмотр (null — экран выезжает снизу). */
    morph: CardMorph? = null,
    /** Pro: скриншот банка сохраняется при чеке. */
    pro: Boolean = false,
    /** Сразу открыть выбор скриншота банка (Pro: ярлык «Чек из скриншота»). */
    startScan: Boolean = false,
) {
    val previewAmount = amount.takeIf { it.signum() > 0 } ?: PREVIEW_AMOUNT
    val colors = AppTheme.colors
    val type = AppTheme.type
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Экран закрывается с анимацией, а потом выполняется действие (сохранить, удалить).
    val screen = remember { arrayOfNulls<FullScreenScope>(1) }
    fun close(then: (() -> Unit)? = null) {
        focus.clearFocus()
        screen[0]?.close(then) ?: (then ?: onDismiss)()
    }
    var confirmExit by remember(card.id) { mutableStateOf(false) }

    var name by remember(card.id) { mutableStateOf(card.name) }
    var currency by remember(card.id) { mutableStateOf(card.currency) }
    var kind by remember(card.id) { mutableStateOf(card.kind) }
    // UnionPay — одна из платёжных систем: выбрали её — карта считается картой UnionPay (без отдельного переключателя).
    var network by remember(card.id) { mutableStateOf(card.paymentNetwork) }
    // Прежние пополнения и какой курс считать: по последнему или в среднем.
    val history = remember(card.id) { mutableStateListOf<TransferReceipt>().apply { addAll(card.history) } }
    var transferMode by remember(card.id) { mutableStateOf(card.transferMode) }
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
    // Свой курс — за переключателем: выключен — считаем по чекам, второго курса на экране нет.
    var manualOn by remember(card.id) { mutableStateOf(card.manualFor(spend, home) != null) }
    // Пополнение у новой карты — сначала заглушка с кнопкой «+ Добавить пополнение».
    var transferOpen by remember(card.id) { mutableStateOf(card.transfer != null || card.history.isNotEmpty() || kind == CardKind.CASH) }
    var confirmDelete by remember(card.id) { mutableStateOf(false) }
    // Сохраняем: кнопка показывает «✓», потом экран закрывается с этой картой.
    var pendingSave by remember(card.id) { mutableStateOf<BankCard?>(null) }
    // Нажали «Сохранить», а в чеке заполнена только одна сумма из двух — подсвечиваем вторую, шторку не закрываем.
    var showErrors by remember(card.id) { mutableStateOf(false) }
    var cardPicker by remember(card.id) { mutableStateOf(false) }

    // Чек из скриншота: уведомление или операция в приложении банка → суммы в поля чека.
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var scan by remember(card.id) { mutableStateOf<ScanResult?>(null) }
    // Карта, приложенная к телефону: система и валюта сразу в поля.
    var nfc by remember(card.id) { mutableStateOf<NfcRead?>(null) }
    val nfcAvailable = remember { nfcReady(context) }
    NfcCardReader(enabled = nfcAvailable && kind == CardKind.CARD) { result ->
        nfc = result
        if (result is NfcRead.Found) {
            result.info.currency?.let { currency = it }
            network = result.info.network
            haptics.play(Haptic.CONFIRM)
        } else {
            haptics.play(Haptic.REJECT)
        }
    }
    // Pro: сохранённые скриншоты чеков этой карты (новые первыми).
    var shots by remember(card.id) { mutableStateOf(if (pro) ReceiptShots.list(context, card.id) else emptyList()) }
    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scan = ScanResult.Reading
        scope.launch {
            val lines = readScreenshotLines(context.applicationContext, uri)
            val receipt = lines?.let { ReceiptScanner.guess(it, currency, spend, home) }
            when (receipt) {
                is ScannedReceipt.Purchase -> {
                    val draft = purchases.firstOrNull { it.spend == receipt.spend }
                        ?: PurchaseDraft(nextKey++, receipt.spend, "", "", LocalDate.now()).also { purchases.add(it) }
                    draft.paid = field(receipt.paid)
                    draft.price = field(receipt.price)
                }
                is ScannedReceipt.Transfer -> {
                    transferOpen = true
                    sent = field(receipt.sent)
                    if (sentCurrency != receipt.sentCurrency) boughtAt = ""
                    sentCurrency = receipt.sentCurrency
                    got = field(receipt.got)
                }
                null -> Unit
            }
            scan = if (receipt == null) ScanResult.NotFound else ScanResult.Found(receipt)
            haptics.play(if (receipt == null) Haptic.REJECT else Haptic.CONFIRM)
            // Pro: копия скриншота к чеку — видно, откуда взялись суммы.
            if (pro && receipt != null) {
                val kind = when (receipt) {
                    is ScannedReceipt.Purchase -> ReceiptShots.purchaseKind(receipt.spend.code)
                    is ScannedReceipt.Transfer -> ReceiptShots.TRANSFER_KIND
                }
                if (ReceiptShots.save(context.applicationContext, uri, card.id, kind) != null) shots = ReceiptShots.list(context, card.id)
            }
        }
    }
    LaunchedEffect(card.id, startScan) {
        if (startScan) pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
        val manualValue = Numbers.parse(manual)?.takeIf { manualOn && it.signum() > 0 }?.divide(manualUnit, MC)
        val oldManual = card.manualFor(spend, home)
        val manualDate = if (oldManual != null && manualValue != null && oldManual.homePerUnit.compareTo(manualValue) == 0) oldManual.date else today
        val otherManual = card.manual.filterNot { it.spend == spend && it.home == home }
        return card.copy(
            name = name.trim().ifEmpty { defaultName },
            currency = currency,
            kind = kind,
            // UnionPay (российская и зарубежная) — ещё и пересчёт по курсу UnionPay; российская хранится как раньше,
            // без отдельной платёжной системы.
            unionPay = network.isUnionPay() && kind == CardKind.CARD,
            network = network?.takeIf { kind == CardKind.CARD && it != CardNetwork.UNIONPAY },
            history = history.toList(),
            transferMode = if (history.isEmpty()) TransferMode.LAST else transferMode,
            settlement = settlement,
            transfer = transfer,
            purchases = receipts,
            manual = otherManual + listOfNotNull(manualValue?.let { ManualRate(spend, home, it, manualDate) }),
        )
    }

    // Ошибки в суммах: ноль или заполнена только одна из пары. По «Сохранить» — подсветить и прокрутить к первой.
    fun invalid(): Boolean =
        pairInvalid(sent, got) || (!cash && purchases.any { pairInvalid(it.paid, it.price) })

    // Что было при открытии: «Назад» с несохранёнными правками спрашивает, выйти ли без сохранения.
    val initial = remember(card.id) { build() }
    fun dirty(): Boolean = build() != initial
    fun back() {
        if (dirty()) confirmExit = true else close()
    }

    FullScreen(onDismissRequest = onDismiss, interceptBack = { dirty().also { if (it) confirmExit = true } }, morph = morph) {
      screen[0] = this
      val saveReduce = LocalReduceMotion.current
      LaunchedEffect(pendingSave) {
          val result = pendingSave ?: return@LaunchedEffect
          if (!saveReduce) delay(SAVE_HOLD_MS)
          close { onSave(result) }
      }
      Column(Modifier.fillMaxSize()) {
        ScreenBar(
            onBack = { back() },
            title = stringResource(
                when {
                    cash && isNew -> R.string.editor_title_new_cash
                    cash -> R.string.editor_title_cash
                    isNew -> R.string.editor_title_new
                    else -> R.string.editor_title_edit
                },
            ),
        ) {
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
        // Блоки редактора появляются каскадом.
        val cascade = rememberCascade()
        // «Сохранить» всегда внизу экрана, прокручивается только содержимое над ней: последний элемент
        // докручивается выше кнопки. Касание мимо полей убирает клавиатуру.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
                .finishCascadeOnTouch(cascade)
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            // Черновик из полей и его курс — для предпросмотра сверху и итога ниже.
            val draft = build()
            val q = RateEngine.quote(draft, spend, home, official, unionPay, cards)
            // Живая карточка: как карта будет выглядеть на главном экране с тем, что уже введено.
            val preview: @Composable (() -> Float) -> Unit = { textAlpha ->
                BankCardView(
                    card = draft,
                    quote = q,
                    spend = spend,
                    home = home,
                    sum = q?.let { Numbers.total(previewAmount.multiply(it.homePerUnit, MC), rounding) },
                    selected = true,
                    best = null,
                    onClick = {},
                    onLongClick = {},
                    width = PREVIEW_WIDTH,
                    height = PREVIEW_HEIGHT,
                    needsReceipt = official?.cross(spend, home) == null,
                    flipOnChange = false,
                    textAlpha = textAlpha,
                )
            }
            // Перелёт: та же карточка летит поверх экрана, а здесь она спрятана, пока не сядет на место.
            if (morph != null) SideEffect { morph.card = preview }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .onGloballyPositioned { if (morph != null) morph.target = it.boundsInWindow() }
                        .graphicsLayer { if (morph?.flying == true && !saveReduce) alpha = 0f },
                ) { preview { 1f } }
            }
            Spacer(Modifier.height(8.dp))
            // Карта или наличные из обменника: у наличных вместо пополнения — обмен, покупок по чекам нет.
            // Карточка и «Карта / Наличные» — сразу; блоки ниже выплывают по очереди.
            KindSwitch(kind) { kind = it }
            Spacer(Modifier.height(10.dp))

            // Блок 1. Основное: название, валюта, платёжная система.
            EditorBlock(stringResource(R.string.editor_block_main), Modifier.cascade(cascade, 0)) {
                if (!cash && nfcAvailable) {
                    NfcHint(nfc)
                    Spacer(Modifier.height(12.dp))
                }
                FieldLabel(stringResource(R.string.editor_name))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    singleLine = true,
                    textStyle = type.body.copy(fontSize = 16.sp, color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.semantics { contentDescription = name.ifEmpty { defaultName } },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (name.isEmpty()) Text(defaultName, style = type.body.copy(fontSize = 16.sp), color = colors.muted.copy(alpha = 0.6f))
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(if (cash) R.string.editor_currency_cash else R.string.editor_currency))
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
                    onMore = { cardPicker = true },
                    moreLabel = stringResource(R.string.currency_more_count, Currency.entries.size),
                )
                if (!cash) {
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.editor_network))
                    NetworkChips(network = network, onChange = { network = it })
                    if (network == null) Hint(stringResource(R.string.editor_network_hint))
                }
            }

            // Блок 2. UnionPay — только если выбрана эта система.
            if (!cash && network.isUnionPay()) {
                Spacer(Modifier.height(10.dp))
                EditorBlock(stringResource(R.string.network_unionpay), Modifier.cascade(cascade, 1)) {
                    UnionPaySection(
                        settlement = settlement ?: UnionPayRates.defaultSettlement(currency),
                        cardCurrency = currency,
                        onSettlement = { settlement = it },
                        onMoreSettlements = { settlementPicker = true },
                    )
                }
            }

            // Блок 3. Курс карты: скриншот, пополнение, покупки, итог, свой курс.
            Spacer(Modifier.height(10.dp))
            EditorBlock(stringResource(if (cash) R.string.editor_block_cash else R.string.editor_block_rate), Modifier.cascade(cascade, 2)) {
                // Свой курс включён — чеки не нужны: блок с ними сворачивается, поле курса сразу на виду.
                val reduceMotion = LocalReduceMotion.current
                AnimatedVisibility(
                    visible = !manualOn,
                    enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(260)) + fadeIn(tween(200)),
                    exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(260)) + fadeOut(tween(160)),
                ) {
                Column {
                    // Скриншот банка — только у карт: обмен наличных в приложении банка не виден.
                    if (!cash) {
                        ScreenshotButton(
                            result = scan,
                            card = currency,
                            onClick = { pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        if (pro && shots.isNotEmpty()) ReceiptShotRow(shots)
                    }
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
                        SubTitle(stringResource(R.string.receipt_atm))
                        if (atmSources.isEmpty()) {
                            Hint(stringResource(R.string.receipt_atm_no_cards))
                        } else {
                            val gotAtm = stringResource(R.string.receipt_atm_got)
                            val paidAtm = stringResource(R.string.receipt_atm_paid)
                            FieldPair(
                                first = { m ->
                                    AmountField(
                                        gotAtm, got, { got = it }, gotAtm, m,
                                        imeAction = ImeAction.Next,
                                        error = amountError(showErrors, got, sent),
                                        trailing = { FieldSuffix(currency.mark) },
                                    )
                                },
                                second = { m ->
                                    AmountField(
                                        paidAtm, sent, { sent = it }, paidAtm, m,
                                        error = amountError(showErrors, sent, got),
                                        trailing = {
                                            CardDropdown(
                                                atmSource,
                                                atmSources,
                                                {
                                                    fromCard = it.id
                                                    sentCurrency = it.currency
                                                },
                                                stringResource(R.string.editor_pick_card),
                                            )
                                        },
                                        selectable = true,
                                    )
                                },
                            )
                            val transfer = draft.transfer
                            if (transfer != null) {
                                ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.mark, transfer.sentCurrency.mark))
                                ReceiptTypo(transfer.sent, transfer.got, official?.cross(currency, transfer.sentCurrency))
                                // Итог в итоговой валюте — по курсу карты, с которой сняли.
                                RateEngine.cardToHome(draft, home, official, cards)?.first?.let { perCash ->
                                    val u = Numbers.unitFor(perCash)
                                    ReceiptRate(
                                        stringResource(
                                            R.string.receipt_bought_total,
                                            "${Numbers.moneyWhole(u)} ${currency.mark}",
                                            "${Numbers.rate(perCash.multiply(u, MC))} ${home.mark}",
                                        ),
                                        whole = true,
                                    )
                                }
                            }
                            Hint(stringResource(R.string.receipt_atm_hint))
                        }
                    } else if (!transferOpen && !cash) {
                        // Новая карта: пополнения ещё нет — заглушка с крупной кнопкой.
                        SubTitle(stringResource(R.string.receipt_transfer))
                        EmptyReceipts(stringResource(R.string.transfer_empty), stringResource(R.string.transfer_add_first)) { transferOpen = true }
                    } else {
                        SubTitle(
                            stringResource(if (cash) R.string.receipt_exchange else R.string.receipt_transfer),
                            trailing = card.transfer?.date?.takeIf { sent.isNotBlank() }?.let(RuDates::short),
                        )
                        val sentLabel = stringResource(if (cash) R.string.receipt_gave else R.string.receipt_sent)
                        val gotLabel = stringResource(if (cash) R.string.receipt_received else R.string.receipt_got)
                        FieldPair(
                            first = { m ->
                                AmountField(
                                    sentLabel, sent, { sent = it }, sentLabel, m,
                                    imeAction = ImeAction.Next,
                                    error = amountError(showErrors, sent, got),
                                    trailing = {
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
                                    },
                                    selectable = true,
                                )
                            },
                            second = { m ->
                                AmountField(
                                    gotLabel, got, { got = it }, gotLabel, m,
                                    error = amountError(showErrors, got, sent),
                                    trailing = { FieldSuffix(currency.mark) },
                                )
                            },
                        )
                        // Чек заполнен — курс по нему; пустой — что будет без него.
                        val transfer = draft.transfer
                        if (transfer != null) {
                            ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.mark, transfer.sentCurrency.mark))
                            ReceiptTypo(transfer.sent, transfer.got, official?.cross(currency, transfer.sentCurrency))
                        } else {
                            Hint(stringResource(if (cash) R.string.receipt_exchange_hint else R.string.receipt_transfer_hint))
                        }
                        // Прежние пополнения: «+ Новое пополнение» уносит заполненное в историю и очищает поля.
                        TransferHistory(
                            history = history,
                            mode = transferMode,
                            cardSymbol = currency.mark,
                            average = draft.effectiveTransfer?.takeIf { history.isNotEmpty() && transferMode == TransferMode.AVERAGE },
                            canAddNew = transfer != null,
                            newLabel = stringResource(if (cash) R.string.exchange_new else R.string.transfer_new),
                            onMode = { transferMode = it },
                            onRemove = { history.remove(it) },
                            onNew = {
                                transfer?.let { history.add(0, it) }
                                while (history.size > HISTORY_MAX) history.removeAt(history.lastIndex)
                                sent = ""
                                got = ""
                                boughtAt = ""
                            },
                        )
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

                    if (!cash) {
                        PurchaseReceipts(
                            purchases = purchases,
                            showErrors = showErrors,
                            official = official,
                            cardCurrency = currency,
                            spend = spend,
                            pick = pick,
                            // Подсказка — только если курс для валюты трат оценочный или идёт через UnionPay.
                            hint = when {
                                draft.purchases.isEmpty() -> null
                                q?.basis == RateBasis.UNIONPAY ->
                                    stringResource(R.string.receipt_unionpay_hint, spend.mark, draft.settlementCurrency.code)
                                q != null && q.basis == RateBasis.ESTIMATE && q.viaUnionPay ->
                                    stringResource(R.string.receipt_estimate_hint_unionpay, spend.mark)
                                q?.basis == RateBasis.ESTIMATE -> stringResource(R.string.receipt_estimate_hint, spend.mark)
                                else -> null
                            },
                            onAdd = {
                                val used = purchases.map { it.spend }.toSet()
                                val first = if (spend !in used) spend else Currency.POPULAR.firstOrNull { it !in used } ?: spend
                                purchases.add(PurchaseDraft(nextKey++, first, "", "", LocalDate.now()))
                            },
                        )
                    }

                    // Итог по чекам (без своего курса) — тот же расчёт, что на главном экране.
                    val auto = RateEngine.quote(draft.withoutManual(spend, home), spend, home, official, unionPay, cards)
                    if (auto != null) {
                        val unit = Numbers.unitFor(auto.homePerUnit)
                        val exact = auto.basis == RateBasis.RECEIPTS
                        ResultBox(
                            title = stringResource(if (exact) R.string.editor_result_title else R.string.editor_result_estimate),
                            value = stringResource(
                                if (exact) R.string.editor_result else R.string.editor_result_approx,
                                "${Numbers.moneyWhole(unit)} ${spend.mark}",
                                Numbers.rateShort(auto.homePerUnit.multiply(unit, MC)),
                                home.mark,
                            ),
                            // Почему примерно — словами: чаще всего не указано, почём купили отданную валюту.
                            sub = when {
                                exact -> null
                                sentCurrency != home && boughtAt.isBlank() && draft.transfer != null && !(cash && atm) ->
                                    stringResource(R.string.editor_result_why_bought, sentCurrency.mark)
                                else -> basisLabel(auto)
                            },
                            exact = exact,
                        )
                    } else {
                        Hint(stringResource(R.string.editor_no_result))
                    }
                }
                }
                // Свой курс — за переключателем; включён — чеки выше свёрнуты.
                val autoRate = RateEngine.quote(draft.withoutManual(spend, home), spend, home, official, unionPay, cards)?.homePerUnit
                ManualRatePanel(
                    on = manualOn,
                    onToggle = { manualOn = it },
                    unit = manualUnit,
                    spend = spend,
                    home = home,
                    value = manual,
                    onValueChange = { manual = it },
                    auto = autoRate,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            if (showErrors && invalid()) {
                Text(
                    stringResource(R.string.editor_fix_fields),
                    style = type.caption.copy(fontSize = 13.sp),
                    color = colors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.danger.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(8.dp))
            }
            // «Сохранить» (Material 3 Expressive): нажали — кнопка сжимается в круг с «✓», чуть держит его и
            // только потом экран закрывается: видно, что сохранилось. Ошибки — подсвечиваем, кнопку не блокируем.
            SaveButton(
                saving = pendingSave != null,
                onClick = {
                    if (invalid()) {
                        showErrors = true
                        haptics.play(Haptic.REJECT)
                    } else {
                        haptics.play(Haptic.CONFIRM)
                        pendingSave = build()
                    }
                },
            )
        }
      }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            text = { Text(stringResource(R.string.editor_exit_confirm), style = type.body, color = colors.ink) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    close()
                }) { Text(stringResource(R.string.editor_exit), style = type.bodyStrong, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.editor_stay), style = type.bodyStrong, color = colors.accent) }
            },
            containerColor = colors.surface,
        )
    }
    if (cardPicker) {
        CurrencyPickerScreen(
            title = stringResource(if (cash) R.string.editor_currency_cash else R.string.editor_currency),
            selected = currency,
            options = Currency.entries,
            onPick = { currency = it },
            onDismiss = { cardPicker = false },
        )
    }
    if (settlementPicker) {
        CurrencyPickerScreen(
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
                    haptics.play(Haptic.THUD)
                    close(onDelete)
                }) { Text(stringResource(R.string.delete), style = type.bodyStrong, color = colors.danger) }
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

/** Значение для поля чека: «2757,48» — разряды поле показывает само. */
private fun field(value: BigDecimal): String = Numbers.plain(value)

private const val MAX_NAME = 24

/** Что несут в обменник за местные наличные (кроме итоговой валюты — она есть всегда). */
private val EXCHANGE_FROM = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

/** Сколько валют показывать чипами в редакторе карты. */
private const val EDITOR_CHIPS = 4

/** Сколько «✓» на кнопке держится перед закрытием экрана. */
private const val SAVE_HOLD_MS = 450L

/** Карточка-предпросмотр в редакторе. */
private val PREVIEW_WIDTH = 190.dp
private val PREVIEW_HEIGHT = 100.dp
private val PREVIEW_AMOUNT = BigDecimal(100)

private fun CardNetwork?.isUnionPay(): Boolean = this == CardNetwork.UNIONPAY || this == CardNetwork.UNIONPAY_FOREIGN
