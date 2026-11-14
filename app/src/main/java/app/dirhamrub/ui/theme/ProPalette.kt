package app.dirhamrub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ProPalette(
    val activeSurface: Color,
    val activeInk: Color,
    val activeOutline: Color,
    val receiptSurface: Color,
    val receiptOutline: Color,
    val iconOutline: Color,
)

private val LightProPalette = ProPalette(
    activeSurface = Color(0xFFE3F5EB),
    activeInk = Color(0xFF15803D),
    activeOutline = Color(0xFF15803D).copy(alpha = 0.20f),
    receiptSurface = LightColors.surface2,
    receiptOutline = LightColors.line,
    iconOutline = Color.Black.copy(alpha = 0.65f),
)

private val DarkProPalette = ProPalette(
    activeSurface = Color(0xFF133020),
    activeInk = Color(0xFF4ADE80),
    activeOutline = Color(0xFF4ADE80).copy(alpha = 0.25f),
    receiptSurface = Color(0xFF22262E),
    receiptOutline = Color.White.copy(alpha = 0.09f),
    iconOutline = Color.White.copy(alpha = 0.75f),
)

internal val AppColors.proPalette: ProPalette
    get() = if (isDark) DarkProPalette else LightProPalette
