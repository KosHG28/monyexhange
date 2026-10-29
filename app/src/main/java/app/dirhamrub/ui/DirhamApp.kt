package app.dirhamrub.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cards.CardEditorSheet
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.currency.CurrencySheet
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.theme.AppTheme

/** Карта в редакторе: новая или существующая. */
private data class Editing(val card: BankCard, val isNew: Boolean)

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

    MainScreen(
        state = state,
        onKey = vm::press,
        onSelectCard = vm::selectCard,
        onEditCard = { editing = Editing(it, isNew = false) },
        onAddCard = { editing = Editing(vm.newCard(), isNew = true) },
        onSelectLine = vm::setActive,
        onChip = vm::putAmount,
        onOpenCurrencies = { currenciesOpen = true },
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
        )
    }
}
