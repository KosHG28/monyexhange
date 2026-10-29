package app.dirhamrub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Палитра из SPEC: прохладный фон, красный дубайского метро. */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val good: Color,
    val goodSoft: Color,
    val isDark: Boolean,
)

val LightColors = AppColors(
    bg = Color(0xFFEDF0F3),
    surface = Color(0xFFFFFFFF),
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF15202A),
    muted = Color(0xFF5D6975),
    line = Color(0xFFD5DCE3),
    accent = Color(0xFFC8272D),
    accentSoft = Color(0xFFFBEAE9),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF1D6E43),
    goodSoft = Color(0xFFE3F2EA),
    isDark = false,
)

val DarkColors = AppColors(
    bg = Color(0xFF0D1217),
    surface = Color(0xFF151D25),
    paper = Color(0xFF1B2530),
    ink = Color(0xFFE7ECF1),
    muted = Color(0xFF93A0AC),
    line = Color(0xFF27323D),
    accent = Color(0xFFFF5C5C),
    accentSoft = Color(0xFF351C20),
    onAccent = Color(0xFF1A0A0B),
    good = Color(0xFF5CCB92),
    goodSoft = Color(0xFF15301F),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** «Банковские» цвета мини-карт — одинаковые в обеих темах. */
object CardPalette {
    val tengeStart = Color(0xFF14B3A6)
    val tengeEnd = Color(0xFF0B5C6E)
    val tengeMark = Color(0xFFBFF3EC)
    val yuanStart = Color(0xFF8E1B22)
    val yuanEnd = Color(0xFF3E0A0F)
    val gold = Color(0xFFE2C275)
    val goldDeep = Color(0xFFB8913E)
    val silver = Color(0xFFDDE7EA)
    val onCard = Color(0xFFFFFFFF)
}
