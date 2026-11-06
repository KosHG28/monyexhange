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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
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
        ReceiptLine(stringResource(R.string.receipt_paid), d.paid, { d.paid = it }, stringResource(R.string.receipt_paid)) {
            CurrencyMark(cardCurrency.symbol)
        }
        ReceiptLine(stringResource(R.string.receipt_for), d.price, { d.price = it }, stringResource(R.string.receipt_for)) {
            CurrencyMark(d.spend.symbol)
        }
        val paid = Numbers.parse(d.paid)?.takeIf { it.signum() > 0 }
        val price = Numbers.parse(d.price)?.takeIf { it.signum() > 0 }
        if (paid != null && price != null) {
            ReceiptRate(Numbers.transferRate(paid.divide(price, MC), cardCurrency.symbol, d.spend.symbol))
        }
        Spacer(Modifier.height(6.dp))
    }
    TextButton(onClick = onAdd) { Text(stringResource(R.string.receipt_add), style = type.bodyStrong, color = colors.accent) }
    hint?.let { Hint(it) }
}
