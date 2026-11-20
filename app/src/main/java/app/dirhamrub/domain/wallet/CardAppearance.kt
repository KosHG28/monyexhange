package app.dirhamrub.domain.wallet

/** Opaque RGB only; formatting and pricing never depend on this value. */
object CardAppearance {
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return hex.toIntOrNull(16)
    }

    fun hex(rgb: Int): String = "#" + rgb.toString(16).padStart(6, '0').uppercase()
}

/** Rebase the visual change onto the current card rather than saving a stale financial snapshot. */
fun Wallet.withAppearance(id: String, style: Int, rgb: Int?): Wallet {
    if (style !in 0 until Wallet.CARD_STYLES || rgb != null && rgb !in 0..0xFFFFFF) return this
    val current = cards.firstOrNull { it.id == id && it.kind == CardKind.CARD } ?: return this
    return upsert(current.copy(style = style, customColor = rgb))
}
