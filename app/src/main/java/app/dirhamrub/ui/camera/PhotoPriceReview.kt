package app.dirhamrub.ui.camera

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.cards.AmountField
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

@Composable
internal fun PhotoPriceReview(
    label: PriceLabel,
    spend: Currency,
    onUse: (BigDecimal, Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var currency by remember(label) { mutableStateOf(label.sourceCurrency ?: spend) }
    var value by remember(label) { mutableStateOf(Numbers.exactInput(label.value)) }
    val parsed = Numbers.parse(value)?.takeIf { it.signum() > 0 }
    val title = stringResource(R.string.camera_review_price)
    val pick = stringResource(R.string.editor_pick_currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AmountField(title, value, { value = it }, title, selectable = true, trailing = {
                    CurrencyDropdown(currency, (listOf(currency, spend) + Currency.POPULAR).distinct(),
                        { currency = it }, pick, Currency.entries)
                })
                if (currency != spend) Text(stringResource(R.string.camera_review_currency, currency.code))
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onUse(it, currency) } }, enabled = parsed != null) {
                Text(stringResource(R.string.price_sheet_to_calc))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = AppTheme.colors.surface,
    )
}
