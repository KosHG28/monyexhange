package app.dirhamrub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dirhamrub.diag.CrashLog
import app.dirhamrub.diag.CrashReportDialog
import app.dirhamrub.ui.DirhamApp
import app.dirhamrub.ui.theme.DirhamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ярлык «Ценник» на иконке открывает сразу камеру.
        val startInCamera = intent?.getStringExtra(EXTRA_SCREEN) == SCREEN_CAMERA
        setContent {
            DirhamTheme {
                DirhamApp(startInCamera = startInCamera)
                CrashReportDialog()
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CAMERA = "camera"
    }
}
