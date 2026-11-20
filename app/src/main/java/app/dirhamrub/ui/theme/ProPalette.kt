package app.dirhamrub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ProPalette(
    val activeSurface: Color,
    val activeInk: Color,
    val activeOutline: Color,
    val tileSurface: Color,
    val amoledOff: Color,
)

private val LightProPalette = ProPalette(
    activeSurface = Color(0xFFE3F5EB),
    activeInk = Color(0xFF15803D),
    activeOutline = Color(0xFF15803D).copy(alpha = 0.20f),
    tileSurface = Color(0xFFF3F5F8),
    amoledOff = Color(0xFF1E222B),
)

private val DarkProPalette = ProPalette(
    activeSurface = Color(0xFF133020),
    activeInk = Color(0xFF4ADE80),
    activeOutline = Color(0xFF4ADE80).copy(alpha = 0.25f),
    tileSurface = Color(0xFF1C2026),
    amoledOff = Color(0xFF1E222B),
)

internal val AppColors.proPalette: ProPalette
    get() = if (isDark) DarkProPalette else LightProPalette
