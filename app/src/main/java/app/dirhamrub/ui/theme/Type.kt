package app.dirhamrub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.dirhamrub.R

/** Единственный шрифт приложения: суммы тонким начертанием, текст — обычным и средним. */
val InterTight = FontFamily(
    Font(R.font.inter_tight_light, FontWeight.Light),
    Font(R.font.inter_tight_regular, FontWeight.Normal),
    Font(R.font.inter_tight_medium, FontWeight.Medium),
    Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
)

/** Цифры везде одинаковой ширины. */
private const val TNUM = "tnum"

/** Стили приложения поверх Material-типографики. */
@Immutable
data class AppType(
    /** Главное число экрана. */
    val hero: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 60.sp, lineHeight = 66.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = TNUM),
    /** Значение поля ввода и крупные суммы. */
    val number: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = TNUM),
    val title: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM),
    val key: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = TNUM),
    val body: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    val bodyStrong: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    val label: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
    val caption: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp, fontFeatureSettings = TNUM),
    /** Курсы и суммы в строку: тот же шрифт, цифры одинаковой ширины. */
    val mono: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    val monoStrong: TextStyle = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
)

val LocalAppType = staticCompositionLocalOf { AppType() }

internal fun materialTypography(t: AppType): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = t.hero,
        displayMedium = t.hero.copy(fontSize = 36.sp, lineHeight = 44.sp),
        displaySmall = t.hero.copy(fontSize = 30.sp, lineHeight = 36.sp),
        headlineLarge = t.title.copy(fontSize = 26.sp, lineHeight = 32.sp),
        headlineMedium = t.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = t.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = t.title,
        titleMedium = t.bodyStrong.copy(fontSize = 16.sp),
        titleSmall = t.bodyStrong.copy(fontSize = 14.sp),
        bodyLarge = t.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = t.body,
        bodySmall = t.caption,
        labelLarge = t.bodyStrong.copy(fontSize = 14.sp),
        labelMedium = t.label,
        labelSmall = t.caption.copy(fontWeight = FontWeight.Medium),
    )
}
