package app.dirhamrub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind

/**
 * Палитра: прохладный фон, синий акцент — ввод и действия, зелёный — выгода,
 * красный ([danger]) — только ошибки и удаление.
 */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    /** Вторая поверхность: клавиши действий, подложки значков. */
    val surface2: Color,
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
    /** Ошибка, удаление, большая наценка. */
    val danger: Color,
    val isDark: Boolean,
)

val LightColors = AppColors(
    bg = Color(0xFFEDF0F3),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE6EAEF),
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF15202A),
    muted = Color(0xFF56616C),
    line = Color(0xFFD5DCE3),
    accent = Color(0xFF1F5FE0),
    accentSoft = Color(0xFFE3ECFD),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF1D6E43),
    goodSoft = Color(0xFFE3F2EA),
    warn = Color(0xFF9A6400),
    danger = Color(0xFFC8272D),
    isDark = false,
)

val DarkColors = AppColors(
    bg = Color(0xFF0D1217),
    surface = Color(0xFF151D25),
    surface2 = Color(0xFF212B35),
    paper = Color(0xFF1B2530),
    ink = Color(0xFFE7ECF1),
    muted = Color(0xFFB8C3CE),
    line = Color(0xFF27323D),
    accent = Color(0xFF4C8DFF),
    accentSoft = Color(0xFF172B4D),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF5CCB92),
    goodSoft = Color(0xFF15301F),
    warn = Color(0xFFE9B949),
    danger = Color(0xFFFF6B6B),
    isDark = true,
)

/**
 * Тёмная тема на чисто чёрном фоне (настройка «Чисто чёрный фон»): на OLED-экране пиксели фона выключены.
 * Карточки и поля — тёмно-серые, чтобы не сливаться с фоном; акцент, «лучшая» и цвета карт — как в тёмной.
 */
val BlackColors = DarkColors.copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF15191E),
    surface2 = Color(0xFF20262D),
    paper = Color(0xFF181D23),
    line = Color(0xFF262C33),
    goodSoft = Color(0xFF0F2A1E),
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

    /** Наличные: зелень купюр — сразу видно, что это не карта. */
    val cash = CardStyle(Color(0xFF3F8F5A), Color(0xFF1F4A30), Color(0xFFCDEBD6), gold)

    fun style(card: BankCard): CardStyle = if (card.kind == CardKind.CASH) cash else style(card.style)
}

/** Сезонные цветы: отдельные цвета темы для светлого и тёмного оформления. */
val AppColors.blossom: Color get() = if (isDark) Color(0xFFF1A8C7) else Color(0xFFB64F80)
val AppColors.blossomSoft: Color get() = if (isDark) Color(0xFF382430) else Color(0xFFF9EAF1)


/** Выбор способа оплаты: в тёмной теме глубину создаёт только тон поверхности. */
fun AppColors.payRowSurface(selected: Boolean, best: Boolean): Color = when {
    isDark && best -> if (selected) Color(0xFF173525) else Color(0xFF11261B)
    isDark -> if (selected) Color(0xFF222733) else Color(0xFF16181D)
    best -> if (selected) Color(0xFFE1F5EA) else Color(0xFFEAF6F0)
    else -> if (selected) Color.White else Color(0xFFF9FAFC)
}

object PayRowShadow {
    val spot = Color(0xFF101828).copy(alpha = 0.12f)
    val ambient = Color(0xFF101828).copy(alpha = 0.06f)
}

object OliveColors {
    val badge = Color(0xFF8FA86A)
    val leaves = listOf(badge, Color(0xFF6E854E), Color(0xFFB2BEB0), Color(0xFF9AA898))
}
val AppColors.oliveSoft: Color get() = if (isDark) Color(0xFF242D22) else Color(0xFFEEF2E9)
val AppColors.seasonBadgeBackground: Color get() =
    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
