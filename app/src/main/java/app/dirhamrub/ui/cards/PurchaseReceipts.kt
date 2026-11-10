package app.dirhamrub.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.ReceiptCheck
import app.dirhamrub.ui.main.signedPercent
import java.math.BigDecimal
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.components.ReceiptLine
import app.dirhamrub.ui.components.ReceiptSection
import app.dirhamrub.ui.theme.AppTheme
import java.time.LocalDate

/** Черновик чека покупки в редакторе: поля — как набрал пользователь. */
internal class PurchaseDraft(val key: Int, spend: Currency, paid: String, price: String, val date: LocalDate) {
    var spend by mutableStateOf(spend)
    var paid by mutableStateOf(paid)
    var price by mutableStateOf(price)
}

/**
 * Чеки покупок карты в разных валютах трат: валюта, «списали» и «за», курс по чеку;
 * ниже — «+ чек покупки» и подсказка [hint].
 */
@Composable
internal fun PurchaseReceipts(
    purchases: SnapshotStateList<PurchaseDraft>,
    /** Подсветить незаполненную сумму в чеках, где заполнена только одна из двух. */
    showErrors: Boolean,
    /** Курсы ЦБ — заметить опечатку в суммах чека. */
    official: OfficialRates?,
    cardCurrency: Currency,
    spend: Currency,
    pick: String,
    hint: String?,
    onAdd: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    ReceiptSection(stringResource(R.string.receipt_purchases))
    for (d in purchases) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrencyDropdown(d.spend, (listOf(spend, d.spend) + Currency.POPULAR).distinct(), { d.spend = it }, pick, chip = true)
            Spacer(Modifier.width(8.dp))
            Text(
                d.spend.nameRu,
                style = type.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val remove = stringResource(R.string.receipt_remove)
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = remove,
                tint = colors.muted,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { purchases.remove(d) }
                    .padding(10.dp),
            )
        }
        ReceiptLine(stringResource(R.string.receipt_paid), d.paid, { d.paid = it }, stringResource(R.string.receipt_paid), error = missing(showErrors, d.paid, d.price)) {
            CurrencyMark(cardCurrency.symbol)
        }
        ReceiptLine(stringResource(R.string.receipt_for), d.price, { d.price = it }, stringResource(R.string.receipt_for), error = missing(showErrors, d.price, d.paid)) {
            CurrencyMark(d.spend.symbol)
        }
        val paid = Numbers.parse(d.paid)?.takeIf { it.signum() > 0 }
        val price = Numbers.parse(d.price)?.takeIf { it.signum() > 0 }
        if (paid != null && price != null) {
            ReceiptRate(Numbers.transferRate(paid.divide(price, MC), cardCurrency.symbol, d.spend.symbol))
            ReceiptTypo(paid, price, official?.cross(d.spend, cardCurrency))
        }
        Spacer(Modifier.height(6.dp))
    }
    TextButton(onClick = onAdd) { Text(stringResource(R.string.receipt_add), style = type.bodyStrong, color = colors.accent) }
    hint?.let { Hint(it) }
}

/** Сумма в поле есть и она больше нуля. */
private fun filled(text: String): Boolean = Numbers.parse(text)?.let { it.signum() > 0 } == true

/** В паре сумм чека заполнена ровно одна: такой чек не сохранится. */
internal fun halfFilled(a: String, b: String): Boolean = filled(a) != filled(b)

/** Пояснение под пустым полем [value], если вторая сумма пары [other] заполнена и пользователь уже нажал «Сохранить». */
@Composable
internal fun missing(show: Boolean, value: String, other: String): String? =
    if (show && !filled(value) && filled(other)) stringResource(R.string.receipt_missing) else null

/**
 * «⚠ Курс по чеку на +900 % от ЦБ — проверьте суммы»: [paid] за [got] при курсе ЦБ [officialPaidPerGot].
 * Сохранять не мешает — чек может быть и правдой (комиссия за мелкую сумму), но чаще это лишний ноль.
 */
@Composable
internal fun ReceiptTypo(paid: BigDecimal, got: BigDecimal, officialPaidPerGot: BigDecimal?) {
    val deviation = ReceiptCheck.suspicious(paid, got, officialPaidPerGot) ?: return
    Text(
        stringResource(if (deviation.signum() > 0) R.string.receipt_typo_high else R.string.receipt_typo_low, signedPercent(deviation)),
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Medium),
        color = AppTheme.colors.warn,
        modifier = Modifier
            .padding(top = 2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
