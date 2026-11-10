package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.MC
import java.math.BigDecimal

/**
 * Проверка чека на опечатку: курс по суммам чека сравнивается с курсом ЦБ. Банк и обменник берут
 * на проценты больше ЦБ, а не в разы: расхождение больше [LIMIT_PERCENT] — почти наверняка лишний
 * или потерянный ноль, перепутанные поля или не та валюта.
 */
object ReceiptCheck {
    /** Наценки банков и обменников бывают до 10 %; дальше — похоже на ошибку ввода. */
    val LIMIT_PERCENT: BigDecimal = BigDecimal(15)

    /**
     * На сколько процентов курс чека отличается от ЦБ: чек — [paid] единиц одной валюты за [got] другой,
     * [officialPaidPerGot] — сколько первой валюты стоит единица второй по ЦБ. null — сравнить не с чем.
     */
    fun deviationPercent(paid: BigDecimal, got: BigDecimal, officialPaidPerGot: BigDecimal?): BigDecimal? {
        if (officialPaidPerGot == null || officialPaidPerGot.signum() <= 0 || paid.signum() <= 0 || got.signum() <= 0) return null
        val rate = paid.divide(got, MC)
        return rate.subtract(officialPaidPerGot).multiply(BigDecimal(100), MC).divide(officialPaidPerGot, MC)
    }

    /** Отклонение, о котором стоит предупредить (со знаком), или null — чек правдоподобен. */
    fun suspicious(paid: BigDecimal, got: BigDecimal, officialPaidPerGot: BigDecimal?): BigDecimal? =
        deviationPercent(paid, got, officialPaidPerGot)?.takeIf { it.abs() > LIMIT_PERCENT }
}
