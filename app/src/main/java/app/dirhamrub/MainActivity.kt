package app.dirhamrub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.diag.CrashLog
import app.dirhamrub.diag.Demo
import app.dirhamrub.diag.CrashReportDialog
import app.dirhamrub.ui.DirhamApp
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
            runBlocking { Demo.seed(WalletRepository(settingsDataStore)) }
        }
        setContent {
            DirhamTheme {
                DirhamApp(startScreen = screen)
                CrashReportDialog()
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CAMERA = "camera"
        const val SCREEN_CHEAT = "cheat"
        const val SCREEN_CURRENCIES = "currencies"
        const val SCREEN_EDITOR = "editor"
        const val EXTRA_DEMO = "demo"
    }
}
