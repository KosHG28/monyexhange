package app.dirhamrub.ui

import app.dirhamrub.widget.QuoteWidget
import app.dirhamrub.ui.pro.ProScreen
import app.dirhamrub.domain.AppIcon
import app.dirhamrub.data.ReceiptShots
import app.dirhamrub.data.ProFeatures
import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.domain.wallet.CardKind
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import app.dirhamrub.ui.components.LocalTripSharedTransition
import app.dirhamrub.ui.components.TripSharedTransition
import app.dirhamrub.ui.components.PageUnderlay
import app.dirhamrub.ui.components.CardFlight
import app.dirhamrub.ui.components.LocalCardFlight
import app.dirhamrub.ui.components.CardMorph
import app.dirhamrub.ui.components.SlidePage
import app.dirhamrub.ui.components.TRIP_DURATION
import app.dirhamrub.ui.components.TRIP_CARD_FLIGHT_DURATION
import app.dirhamrub.ui.components.TripEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.CameraTileService
import app.dirhamrub.BuildConfig
import app.dirhamrub.MainActivity
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cards.CardEditorScreen
import app.dirhamrub.ui.currency.CountryScreen
import app.dirhamrub.ui.guide.CountryGuideScreen
import app.dirhamrub.ui.main.CountryOffer
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.main.placeOf
import app.dirhamrub.ui.money.MoneyActions
import app.dirhamrub.ui.money.MoneyScreen
import app.dirhamrub.ui.onboarding.OnboardingScreen
import app.dirhamrub.ui.settings.SettingsActions
import app.dirhamrub.ui.settings.SettingsScreen
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import kotlin.math.hypot

/** Карта в редакторе: новая или существующая; [addPurchase] — сразу добавить чек покупки в этой валюте. */
private data class Editing(
    val card: BankCard,
    val isNew: Boolean,
    val addPurchase: Currency? = null,
    val fromMain: Boolean = false,
    val morph: CardMorph? = null,
    /** Сразу открыть выбор скриншота банка (Pro: ярлык «Чек из скриншота»). */
    val scan: Boolean = false,
    val fromOnboarding: Boolean = false,
    val onboardingAmount: BigDecimal? = null,
)

private val editingSaver = listSaver<Editing?, String>(
    save = { edit ->
        if (edit == null) emptyList() else listOf(
            WalletCodec.encode(Wallet(cards = listOf(edit.card))), edit.isNew.toString(), edit.addPurchase?.code.orEmpty(),
            edit.scan.toString(), edit.fromOnboarding.toString(), edit.onboardingAmount?.toPlainString().orEmpty(), edit.fromMain.toString(),
        )
    },
    restore = { data ->
        WalletCodec.decode(data[0])?.cards?.singleOrNull()?.let { card ->
            Editing(card, data[1].toBoolean(), Currency.byCode(data[2]), scan = data[3].toBoolean(),
                fromOnboarding = data[4].toBoolean(), onboardingAmount = data[5].toBigDecimalOrNull(), fromMain = data.getOrNull(6).toBoolean())
        }
    },
)

/** Страна сотовой сети («KZ»); без SIM, в авиарежиме или на планшете — null. Интернет не нужен. */
private fun networkCountry(context: Context): String? =
    runCatching { context.getSystemService(TelephonyManager::class.java)?.networkCountryIso }
        .getOrNull()
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 2 }

/** Корень интерфейса: главный экран, камера и шторки. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DirhamApp(
    startScreen: String? = null,
    startCard: String? = null,
    startStep: Int = 0,
    /** Ярлык «Вернуться в …» (Pro): код валюты трат. */
    startCurrency: String? = null,
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
    incomingScreen: android.content.Intent? = null,
    onScreenHandled: () -> Unit = {},
) {
    val state by vm.navigation.collectAsStateWithLifecycle()
    app.dirhamrub.diag.RecompositionProbe("Navigation")
    val ioScope = rememberCoroutineScope()
    var completionCard by remember { mutableStateOf<BankCard?>(null) }
    val reduceWelcome = LocalReduceMotion.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // «Карты и наличные» — экран из настроек.
    var moneyOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_MONEY) }
    // Шторка страны «Чем платить» (ⓘ рядом со страной в шапке).
    var guideOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_GUIDE) }
    var currenciesOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CURRENCIES) }
    var cameraOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CAMERA) }
    var cameraFromTile by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_SETTINGS) }
    // «Курс по чеку Pro» — из настроек, из закрытых Pro-строк и с виджета без Pro.
    var proOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_PRO) }
    var cardStyleOpen by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && startScreen == MainActivity.SCREEN_CARD_STYLE) }
    // Снимки CI: знакомство поверх демо-данных, с нужного шага.
    var onboardingForced by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_ONBOARDING) }
    // Знакомство открыли повторно из настроек: валюту трат по стране сети уже не подставляем.
    var onboardingReshown by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable(stateSaver = editingSaver) { mutableStateOf<Editing?>(null) }
    var editorClosing by remember { mutableStateOf(false) }
    val cardFlight = remember { CardFlight() }
    var cameraOrigin by remember { mutableStateOf<Offset?>(null) }
    // Pro: ярлыки «Чек из скриншота» и «Вернуться в …» — сразу после загрузки.
    LaunchedEffect(state.loaded) {
        if (!state.loaded || !state.settings.pro || editing != null) return@LaunchedEffect
        when (startScreen) {
            MainActivity.SCREEN_SCAN -> editing = Editing(vm.newCard(), isNew = true, scan = true)
            MainActivity.SCREEN_SPEND -> Currency.entries.firstOrNull { it.code == startCurrency }?.let { vm.setSpend(it) }
        }
    }
    // Скриншоты CI: сразу редактор выбранной карты.
    LaunchedEffect(state.loaded) {
        if (state.loaded && editing == null && startScreen == MainActivity.SCREEN_EDITOR) {
            if (startCard == MainActivity.CARD_NEW) {
                editing = Editing(vm.newCard(), isNew = true)
            } else {
                (state.wallet.cards.firstOrNull { it.id == startCard } ?: state.selectedCard)?.let { editing = Editing(it, isNew = false) }
            }
        }
    }
    val context = LocalContext.current
    LaunchedEffect(vm, context) {
        vm.storageErrors.collect {
            android.widget.Toast.makeText(context, R.string.editor_storage_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    // Страну сети перечитываем при каждом возвращении в приложение: прилетели — подсказка уже на месте.
    var country by remember { mutableStateOf<String?>(null) }
    // Страну сети уже прочитали (null после этого — российская SIM или её нет).
    var countryRead by remember { mutableStateOf(false) }
    // Заодно курсы: старше 6 часов — скачиваем (если так в настройках).
    LifecycleResumeEffect(Unit) {
        country = networkCountry(context)
        countryRead = true
        vm.onResume()
        // Уходим с экрана — дописываем последние нажатия, не дожидаясь задержки.
        onPauseOrDispose { vm.flush() }
    }

    if (!state.loaded) {
        // DataStore читается за доли секунды; до этого — пустой фон без мигания чисел.
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        return
    }

    LaunchedEffect(vm, context) {
        vm.widgetUpdates.collect { QuoteWidget.refresh(context.applicationContext) }
    }

    LaunchedEffect(incomingScreen, state.loaded) {
        if (incomingScreen == null || !state.loaded) return@LaunchedEffect
        when (incomingScreen.getStringExtra(MainActivity.EXTRA_SCREEN)) {
            MainActivity.SCREEN_CAMERA -> if (!cameraOpen) cameraFromTile = true
            MainActivity.SCREEN_PRO -> proOpen = true
        }
        onScreenHandled()
    }

    fun finishOnboarding(card: BankCard? = null, amount: BigDecimal? = null) {
        onboardingForced = false
        vm.finishOnboarding(card, amount)
        country?.takeIf { Currency.forCountry(it) != null }?.let { vm.handleCountry(it, accept = null) }
    }

    fun sendPrices(items: List<BigDecimal>, currency: Currency) {
        if (currency != vm.state.value.wallet.spend) vm.setSpend(currency)
        vm.putItems(items)
    }

    val offer = country?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }

    val showOnboarding = (!state.onboardingDone || onboardingForced) && !cameraOpen
    val navigation = androidx.compose.animation.core.updateTransition(showOnboarding, label = "tripNavigation")
    navigation.animateFloat(transitionSpec = {
        if (reduceWelcome || !initialState || targetState) snap() else tween(TRIP_DURATION, easing = LinearEasing)
    }, label = "tripOneSecondClock") { if (it) 0f else 1f }
    val cardProgress = navigation.animateFloat(transitionSpec = {
        if (reduceWelcome || !initialState || targetState) snap()
        else tween(TRIP_CARD_FLIGHT_DURATION, easing = TripEasing)
    }, label = "tripCardFlight") { if (it) 0f else 1f }
    app.dirhamrub.ui.theme.HighFrameRate(navigation.isRunning)
    CompositionLocalProvider(LocalCardFlight provides cardFlight) {
    androidx.compose.animation.SharedTransitionLayout {
    val sharedScope = this
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
    navigation.AnimatedContent(
        transitionSpec = {
            val enterMillis = when {
                !initialState || targetState -> 160
                completionCard?.kind == CardKind.CARD -> TRIP_CARD_FLIGHT_DURATION
                else -> 350
            }
            (if (reduceWelcome) EnterTransition.None else fadeIn(tween(enterMillis))) togetherWith
                (if (reduceWelcome || initialState && !targetState) ExitTransition.None else fadeOut(tween(130))) using SizeTransform(clip = false)
        },
    ) { onboarding ->
    val visibilityScope = this
    val tripShared = remember(sharedScope, visibilityScope, completionCard?.id, cardProgress) {
        TripSharedTransition(sharedScope, visibilityScope, completionCard?.id, cardProgress)
    }
    val transitioning = navigation.currentState != navigation.targetState || navigation.isRunning
    val transitionInput = if (transitioning) Modifier.clearAndSetSemantics { hideFromAccessibility() }
    else Modifier
    Box(transitionInput.fillMaxSize()) {
    CompositionLocalProvider(LocalTripSharedTransition provides tripShared) {
    if (onboarding) {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        OnboardingScreen(
            startStep = startStep,
            reflectLight = showOnboarding,
            spend = state.wallet.spend,
            home = state.wallet.home,
            place = placeOf(state.wallet.spend, state.wallet.place, country),
            country = country,
            selectedPlaceIso = state.wallet.place,
            // Первый запуск: страна сотовой сети, а с российской SIM (или без неё) — Турция, самое частое
            // направление. Знакомство из настроек страну не трогает.
            // Исходящий экран ещё живёт во время shared transition, но уже не выбирает страну SIM.
            suggested = if (!showOnboarding || onboardingReshown || onboardingForced || !countryRead) {
                null
            } else {
                country?.let { iso -> Currency.forCountry(iso)?.let { it to iso } } ?: (Currency.TRY to "TR")
            },
            recent = state.wallet.recent,
            official = state.reference,
            unionPay = state.unionPay,
            onSpend = { currency, iso -> if (showOnboarding) vm.setSpend(currency, iso) },
            newCard = vm::newCard,
            onFinish = { card, amount ->
                if (showOnboarding) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    completionCard = card
                    finishOnboarding(card, amount)
                }
            },
            onOpenEditor = { card, amount ->
                if (showOnboarding && !transitioning) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    editing = Editing(card, isNew = true, scan = true, fromOnboarding = true, onboardingAmount = amount)
                }
            },
        )
        }
    }

    if (!onboarding) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
    // Плитка «Ценник» в шторке: предлагаем один раз, после первого знакомства с камерой (Android 13+).
    val tileOffer = state.settings.pro && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.cameraUsed && !state.tileOffered
    // Справочник — той страны, что в шапке (выбранная, страна сети или главная страна валюты): у евро их много.
    val placeIso = remember(state.wallet.spend, state.wallet.place, country) {
        Countries.placeFor(state.wallet.spend, state.wallet.place, country)?.iso
    }
    val guide = remember(placeIso, state.guides, state.wallet.spend, country) {
        state.guides?.forSpend(state.wallet.spend, country, placeIso)
    }
    val place = remember(state.wallet.spend, state.wallet.place, country) {
        placeOf(state.wallet.spend, state.wallet.place, country)
    }
    PageUnderlay(
        pageOnTop = guideOpen || settingsOpen || moneyOpen || proOpen,
        editorOnTop = editing?.let { it.fromMain && it.morph == null } == true && !editorClosing,
        editorOpen = editing?.fromMain == true,
    ) {
        MainScreen(
            states = vm.state,
            place = place,
            countryOffer = offer,
            tileOffer = tileOffer,
            onTileOffer = { accept ->
                if (accept && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ProFeatures.requestCameraTile(context)
                vm.markTileOffered()
            },
            onKey = vm::press,
            onSelectCard = vm::selectCard,
            onReorderPayments = vm::reorderPaymentWays,
            onEditCard = { card ->
                val origin = if (card.kind == CardKind.CARD && !reduceWelcome) cardFlight.originOf(card.id) else null
                editing = Editing(card, isNew = false, fromMain = true, morph = origin?.let { CardMorph(it, card.id) })
            },
            onAddReceipt = { editing = Editing(it, isNew = false, fromMain = true, addPurchase = state.wallet.spend) },
            onAddCard = { editing = Editing(vm.newCard(), isNew = true, fromMain = true) },
            onSelectLine = vm::setActive,
            onOpenCountries = { currenciesOpen = true },
            onOpenPlace = { guideOpen = true },
            onOpenSettings = { settingsOpen = true },
            onCountry = { accept -> offer?.let { vm.handleCountry(it.iso, if (accept) it.currency else null) } },
            onRefreshRates = vm::refreshRates,
            onOpenCamera = { cameraOpen = true },
            onCameraPlaced = { cameraOrigin = it },
        )
    }
    val reduceMotion = LocalReduceMotion.current
    SlidePage(guideOpen) {
        CountryGuideScreen(
            guide = guide,
            place = place,
            iso = placeIso,
            currency = state.wallet.spend,
            cards = state.wallet.cards,
            onDismiss = { guideOpen = false },
            backEnabled = guideOpen,
        )
    }
    // «Где вы тратите?» — полноэкранный выбор страны, въезжает снизу.
    AnimatedVisibility(
        visible = currenciesOpen,
        enter = if (reduceMotion) EnterTransition.None else Springs.bottomIn,
        exit = if (reduceMotion) ExitTransition.None else Springs.bottomOut,
    ) {
        CountryScreen(
            spend = state.wallet.spend,
            home = state.wallet.home,
            onSpend = vm::setSpend,
            onBack = { currenciesOpen = false },
            recent = state.wallet.recent,
            country = country,
            selectedPlaceIso = state.wallet.place,
            official = state.reference,
            backEnabled = currenciesOpen,
        )
    }
    // Настройки — экран поверх главного, въезжает справа.
    SlidePage(settingsOpen) {
        BackHandler(enabled = settingsOpen && !moneyOpen && !proOpen) { settingsOpen = false }
        PageUnderlay(pageOnTop = moneyOpen || proOpen) {
            SettingsScreen(
                state = state,
                actions = SettingsActions(
                    onBack = { settingsOpen = false },
                    onRefresh = vm::refreshRates,
                    onRatesUpdate = vm::setRatesUpdate,
                    onHome = vm::setHome,
                    onCameraEmbedded = vm::setCameraEmbedded,
                    onTheme = vm::setTheme,
                    onMotion = vm::setMotion,
                    onKeyHaptics = vm::setKeyHaptics,
                    onWifiOnly = vm::setWifiOnly,
                    onRounding = vm::setRounding,
                    onOnboarding = {
                        settingsOpen = false
                        completionCard = null
                        onboardingReshown = true
                        vm.restartOnboarding()
                    },
                    onCards = { moneyOpen = true },
                    onOpenPro = { proOpen = true },
                    onDynamicColor = vm::setDynamicColor,
                ),
            )
        }
    }
    SlidePage(moneyOpen) {
        BackHandler(enabled = moneyOpen) { moneyOpen = false }
        MoneyScreen(
            state = state,
            reflectLight = moneyOpen && !showOnboarding && editing == null && !cameraOpen && !cameraFromTile && !proOpen && !cardStyleOpen,
            actions = MoneyActions(
                onBack = { moneyOpen = false },
                onEditCard = { editing = Editing(it, isNew = false) },
                onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
                onAddCash = { editing = Editing(vm.newCard().copy(kind = CardKind.CASH), isNew = true) },
                onReorder = vm::reorderWallet,
            ),
        )
    }
    // Камера раскрывается кругом из кнопки «Ценник» и сворачивается в неё обратно; главный экран — под ней.
    val reveal = remember { Animatable(if (cameraOpen) 1f else 0f) }
    LaunchedEffect(cameraOpen, reduceMotion) {
        val target = if (cameraOpen) 1f else 0f
        if (reduceMotion) {
            reveal.snapTo(target)
        } else {
            reveal.animateTo(target, tween(if (cameraOpen) 420 else 340, easing = FastOutSlowInEasing))
        }
    }
    val cameraShown by remember { derivedStateOf { cameraOpen || reveal.value > 0f } }
    if (cameraShown) {
        if (cameraOpen) {
            LaunchedEffect(Unit) { vm.markCameraUsed() }
            BackHandler { cameraOpen = false }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = reveal.value
                    if (p < 1f) {
                        val center = cameraOrigin ?: Offset(size.width / 2f, size.height)
                        val far = maxOf(
                            hypot(center.x, center.y),
                            hypot(size.width - center.x, center.y),
                            hypot(center.x, size.height - center.y),
                            hypot(size.width - center.x, size.height - center.y),
                        )
                        clip = true
                        shape = CircleReveal(center, START_RADIUS.toPx() + (far - START_RADIUS.toPx()) * p)
                    } else {
                        clip = false
                    }
                },
        ) {
            CameraScreen(
                // Камера считает тем же способом, что и пересчёт (выбранным или лучшим).
                wallet = state.wallet.copy(selectedId = state.selectedCard?.id),
                quote = state.quote,
                embedded = state.cameraEmbedded,
                onEmbeddedChange = vm::setCameraEmbedded,
                onSelectCard = vm::selectCard,
                onToCalculator = { items, currency ->
                    sendPrices(items, currency)
                    cameraOpen = false
                },
                onBack = { cameraOpen = false },
            )
        }
    }
    SlidePage(proOpen) {
        ProScreen(
            settings = state.settings,
            cards = state.wallet.cards,
            onPureBlack = vm::setPureBlack,
            onStyles = { cardStyleOpen = true },
            onAppIcon = vm::setAppIcon,
            onActivate = vm::activatePro,
            onDismiss = { proOpen = false },
            backEnabled = proOpen && !cardStyleOpen,
        )
    }
    if (cardStyleOpen) {
        val appearanceState by vm.state.collectAsStateWithLifecycle()
        app.dirhamrub.ui.pro.CardStyleScreen(appearanceState, vm::setCardAppearance) { cardStyleOpen = false }
    }
    // Pro вне экрана: значок на рабочем столе, ярлыки на нём, виджет. Без Pro — всё по-обычному.
    if (state.loaded) {
        val pro = state.settings.pro
        LaunchedEffect(pro, state.settings.appIcon) {
            ProFeatures.applyAppIcon(context, if (pro) state.settings.appIcon else AppIcon.CLASSIC)
        }
        LaunchedEffect(Unit) { ProFeatures.retireShortcuts(context) }
        LaunchedEffect(Unit) {
            val cutoff = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                ReceiptShots.cleanup(context.applicationContext, vm.state.value.wallet.cards.map { it.id }.toSet(), cutoff)
            }
        }
    }
    }
    }
    } // CompositionLocalProvider
    } // Transition input and semantics
    } // AnimatedContent
    editing?.let { e ->
      key(e.card.id) {
        val editorState by vm.state.collectAsStateWithLifecycle()
        val editorSpend = rememberSaveable { state.wallet.spend }
        val editorHome = rememberSaveable { state.wallet.home }
        CardEditorScreen(
            card = e.card,
            isNew = e.isNew,
            spend = editorSpend,
            home = editorHome,
            official = state.reference,
            unionPay = state.unionPay,
            cards = state.wallet.cards,
            onSave = { card, images, staleShots ->
                val saved = vm.saveCard(card)
                if (saved) {
                    if (e.fromOnboarding) {
                        completionCard = null
                        finishOnboarding(card, e.onboardingAmount ?: card.purchaseFor(editorSpend)?.price)
                    }
                    if (images.isNotEmpty() || staleShots.isNotEmpty()) ioScope.launch {
                        ReceiptShots.update(context.applicationContext, card.id, images, staleShots)
                    }
                }
                saved
            },
            onDelete = {
                val deleted = vm.deleteCard(e.card.id)
                if (deleted) ioScope.launch { ReceiptShots.deleteCard(context.applicationContext, e.card.id) }
                deleted
            },
            onDismiss = {
                editing = null
                editorClosing = false
            },
            addPurchaseFor = e.addPurchase,
            amount = editorState.totals.spend,
            rounding = state.settings.rounding,
            editorMotion = e.fromMain,
            morph = e.morph,
            onExitStarted = { editorClosing = true },
            pro = state.settings.pro,
            startScan = e.scan,
            coveredByCamera = cameraFromTile,
        )
      }
    }
    if (cameraFromTile) {
        LaunchedEffect(Unit) { vm.markCameraUsed() }
        Dialog(
            onDismissRequest = { cameraFromTile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val view = LocalView.current
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    window.setDimAmount(0f)
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                }
            }
            CameraScreen(
                wallet = state.wallet.copy(selectedId = state.selectedCard?.id),
                quote = state.quote,
                embedded = state.cameraEmbedded,
                onEmbeddedChange = vm::setCameraEmbedded,
                onSelectCard = vm::selectCard,
                onToCalculator = { prices, currency ->
                    sendPrices(prices, currency)
                    cameraFromTile = false
                },
                onBack = { cameraFromTile = false },
            )
        }
    }
    if (navigation.currentState != navigation.targetState || navigation.isRunning) {
        BackHandler { }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
    }
    } // root Box
    } // SharedTransitionLayout
    } // CardFlight
}

/** Круг с центром [center] и радиусом [radius] — форма раскрытия камеры. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/** Камера начинает раскрываться с круга размером с палец. */
private val START_RADIUS = 24.dp

/** Через сколько после изменения кошелька обновить виджет (кошелёк к этому времени сохранён). */
