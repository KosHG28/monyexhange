package app.dirhamrub.domain.wallet

/**
 * Местные знаки валют, которых нет в [Currency.symbol] (там знак только однозначный — по нему распознают ценники
 * и ищут страну): арабские сокращения («د.م.» — марокканский дирхам), общие «kr», «Rs», «$» и подобные.
 * Только для показа: крупный итог главного и строка валюты страны в шторке ⓘ.
 */
private val LOCAL_SIGNS: Map<Currency, String> = mapOf(
    Currency.MAD to "د.م.",
)

/** Знак для показа: местный, если есть, иначе [Currency.symbol] (у валют без знака — код). */
val Currency.sign: String get() = LOCAL_SIGNS[this] ?: symbol
