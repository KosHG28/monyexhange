package app.dirhamrub.ui.theme

import android.os.PowerManager
import android.content.Context
import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.ThemeMode

/** true, если в системе отключены анимации («уменьшить анимацию»). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Виброотклик в приложении (настройка «Виброотклик»): клавиши, выбор карты, камера, скриншоты — см. [Haptics]. */
val LocalKeyHaptics = staticCompositionLocalOf { true }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppType
        @Composable @ReadOnlyComposable get() = LocalAppType.current
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

/** Сдержанная анимация или мгновенный переход, если анимации отключены. */
@Composable
@ReadOnlyComposable
fun <T> motion(durationMs: Int = 220): AnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMs)

/**
 * Тема приложения: [theme] — как в системе или своя, [motion] — анимации как в системе или выключены.
 * Значки системных панелей — под тему приложения.
 */
@Composable
fun DirhamTheme(
    theme: ThemeMode = ThemeMode.SYSTEM,
    motion: MotionMode = MotionMode.SYSTEM,
    keyHaptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val base = if (dark) DarkColors else LightColors
    val colors = base
    // Анимации выключены в системе или включено энергосбережение — без анимаций (слабые устройства).
    val systemReduce = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }
    val reduceMotion = motion == MotionMode.OFF || systemReduce
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val type = remember { AppType() }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.ink,
            secondary = colors.good, onSecondary = colors.onAccent,
            secondaryContainer = colors.goodSoft, onSecondaryContainer = colors.ink,
            tertiary = colors.good, onTertiary = colors.onAccent,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.bg, onSurfaceVariant = colors.muted,
            surfaceContainerLowest = colors.surface, surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surface,
            outline = colors.muted, outlineVariant = colors.line,
            error = colors.danger, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.ink,
            secondary = colors.good, onSecondary = colors.onAccent,
            secondaryContainer = colors.goodSoft, onSecondaryContainer = colors.ink,
            tertiary = colors.good, onTertiary = colors.onAccent,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.bg, onSurfaceVariant = colors.muted,
            surfaceContainerLowest = colors.surface, surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surface,
            outline = colors.muted, outlineVariant = colors.line,
            error = colors.danger, onError = Color.White,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides type,
        LocalReduceMotion provides reduceMotion,
        LocalKeyHaptics provides keyHaptics,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialTypography(type), content = content)
    }
}

