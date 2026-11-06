package app.dirhamrub.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cards.CardEditorSheet
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.currency.CurrencySheet
import app.dirhamrub.ui.main.CountryOffer
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.onboarding.OnboardingScreen
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
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cheatOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CHEAT) }
    var currenciesOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CURRENCIES) }
    var cameraOpen by rememberSaveable { mutableStateOf(startScreen == MainActivity.SCREEN_CAMERA) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var cameraOrigin by remember { mutableStateOf<Offset?>(null) }
    // Скриншоты CI: сразу редактор выбранной карты.
    LaunchedEffect(state.loaded) {
        if (state.loaded && startScreen == MainActivity.SCREEN_EDITOR) state.wallet.selected?.let { editing = Editing(it, isNew = false) }
    }
    val context = LocalContext.current
    // Страну сети перечитываем при каждом возвращении в приложение: прилетели — подсказка уже на месте.
    var country by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        country = networkCountry(context)
        onPauseOrDispose { }
    }

    if (!state.loaded) {
        // DataStore читается за доли секунды; до этого — пустой фон без мигания чисел.
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        return
    }

    val offer = country?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }

    if (!state.onboardingDone && !cameraOpen) {
        fun finish() {
            vm.finishOnboarding()
            // Валюту трат только что выбрали сами — плашку «вы в другой стране» сразу не показываем.
            country?.takeIf { Currency.forCountry(it) != null }?.let { vm.handleCountry(it, accept = null) }
        }
        OnboardingScreen(
            spend = state.wallet.spend,
            home = state.wallet.home,
            suggested = country?.let { Currency.forCountry(it) },
            onSpend = vm::setSpend,
            onHome = vm::setHome,
            onAddCard = {
                finish()
                editing = Editing(vm.newCard(), isNew = true)
            },
            onSkip = { finish() },
        )
        return
    }

    // Плитка «Ценник» в шторке: предлагаем один раз, после первого знакомства с камерой (Android 13+).
    val tileOffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.cameraUsed && !state.tileOffered
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
        onRefreshRates = { vm.refreshRates(force = true) },
        onOpenCheat = { cheatOpen = true },
        onOpenCamera = { cameraOpen = true },
        onCameraPlaced = { cameraOrigin = it },
    )
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
        )
    }
    // Камера раскрывается кругом из кнопки «Ценник» и сворачивается в неё обратно; главный экран — под ней.
    val reduceMotion = LocalReduceMotion.current
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
            )
        }
    }
    editing?.let { e ->
        CardEditorSheet(
            card = e.card,
            isNew = e.isNew,
            spend = state.wallet.spend,
            home = state.wallet.home,
            official = state.official,
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
