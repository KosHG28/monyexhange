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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
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
)

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
    /** Ярлык «Вернуться в …» (Pro): код валюты трат. */
    startCurrency: String? = null,
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var welcome by remember { mutableStateOf<WelcomeReveal?>(null) }
    val reduceWelcome = app.dirhamrub.ui.theme.LocalReduceMotion.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(welcome, reduceWelcome) {
        val reveal = welcome ?: return@LaunchedEffect
        if (!reduceWelcome) reveal.progress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        welcome = null
    }

    // «Карты и наличные» — экран из настроек.
    var moneyOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_MONEY) }
    // Шторка страны «Чем платить» (ⓘ рядом со страной в шапке).
    var guideOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_GUIDE) }
    var currenciesOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CURRENCIES) }
    var cameraOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CAMERA) }
    var settingsOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_SETTINGS) }
    // «Курс по чеку Pro» — из настроек, из закрытых Pro-строк и с виджета без Pro.
    var proOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_PRO) }
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
    // Где на главном мини-карты строк — из них карта «перелетает» в настройку.
    val cardFlight = LocalCardFlight.current
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

    val offer = country?.takeIf { state.settings.countryHint }?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }

    val showOnboarding = (!state.onboardingDone || onboardingForced) && !cameraOpen
    Box(Modifier.fillMaxSize()) {
    if (showOnboarding || welcome != null) {
        fun finish() {
            onboardingForced = false
            vm.finishOnboarding()
            // Валюту трат только что выбрали сами — плашку «вы в другой стране» сразу не показываем.
            country?.takeIf { Currency.forCountry(it) != null }?.let { vm.handleCountry(it, accept = null) }
        }
        Box(Modifier.then(if (welcome != null) Modifier.clearAndSetSemantics { } else Modifier)) {
        OnboardingScreen(
            startStep = startStep,
            spend = state.wallet.spend,
            home = state.wallet.home,
            place = placeOf(state.wallet.spend, state.wallet.place, country),
            country = country,
            // Первый запуск: страна сотовой сети, а с российской SIM (или без неё) — Турция, самое частое
            // направление. Знакомство из настроек страну не трогает.
            suggested = if (onboardingReshown || onboardingForced || !countryRead) {
                null
            } else {
                country?.let { iso -> Currency.forCountry(iso)?.let { it to iso } } ?: (Currency.TRY to "TR")
            },
            recent = state.wallet.recent,
            official = state.reference,
            unionPay = state.activeUnionPay,
            onSpend = vm::setSpend,
            newCard = vm::newCard,
            onFinish = { card, center ->
                if (welcome == null) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    card?.let { vm.saveCard(it); vm.selectCard(it.id) }
                    if (card != null && !reduceWelcome) welcome = WelcomeReveal(center)
                    finish()
                }
            },
            onOpenEditor = { card ->
                finish()
                editing = Editing(card, isNew = true)
            },
        )
        }
    }

    if (!showOnboarding || welcome != null) {
    Box(Modifier.fillMaxSize()
        .graphicsLayer {
            val reveal = welcome
            clip = reveal != null
            if (reveal != null) {
                val center = reveal.origin ?: Offset(size.width / 2f, size.height * 0.4f)
                val dx = maxOf(center.x, size.width - center.x)
                val dy = maxOf(center.y, size.height - center.y)
                shape = CircleReveal(center, kotlin.math.sqrt(dx * dx + dy * dy) * reveal.progress.value)
                clip = true
            }
        }
        .then(if (welcome != null) Modifier.clearAndSetSemantics { } else Modifier)
    ) {
    // Плитка «Ценник» в шторке: предлагаем один раз, после первого знакомства с камерой (Android 13+).
    val tileOffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.cameraUsed && !state.tileOffered
    // Справочник — той страны, что в шапке (выбранная, страна сети или главная страна валюты): у евро их много.
    val placeIso = Countries.placeFor(state.wallet.spend, state.wallet.place, country)?.iso
    val guide = placeIso?.let { state.guides?.countries?.get(it) } ?: state.guides?.forSpend(state.wallet.spend, country)
    val place = placeOf(state.wallet.spend, state.wallet.place, country)
    MainScreen(
        state = state,
        place = place,
        countryOffer = offer,
        tileOffer = tileOffer,
        onTileOffer = { accept ->
            if (accept && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestCameraTile(context)
            vm.markTileOffered()
        },
        onKey = vm::press,
        onSelectCard = vm::selectCard,
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
        covered = settingsOpen || moneyOpen || currenciesOpen || cameraOpen,
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
        enter = if (reduceMotion) EnterTransition.None else Springs.screenIn,
        exit = if (reduceMotion) ExitTransition.None else Springs.screenOut,
    ) {
        BackHandler { currenciesOpen = false }
        CountryScreen(
            spend = state.wallet.spend,
            home = state.wallet.home,
            onSpend = vm::setSpend,
            onBack = { currenciesOpen = false },
            recent = state.wallet.recent,
            country = country,
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
                onUnionPay = vm::setUnionPayEnabled,
                onRatesUpdate = vm::setRatesUpdate,
                onHome = vm::setHome,
                onCountryHint = vm::setCountryHint,
                onCameraEmbedded = vm::setCameraEmbedded,
                onAddTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestCameraTile(context) }
                } else {
                    null
                },
                onTheme = vm::setTheme,
                onMotion = vm::setMotion,
                onKeyHaptics = vm::setKeyHaptics,
                onPureBlack = vm::setPureBlack,
                onWifiOnly = vm::setWifiOnly,
                onRounding = vm::setRounding,
                onOnboarding = {
                    settingsOpen = false
                    onboardingReshown = true
                    vm.restartOnboarding()
                },
                onCards = { moneyOpen = true },
                onOpenPro = { proOpen = true },
                onDynamicColor = vm::setDynamicColor,
                onAppIcon = vm::setAppIcon,
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
            actions = MoneyActions(
                onBack = { moneyOpen = false },
                onEditCard = { editing = Editing(it, isNew = false) },
                onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
            ),
        )
    }
    // Камера раскрывается кругом из кнопки «Ценник» и сворачивается в неё обратно; главный экран — под ней.
    val reveal = remember { Animatable(if (cameraOpen) 1f else 0f) }
    LaunchedEffect(cameraOpen) {
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
                onToCalculator = { items ->
                    vm.putItems(items)
                    cameraOpen = false
                },
                onBack = { cameraOpen = false },
            )
        }
    }
    editing?.let { e ->
      key(e) {
        CardEditorScreen(
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
                cardFlight.flying = null
            },
            onDelete = {
                vm.deleteCard(e.card.id)
                ReceiptShots.deleteCard(context, e.card.id)
                editing = null
                cardFlight.flying = null
            },
            onDismiss = {
                editing = null
                cardFlight.flying = null
            },
            addPurchaseFor = e.addPurchase,
            amount = state.totals.spend,
            rounding = state.settings.rounding,
            morph = e.morph,
            pro = state.settings.pro,
            startScan = e.scan,
        )
      }
    }
    if (proOpen) {
        ProScreen(
            pro = state.settings.pro,
            onActivate = vm::activatePro,
            onDismiss = { proOpen = false },
        )
    }
    // Pro вне экрана: значок на рабочем столе, ярлыки на нём, виджет. Без Pro — всё по-обычному.
    if (state.loaded) {
        val pro = state.settings.pro
        LaunchedEffect(pro, state.settings.appIcon) {
            ProFeatures.applyAppIcon(context, if (pro) state.settings.appIcon else AppIcon.CLASSIC)
        }
        LaunchedEffect(pro, state.wallet.spend, state.wallet.recent) {
            ProFeatures.updateShortcuts(context, pro, state.wallet.spend, state.wallet.recent)
        }
        LaunchedEffect(pro, state.wallet, state.reference) {
            // Кошелёк сохраняется чуть позже — виджет перечитывает его после паузы.
            delay(WIDGET_REFRESH_DELAY_MS)
            QuoteWidget.refresh(context)
        }
        LaunchedEffect(Unit) { ReceiptShots.cleanup(context, state.wallet.cards.map { it.id }.toSet()) }
    }
    }
    }
    if (welcome != null) {
        BackHandler { }
        // Короткий переход не принимает касаний и не даёт повторно сохранить ту же карту.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
    }
    }

}

/** Прогресс создаётся в момент нажатия с нуля: первый кадр не может показать весь главный экран. */
private class WelcomeReveal(val origin: Offset?) {
    val progress = Animatable(0f)
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
