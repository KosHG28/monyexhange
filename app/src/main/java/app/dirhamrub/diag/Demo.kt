package app.dirhamrub.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.Wallet
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Демо-данные для скриншотов экранов в CI (`tools/screens.sh`): две карты с чеками, курсы ЦБ
 * на сегодня и неделю назад, сумма 167 AED. Только в отладочной сборке — в release недоступно.
 * [spendCode] — другая валюта трат для снимков: «IDR» (курс ЦБ есть, у карт — оценка),
 * «LKR» (у ЦБ курса нет: у карты в юанях есть чек покупки в рупиях, у карты в тенге — нет).
 */
object Demo {
    fun allowed(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    suspend fun seed(repository: WalletRepository, spendCode: String? = null) {
        val spend = Currency.byCode(spendCode) ?: Currency.AED
        val today = LocalDate.now()
        val day = today.minusDays(3)
        fun bd(s: String) = BigDecimal(s)
        val tenge = BankCard(
            id = "demo-kzt", name = "Карта в тенге", currency = Currency.KZT, style = 0,
            transfer = TransferReceipt(bd("15000"), Currency.RUB, bd("80900"), day),
            purchases = listOf(PurchaseReceipt(Currency.AED, bd("2757.48"), bd("22"), day)),
        )
        val yuan = BankCard(
            id = "demo-cny", name = "Карта в юанях", currency = Currency.CNY, style = 1,
            transfer = TransferReceipt(bd("30000"), Currency.RUB, bd("2439.02"), day),
            purchases = listOf(PurchaseReceipt(Currency.AED, bd("691.65"), bd("379"), day)) +
                if (spend == Currency.LKR) listOf(PurchaseReceipt(Currency.LKR, bd("59.50"), bd("2500"), day)) else emptyList(),
        )
        val now = OfficialRates(
            today,
            mapOf(
                Currency.AED to bd("21.97"), Currency.CNY to bd("11.42"), Currency.KZT to bd("0.1765"),
                Currency.USD to bd("80.70"), Currency.EUR to bd("93.90"), Currency.TRY to bd("2.36"),
                Currency.THB to bd("2.45"), Currency.GEL to bd("29.80"), Currency.AMD to bd("0.2090"),
                Currency.VND to bd("0.00312"), Currency.BYN to bd("24.70"), Currency.KGS to bd("0.9230"),
                Currency.UZS to bd("0.00640"), Currency.AZN to bd("47.47"), Currency.MDL to bd("4.62"),
                Currency.TJS to bd("8.58"), Currency.TMT to bd("23.06"), Currency.UAH to bd("1.95"),
                Currency.IDR to bd("0.004950"), Currency.INR to bd("0.9270"), Currency.JPY to bd("0.5330"),
                Currency.KRW to bd("0.0571"), Currency.GBP to bd("107.50"), Currency.CHF to bd("100.90"),
                Currency.EGP to bd("1.650"), Currency.SAR to bd("21.52"), Currency.QAR to bd("22.17"),
                Currency.HKD to bd("10.37"), Currency.SGD to bd("62.60"), Currency.CAD to bd("58.50"),
                Currency.AUD to bd("52.80"), Currency.PLN to bd("22.05"), Currency.CZK to bd("3.72"),
                Currency.HUF to bd("0.2300"), Currency.RSD to bd("0.8010"), Currency.RON to bd("18.43"),
                Currency.SEK to bd("8.47"), Currency.NOK to bd("7.92"), Currency.DKK to bd("12.58"),
                Currency.NZD to bd("47.50"), Currency.BRL to bd("14.80"), Currency.ZAR to bd("4.52"),
                Currency.BHD to bd("214.60"), Currency.OMR to bd("209.60"), Currency.IRR to bd("0.00192"),
                Currency.CUP to bd("3.36"), Currency.MNT to bd("0.02240"), Currency.NGN to bd("0.05270"),
                Currency.DZD to bd("0.6200"), Currency.BOB to bd("11.68"), Currency.ETB to bd("0.5800"),
                Currency.MMK to bd("0.03840"),
            ),
        )
        val weekAgo = OfficialRates(
            today.minusDays(7),
            now.rubPerUnit.mapValues { (c, v) -> if (c == Currency.AED) bd("21.88") else v },
        )
        repository.saveDemo(
            wallet = Wallet(
                cards = listOf(tenge, yuan),
                spend = spend,
                selectedId = yuan.id,
                recent = listOf(spend, Currency.AED, Currency.KZT, Currency.THB, Currency.TRY).distinct(),
            ),
            official = now,
            weekAgo = weekAgo,
            expression = Expression.of(
                when (spend) {
                    Currency.AED -> bd("167")
                    Currency.LKR -> bd("2500")
                    else -> QuickSums.chips(spend)[4]
                },
            ),
        )
    }
}
