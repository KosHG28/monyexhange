package app.dirhamrub.ui.theme

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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

/** Вибрация при нажатии клавиш калькулятора (настройка «Вибрация клавиш»). */
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
 * [dynamic] — цвета из обоев (Android 12+): фон, поверхности и акцент берутся из системной палитры,
 * «хорошо/заметно» остаются своими — у них смысл. Значки системных панелей — под тему приложения.
 */
@Composable
fun DirhamTheme(
    theme: ThemeMode = ThemeMode.SYSTEM,
    motion: MotionMode = MotionMode.SYSTEM,
    dynamic: Boolean = false,
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
    val colors = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember(context, dark) { dynamicColors(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), base) }
    } else {
        base
    }
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

/** Свои роли из системной палитры Material You; зелёный и жёлтый — свои: «выгодно» и «заметно» не должны зависеть от обоев. */
private fun dynamicColors(scheme: ColorScheme, base: AppColors): AppColors = base.copy(
    bg = scheme.surface,
    surface = scheme.surfaceContainer,
    surface2 = scheme.surfaceContainerHighest,
    paper = scheme.surfaceContainerHigh,
    ink = scheme.onSurface,
    muted = scheme.onSurfaceVariant,
    line = scheme.outlineVariant,
    accent = scheme.primary,
    accentSoft = scheme.primaryContainer,
    onAccent = scheme.onPrimary,
)
