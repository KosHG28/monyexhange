package app.dirhamrub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dirhamrub.ui.DirhamApp
import app.dirhamrub.ui.theme.DirhamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DirhamTheme {
                DirhamApp()
            }
        }
    }
}
