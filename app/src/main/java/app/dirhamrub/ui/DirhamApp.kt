package app.dirhamrub.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cards.CardEditorSheet
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.currency.CurrencySheet
import app.dirhamrub.ui.main.CountryOffer
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.theme.AppTheme

/** Карта в редакторе: новая или существующая; [addPurchase] — сразу добавить чек покупки в этой валюте. */
private data class Editing(val card: BankCard, val isNew: Boolean, val addPurchase: Currency? = null)

/** Страна сотовой сети («KZ»); без SIM, в авиарежиме или на планшете — null. Интернет не нужен. */
private fun networkCountry(context: Context): String? =
    runCatching { context.getSystemService(TelephonyManager::class.java)?.networkCountryIso }
        .getOrNull()
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 2 }

/** Корень интерфейса: главный экран, камера и шторки. */
@Composable
fun DirhamApp(
    startInCamera: Boolean = false,
    vm: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cheatOpen by rememberSaveable { mutableStateOf(false) }
    var currenciesOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by rememberSaveable { mutableStateOf(startInCamera) }
    var editing by remember { mutableStateOf<Editing?>(null) }
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

    if (cameraOpen) {
        BackHandler { cameraOpen = false }
        CameraScreen(
            wallet = state.wallet,
            quote = state.quote,
            onSelectCard = vm::selectCard,
            onToCalculator = { items ->
                vm.putItems(items)
                cameraOpen = false
            },
            onBack = { cameraOpen = false },
        )
        return
    }

    val offer = country?.let { iso ->
        Currency.forCountry(iso)
            ?.takeIf { it != state.wallet.spend && iso != state.countryHandled }
            ?.let { CountryOffer(iso, it) }
    }
    MainScreen(
        state = state,
        countryOffer = offer,
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
