package app.dirhamrub

import app.dirhamrub.ui.theme.AdaptiveFrameRate
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import app.dirhamrub.ui.theme.AppMotionDurationScale
import app.dirhamrub.ui.theme.DirhamTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) app.dirhamrub.diag.RecompositionTrace.configure(
            intent?.getBooleanExtra("traceRecompositions", false) == true,
        )
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("checkImageMemory", false) == true) {
            val appContext = applicationContext
            lifecycleScope.launch(Dispatchers.IO) { app.dirhamrub.diag.checkImageMemory(appContext) }
        }
        // Ярлык «Ценник» на иконке открывает сразу камеру; остальные экраны по extra — для скриншотов CI.
        val screen = intent?.getStringExtra(EXTRA_SCREEN)
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_DEMO, false) == true && Demo.allowed(this)) {
            // Только отладочная сборка: демо-данные пишутся до чтения состояния, поэтому синхронно.
            runBlocking {
                Demo.seed(
                    WalletRepository(settingsDataStore, unionPayDataStore, ratesDataStore),
                    intent.getStringExtra(EXTRA_SPEND),
                    // На пересчёте карта из extra — выбранная вручную (в редакторе — открытая).
                    selected = intent.getStringExtra(EXTRA_CARD).takeIf { screen == null },
                    noCards = intent.getBooleanExtra(EXTRA_NO_CARDS, false),
                    noAmount = intent.getBooleanExtra(EXTRA_NO_AMOUNT, false),
                    amount = intent.getStringExtra(EXTRA_AMOUNT),
                )
            }
        }
        if (savedInstanceState == null && BuildConfig.DEBUG && intent?.getBooleanExtra("receiptDemo", false) == true && Demo.allowed(this)) {
            runBlocking {
                app.dirhamrub.diag.seedReceiptFixture(applicationContext)
                WalletRepository(settingsDataStore, unionPayDataStore, ratesDataStore)
                    .saveSettings(app.dirhamrub.domain.AppSettings(pro = true))
            }
        }
        // Compose and Material components use this window's scale, including sheets and popups.
        val motionScale = AppMotionDurationScale()
        val view = ComposeView(this)
        view.setParentCompositionContext(view.createLifecycleAwareWindowRecomposer(motionScale, lifecycle))
        view.setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
            val settings by vm.settings.collectAsStateWithLifecycle()
            DirhamTheme(
                theme = settings.theme,
                motion = settings.motion,
                keyHaptics = settings.keyHaptics,
                pureBlack = settings.pureBlack,
                dynamicColor = settings.dynamicColor,
                onMotionScaleChanged = { motionScale.scaleFactor = it },
            ) {
                AdaptiveFrameRate {
                    DirhamApp(
                        startScreen = screen,
                        startCard = intent?.getStringExtra(EXTRA_CARD),
                        startStep = intent?.getIntExtra(EXTRA_STEP, 0) ?: 0,
                        startCurrency = intent?.getStringExtra(EXTRA_CURRENCY),
                        vm = vm,
                    )
                    CrashReportDialog()
                }
            }
        }
        setContentView(view)
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

        /** Pro: ярлык «Чек из скриншота» — новая карта и сразу выбор скриншота банка. */
        const val SCREEN_SCAN = "scan"

        /** Pro: ярлык «Вернуться в …» — валюта трат [EXTRA_CURRENCY]. */
        const val SCREEN_SPEND = "spend"
        const val EXTRA_CURRENCY = "currency"

        /** Экран Pro (касание виджета без Pro). */
        const val SCREEN_PRO = "pro"
        const val SCREEN_CARD_STYLE = "card-style"

        /** Знакомство поверх демо-данных — для снимков CI; [EXTRA_STEP] — с какого шага. */
        const val SCREEN_ONBOARDING = "onboarding"
        const val EXTRA_STEP = "step"
        const val EXTRA_DEMO = "demo"

        /** Только для снимков CI: демо-данные без карт. */
        const val EXTRA_NO_CARDS = "nocards"

        /** Только для снимков CI: ничего не набрано (пустой экран). */
        const val EXTRA_NO_AMOUNT = "noamount"
        /** Только в debug-демо: сумма для проверки длинных чисел на снимках. */
        const val EXTRA_AMOUNT = "amount"

    }
}
