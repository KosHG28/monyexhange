package app.dirhamrub.domain.wallet

/**
 * Местные знаки валют, которых нет в [Currency.symbol] (там знак только однозначный — по нему распознают ценники
 * и ищут страну): арабские сокращения («د.م.» — марокканский дирхам), общие «kr», «Rs», «$» и подобные.
 * Только для показа: крупный итог главного и строка валюты страны в шторке ⓘ.
 */
private val LOCAL_SIGNS: Map<Currency, String> = mapOf(
    Currency.AED to "د.إ", Currency.ALL to "L", Currency.ARS to "\$", Currency.AWG to "ƒ", Currency.BBD to "Bds\$", Currency.BHD to "د.ب",
    Currency.BIF to "FBu", Currency.BMD to "\$", Currency.BND to "B\$", Currency.BSD to "B\$", Currency.BTN to "Nu.", Currency.BZD to "BZ\$",
    Currency.CDF to "FC", Currency.CHF to "Fr.", Currency.CLP to "\$", Currency.COP to "\$", Currency.CUP to "\$", Currency.CVE to "Esc",
    Currency.DJF to "Fdj", Currency.DKK to "kr.", Currency.DOP to "RD\$", Currency.DZD to "د.ج", Currency.ERN to "Nfk", Currency.ETB to "Br",
    Currency.FJD to "FJ\$", Currency.FKP to "£", Currency.GIP to "£", Currency.GMD to "D", Currency.GYD to "G\$", Currency.HNL to "L",
    Currency.HTG to "G", Currency.IQD to "ع.د", Currency.IRR to "﷼", Currency.ISK to "kr", Currency.JMD to "J\$", Currency.JOD to "د.ا",
    Currency.JPY to "¥", Currency.KES to "KSh", Currency.KPW to "₩", Currency.KWD to "د.ك", Currency.KYD to "CI\$", Currency.LKR to "Rs",
    Currency.LRD to "L\$", Currency.LSL to "L", Currency.LYD to "ل.د", Currency.MAD to "د.م.", Currency.MDL to "L", Currency.MKD to "ден",
    Currency.MOP to "MOP\$", Currency.MRU to "UM", Currency.MUR to "Rs", Currency.MVR to "Rf", Currency.MWK to "MK", Currency.MZN to "MT",
    Currency.NAD to "N\$", Currency.NOK to "kr", Currency.NPR to "रू", Currency.OMR to "ر.ع.", Currency.PEN to "S/", Currency.PGK to "K",
    Currency.PKR to "Rs", Currency.QAR to "ر.ق", Currency.RON to "lei", Currency.RSD to "дин.", Currency.SAR to "ر.س", Currency.SBD to "SI\$",
    Currency.SCR to "SR", Currency.SDG to "ج.س.", Currency.SEK to "kr", Currency.SGD to "S\$", Currency.SHP to "£", Currency.SLE to "Le",
    Currency.SOS to "Sh", Currency.SRD to "\$", Currency.SSP to "£", Currency.SYP to "ل.س", Currency.SZL to "E", Currency.TJS to "смн",
    Currency.TMT to "m", Currency.TND to "د.ت", Currency.TTD to "TT\$", Currency.TZS to "TSh", Currency.UGX to "USh", Currency.UYU to "\$U",
    Currency.VES to "Bs.", Currency.VUV to "VT", Currency.WST to "WS\$", Currency.YER to "﷼",
)

/** Знак для показа: местный, если есть, иначе [Currency.symbol] (у валют без знака — код). */
val Currency.sign: String get() = LOCAL_SIGNS[this] ?: symbol
