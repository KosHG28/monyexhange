package app.dirhamrub

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Плитка «Ценник» в шторке быстрых настроек: открывает камеру с любого экрана. */
class CameraTileService : TileService() {

    override fun onStartListening() {
        // Плитка-кнопка, не переключатель: всегда в обычном состоянии.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { openCamera() } else openCamera()
    }

    // Вариант с Intent вызывается только до Android 14, где он ещё работает; с 14 — PendingIntent.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openCamera() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_CAMERA)
            // Приложение уже открыто — пересоздаём экран, чтобы сразу попасть в камеру.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
