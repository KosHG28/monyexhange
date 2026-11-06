package app.dirhamrub.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.Wallet
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Демо-данные для скриншотов экранов в CI (`tools/screens.sh`): две карты с чеками, курсы ЦБ
 * на сегодня и неделю назад, сумма 167 AED. Только в отладочной сборке — в release недоступно.
 */
object Demo {
    fun allowed(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    suspend fun seed(repository: WalletRepository) {
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
            purchases = listOf(PurchaseReceipt(Currency.AED, bd("691.65"), bd("379"), day)),
        )
        val now = OfficialRates(
            today,
            mapOf(
                Currency.AED to bd("21.97"), Currency.CNY to bd("11.42"), Currency.KZT to bd("0.1765"),
                Currency.USD to bd("80.70"), Currency.EUR to bd("93.90"), Currency.TRY to bd("2.36"),
                Currency.THB to bd("2.45"), Currency.GEL to bd("29.80"), Currency.AMD to bd("0.2090"),
                Currency.VND to bd("0.00312"),
            ),
        )
        val weekAgo = OfficialRates(
            today.minusDays(7),
            now.rubPerUnit.mapValues { (c, v) -> if (c == Currency.AED) bd("21.88") else v },
        )
        repository.saveDemo(
            wallet = Wallet(cards = listOf(tenge, yuan), spend = Currency.AED, selectedId = yuan.id),
            official = now,
            weekAgo = weekAgo,
            expression = Expression.of(bd("167")),
        )
    }
}
