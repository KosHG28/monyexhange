package app.dirhamrub.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.RateBasis
import java.math.BigDecimal

/** Насколько курс карты дороже ЦБ: до 3 % — хорошо, до 7 % — заметно, дальше — дорого. */
enum class Markup { GOOD, WARN, BAD }

fun markupOf(vsOfficial: BigDecimal): Markup = when {
    vsOfficial < BigDecimal(3) -> Markup.GOOD
    vsOfficial < BigDecimal(7) -> Markup.WARN
    else -> Markup.BAD
}

/** «по чекам», «оценка», «курс ЦБ» (или «курс UnionPay»), «вручную». */
@Composable
fun basisShort(q: CardQuote): String = stringResource(
    when (q.basis) {
        RateBasis.RECEIPTS -> R.string.card_basis_receipts
        RateBasis.ESTIMATE -> R.string.card_basis_estimate
        RateBasis.OFFICIAL -> if (q.viaUnionPay) R.string.card_basis_unionpay else R.string.card_basis_official
        RateBasis.MANUAL -> R.string.card_basis_manual
        RateBasis.UNIONPAY -> R.string.card_basis_unionpay
    },
)

/** «22,45 ₽ за AED · по чекам · +4,7 % к ЦБ» — как посчитан курс карты. */
@Composable
fun basisLabel(q: CardQuote): String {
    val basis = basisShort(q)
    val vs = q.vsOfficial?.takeIf { q.basis != RateBasis.OFFICIAL }?.let { signedPercent(it) }
    return if (vs == null) basis else basis + " · " + stringResource(if (q.viaUnionPay) R.string.card_vs_unionpay else R.string.card_vs_official, vs)
}

/** «+4,7», «−1,2», «0». */
fun signedPercent(value: BigDecimal): String {
    val text = Numbers.percent(value.abs())
    return when {
        text == "0" -> "0"
        value.signum() > 0 -> "+$text"
        else -> "−$text"
    }
}
