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
    /** Заметная, но не критичная наценка к ЦБ. */
    val warn: Color,
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
    warn = Color(0xFF9A6400),
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
    warn = Color(0xFFE9B949),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Оформление «банковской» карты: градиент, водяной знак, чип. Одинаково в обеих темах. */
@Immutable
data class CardStyle(val start: Color, val end: Color, val mark: Color, val chip: Color)

object CardPalette {
    val onCard = Color(0xFFFFFFFF)
    /** Наценка к ЦБ и «лучшая» поверх градиента карты: светлые, на тёмной подложке. */
    val goodOnCard = Color(0xFF7BE3AE)
    val warnOnCard = Color(0xFFFFD27A)
    val badOnCard = Color(0xFFFF9A9A)
    val star = Color(0xFFFFD66B)
    val chipScrim = Color(0x47000000)
    private val gold = Color(0xFFE2C275)
    private val silver = Color(0xFFDDE7EA)

    /** Семь оформлений по очереди для новых карт: бирюза, бордо с золотом, индиго, изумруд, янтарь, фиолет, графит. */
    val styles: List<CardStyle> = listOf(
        CardStyle(Color(0xFF14B3A6), Color(0xFF0B5C6E), Color(0xFFBFF3EC), silver),
        CardStyle(Color(0xFF8E1B22), Color(0xFF3E0A0F), gold, gold),
        CardStyle(Color(0xFF3F51B5), Color(0xFF1A237E), Color(0xFFC5CAE9), silver),
        CardStyle(Color(0xFF2E9E62), Color(0xFF14502F), Color(0xFFC8F2D9), gold),
        CardStyle(Color(0xFFE08A1E), Color(0xFF8A4A06), Color(0xFFFFE2B8), silver),
        CardStyle(Color(0xFF7B3FA8), Color(0xFF3C1858), Color(0xFFE3C9F5), gold),
        CardStyle(Color(0xFF4A5560), Color(0xFF1E252B), Color(0xFFD5DCE3), gold),
    )

    fun style(index: Int): CardStyle = styles[((index % styles.size) + styles.size) % styles.size]
}
