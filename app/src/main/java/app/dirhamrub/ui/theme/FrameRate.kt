package app.dirhamrub.ui.theme

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Пока идёт анимация ([active]) — просим у экрана высокую частоту кадров (120 Гц на флагманах с адаптивной
 * частотой, Android 15+), потом отпускаем: в покое экран сам снижает частоту и бережёт батарею.
 */
@Composable
fun HighFrameRate(active: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) requestHigh(view, true)
        onDispose {
            if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) requestHigh(view, false)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun requestHigh(view: View, on: Boolean) {
    view.requestedFrameRate = if (on) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
}
