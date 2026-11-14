package app.dirhamrub.ui.theme

import androidx.compose.ui.graphics.Color

enum class EasterEggTone { OLIVE, DESERT, BLOSSOM, FOREST, WINTER, SEA, HARVEST, FESTIVAL, LAVENDER }

data class EasterEggPalette(val icon: Color, val card: Color, val particles: List<Color>)

/** Explicit country palettes; the tone remains available for future entries. */
fun AppColors.easterEggPalette(tone: EasterEggTone, iso: String? = null): EasterEggPalette {
    val pair = countryEggPalettes[iso] ?: toneEggPalettes.getValue(tone)
    return if (isDark) pair.second else pair.first
}

private fun palette(icon: Long, card: Long, first: Long, second: Long) =
    EasterEggPalette(Color(icon), Color(card), listOf(Color(first), Color(second)))

private val countryEggPalettes = mapOf(
    "RU" to (palette(0xFF406B9B, 0xFFEAF1F8, 0xFF4A89C8, 0xFF7199B8) to palette(0xFFA3BAD5, 0xFF172330, 0xFFA8D5FF, 0xFFC3CDD9)),
    "BY" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFFC57B39, 0xFF98734D) to palette(0xFFC8A679, 0xFF2B211A, 0xFFE9B87F, 0xFFBC9AD0)),
    "KZ" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "KG" to (palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFC9922E, 0xFF3B6EB5) to palette(0xFF7EA6E0, 0xFF131D2E, 0xFFF2D184, 0xFF8BB4F0)),
    "TJ" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "UZ" to (palette(0xFF2E7D5B, 0xFFE8F2E7, 0xFF2E7D5B, 0xFF7A995D) to palette(0xFF99B483, 0xFF19251C, 0xFF6FCF97, 0xFFB0C38D)),
    "TM" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "AZ" to (palette(0xFF2E7D5B, 0xFFE8F2E7, 0xFF2E7D5B, 0xFF7A995D) to palette(0xFF99B483, 0xFF19251C, 0xFF6FCF97, 0xFFB0C38D)),
    "AM" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFFC57B39, 0xFF98734D) to palette(0xFFC8A679, 0xFF2B211A, 0xFFE9B87F, 0xFFBC9AD0)),
    "MD" to (palette(0xFF8156A4, 0xFFF4EBF8, 0xFFB85C66, 0xFF9A8E86) to palette(0xFFC1ABDA, 0xFF241D30, 0xFFE7A0A8, 0xFFD7D2CD)),
    "GE" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFF7F6598, 0xFF668F51) to palette(0xFFC8A679, 0xFF2B211A, 0xFFB6A0CD, 0xFF9EBE89)),
    "TR" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "AE" to (palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFC9922E, 0xFF3B6EB5) to palette(0xFF7EA6E0, 0xFF131D2E, 0xFFF2D184, 0xFF8BB4F0)),
    "EG" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "OM" to (palette(0xFF287C77, 0xFFE7F3F0, 0xFF2A8C82, 0xFF4EA89E) to palette(0xFF87BEC3, 0xFF14272A, 0xFF64DFDF, 0xFF72EFDD)),
    "QA" to (palette(0xFF287C77, 0xFFE7F3F0, 0xFF2A8C82, 0xFF4EA89E) to palette(0xFF87BEC3, 0xFF14272A, 0xFF64DFDF, 0xFF72EFDD)),
    "TH" to (palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFC9922E, 0xFFB56C30) to palette(0xFF7EA6E0, 0xFF131D2E, 0xFFF2CE87, 0xFFFFB76B)),
    "VN" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD99540, 0xFFC36D86) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFE9C37A, 0xFFE8A7BA)),
    "ID" to (palette(0xFF2E7D5B, 0xFFE8F2E7, 0xFF2E7D5B, 0xFF7A995D) to palette(0xFF99B483, 0xFF19251C, 0xFF6FCF97, 0xFFB0C38D)),
    "MY" to (palette(0xFF8156A4, 0xFFF4EBF8, 0xFFB94C3F, 0xFFB88730) to palette(0xFFC1ABDA, 0xFF241D30, 0xFFEC9987, 0xFFE9C078)),
    "IN" to (palette(0xFF8156A4, 0xFFF4EBF8, 0xFFC86A82, 0xFF8A6AB0) to palette(0xFFC1ABDA, 0xFF241D30, 0xFFECA1BC, 0xFFC3AAEA)),
    "LK" to (palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFD97724, 0xFFB49338) to palette(0xFF7EA6E0, 0xFF131D2E, 0xFFFFB76B, 0xFFE3D39B)),
    "MV" to (palette(0xFF287C77, 0xFFE7F3F0, 0xFF2A8C82, 0xFF4EA89E) to palette(0xFF87BEC3, 0xFF14272A, 0xFF64DFDF, 0xFF72EFDD)),
    "SC" to (palette(0xFF287C77, 0xFFE7F3F0, 0xFF2A8C82, 0xFF4EA89E) to palette(0xFF87BEC3, 0xFF14272A, 0xFF64DFDF, 0xFF72EFDD)),
    "CN" to (palette(0xFF2F5FA3, 0xFFEBF2FC, 0xFFC9922E, 0xFF3B6EB5) to palette(0xFF7EA6E0, 0xFF131D2E, 0xFFF2D184, 0xFF8BB4F0)),
    "JP" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "KR" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "FR" to (palette(0xFF715295, 0xFFF0EBF6, 0xFF8D6AB0, 0xFF6A8A62) to palette(0xFFC2A8E5, 0xFF211A2D, 0xFFBEA4E3, 0xFFA6C092)),
    "IT" to (palette(0xFF8156A4, 0xFFF4EBF8, 0xFFC87563, 0xFF8B63AF) to palette(0xFFC1ABDA, 0xFF241D30, 0xFFD5ACED, 0xFFE8B672)),
    "ES" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "GR" to (palette(0xFF557032, 0xFFEDF4E6, 0xFF557032, 0xFF889985) to palette(0xFF8FA86A, 0xFF162114, 0xFF8FA86A, 0xFFB2BEB0)),
    "CY" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "ME" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFFC57B39, 0xFF98734D) to palette(0xFFC8A679, 0xFF2B211A, 0xFFE9B87F, 0xFFBC9AD0)),
    "RS" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFF68834A, 0xFF86609B) to palette(0xFFC8A679, 0xFF2B211A, 0xFFA1BA82, 0xFFBFA2D3)),
    "NL" to (palette(0xFFA34E70, 0xFFFAECF2, 0xFFD96B8A, 0xFFBE718A) to palette(0xFFD6A1B9, 0xFF2A1D25, 0xFFF4A6BE, 0xFFD3ACDF)),
    "CZ" to (palette(0xFF406B9B, 0xFFEAF1F8, 0xFFB88932, 0xFF957038) to palette(0xFFA3BAD5, 0xFF172330, 0xFFE8C58B, 0xFFDDBA6B)),
    "CA" to (palette(0xFF96602E, 0xFFF8EFE5, 0xFFB76B30, 0xFFAB5148) to palette(0xFFC8A679, 0xFF2B211A, 0xFFE8B179, 0xFFD68B85)),
)

private val toneEggPalettes = mapOf(
    EasterEggTone.OLIVE to countryEggPalettes.getValue("GR"),
    EasterEggTone.DESERT to countryEggPalettes.getValue("AE"),
    EasterEggTone.BLOSSOM to countryEggPalettes.getValue("JP"),
    EasterEggTone.FOREST to countryEggPalettes.getValue("UZ"),
    EasterEggTone.WINTER to countryEggPalettes.getValue("RU"),
    EasterEggTone.SEA to countryEggPalettes.getValue("MV"),
    EasterEggTone.HARVEST to countryEggPalettes.getValue("BY"),
    EasterEggTone.FESTIVAL to countryEggPalettes.getValue("IT"),
    EasterEggTone.LAVENDER to countryEggPalettes.getValue("FR"),
)
