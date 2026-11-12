package app.dirhamrub

import app.dirhamrub.ui.theme.ProvideTilt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.ratesDataStore
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.data.unionPayDataStore
import app.dirhamrub.diag.CrashLog
import app.dirhamrub.diag.Demo
import app.dirhamrub.diag.CrashReportDialog
import app.dirhamrub.ui.DirhamApp
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.theme.DirhamTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ярлык «Ценник» на иконке открывает сразу камеру; остальные экраны по extra — для скриншотов CI.
        val screen = intent?.getStringExtra(EXTRA_SCREEN)
        if (intent?.getBooleanExtra(EXTRA_DEMO, false) == true && Demo.allowed(this)) {
            // Только отладочная сборка: демо-данные пишутся до чтения состояния, поэтому синхронно.
            runBlocking {
                Demo.seed(
                    WalletRepository(settingsDataStore, unionPayDataStore, ratesDataStore),
                    intent.getStringExtra(EXTRA_SPEND),
                    // На пересчёте карта из extra — выбранная вручную (в редакторе — открытая).
                    selected = intent.getStringExtra(EXTRA_CARD).takeIf { screen == null },
                    noCards = intent.getBooleanExtra(EXTRA_NO_CARDS, false),
                    noAmount = intent.getBooleanExtra(EXTRA_NO_AMOUNT, false),
                )
            }
        }
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
            val state = vm.state.collectAsStateWithLifecycle()
            // Тема и анимации — из настроек; корень перерисовывается только при их смене.
            val theme by remember { derivedStateOf { state.value.settings.theme } }
            val motion by remember { derivedStateOf { state.value.settings.motion } }
            val keyHaptics by remember { derivedStateOf { state.value.settings.keyHaptics } }
            val pureBlack by remember { derivedStateOf { state.value.settings.pureBlack } }
            DirhamTheme(theme = theme, motion = motion, keyHaptics = keyHaptics, pureBlack = pureBlack) {
                // Наклон телефона — для блика на картах (флагманы, Android 13+).
                ProvideTilt {
                    DirhamApp(
                        startScreen = screen,
                        startCard = intent?.getStringExtra(EXTRA_CARD),
                        startStep = intent?.getIntExtra(EXTRA_STEP, 0) ?: 0,
                        vm = vm,
                    )
                }
                CrashReportDialog()
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"

        /** Только для снимков CI: валюта трат демо-данных («IDR», «LKR»). */
        const val EXTRA_SPEND = "spend"

        /** Только для снимков CI: редактор этой карты (id из демо-данных). */
        const val EXTRA_CARD = "card"

        /** Для снимков CI: редактор новой карты. */
        const val CARD_NEW = "new"
        const val SCREEN_CAMERA = "camera"
        const val SCREEN_CURRENCIES = "currencies"
        const val SCREEN_EDITOR = "editor"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_GUIDE = "guide"
        const val SCREEN_MONEY = "money"

        /** Знакомство поверх демо-данных — для снимков CI; [EXTRA_STEP] — с какого шага. */
        const val SCREEN_ONBOARDING = "onboarding"
        const val EXTRA_STEP = "step"
        const val EXTRA_DEMO = "demo"

        /** Только для снимков CI: демо-данные без карт. */
        const val EXTRA_NO_CARDS = "nocards"

        /** Только для снимков CI: ничего не набрано (пустой экран). */
        const val EXTRA_NO_AMOUNT = "noamount"

    }
}
