package app.dirhamrub.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.ThemeMode

/** true, если в системе отключены анимации («уменьшить анимацию»). */
val LocalReduceMotion = staticCompositionLocalOf { false }

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
 * Значки системных панелей подстраиваются под тему приложения, а не системы.
 */
@Composable
fun DirhamTheme(
    theme: ThemeMode = ThemeMode.SYSTEM,
    motion: MotionMode = MotionMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val systemReduce = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
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
            error = colors.accent, onError = colors.onAccent,
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
            error = colors.accent, onError = colors.onAccent,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialTypography(type), content = content)
    }
}
