package app.dirhamrub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.ui.cheat.CheatSheet
import app.dirhamrub.ui.main.MainScreen
import app.dirhamrub.ui.main.MainViewModel

/** Корень интерфейса: главный экран и шторки. */
@Composable
fun DirhamApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cheatOpen by rememberSaveable { mutableStateOf(false) }

    MainScreen(
        state = state,
        onKey = vm::press,
        onSelectCard = vm::selectCard,
        onSelectLine = vm::setActive,
        onChip = vm::putAed,
        onOpenRate = {},
        onOpenCheat = { cheatOpen = true },
        onOpenCamera = {},
    )
    if (cheatOpen) {
        CheatSheet(rates = state.rates, selected = state.selected, onDismiss = { cheatOpen = false })
    }
}
