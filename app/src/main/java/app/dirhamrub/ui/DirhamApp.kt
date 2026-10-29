package app.dirhamrub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.ui.camera.CameraScreen
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.rate.RateSheet
import app.dirhamrub.ui.theme.AppTheme

/** Корень интерфейса: главный экран и шторки. */
@Composable
fun DirhamApp(vm: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cheatOpen by rememberSaveable { mutableStateOf(false) }
    var rateOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }

    if (!state.loaded) {
        // DataStore читается за доли секунды; до этого — пустой фон без мигания стартовых чисел.
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        return
    }

    if (cameraOpen) {
        BackHandler { cameraOpen = false }
        CameraScreen(
            rates = state.rates,
            selected = state.selected,
            onSelectCard = vm::selectCard,
            onToCalculator = { items ->
                vm.putAedItems(items)
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
        onSelectLine = vm::setActive,
        onChip = vm::putAed,
        onOpenRate = { rateOpen = true },
        onOpenCheat = { cheatOpen = true },
        onOpenCamera = { cameraOpen = true },
    )
    if (cheatOpen) {
        CheatSheet(rates = state.rates, selected = state.selected, onDismiss = { cheatOpen = false })
    }
    if (rateOpen) {
        val card = state.selected
        RateSheet(
            card = card,
            rate = state.rate,
            onManualRate = { vm.setManualRate(card, it) },
            onReceipt = { vm.applyReceipt(card, it) },
            onResetReceipt = { vm.resetReceipt(card) },
            onDismiss = { rateOpen = false },
        )
    }
}
