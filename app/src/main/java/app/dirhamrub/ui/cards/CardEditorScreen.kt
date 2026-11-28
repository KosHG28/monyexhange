package app.dirhamrub.ui.cards

import app.dirhamrub.data.ReceiptShots
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import app.dirhamrub.ui.components.CardMorph
import app.dirhamrub.ui.components.cardFlightBounds
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.rememberReflectionTilt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
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
import app.dirhamrub.ui.components.ScrollBarDivider
import app.dirhamrub.ui.components.fadingEdges
import app.dirhamrub.ui.components.finishCascadeOnTouch
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Rounding
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.HISTORY_MAX
import app.dirhamrub.domain.wallet.ManualRate
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.BankImport
import app.dirhamrub.domain.wallet.ScannedReceipt
import app.dirhamrub.domain.wallet.TransferMode
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import app.dirhamrub.domain.wallet.preserveWithdrawalCost
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.currency.CurrencyPickerScreen
import app.dirhamrub.ui.main.BankCardView
import app.dirhamrub.ui.main.RING_GAP
import app.dirhamrub.ui.main.basisLabel
import app.dirhamrub.ui.components.rateLabel
import app.dirhamrub.ui.theme.surfaceOutline
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onSave: suspend (BankCard, Map<String, android.net.Uri>, Set<String>) -> Boolean,
    onDelete: suspend () -> Boolean,
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
    editorMotion: Boolean = false,
    morph: CardMorph? = null,
    onExitStarted: () -> Unit = {},
    /** Pro: скриншот банка сохраняется при чеке. */
    pro: Boolean = false,
    /** Сразу открыть выбор скриншота банка (Pro: ярлык «Чек из скриншота»). */
    startScan: Boolean = false,
    coveredByCamera: Boolean = false,
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

    val initialTemplate = stringResource(if (card.kind == CardKind.CASH) R.string.cash_default_name else R.string.card_default_name, card.currency.code)
    var name by rememberSaveable(card.id) { mutableStateOf(card.name.takeUnless { isNew && it == initialTemplate }.orEmpty()) }
    var nameEdited by rememberSaveable(card.id) { mutableStateOf(false) }
    var currency by rememberSaveable(card.id) { mutableStateOf(card.currency) }
    var newKind by rememberSaveable(card.id) { mutableStateOf(card.kind) }
    val kind = if (isNew) newKind else card.kind
    // UnionPay — одна из платёжных систем: выбрали её — карта считается картой UnionPay (без отдельного переключателя).
    var network by rememberSaveable(card.id) { mutableStateOf(card.paymentNetwork) }
    val cash = kind == CardKind.CASH
    val savedTransfers = remember(card.id) {
        (listOfNotNull(card.transfer) + card.history).sortedByDescending { it.date }
    }
    val savedTransfer = savedTransfers.firstOrNull()
    val savedRegular = savedTransfers.firstOrNull { card.kind != CardKind.CASH || it.fromCard == null }
    val savedWithdrawal = savedTransfers.firstOrNull { card.kind == CardKind.CASH && it.fromCard != null }
    val history = rememberSaveable(card.id, saver = transferDraftsSaver) {
        mutableStateListOf<TransferReceipt>().apply {
            addAll(savedTransfers.filter { it !== savedRegular && it !== savedWithdrawal })
        }
    }
    var transferMode by rememberSaveable(card.id) { mutableStateOf(card.transferMode) }
    val atmSources = cards.filter { it.kind == CardKind.CARD && it.id != card.id }
    val referenceAtOpen = remember(card.id) { official }
    fun boughtUnitFor(c: Currency, receipt: TransferReceipt?): BigDecimal = Numbers.unitFor(
        receipt?.bought?.takeIf { it.second == home && it.first.signum() > 0 }?.first
            ?: referenceAtOpen?.cross(c, home)
            ?: BigDecimal.ONE,
    )
    fun displayBought(receipt: TransferReceipt?): String = receipt?.bought?.takeIf { it.second == home }?.let {
        Numbers.plain(it.first.multiply(boughtUnitFor(receipt.sentCurrency, receipt), MC), 4)
    }.orEmpty()
    val regular = rememberSaveable(card.id, saver = fundingDraftSaver) {
        FundingDraft(savedRegular, home, boughtAt = displayBought(savedRegular))
    }
    val withdrawal = rememberSaveable(card.id, saver = fundingDraftSaver) {
        val first = atmSources.firstOrNull()
        FundingDraft(savedWithdrawal, first?.currency ?: home, defaultCard = first?.id)
    }
    var atm by rememberSaveable(card.id) { mutableStateOf(savedTransfer?.fromCard != null && card.kind == CardKind.CASH) }
    val funding = if (cash && atm) withdrawal else regular
    var sent by funding.sent
    var got by funding.got
    var sentCurrency by funding.sentCurrency
    var fromCard by funding.fromCard
    var boughtAt by funding.boughtAt
    val boughtUnit = boughtUnitFor(sentCurrency, funding.original.value)
    val purchases = rememberSaveable(card.id, saver = purchaseDraftsSaver) {
        mutableStateListOf<PurchaseDraft>().apply {
            card.purchases.forEachIndexed { i, p -> add(PurchaseDraft(i, p.spend, field(p.paid), field(p.price), p.date)) }
            // Открыли по подсказке «добавить чек»: сразу пустая строка покупки в валюте трат.
            if (addPurchaseFor != null && card.purchases.none { it.spend == addPurchaseFor }) {
                add(PurchaseDraft(card.purchases.size, addPurchaseFor, "", "", LocalDate.now()))
            }
        }
    }
    var nextKey by rememberSaveable(card.id) { mutableStateOf(purchases.size) }
    // Свой курс — на ту же единицу, что в итоге и на картах («100 ₸ = …»); единица считается один раз при открытии,
    // чтобы не менялась, пока курс набирают.
    val manualUnit = rememberSaveable(card.id) {
        val auto = RateEngine.quote(card.withoutManual(spend, home), spend, home, official, unionPay, cards)?.homePerUnit
        (auto ?: card.manualFor(spend, home)?.homePerUnit)?.let(Numbers::unitFor) ?: BigDecimal.ONE
    }
    var manual by rememberSaveable(card.id) {
        mutableStateOf(card.manualFor(spend, home)?.homePerUnit?.let { Numbers.exactInput(it.multiply(manualUnit)) }.orEmpty())
    }
    // Свой курс — за переключателем: выключен — считаем по чекам, второго курса на экране нет.
    var manualOn by rememberSaveable(card.id) { mutableStateOf(card.manualFor(spend, home) != null) }
    // Пополнение у новой карты — сначала заглушка с кнопкой «+ Добавить пополнение».
    var transferOpen by rememberSaveable(card.id) { mutableStateOf(card.transfer != null || card.history.isNotEmpty() || kind == CardKind.CASH) }
    var confirmDelete by remember(card.id) { mutableStateOf(false) }
    // Сохраняем: кнопка показывает «✓», потом экран закрывается с этой картой.
    var pendingSave by remember(card.id) { mutableStateOf<BankCard?>(null) }
    var saved by remember(card.id) { mutableStateOf(false) }
    var deleting by remember(card.id) { mutableStateOf(false) }
    var storageFailed by remember(card.id) { mutableStateOf(false) }
    // Нажали «Сохранить», а в чеке заполнена только одна сумма из двух — подсвечиваем вторую, шторку не закрываем.
    var showErrors by remember(card.id) { mutableStateOf(false) }
    var cardPicker by remember(card.id) { mutableStateOf(false) }

    // Чек из скриншота: уведомление или операция в приложении банка → суммы в поля чека.
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var scan by remember(card.id) { mutableStateOf<ScanResult?>(null) }
    var bankReview by remember(card.id) { mutableStateOf<BankReview?>(null) }
    val stagedShots = remember(card.id) { mutableMapOf<String, StagedReceiptImage>() }
    var currencyConflict by remember(card.id) { mutableStateOf(false) }
    fun hasReceiptAmounts(): Boolean =
        history.isNotEmpty() || regular.hasAmounts || withdrawal.hasAmounts ||
            purchases.any { it.paid.isNotBlank() || it.price.isNotBlank() }

    fun changeCurrency(value: Currency): Boolean {
        if (currency == value) return true
        if (hasReceiptAmounts() || bankReview != null || scan == ScanResult.Reading) {
            currencyConflict = true
            return false
        }
        currency = value
        return true
    }

    fun trimHistory() {
        if (history.size <= HISTORY_MAX) return
        val keep = history.sortedByDescending { it.date }.take(HISTORY_MAX)
        history.clear()
        history.addAll(keep)
    }

    fun removeCurrentTransfer() {
        focus.clearFocus()
        if (!cash) stagedShots.remove(ReceiptShots.TRANSFER_KIND)
        val source = funding.fromCard.value
        val previous = history.filter { !cash || (it.fromCard != null) == atm }.maxByOrNull { it.date }
        if (previous != null) history.remove(previous)
        funding.replace(previous, if (cash && atm) sentCurrency else home, displayBought(previous))
        if (cash && atm && previous == null) funding.fromCard.value = source
        if (history.isEmpty()) transferMode = TransferMode.LAST
        transferOpen = previous != null
    }

    // Карта, приложенная к телефону: система и валюта сразу в поля.
    val resources = LocalResources.current
    var nfc by remember(card.id) { mutableStateOf<NfcRead?>(null) }
    val nfcAvailability = rememberNfcAvailability(observe = !cash)
    NfcCardReader(enabled = nfcAvailability == NfcAvailability.READY && !cash && pendingSave == null && !deleting && !coveredByCamera) { result ->
        nfc = result
        if (result is NfcRead.Found) {
            result.info.currency?.let { detected ->
                val oldTemplate = resources.getString(R.string.card_default_name, currency.code)
                if (changeCurrency(detected)) {
                    name = nameAfterNfcRead(name, nameEdited, oldTemplate,
                        resources.getString(R.string.card_default_name, detected.code))
                } else {
                    nfc = null
                    haptics.play(Haptic.REJECT)
                    return@NfcCardReader
                }
            }
            network = result.info.network
            haptics.play(Haptic.TICK)
        } else {
            haptics.play(Haptic.REJECT)
        }
    }
    // Pro: сохранённые скриншоты чеков этой карты (новые первыми).
    var shots by remember(card.id) { mutableStateOf<List<app.dirhamrub.data.ReceiptFile>>(emptyList()) }
    LaunchedEffect(card.id, pro) {
        shots = if (pro) {
            val kinds = buildSet {
                card.purchases.forEach { add(ReceiptShots.purchaseKind(it.spend.code)) }
                if (savedTransfer != null) add(ReceiptShots.TRANSFER_KIND)
            }
            ReceiptShots.entries(context.applicationContext, card.id, kinds)
        } else emptyList()
    }
    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scan = ScanResult.Reading
        val cardCurrency = currency
        scope.launch {
            val receipt = readBankScreenshot(context.applicationContext, uri)
            val candidates = withContext(Dispatchers.Default) {
                receipt?.let { BankImport.candidates(it.lines, cardCurrency) }.orEmpty()
            }
            scan = when {
                receipt == null -> ScanResult.ReadError
                candidates.isEmpty() -> ScanResult.NotFound
                else -> null
            }
            if (receipt != null && candidates.isNotEmpty()) bankReview = BankReview(uri, receipt, candidates, cardCurrency)
            else haptics.play(Haptic.REJECT)
        }
    }
    var autoScanLaunched by rememberSaveable(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id, startScan) {
        if (startScan && !autoScanLaunched) {
            autoScanLaunched = true
            pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val defaultName = stringResource(if (cash) R.string.cash_default_name else R.string.card_default_name, currency.code)
    fun fundingReceipt(data: FundingDraft, fromAtm: Boolean): TransferReceipt? {
        val sentValue = Numbers.parse(data.sent.value)?.takeIf { it.signum() > 0 } ?: return null
        val gotValue = Numbers.parse(data.got.value)?.takeIf { it.signum() > 0 } ?: return null
        val sourceId = if (fromAtm) data.fromCard.value ?: return null else null
        val sourceCurrency = data.sentCurrency.value
        val original = data.original.value?.takeIf { it.sentCurrency == sourceCurrency && it.fromCard == sourceId }
        val sameAmounts = original != null && original.sent.compareTo(sentValue) == 0 && original.got.compareTo(gotValue) == 0
        val keptBought = original?.bought?.takeIf {
            (fromAtm && sameAmounts) || (!fromAtm && data.boughtAt.value == displayBought(original))
        }
        val newBought = if (fromAtm || sourceCurrency == home) null else
            Numbers.parse(data.boughtAt.value)?.takeIf { it.signum() > 0 }?.divide(boughtUnitFor(sourceCurrency, original), MC)
        val unchangedRate = original?.bought == keptBought && (keptBought != null || newBought == null)
        if (sameAmounts && unchangedRate) return original
        val receipt = TransferReceipt(
            sentValue,
            sourceCurrency,
            gotValue,
            data.original.value?.date ?: LocalDate.now(),
            keptBought?.first ?: newBought,
            keptBought?.second ?: newBought?.let { home },
            fromCard = sourceId,
            sourceRateEstimated = keptBought != null && original.sourceRateEstimated,
        )
        val source = if (fromAtm) atmSources.firstOrNull { it.id == sourceId } else null
        return if (source == null) receipt else receipt.preserveWithdrawalCost(source, home, official)
    }

    fun build(forSave: Boolean = false): BankCard {
        val today = LocalDate.now()
        val active = fundingReceipt(funding, cash && atm)
        val inactive = if (cash && forSave) fundingReceipt(if (atm) regular else withdrawal, !atm) else null
        val drafts = if (inactive != null && savedTransfer != null && inactive == savedTransfer &&
            active == funding.original.value
        ) listOfNotNull(inactive, active) else listOfNotNull(active, inactive)
        val saved = drafts + if (forSave) history.toList() else history.filter { !cash || (it.fromCard != null) == atm }
        // У наличных покупок по чекам нет: платят ровно ценой в той же валюте.
        val receipts = if (kind == CardKind.CASH) emptyList() else purchases.mapNotNull { d ->
            val receipt = d.receipt ?: return@mapNotNull null
            val old = card.purchases.getOrNull(d.key)
            old?.takeIf { it.spend == receipt.spend && it.date == receipt.date &&
                it.paid.compareTo(receipt.paid) == 0 && it.price.compareTo(receipt.price) == 0 } ?: receipt
        }
        val manualValue = Numbers.parse(manual)?.takeIf { manualOn && it.signum() > 0 }?.divide(manualUnit)
        val oldManual = card.manualFor(spend, home)
        val updatedManual = manualValue?.let { value ->
            oldManual?.takeIf { it.homePerUnit.compareTo(value) == 0 } ?: ManualRate(spend, home, value, today)
        }
        val otherManual = card.manual.filterNot { it.spend == spend && it.home == home }
        val previewOrSaved = card.copy(
            name = name.trim().ifEmpty { defaultName },
            currency = currency,
            kind = kind,
            // UnionPay (российская и зарубежная) — ещё и пересчёт по курсу UnionPay; российская хранится как раньше,
            // без отдельной платёжной системы.
            unionPay = network.isUnionPay() && kind == CardKind.CARD,
            network = network?.takeIf { kind == CardKind.CARD && it != CardNetwork.UNIONPAY },
            history = emptyList(),
            transferMode = if (saved.size <= 1) TransferMode.LAST else transferMode,
            transfer = null,
            purchases = receipts,
            manual = otherManual + listOfNotNull(updatedManual),
        )
        if (forSave) return previewOrSaved.withTransfers(saved)
        return previewOrSaved.copy(transfer = active, history = saved.drop(if (active != null) 1 else 0))
    }

    fun invalid(data: FundingDraft): Boolean = pairInvalid(data.sent.value, data.got.value)
    fun invalidManual(): Boolean = manualOn && Numbers.parse(manual)?.signum() != 1
    fun invalid(): Boolean = invalidManual() || invalid(regular) || (cash && invalid(withdrawal)) ||
        (!cash && purchases.any { pairInvalid(it.paid, it.price) })

    // A partially filled tab is dirty even when it contains no complete receipt yet.
    val initial = rememberSaveable(card.id, saver = bankCardSaver) { build(forSave = true) }
    fun dirty(): Boolean = invalid() || build(forSave = true) != initial
    fun back() {
        if (pendingSave != null || deleting) return
        if (dirty()) confirmExit = true else close()
    }

    FullScreen(onDismissRequest = onDismiss, interceptBack = { pendingSave != null || deleting || dirty().also { if (it) confirmExit = true } },
        editorMotion = editorMotion, morph = morph, onExitStarted = onExitStarted) {
      screen[0] = this
      val saveReduce = LocalReduceMotion.current
      LaunchedEffect(pendingSave) {
          val result = pendingSave ?: return@LaunchedEffect
          val images = stagedShots.mapNotNull { (key, image) ->
              if (image.matches(result)) key to image.uri else null
          }.toMap()
          val staleShots = card.purchases.map { it.spend }.distinct().mapNotNull { spend ->
              ReceiptShots.purchaseKind(spend.code).takeIf { card.purchaseFor(spend) != result.purchaseFor(spend) }
          }.toMutableSet()
          if (savedTransfer != null && savedTransfer !in (listOfNotNull(result.transfer) + result.history)) {
              staleShots += ReceiptShots.TRANSFER_KIND
          }
          if (onSave(result, images, staleShots)) {
              saved = true
              haptics.play(Haptic.CONFIRM)
              if (!saveReduce) delay(SAVE_CONFIRMATION_MS)
              close()
          } else {
              pendingSave = null
              storageFailed = true
              haptics.play(Haptic.REJECT)
          }
      }
      LaunchedEffect(deleting) {
          if (!deleting) return@LaunchedEffect
          if (onDelete()) close() else {
              deleting = false
              storageFailed = true
              haptics.play(Haptic.REJECT)
          }
      }
      Column(Modifier.fillMaxSize()) {
        val editorScroll = rememberLazyListState()
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
            if (!isNew && pendingSave == null && !deleting) {
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
        ScrollBarDivider(editorScroll.canScrollBackward)
        // Блоки редактора на остальных маршрутах могут появляться каскадом.
        val cascade = rememberCascade(lastIndex = 1, enabled = !editorMotion && morph == null)
        // «Сохранить» всегда внизу экрана, прокручивается только содержимое над ней: последний элемент
        // докручивается выше кнопки. Касание мимо полей убирает клавиатуру.
        val pick = stringResource(R.string.editor_pick_currency)
        val blockTitle = stringResource(if (cash) R.string.editor_block_cash else R.string.editor_block_rate)
        fun addPurchase() {
            val used = purchases.map { it.spend }.toSet()
            val first = if (spend !in used) spend else Currency.POPULAR.firstOrNull { it !in used } ?: spend
            purchases.add(PurchaseDraft(nextKey++, first, "", "", LocalDate.now()))
        }
        // Черновик из полей и его курс — для предпросмотра сверху и итога ниже.
        val draft by remember(card, spend, home, kind, defaultName, funding, official, cards) { derivedStateOf { build() } }
        val previewSpend = if (cash) currency else spend
        val travelQuote = remember(draft, previewSpend, home, official, unionPay, cards) {
            RateEngine.quote(draft, previewSpend, home, official, unionPay, cards)
        }
        val auto = remember(draft, travelQuote, previewSpend, spend, home, official, unionPay, cards) {
            if (previewSpend == spend && travelQuote?.basis != RateBasis.MANUAL) travelQuote
            else RateEngine.quote(draft.withoutManual(spend, home), spend, home, official, unionPay, cards)
        }
        val receipt = draft.effectiveTransfer?.takeIf { cash && it.isValid }
        val previewHome = if (receipt != null && (travelQuote == null || currency == home)) receipt.sentCurrency else home
        val q = if (previewHome == home) travelQuote else RateEngine.quote(draft, previewSpend, previewHome, official, unionPay, cards)
        val amountInPreview = if (previewSpend == spend) previewAmount else PREVIEW_AMOUNT
        val preview = EditorCardPreview(
            draft, q, previewSpend, previewHome,
            q?.let { Numbers.total(amountInPreview.multiply(it.homePerUnit, MC), rounding, previewHome.digits.coerceAtLeast(2)) },
            needsReceipt = official?.cross(previewSpend, previewHome) == null,
        )
        LazyColumn(
            state = editorScroll,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
                .finishCascadeOnTouch(cascade)
                .fadingEdges(editorScroll, edge = 0.dp, bottomEdge = 32.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
          item(key = "main") {
            Column {
            if (!isNew) Spacer(Modifier.height(6.dp))
            var previewVisible by remember { mutableStateOf(false) }
            val reflectionTilt = rememberReflectionTilt(
                active = !coveredByCamera && previewVisible && !isClosing && !cardPicker && !confirmExit && !confirmDelete && morph?.flying != true,
            )
            if (morph != null && !saveReduce) {
                SideEffect { morph.card = { textAlpha -> PreviewCard(preview, reflectionTilt, textAlpha) } }
                DisposableEffect(morph) {
                    onDispose {
                        morph.target = null
                        morph.card = null
                    }
                }
            }
            val previewInset = if (isNew) 8.dp else 0.dp
            Box(
                Modifier.fillMaxWidth().height(PREVIEW_HEIGHT + (RING_GAP + previewInset) * 2)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        previewVisible = !bounds.isEmpty
                        if (morph != null && !saveReduce) {
                            morph.target = it.cardFlightBounds().takeUnless { target ->
                                target.isEmpty || morph.settled && bounds.isEmpty
                            }
                            morph.targetMeasured = true
                        }
                    }.graphicsLayer { if (morph?.flying == true && !saveReduce) alpha = 0f },
                ) {
                    if (isNew) {
                        AnimatedContent(
                            targetState = preview,
                            contentKey = { it.card.kind },
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                if (saveReduce) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    val direction = if (targetState.card.kind == CardKind.CASH) 1 else -1
                                    val easing = FastOutSlowInEasing
                                    val enter = slideInHorizontally(tween(EDITOR_KIND_MOTION_MS, easing = easing)) {
                                        (it * 0.24f * direction).toInt()
                                    } + fadeIn(tween(EDITOR_KIND_MOTION_MS, easing = easing)) +
                                        scaleIn(tween(EDITOR_KIND_MOTION_MS, easing = easing), initialScale = 0.94f)
                                    val exit = slideOutHorizontally(tween(EDITOR_KIND_MOTION_MS, easing = easing)) {
                                        (-it * 0.18f * direction).toInt()
                                    } + fadeOut(tween(EDITOR_KIND_MOTION_MS, easing = easing)) +
                                        scaleOut(tween(EDITOR_KIND_MOTION_MS, easing = easing), targetScale = 0.92f)
                                    (enter togetherWith exit).apply { targetContentZIndex = 1f }
                                        .using(SizeTransform(clip = false))
                                }
                            },
                            label = "CardKindPreview",
                        ) { shown ->
                            // The fading layer includes space for the card shadow on every side.
                            Box(Modifier.padding(previewInset)) { PreviewCard(shown, reflectionTilt) }
                        }
                    } else {
                        PreviewCard(preview, reflectionTilt)
                    }
                }
            }
            if (!isNew) Spacer(Modifier.height(8.dp))
            // Карта или наличные из обменника: у наличных вместо пополнения — обмен, покупок по чекам нет.
            if (isNew) {
                KindSwitch(kind) { newKind = it }
                Spacer(Modifier.height(10.dp))
            }

            // Блок 1. Основное: название, валюта, платёжная система.
            EditorBlock(stringResource(R.string.editor_block_main), Modifier.cascade(cascade, 0)) {
                if (!cash && nfcAvailability != NfcAvailability.UNSUPPORTED) {
                    NfcHint(nfc, enabled = nfcAvailability == NfcAvailability.READY, animate = !isClosing)
                    Spacer(Modifier.height(12.dp))
                }
                FieldLabel(stringResource(R.string.editor_name))
                BasicTextField(
                    value = name,
                    onValueChange = {
                        val value = it.take(MAX_NAME)
                        if (value != name) {
                            name = value
                            nameEdited = true
                        }
                    },
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
                    onSelect = { changeCurrency(it) },
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

            Spacer(Modifier.height(10.dp))
            }
          }
          item(key = "funding") {
            EditorRateSegment(top = true, modifier = Modifier.cascade(cascade, 1)) {
                Text(blockTitle.uppercase(), style = type.section, color = colors.muted,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })

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
                    val removeTransfer: (@Composable () -> Unit)? = if (sent.isNotBlank() || got.isNotBlank()) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.transfer_remove_current),
                                tint = AppTheme.colors.muted,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = ::removeCurrentTransfer).padding(13.dp),
                            )
                        }
                    } else null
                    // Наличные — из обменника или из банкомата (снятие с одной из карт).
                    if (cash) {
                        CashSourceSwitch(atm) { on ->
                            if (on != atm) {
                                focus.clearFocus()
                                atm = on
                            }
                        }
                        val date = draft.transfer?.date?.let(RuDates::short)
                        if (date != null || removeTransfer != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                date?.let { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.muted) }
                                removeTransfer?.invoke()
                            }
                        }
                    }
                    if (cash && atm) {
                        Spacer(Modifier.height(10.dp))
                        if (atmSources.isEmpty() && fromCard == null) {
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
                                        trailing = { FieldSuffix(currency.receiptMark) },
                                    )
                                },
                                second = { m ->
                                    AmountField(
                                        paidAtm, sent, { sent = it }, paidAtm, m,
                                        error = amountError(showErrors, sent, got),
                                        trailing = {
                                            CardDropdown(
                                                sentCurrency,
                                                atmSources,
                                                {
                                                    if (it.currency != sentCurrency) sent = ""
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
                                ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.receiptMark, transfer.sentCurrency.receiptMark))
                                ReceiptTypo(transfer.sent, transfer.got, official?.cross(currency, transfer.sentCurrency))
                                // Итог в итоговой валюте — по курсу карты, с которой сняли.
                                RateEngine.cardToHome(draft, home, official, cards)?.first?.let { perCash ->
                                    val u = Numbers.unitFor(perCash)
                                    ReceiptRate(
                                        stringResource(
                                            R.string.receipt_bought_total,
                                            "${Numbers.moneyWhole(u)} ${currency.receiptMark}",
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
                        if (cash) Spacer(Modifier.height(10.dp)) else SubTitle(
                            stringResource(R.string.receipt_transfer),
                            trailing = draft.transfer?.date?.let(RuDates::short),
                            action = removeTransfer,
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
                                            Currency.entries.filter { it != currency },
                                        )
                                    },
                                    selectable = true,
                                )
                            },
                            second = { m ->
                                AmountField(
                                    gotLabel, got, { got = it }, gotLabel, m,
                                    error = amountError(showErrors, got, sent),
                                    trailing = { FieldSuffix(currency.receiptMark) },
                                )
                            },
                        )
                        // Чек заполнен — курс по нему; пустой — что будет без него.
                        val transfer = draft.transfer
                        if (transfer != null) {
                            ReceiptRate(Numbers.transferRate(transfer.got.divide(transfer.sent, MC), currency.receiptMark, transfer.sentCurrency.receiptMark))
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
                    if (cash || transferOpen) {
                        val records = history.filter { (it.fromCard != null) == (cash && atm) }
                        TransferHistory(
                            history = records,
                            mode = transferMode,
                            cardSymbol = currency.receiptMark,
                            average = draft.effectiveTransfer?.takeIf { records.isNotEmpty() && transferMode == TransferMode.AVERAGE },
                            canAddNew = draft.transfer != null && (!cash || !atm || atmSources.isNotEmpty()),
                            newLabel = stringResource(when {
                                !cash -> R.string.transfer_new
                                atm -> R.string.withdrawal_new
                                else -> R.string.exchange_new
                            }),
                            onMode = { transferMode = it },
                            onRemove = {
                                history.remove(it)
                            },
                            onNew = {
                                draft.transfer?.let { history.add(0, it) }
                                trimHistory()
                                val source = fromCard
                                funding.replace(null, sentCurrency)
                                if (cash && atm) funding.fromCard.value = source
                                if (!cash) stagedShots.remove(ReceiptShots.TRANSFER_KIND)
                            },
                        )
                    }

                }
                }
            }
          }
          if (!cash && !manualOn) {
            item(key = "purchases") {
                EditorRateSegment {
                    SubTitle(stringResource(R.string.receipt_purchases))
                    if (purchases.isEmpty()) {
                        val union = draft.unionPay && currency != spend && unionPay?.cardRate(spend, currency) != null
                        val message = if (union) R.string.purchases_empty_unionpay else R.string.purchases_empty
                        EmptyReceipts(stringResource(message), stringResource(R.string.purchases_add_first), ::addPurchase)
                    }
                }
            }
            itemsIndexed(purchases, key = { _, draft -> "purchase-${draft.key}" }) { index, entry ->
                EditorRateSegment {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    PurchaseReceiptFields(entry, showErrors, official, currency, spend, pick) { purchases.remove(entry) }
                }
            }
          }
          item(key = "rate") {
            EditorRateSegment(bottom = true, modifier = Modifier.cascade(cascade, 1)) {
                val reduceMotion = LocalReduceMotion.current
                AnimatedVisibility(
                    visible = !manualOn,
                    enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(260)) + fadeIn(tween(200)),
                    exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(260)) + fadeOut(tween(160)),
                ) {
                  Column {
                    if (!cash && purchases.isNotEmpty()) {
                        AddAction(stringResource(R.string.receipt_add), ::addPurchase)
                        when {
                            q?.basis == RateBasis.UNIONPAY -> Hint(stringResource(R.string.receipt_unionpay_hint, spend.mark))
                            q?.basis == RateBasis.ESTIMATE && q.viaUnionPay ->
                                Hint(stringResource(R.string.receipt_estimate_hint_unionpay, spend.mark))
                            q?.basis == RateBasis.ESTIMATE -> Hint(stringResource(R.string.receipt_estimate_hint, spend.mark))
                        }
                    }
                    // Итог по чекам (без своего курса) — тот же расчёт, что на главном экране.
                    if (auto != null) {
                        val exact = auto.basis == RateBasis.RECEIPTS
                        ResultBox(
                            title = stringResource(if (exact) R.string.editor_result_title else R.string.editor_result_estimate),
                            value = rateLabel(auto.homePerUnit, spend, home, approximate = !exact),
                            // Почему примерно — словами: чаще всего не указано, почём купили отданную валюту.
                            sub = when {
                                exact -> null
                                sentCurrency != home && boughtAt.isBlank() && draft.transfer != null && !(cash && atm) ->
                                    stringResource(R.string.editor_result_why_bought, sentCurrency.receiptMark)
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
                ManualRatePanel(
                    on = manualOn,
                    onToggle = { manualOn = it },
                    unit = manualUnit,
                    spend = spend,
                    home = home,
                    value = manual,
                    onValueChange = { manual = it },
                    auto = auto?.homePerUnit,
                )
            }
          }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            if (storageFailed || showErrors && invalid()) {
                Text(
                    stringResource(when {
                        storageFailed -> R.string.editor_storage_failed
                        invalidManual() -> R.string.editor_manual_invalid
                        else -> R.string.editor_fix_fields
                    }),
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
            SaveButton(
                saving = pendingSave != null,
                saved = saved,
                enabled = !deleting && !isClosing,
                onClick = {
                    storageFailed = false
                    if (invalid()) {
                        showErrors = true
                        if (cash && invalid(if (atm) regular else withdrawal) && !invalid(funding)) atm = !atm
                        if (!cash && !manualOn) {
                            val invalidIndex = purchases.indexOfFirst { pairInvalid(it.paid, it.price) }
                            if (invalidIndex >= 0) scope.launch { editorScroll.scrollToItem(3 + invalidIndex) }
                        }
                        haptics.play(Haptic.REJECT)
                    } else {
                        focus.clearFocus(force = true)
                        pendingSave = build(forSave = true)
                    }
                },
            )
        }
      }
    }
    bankReview?.let { review ->
        BankReviewDialog(review, official, allowPurchase = !cash,
            onDismiss = { bankReview = null },
            onApply = { receipt, date ->
                when (receipt) {
                    is ScannedReceipt.Purchase -> {
                        val existing = purchases.lastOrNull {
                            it.spend == receipt.spend && it.date == date &&
                                Numbers.parse(it.paid)?.compareTo(receipt.paid) == 0 &&
                                Numbers.parse(it.price)?.compareTo(receipt.price) == 0
                        }
                        if (existing == null) {
                            purchases.add(PurchaseDraft(nextKey++, receipt.spend, field(receipt.paid), field(receipt.price), date))
                        }
                        if (pro && build().purchaseFor(receipt.spend)?.date == date) {
                            stagedShots[ReceiptShots.purchaseKind(receipt.spend.code)] =
                                StagedReceiptImage(review.uri, receipt, review.cardCurrency, date)
                        }
                    }
                    is ScannedReceipt.Transfer -> {
                        val imported = TransferReceipt(receipt.sent, receipt.sentCurrency, receipt.got, date)
                        val current = fundingReceipt(regular, fromAtm = false)
                        if (current != null && date.isBefore(current.date)) {
                            if (imported !in history) history.add(0, imported)
                        } else {
                            if (current != null && current != imported) history.add(0, current)
                            regular.replace(imported, home)
                            if (pro) stagedShots[ReceiptShots.TRANSFER_KIND] =
                                StagedReceiptImage(review.uri, receipt, review.cardCurrency, date)
                        }
                        trimHistory()
                        transferOpen = true
                        atm = false
                    }
                }
                scan = ScanResult.Found(receipt)
                haptics.play(Haptic.CONFIRM)
                bankReview = null
            })
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
            onPick = { changeCurrency(it) },
            onDismiss = { cardPicker = false },
        )
    }
    if (currencyConflict) {
        AlertDialog(
            onDismissRequest = { currencyConflict = false },
            text = { Text(stringResource(R.string.editor_currency_receipts_protected)) },
            confirmButton = {
                TextButton(onClick = { currencyConflict = false }) { Text(stringResource(android.R.string.ok)) }
            },
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
                    storageFailed = false
                    deleting = true
                }) { Text(stringResource(R.string.delete), style = type.bodyStrong, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), style = type.bodyStrong, color = colors.muted) }
            },
            containerColor = colors.surface,
        )
    }
}

internal fun nameAfterNfcRead(name: String, edited: Boolean, oldTemplate: String, newTemplate: String): String =
    if (name == oldTemplate && !edited) newTemplate else name

/** Карта без своего курса для этой пары — чтобы показать, сколько выходит по чекам. */
private fun BankCard.withoutManual(spend: Currency, home: Currency): BankCard =
    copy(manual = manual.filterNot { it.spend == spend && it.home == home })

/** Значение для поля чека: «2757,48» — разряды поле показывает само. */
private class FundingDraft(
    receipt: TransferReceipt?,
    defaultCurrency: Currency,
    boughtAt: String = "",
    defaultCard: String? = null,
) {
    val sent = mutableStateOf(receipt?.sent?.let(::field).orEmpty())
    val got = mutableStateOf(receipt?.got?.let(::field).orEmpty())
    val sentCurrency = mutableStateOf(receipt?.sentCurrency ?: defaultCurrency)
    val fromCard = mutableStateOf(receipt?.fromCard ?: defaultCard)
    val boughtAt = mutableStateOf(boughtAt)
    val original = mutableStateOf(receipt)
    val hasAmounts: Boolean get() = sent.value.isNotBlank() || got.value.isNotBlank()

    fun replace(receipt: TransferReceipt?, currency: Currency, bought: String = "") {
        original.value = receipt
        sent.value = receipt?.sent?.let(::field).orEmpty()
        got.value = receipt?.got?.let(::field).orEmpty()
        sentCurrency.value = receipt?.sentCurrency ?: currency
        fromCard.value = receipt?.fromCard
        boughtAt.value = bought
    }
}

@Composable
private fun EditorRateSegment(
    modifier: Modifier = Modifier,
    top: Boolean = false,
    bottom: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val radiusTop = if (top) 18.dp else 0.dp
    val radiusBottom = if (bottom) 18.dp else 0.dp
    val shape = RoundedCornerShape(radiusTop, radiusTop, radiusBottom, radiusBottom)
    Column(modifier.fillMaxWidth().clip(shape).background(AppTheme.colors.surface)
        .drawWithContent {
            // Leave the joins open so all lazy rows read as one card.
            val inset = 1.dp.toPx().coerceAtMost(size.height / 2f)
            clipRect(top = if (top) 0f else inset, bottom = if (bottom) size.height else size.height - inset) {
                this@drawWithContent.drawContent()
            }
        }
        .surfaceOutline(shape)
        .padding(start = 14.dp, end = 14.dp, top = if (top) 12.dp else 0.dp, bottom = if (bottom) 14.dp else 0.dp),
        content = content,
    )
}

private val fundingDraftSaver = listSaver<FundingDraft, String>(
    save = { draft ->
        listOf(draft.sent.value, draft.got.value, draft.sentCurrency.value.code, draft.fromCard.value.orEmpty(),
            draft.boughtAt.value, draft.original.value?.let(WalletCodec::encodeTransfer).orEmpty())
    },
    restore = { fields ->
        val currency = Currency.byCode(fields[2]) ?: Currency.RUB
        FundingDraft(WalletCodec.decodeTransfer(fields[5]), currency, boughtAt = fields[4]).apply {
            sent.value = fields[0]
            got.value = fields[1]
            sentCurrency.value = currency
            fromCard.value = fields[3].ifEmpty { null }
        }
    },
)

private val transferDraftsSaver = listSaver<SnapshotStateList<TransferReceipt>, String>(
    save = { list -> list.map(WalletCodec::encodeTransfer) },
    restore = { list -> list.mapNotNull(WalletCodec::decodeTransfer).toMutableStateList() },
)

private val purchaseDraftsSaver = listSaver<SnapshotStateList<PurchaseDraft>, String>(
    save = { list -> list.flatMap { listOf(it.key.toString(), it.spend.code, it.paid, it.price, it.date.toString()) } },
    restore = { list -> list.chunked(5).map { entry ->
        PurchaseDraft(entry[0].toInt(), Currency.byCode(entry[1]) ?: Currency.RUB, entry[2], entry[3], LocalDate.parse(entry[4]))
    }.toMutableStateList() },
)

private val bankCardSaver = Saver<BankCard, String>(
    save = { WalletCodec.encode(Wallet(cards = listOf(it))) },
    restore = { WalletCodec.decode(it)?.cards?.singleOrNull() },
)

private fun field(value: BigDecimal): String = Numbers.exactInput(value)

private const val MAX_NAME = 24
private const val SAVE_CONFIRMATION_MS = 180L

/** Что несут в обменник за местные наличные (кроме итоговой валюты — она есть всегда). */
private val EXCHANGE_FROM = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

/** Сколько валют показывать чипами в редакторе карты. */
private const val EDITOR_CHIPS = 4

/** Карточка-предпросмотр в редакторе. */
private data class EditorCardPreview(
    val card: BankCard,
    val quote: CardQuote?,
    val spend: Currency,
    val home: Currency,
    val sum: String?,
    val needsReceipt: Boolean,
)

@Composable
private fun PreviewCard(preview: EditorCardPreview, tilt: State<Offset>, textAlpha: () -> Float = { 1f }) {
    BankCardView(
        card = preview.card, quote = preview.quote, spend = preview.spend, home = preview.home,
        sum = preview.sum, selected = true, best = null, onClick = {}, onLongClick = {},
        width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT, needsReceipt = preview.needsReceipt,
        flipOnChange = false, reflectionTilt = tilt, textAlpha = textAlpha,
    )
}

private val PREVIEW_WIDTH = 190.dp
private val PREVIEW_HEIGHT = 120.dp
private val PREVIEW_AMOUNT = BigDecimal(100)

private fun CardNetwork?.isUnionPay(): Boolean = this == CardNetwork.UNIONPAY || this == CardNetwork.UNIONPAY_FOREIGN
