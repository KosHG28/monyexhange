package app.dirhamrub.ui.theme

import androidx.compose.ui.graphics.Color

enum class EasterEggTone { OLIVE, DESERT, BLOSSOM, FOREST, WINTER, SEA, HARVEST, FESTIVAL }

data class EasterEggPalette(val icon: Color, val card: Color, val particles: List<Color>)

fun AppColors.easterEggPalette(tone: EasterEggTone): EasterEggPalette = when (tone) {
    EasterEggTone.OLIVE -> if (isDark) palette(0xFF8FA86A, 0xFF162114, 0xFF8FA86A, 0xFFB2BEB0)
        else palette(0xFF557032, 0xFFEDF4E6, 0xFF5E7A3A, 0xFF889985)
    EasterEggTone.DESERT -> if (isDark) palette(0xFF7EA6E0, 0xFF131D2E, 0xFFF2D184, 0xFF8BB4F0)
        else palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFC9922E, 0xFF3B6EB5)
    EasterEggTone.BLOSSOM -> if (isDark) palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFD6A1B9, 0xFFB6A3D1)
        else palette(0xFF914F6F, 0xFFF5EAF0, 0xFFB36588, 0xFF8C70B3)
    EasterEggTone.FOREST -> if (isDark) palette(0xFF99B483, 0xFF19251C, 0xFF99B483, 0xFFD2BA80)
        else palette(0xFF4C7044, 0xFFEDF3E8, 0xFF557D4C, 0xFFAF7B37)
    EasterEggTone.WINTER -> if (isDark) palette(0xFFA3BAD5, 0xFF172330, 0xFFA3BAD5, 0xFFC3CDD9)
        else palette(0xFF426B93, 0xFFEAF1F7, 0xFF507DA6, 0xFF738DAB)
    EasterEggTone.SEA -> if (isDark) palette(0xFF87BEC3, 0xFF14272A, 0xFF87BEC3, 0xFFA7C7AB)
        else palette(0xFF316F77, 0xFFE7F3F2, 0xFF3C7F8C, 0xFF5D8968)
    EasterEggTone.HARVEST -> if (isDark) palette(0xFFC8A679, 0xFF2B211A, 0xFFC8A679, 0xFFB29BC6)
        else palette(0xFF866032, 0xFFF6EEE2, 0xFFA06D36, 0xFF866398)
    EasterEggTone.FESTIVAL -> if (isDark) palette(0xFFC1ABDA, 0xFF241D30, 0xFFC1ABDA, 0xFFD6B17D)
        else palette(0xFF76529B, 0xFFF1EBF7, 0xFF9271B4, 0xFFB8883E)
}

private fun palette(icon: Long, card: Long, first: Long, second: Long) =
    EasterEggPalette(Color(icon), Color(card), listOf(Color(first), Color(second)))
