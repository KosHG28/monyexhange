package app.dirhamrub.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.dirhamrub.MainActivity
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Exchanger
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cards.CardEditorSheet
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.currency.CurrencySheet
import app.dirhamrub.ui.exchange.BoardCamera
import app.dirhamrub.ui.exchange.EXCHANGE_GIVE
import app.dirhamrub.ui.exchange.ExchangeSheet
import app.dirhamrub.ui.guide.CountryGuideSheet
import app.dirhamrub.ui.main.CountryOffer
import app.dirhamrub.ui.main.CardsSheet
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.main.MenuSheet
import app.dirhamrub.ui.onboarding.OnboardingScreen
import app.dirhamrub.ui.settings.SettingsActions
import app.dirhamrub.ui.settings.SettingsScreen
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlin.math.hypot

/** Карта в редакторе: новая или существующая; [addPurchase] — сразу добавить чек покупки в этой валюте. */
private data class Editing(val card: BankCard, val isNew: Boolean, val addPurchase: Currency? = null)

/** Страна сотовой сети («KZ»); без SIM, в авиарежиме или на планшете — null. Интернет не нужен. */
private fun networkCountry(context: Context): String? =
    runCatching { context.getSystemService(TelephonyManager::class.java)?.networkCountryIso }
        .getOrNull()
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 2 }

/** Системный запрос «Добавить плитку «Ценник» в быстрые настройки?». */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestCameraTile(context: Context) {
    val bar = context.getSystemService(StatusBarManager::class.java) ?: return
    bar.requestAddTileService(
        ComponentName(context, CameraTileService::class.java),
        context.getString(R.string.tile_camera),
        Icon.createWithResource(context, R.drawable.ic_camera_white),
        context.mainExecutor,
    ) { }
}

/** Корень интерфейса: главный экран, камера и шторки. */
@Composable
fun DirhamApp(
    startScreen: String? = null,
    startCard: String? = null,
    startStep: Int = 0,
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Камера «Обменник»: какую валюту несёте (null — закрыта) и что она распознала (для формы проверки).
    var boardGive by rememberSaveable { mutableStateOf<String?>(null) }
    var exchangePrefill by remember { mutableStateOf<Exchanger?>(null) }
    var exchangeOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_EXCHANGE) }
    // Местная валюта обменников: валюта трат, а из редактора наличных — их валюта.
    var exchangeLocal by rememberSaveable { mutableStateOf<String?>(null) }
    var cardsOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CARDS) }
    var menuOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_MENU) }
    var guideOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_GUIDE) }
    var cheatOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CHEAT) }
    var currenciesOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CURRENCIES) }
    var cameraOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CAMERA) }
    var settingsOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_SETTINGS) }
    // Снимки CI: знакомство поверх демо-данных, с нужного шага.
    var onboardingForced by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_ONBOARDING) }
    // Знакомство открыли повторно из настроек: валюту трат по стране сети уже не подставляем.
    var onboardingReshown by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var cameraOrigin by remember { mutableStateOf<Offset?>(null) }
    // Скриншоты CI: сразу редактор выбранной карты.
    LaunchedEffect(state.loaded) {
        if (state.loaded && startScreen == MainActivity.SCREEN_EDITOR) {
            (state.wallet.cards.firstOrNull { it.id == startCard } ?: state.wallet.selected)?.let { editing = Editing(it, isNew = false) }
        }
    }
    val context = LocalContext.current
    // Страну сети перечитываем при каждом возвращении в приложение: прилетели — подсказка уже на месте.
    var country by remember { mutableStateOf<String?>(null) }
    // Заодно курсы: старше 6 часов — скачиваем (если так в настройках).
    LifecycleResumeEffect(Unit) {
        country = networkCountry(context)
        vm.onResume()
        // Уходим с экрана — дописываем последние нажатия, не дожидаясь задержки.
        onPauseOrDispose { vm.flush() }
    }

    if (!state.loaded) {
        // DataStore читается за доли секунды; до этого — пустой фон без мигания чисел.
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        return
    }

    val offer = country?.takeIf { state.settings.countryHint }?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }

    if ((!state.onboardingDone || onboardingForced) && !cameraOpen) {
        fun finish() {
            onboardingForced = false
            vm.finishOnboarding()
            // Валюту трат только что выбрали сами — плашку «вы в другой стране» сразу не показываем.
            country?.takeIf { Currency.forCountry(it) != null }?.let { vm.handleCountry(it, accept = null) }
        }
        OnboardingScreen(
            startStep = startStep,
            spend = state.wallet.spend,
            home = state.wallet.home,
            suggested = country?.takeIf { !onboardingReshown && !onboardingForced }?.let { Currency.forCountry(it) },
            official = state.reference,
            unionPay = state.activeUnionPay,
            onSpend = vm::setSpend,
            onHome = vm::setHome,
            newCard = vm::newCard,
            onFinish = { card ->
                card?.let(vm::saveCard)
                finish()
            },
            onOpenEditor = { card ->
                finish()
                editing = Editing(card, isNew = true)
            },
        )
        return
    }

    // Плитка «Ценник» в шторке: предлагаем один раз, после первого знакомства с камерой (Android 13+).
    val tileOffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.cameraUsed && !state.tileOffered
    // Справочник «Чем платить»: по валюте трат и стране сотовой сети.
    val guide = state.guides?.forSpend(state.wallet.spend, country)
    val defaultGive = EXCHANGE_GIVE.firstOrNull { it != state.wallet.spend && it != state.wallet.home } ?: Currency.USD
    MainScreen(
        state = state,
        countryOffer = offer,
        tileOffer = tileOffer,
        onTileOffer = { accept ->
            if (accept && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestCameraTile(context)
            vm.markTileOffered()
        },
        onKey = vm::press,
        onSelectCard = vm::selectCard,
        onStepCard = vm::stepCard,
        onEditCard = { editing = Editing(it, isNew = false) },
        onAddReceipt = { editing = Editing(it, isNew = false, addPurchase = state.wallet.spend) },
        onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
        onSelectLine = vm::setActive,
        onChip = vm::putAmount,
        onOpenCurrencies = { currenciesOpen = true },
        onCountry = { accept -> offer?.let { vm.handleCountry(it.iso, if (accept) it.currency else null) } },
        onRefreshRates = vm::refreshRates,
        onOpenMenu = { menuOpen = true },
        onOpenCards = { cardsOpen = true },
        onOpenCamera = { cameraOpen = true },
        onCameraPlaced = { cameraOrigin = it },
    )
    // Шторка «Карты» (касание строки карты): выбрать, изменить, добавить; курс ЦБ.
    if (cardsOpen) {
        CardsSheet(
            state = state,
            onSelect = {
                vm.selectCard(it)
                cardsOpen = false
            },
            onEdit = {
                cardsOpen = false
                editing = Editing(it, isNew = false)
            },
            onAdd = {
                cardsOpen = false
                editing = Editing(vm.newCard(), isNew = true)
            },
            onRefreshRates = vm::refreshRates,
            onDismiss = { cardsOpen = false },
        )
    }
    // Шторка «Ещё» (☰): единственный вход в справочник страны, обменники, шпаргалку и настройки.
    if (menuOpen) {
        MenuSheet(
            state = state,
            guide = guide,
            onOpenGuide = {
                menuOpen = false
                guideOpen = true
            },
            onOpenExchange = {
                menuOpen = false
                exchangeLocal = null
                exchangeOpen = true
            },
            onOpenCheat = {
                menuOpen = false
                cheatOpen = true
            },
            onOpenSettings = {
                menuOpen = false
                settingsOpen = true
            },
            onDismiss = { menuOpen = false },
        )
    }
    if (guideOpen && guide != null) {
        CountryGuideSheet(guide, state.wallet.cards, onDismiss = { guideOpen = false })
    }
    if (exchangeOpen) {
        ExchangeSheet(
            local = Currency.byCode(exchangeLocal) ?: state.wallet.spend,
            home = state.wallet.home,
            cards = state.wallet.cards,
            quote = state.quote,
            official = state.reference,
            exchangers = state.exchangers,
            onSave = vm::saveExchanger,
            onDelete = vm::deleteExchanger,
            onExchanged = { card ->
                exchangeOpen = false
                editing = Editing(card, isNew = state.wallet.cards.none { it.id == card.id })
            },
            newCard = vm::newCard,
            onDismiss = {
                exchangeOpen = false
                exchangePrefill = null
            },
            onScan = { give ->
                exchangeOpen = false
                exchangePrefill = null
                boardGive = give.code
            },
            prefill = exchangePrefill,
        )
    }
    if (cheatOpen) {
        CheatSheet(
            quote = state.quote,
            spend = state.wallet.spend,
            home = state.wallet.home,
            selectedId = state.wallet.selected?.id,
            amount = state.totals.spend,
            onDismiss = { cheatOpen = false },
        )
    }
    if (currenciesOpen) {
        CurrencySheet(
            spend = state.wallet.spend,
            home = state.wallet.home,
            onSpend = vm::setSpend,
            onHome = vm::setHome,
            onDismiss = { currenciesOpen = false },
            recent = state.wallet.recent,
            country = country,
            official = state.reference,
        )
    }
    val reduceMotion = LocalReduceMotion.current
    // Настройки — экран поверх главного, въезжает справа.
    AnimatedVisibility(
        visible = settingsOpen,
        enter = if (reduceMotion) EnterTransition.None else slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)),
        exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180)),
    ) {
        BackHandler { settingsOpen = false }
        SettingsScreen(
            state = state,
            actions = SettingsActions(
                onBack = { settingsOpen = false },
                onRefresh = vm::refreshRates,
                onUnionPay = vm::setUnionPayEnabled,
                onRatesUpdate = vm::setRatesUpdate,
                onHome = vm::setHome,
                onCountryHint = vm::setCountryHint,
                onEditCard = { editing = Editing(it, isNew = false) },
                onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
                onReorder = vm::reorderCards,
                onCameraEmbedded = vm::setCameraEmbedded,
                onAddTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestCameraTile(context) }
                } else {
                    null
                },
                onTheme = vm::setTheme,
                onMotion = vm::setMotion,
                onDynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) vm::setDynamicColor else null,
                onKeyHaptics = vm::setKeyHaptics,
                onShowChips = vm::setShowChips,
                onOnboarding = {
                    settingsOpen = false
                    onboardingReshown = true
                    vm.restartOnboarding()
                },
            ),
        )
    }
    // Камера раскрывается кругом из кнопки «Ценник» и сворачивается в неё обратно; главный экран — под ней.
    val reveal = remember { Animatable(if (cameraOpen) 1f else 0f) }
    LaunchedEffect(cameraOpen) {
        val target = if (cameraOpen) 1f else 0f
        if (reduceMotion) reveal.snapTo(target) else reveal.animateTo(target, tween(if (cameraOpen) 420 else 340, easing = FastOutSlowInEasing))
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
                wallet = state.wallet,
                quote = state.quote,
                embedded = state.cameraEmbedded,
                onEmbeddedChange = vm::setCameraEmbedded,
                onSelectCard = vm::selectCard,
                onToCalculator = { items ->
                    vm.putItems(items)
                    cameraOpen = false
                },
                onBack = { cameraOpen = false },
                onExchangeMode = if (state.wallet.spend != state.wallet.home) {
                    {
                        cameraOpen = false
                        exchangeLocal = null
                        boardGive = defaultGive.code
                    }
                } else {
                    null
                },
            )
        }
    }
    boardGive?.let { code ->
        BackHandler { boardGive = null }
        val local = Currency.byCode(exchangeLocal) ?: state.wallet.spend
        BoardCamera(
            give = Currency.byCode(code) ?: Currency.USD,
            local = local,
            home = state.wallet.home,
            cards = state.wallet.cards,
            quote = state.quote,
            official = state.reference,
            onRecognized = { ex ->
                boardGive = null
                exchangePrefill = ex
                exchangeOpen = true
            },
            onPriceMode = {
                boardGive = null
                cameraOpen = true
            },
            onBack = { boardGive = null },
        )
    }
    editing?.let { e ->
        CardEditorSheet(
            card = e.card,
            isNew = e.isNew,
            spend = state.wallet.spend,
            home = state.wallet.home,
            official = state.reference,
            unionPay = state.activeUnionPay,
            cards = state.wallet.cards,
            onSave = {
                vm.saveCard(it)
                editing = null
            },
            onDelete = {
                vm.deleteCard(e.card.id)
                editing = null
            },
            onDismiss = { editing = null },
            addPurchaseFor = e.addPurchase,
            amount = state.totals.spend,
        )
    }
}

/** Круг с центром [center] и радиусом [radius] — форма раскрытия камеры. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/** Камера начинает раскрываться с круга размером с палец. */
private val START_RADIUS = 24.dp
