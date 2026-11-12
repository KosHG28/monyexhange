package app.dirhamrub.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.dirhamrub.MainActivity
import app.dirhamrub.R
import app.dirhamrub.domain.AppIcon
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.Currency

/** Функции Pro, которые живут вне экрана приложения: значок на рабочем столе и ярлыки на нём. */
object ProFeatures {
    private val ALIASES = mapOf(
        AppIcon.CLASSIC to "app.dirhamrub.LauncherClassic",
        AppIcon.DARK to "app.dirhamrub.LauncherDark",
        AppIcon.GOLD to "app.dirhamrub.LauncherGold",
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

    /**
     * Ярлыки на значке (долгое нажатие): с Pro — «Чек из скриншота» и «Вернуться в …» (прошлая страна);
     * без Pro — только постоянный «Ценник» из shortcuts.xml.
     */
    fun updateShortcuts(context: Context, pro: Boolean, spend: Currency, recent: List<Currency>) {
        if (!pro) {
            runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
            return
        }
        val shortcuts = buildList {
            add(
                ShortcutInfoCompat.Builder(context, "scan")
                    .setShortLabel(context.getString(R.string.shortcut_scan_short))
                    .setLongLabel(context.getString(R.string.shortcut_scan_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_scan))
                    .setIntent(open(context, MainActivity.SCREEN_SCAN))
                    .build(),
            )
            recent.firstOrNull { it != spend }?.let { previous ->
                val country = Countries.placeFor(previous, null, null)?.nameRu ?: previous.shortRu
                add(
                    ShortcutInfoCompat.Builder(context, "previous")
                        .setShortLabel(country)
                        .setLongLabel(context.getString(R.string.shortcut_back_long, country, previous.code))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_place))
                        .setIntent(open(context, MainActivity.SCREEN_SPEND).putExtra(MainActivity.EXTRA_CURRENCY, previous.code))
                        .build(),
                )
            }
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun open(context: Context, screen: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setClass(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
