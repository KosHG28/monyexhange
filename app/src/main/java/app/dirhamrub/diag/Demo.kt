package app.dirhamrub.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.domain.AppSettings
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Exchanger
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.Wallet
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Демо-данные для скриншотов экранов в CI (`tools/screens.sh`): две карты с чеками, курсы ЦБ
 * на сегодня и неделю назад, сумма 167 AED. Только в отладочной сборке — в release недоступно.
 * [spendCode] — другая валюта трат для снимков: «IDR» (курс ЦБ есть, у карт — оценка),
 * «LKR» (у ЦБ курса нет: карта в юанях — UnionPay, курс по UnionPay; у карты в тенге — справочный курс
 * UnionPay × ЦБ с наценкой карты). Курсы UnionPay — настоящие на 06.11.2026 (к доллару), к юаню — через ЦБ.
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
            network = CardNetwork.VISA_MC_FOREIGN,
        )
        val yuan = BankCard(
            id = "demo-cny", name = "Карта в юанях", currency = Currency.CNY, style = 1,
            transfer = TransferReceipt(bd("30000"), Currency.RUB, bd("2439.02"), day),
            purchases = listOf(PurchaseReceipt(Currency.AED, bd("691.65"), bd("379"), day)),
            unionPay = true,
            // Прежнее пополнение — для снимка редактора с историей (курс — по последнему, как раньше).
            history = listOf(TransferReceipt(bd("20000"), Currency.RUB, bd("1660"), today.minusDays(20))),
        )
        // Наличные дирхамы из обменника — выменяны на доллары, купленные по 81,50 ₽: в ряду карт —
        // только когда тратите AED.
        // Баты из банкомата в Таиланде — сняты с карты в юанях (комиссия банкомата внутри списания).
        val atm = BankCard(
            id = "demo-thb-atm", name = "Наличные THB", currency = Currency.THB, style = 2, kind = CardKind.CASH,
            transfer = TransferReceipt(bd("2210"), Currency.CNY, bd("10000"), day, fromCard = "demo-cny"),
        )
        val cash = BankCard(
            id = "demo-aed-cash", name = "Наличные AED", currency = Currency.AED, style = 2, kind = CardKind.CASH,
            transfer = TransferReceipt(bd("300"), Currency.USD, bd("1101.75"), day, boughtAt = bd("81.50"), boughtIn = Currency.RUB),
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
                Currency.MMK to bd("0.03840"), Currency.BDT to bd("0.6563"),
            ),
        )
        val weekAgo = OfficialRates(
            today.minusDays(7),
            now.rubPerUnit.mapValues { (c, v) -> if (c == Currency.AED) bd("21.88") else v },
        )
        repository.saveDemo(
            wallet = Wallet(
                cards = listOf(tenge, yuan, cash, atm),
                spend = spend,
                selectedId = yuan.id,
                recent = listOf(spend, Currency.AED, Currency.KZT, Currency.THB, Currency.TRY).distinct(),
            ),
            official = now,
            weekAgo = weekAgo,
            unionPay = unionPay(now, today),
            expression = Expression.of(
                when (spend) {
                    Currency.AED -> bd("167")
                    Currency.LKR -> bd("2500")
                    else -> QuickSums.chips(spend)[4]
                },
            ),
        )
        // Настройки по умолчанию (без быстрых сумм) — как у нового пользователя.
        repository.saveSettings(AppSettings())
        // Обменники для снимка «Сравнить обменники»: доллары → дирхамы.
        val t = System.currentTimeMillis()
        repository.saveExchangers(
            listOf(
                Exchanger("demo-ex-1", "У метро", Currency.USD, Currency.AED, bd("3.65"), bd("3.67"), t - 3_600_000),
                Exchanger("demo-ex-2", "В торговом центре", Currency.USD, Currency.AED, bd("3.61"), bd("3.66"), t - 7_200_000),
                Exchanger("demo-ex-3", "Аэропорт", Currency.USD, Currency.AED, bd("3.40"), bd("3.85"), t - 86_400_000),
            ),
        )
    }

    /** Курсы UnionPay для демо: валюты без ЦБ — настоящие к доллару; к юаню и остальным валютам ЦБ — через ЦБ. */
    private fun unionPay(cbr: OfficialRates, day: LocalDate): UnionPayRates {
        val usd = cbr.rubPerUnit.getValue(Currency.USD)
        val perUsd = HashMap<Currency, BigDecimal>()
        for ((c, rub) in cbr.rubPerUnit) perUsd[c] = rub.divide(usd, MC)
        for (pair in NO_CBR_PER_USD.split(' ')) {
            val (code, v) = pair.split(':')
            Currency.byCode(code)?.let { perUsd[it] = BigDecimal(v) }
        }
        val cnyPerUsd = BigDecimal.ONE.divide(perUsd.getValue(Currency.CNY), MC)
        return UnionPayRates(
            day,
            mapOf(
                Currency.USD to perUsd,
                Currency.CNY to perUsd.mapValues { (_, v) -> v.multiply(cnyPerUsd, MC) },
            ),
        )
    }

    private const val NO_CBR_PER_USD =
            "AFN:0.01540247 ALL:0.01235994 AOA:0.00109058 ARS:0.00067515 AWG:0.56034024 BAM:0.5748572 " +
            "BBD:0.49799366 BIF:0.00033529 BMD:1.00300903 BND:0.78406021 BSD:1.00300903 BTN:0.0106161 " +
            "BWP:0.07321966 BZD:0.49871173 CDF:0.00043345 CLP:0.00102495 COP:0.00030963 CRC:0.00221173 " +
            "CVE:0.01017044 DJF:0.00563251 DOP:0.01669178 ERN:0.0665346 FJD:0.44488465 FKP:1.35230692 " +
            "GHS:0.08598449 GIP:1.35230692 GMD:0.01343166 GNF:0.00011324 GTQ:0.13125813 GYD:0.00478444 " +
            "HNL:0.03731842 HTG:0.00765364 ILS:0.31287324 IQD:0.00066162 ISK:0.00821869 JMD:0.00631976 " +
            "JOD:1.41468128 KES:0.00772139 KHR:0.00024535 KMF:0.00228304 KWD:3.25810956 KYD:1.2066274 " +
            "LAK:4.458e-05 LBP:1.12e-05 LKR:0.003014 LRD:0.00590005 LSL:0.06077167 LYD:0.15672873 MAD:0.10279049 " +
            "MGA:0.00021919 MKD:0.01830475 MOP:0.12416551 MRU:0.02484365 MUR:0.02149786 MVR:0.06487769 " +
            "MWK:0.00057855 MXN:0.05357053 MYR:0.24766514 MZN:0.01570023 NAD:0.06077167 NIO:0.02725568 " +
            "NPR:0.00664809 PEN:0.29111541 PGK:0.22016048 PHP:0.01591787 PKR:0.00362634 PYG:0.00016046 " +
            "RWF:0.0006807 SBD:0.12567703 SCR:0.07258386 SDG:0.00167443 SHP:1.34558677 SOS:0.00175505 " +
            "SRD:0.02667435 SSP:0.00017458 STN:0.04542922 SYP:0.00822004 SZL:0.06077001 TND:0.33394674 " +
            "TOP:0.41985958 TTD:0.14816808 TWD:0.03043133 TZS:0.00037957 UGX:0.00024809 UYU:0.02492877 " +
            "VES:0.00111955 VUV:0.00828385 WST:0.36379137 XAF:0.00171402 XCD:0.37079816 XOF:0.00171402 " +
            "XPF:0.00942196 YER:0.00424698 ZMW:0.04886172"
}
