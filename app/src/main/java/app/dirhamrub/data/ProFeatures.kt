package app.dirhamrub.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.appwidget.AppWidgetManager
import androidx.core.content.pm.ShortcutManagerCompat
import app.dirhamrub.R
import app.dirhamrub.domain.AppIcon
import app.dirhamrub.widget.QuoteWidget

/** Функции Pro, которые живут вне экрана приложения: значок, виджет и плитка в шторке. */
object ProFeatures {
    /** Launcher's confirmation remains in control; unsupported launchers use the screen's help. */
    fun requestWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return try {
            manager.requestPinAppWidget(ComponentName(context, QuoteWidget::class.java), null, null)
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private val ALIASES = mapOf(
        AppIcon.CLASSIC to "app.dirhamrub.LauncherClassic",
        AppIcon.DARK to "app.dirhamrub.LauncherDark",
        AppIcon.GOLD to "app.dirhamrub.LauncherGold",
        AppIcon.EMERALD to "app.dirhamrub.LauncherEmerald",
        AppIcon.PLATINUM to "app.dirhamrub.LauncherPlatinum",
    )

    /**
     * Включить значок [icon] и выключить остальные (они — «входы» в одно и то же приложение). Значок уже такой —
     * ничего не трогаем: лаунчер не перерисовывает рабочий стол зря.
     */
    fun applyAppIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        for ((variant, name) in ALIASES) {
            val component = ComponentName(context.packageName, name)
            val want = if (variant == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val now = pm.getComponentEnabledSetting(component)
            val enabledNow = now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && variant == AppIcon.CLASSIC)
            val enabledWant = want == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (enabledNow != enabledWant) {
                runCatching { pm.setComponentEnabledSetting(component, want, PackageManager.DONT_KILL_APP) }
            }
        }
    }

    /** Retire advertised dynamic actions; existing user-pinned intents remain valid. */
    fun retireShortcuts(context: Context) {
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("scan", "previous")) }
    }

    fun requestCameraTile(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return false
        val bar = context.getSystemService(android.app.StatusBarManager::class.java) ?: return false
        return try {
            bar.requestAddTileService(
                ComponentName(context, app.dirhamrub.CameraTileService::class.java),
                context.getString(R.string.tile_camera),
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_camera_white),
                context.mainExecutor,
            ) { }
            true
        } catch (_: SecurityException) { false } catch (_: IllegalArgumentException) { false }
    }
}
