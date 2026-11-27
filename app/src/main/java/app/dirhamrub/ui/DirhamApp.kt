package app.dirhamrub.ui

import kotlinx.coroutines.delay
import app.dirhamrub.widget.QuoteWidget
import app.dirhamrub.ui.pro.ProScreen
import app.dirhamrub.domain.AppIcon
import app.dirhamrub.data.ReceiptShots
import app.dirhamrub.data.ProFeatures
import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.ui.components.LocalCardFlight
import app.dirhamrub.ui.components.CardMorph
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
import app.dirhamrub.ui.components.TRIP_DURATION
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
import kotlin.math.hypot

/** Карта в редакторе: новая или существующая; [addPurchase] — сразу добавить чек покупки в этой валюте. */
private data class Editing(
    val card: BankCard,
    val isNew: Boolean,
    val addPurchase: Currency? = null,
    /** Перелёт карты из строки главного в настройку (null — экран выезжает снизу). */
    val morph: CardMorph? = null,
    /** Сразу открыть выбор скриншота банка (Pro: ярлык «Чек из скриншота»). */
    val scan: Boolean = false,
    val fromOnboarding: Boolean = false,
    val onboardingAmount: BigDecimal? = null,
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
    val cardFlight = LocalCardFlight.current
    var completionCardId by remember { mutableStateOf<String?>(null) }
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
    var editing by remember { mutableStateOf<Editing?>(null) }
    var cameraOrigin by remember { mutableStateOf<Offset?>(null) }
    // Pro: ярлыки «Чек из скриншота» и «Вернуться в …» — сразу после загрузки.
    LaunchedEffect(state.loaded) {
        if (!state.loaded || !state.settings.pro) return@LaunchedEffect
        when (startScreen) {
            MainActivity.SCREEN_SCAN -> editing = Editing(vm.newCard(), isNew = true, scan = true)
            MainActivity.SCREEN_SPEND -> Currency.entries.firstOrNull { it.code == startCurrency }?.let { vm.setSpend(it) }
        }
    }
    // Скриншоты CI: сразу редактор выбранной карты.
    LaunchedEffect(state.loaded) {
        if (state.loaded && startScreen == MainActivity.SCREEN_EDITOR) {
            if (startCard == MainActivity.CARD_NEW) {
                editing = Editing(vm.newCard(), isNew = true)
            } else {
                (state.wallet.cards.firstOrNull { it.id == startCard } ?: state.selectedCard)?.let { editing = Editing(it, isNew = false) }
            }
        }
    }
    val context = LocalContext.current
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

    val offer = country?.takeIf { state.settings.countryHint }?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }

    val showOnboarding = (!state.onboardingDone || onboardingForced) && !cameraOpen
    val navigation = androidx.compose.animation.core.updateTransition(showOnboarding, label = "tripNavigation")
    navigation.animateFloat(transitionSpec = {
        if (reduceWelcome || !initialState || targetState) snap() else tween(TRIP_DURATION, easing = LinearEasing)
    }, label = "tripOneSecondClock") { if (it) 0f else 1f }
    app.dirhamrub.ui.theme.HighFrameRate(navigation.isRunning)
    androidx.compose.animation.SharedTransitionLayout {
    val sharedScope = this
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
    navigation.AnimatedContent(
        transitionSpec = {
            (if (reduceWelcome) EnterTransition.None else fadeIn(tween(if (initialState && !targetState) 350 else 160))) togetherWith
                (if (reduceWelcome || initialState && !targetState) ExitTransition.None else fadeOut(tween(130))) using SizeTransform(clip = false)
        },
    ) { onboarding ->
    val visibilityScope = this
    val tripShared = remember(sharedScope, visibilityScope, completionCardId) {
        TripSharedTransition(sharedScope, visibilityScope, completionCardId)
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
                    completionCardId = card?.id
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
            // С главного карта «перелетает» из своей строки; наличные — экран снизу, как раньше.
            val origin = if (card.kind == CardKind.CARD) cardFlight.originOf(card.id) else null
            editing = Editing(card, isNew = false, morph = origin?.let { CardMorph(it, card.id) })
        },
        onAddReceipt = { editing = Editing(it, isNew = false, addPurchase = state.wallet.spend) },
        onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
        onSelectLine = vm::setActive,
        onOpenCountries = { currenciesOpen = true },
        onOpenPlace = { guideOpen = true },
        onOpenSettings = { settingsOpen = true },
        onCountry = { accept -> offer?.let { vm.handleCountry(it.iso, if (accept) it.currency else null) } },
        onRefreshRates = vm::refreshRates,
        onOpenCamera = { cameraOpen = true },
        onCameraPlaced = { cameraOrigin = it },
    )
    val reduceMotion = LocalReduceMotion.current
    if (guideOpen) {
        CountryGuideScreen(
            guide = guide,
            place = place,
            iso = placeIso,
            currency = state.wallet.spend,
            cards = state.wallet.cards,
            onDismiss = { guideOpen = false },
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
        )
    }
    // Настройки — экран поверх главного, въезжает справа.
    AnimatedVisibility(
        visible = settingsOpen,
        enter = if (reduceMotion) EnterTransition.None else Springs.sideIn,
        exit = if (reduceMotion) ExitTransition.None else Springs.sideOut,
    ) {
        BackHandler { settingsOpen = false }
        SettingsScreen(
            state = state,
            actions = SettingsActions(
                onBack = { settingsOpen = false },
                onRefresh = vm::refreshRates,
                onRatesUpdate = vm::setRatesUpdate,
                onHome = vm::setHome,
                onCountryHint = vm::setCountryHint,
                onCameraEmbedded = vm::setCameraEmbedded,
                onTheme = vm::setTheme,
                onMotion = vm::setMotion,
                onKeyHaptics = vm::setKeyHaptics,
                onWifiOnly = vm::setWifiOnly,
                onRounding = vm::setRounding,
                onOnboarding = {
                    settingsOpen = false
                    completionCardId = null
                    onboardingReshown = true
                    vm.restartOnboarding()
                },
                onCards = { moneyOpen = true },
                onOpenPro = { proOpen = true },
                onDynamicColor = vm::setDynamicColor,
            ),
        )
    }
    // «Карты и наличные» — поверх настроек, выезжает снизу (справа — только настройки).
    AnimatedVisibility(
        visible = moneyOpen,
        enter = if (reduceMotion) EnterTransition.None else Springs.screenIn,
        exit = if (reduceMotion) ExitTransition.None else Springs.screenOut,
    ) {
        BackHandler { moneyOpen = false }
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
    if (proOpen) {
        ProScreen(
            settings = state.settings,
            cards = state.wallet.cards,
            onPureBlack = vm::setPureBlack,
            onStyles = { cardStyleOpen = true },
            onAppIcon = vm::setAppIcon,
            onActivate = vm::activatePro,
            onDismiss = { proOpen = false },
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
        LaunchedEffect(pro, state.wallet, state.reference) {
            // Кошелёк сохраняется чуть позже — виджет перечитывает его после паузы.
            delay(WIDGET_REFRESH_DELAY_MS)
            QuoteWidget.refresh(context)
        }
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
      key(e) {
        val editorState by vm.state.collectAsStateWithLifecycle()
        val (editorSpend, editorHome) = remember(e) { state.wallet.spend to state.wallet.home }
        CardEditorScreen(
            card = e.card,
            isNew = e.isNew,
            spend = editorSpend,
            home = editorHome,
            official = state.reference,
            unionPay = state.unionPay,
            cards = state.wallet.cards,
            onSave = { card, images, staleShots ->
                if (e.fromOnboarding) {
                    completionCardId = null
                    finishOnboarding(card, e.onboardingAmount ?: card.purchaseFor(editorSpend)?.price)
                } else vm.saveCard(card)
                if (images.isNotEmpty() || staleShots.isNotEmpty()) ioScope.launch {
                    ReceiptShots.update(context.applicationContext, card.id, images, staleShots)
                }
                editing = null
                cardFlight.flying = null
            },
            onDelete = {
                vm.deleteCard(e.card.id)
                ioScope.launch { ReceiptShots.deleteCard(context.applicationContext, e.card.id) }
                editing = null
                cardFlight.flying = null
            },
            onDismiss = {
                editing = null
                cardFlight.flying = null
            },
            addPurchaseFor = e.addPurchase,
            amount = editorState.totals.spend,
            rounding = state.settings.rounding,
            morph = e.morph,
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
}

/** Круг с центром [center] и радиусом [radius] — форма раскрытия камеры. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/** Камера начинает раскрываться с круга размером с палец. */
private val START_RADIUS = 24.dp

/** Через сколько после изменения кошелька обновить виджет (кошелёк к этому времени сохранён). */
private const val WIDGET_REFRESH_DELAY_MS = 1500L
