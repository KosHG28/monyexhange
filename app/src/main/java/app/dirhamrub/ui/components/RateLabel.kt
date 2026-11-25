package app.dirhamrub.ui.components

import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import java.math.BigDecimal

/** «1 AED = 22,19 ₽»; у дешёвых валют — «100 AMD», «1 000 VND». */
internal fun rateLabel(homePerUnit: BigDecimal, spend: Currency, home: Currency, approximate: Boolean): String {
    val unit = Numbers.unitFor(homePerUnit)
    val sign = if (approximate) "≈" else "="
    return "${Numbers.moneyWhole(unit)} ${spend.code} $sign ${Numbers.rateShort(homePerUnit.multiply(unit, MC))} ${home.mark}"
}
