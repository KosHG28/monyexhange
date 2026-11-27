package app.dirhamrub.ui.cards

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.data.ReceiptImages
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.*
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal data class BankReview(
    val uri: Uri,
    val reading: ScreenshotReading,
    val candidates: List<BankImportCandidate>,
    val cardCurrency: Currency,
)

internal data class StagedReceiptImage(val uri: Uri, val receipt: ScannedReceipt, val currency: Currency, val date: LocalDate) {
    fun matches(card: BankCard): Boolean {
        if (card.currency != currency) return false
        return when (val r = receipt) {
            is ScannedReceipt.Purchase -> card.purchaseFor(r.spend)?.let {
                it.paid.compareTo(r.paid) == 0 && it.price.compareTo(r.price) == 0 && it.date == date
            } == true
            is ScannedReceipt.Transfer -> card.transfer?.let {
                it.sentCurrency == r.sentCurrency && it.sent.compareTo(r.sent) == 0 && it.got.compareTo(r.got) == 0 && it.date == date
            } == true
        }
    }
}

@Composable
internal fun BankReviewDialog(
    review: BankReview,
    rates: OfficialRates?,
    allowPurchase: Boolean,
    onDismiss: () -> Unit,
    onApply: (ScannedReceipt, LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    var index by remember(review) { mutableIntStateOf(0) }
    val candidate = review.candidates[index]
    var operation by remember(candidate) { mutableStateOf(candidate.operation) }
    val options = remember(review.cardCurrency) { Currency.entries.filter { it != review.cardCurrency } }
    var currency by remember(candidate) { mutableStateOf(candidate.counterpart?.currency ?: options.first()) }
    var counterpart by remember(candidate) { mutableStateOf(candidate.counterpart?.amount?.let(Numbers::exactInput).orEmpty()) }
    var onCard by remember(candidate) { mutableStateOf(candidate.onCard?.amount?.let(Numbers::exactInput).orEmpty()) }
    var dateText by remember(candidate) { mutableStateOf((candidate.date ?: LocalDate.now()).toString()) }
    val paid = Numbers.parse(counterpart)?.takeIf { it.signum() > 0 }
    val received = Numbers.parse(onCard)?.takeIf { it.signum() > 0 }
    val date = remember(dateText) { runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() }
    val accepted = (operation == BankOperation.TRANSFER || operation == BankOperation.PURCHASE && allowPurchase) && !candidate.operation.blocked &&
        paid != null && received != null && date != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_review_title)) },
        text = {
            Column(Modifier.heightIn(max = 475.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.candidates.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { index-- }, enabled = index > 0) { Text(stringResource(R.string.scan_previous)) }
                        Text(stringResource(R.string.scan_position, index + 1, review.candidates.size))
                        TextButton(onClick = { index++ }, enabled = index < review.candidates.lastIndex) { Text(stringResource(R.string.scan_next)) }
                    }
                }
                ScreenshotFragment(review, candidate.sourceLines)
                if (candidate.operation.blocked) {
                    val message = if (candidate.operation == BankOperation.REFUND) R.string.scan_refund else R.string.scan_unsuccessful
                    Text(stringResource(message), color = colors.muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (allowPurchase) FilterChip(operation == BankOperation.PURCHASE, { operation = BankOperation.PURCHASE },
                            { Text(stringResource(R.string.scan_type_purchase)) })
                        FilterChip(operation == BankOperation.TRANSFER, { operation = BankOperation.TRANSFER },
                            { Text(stringResource(R.string.scan_type_transfer)) })
                    }
                    val transfer = operation == BankOperation.TRANSFER
                    val a = stringResource(if (transfer) R.string.receipt_sent else R.string.scan_price)
                    val b = stringResource(if (transfer) R.string.receipt_got else R.string.receipt_paid)
                    val currencyLabel = stringResource(R.string.editor_pick_currency)
                    AmountField(a, counterpart, { counterpart = it }, a, selectable = true, trailing = {
                        CurrencyDropdown(currency, (listOf(currency) + Currency.FREQUENT).distinct().filter { it in options },
                            { currency = it }, currencyLabel, options)
                    })
                    AmountField(b, onCard, { onCard = it }, b, trailing = { FieldSuffix(review.cardCurrency.code) })
                    if (paid != null && received != null) {
                        ReceiptTypo(paid, received, rates?.cross(review.cardCurrency, currency))
                    }
                    if (candidate.verifiedTotal) Text(stringResource(R.string.scan_fee_included), color = colors.muted)
                    OutlinedTextField(dateText, { dateText = it.take(10) }, label = { Text(stringResource(R.string.scan_date)) },
                        singleLine = true, isError = date == null, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            if (!candidate.operation.blocked) {
                TextButton(
                    enabled = accepted,
                    onClick = {
                        if (paid != null && received != null && date != null) {
                            val result = if (operation == BankOperation.TRANSFER) ScannedReceipt.Transfer(paid, currency, received)
                            else ScannedReceipt.Purchase(currency, received, paid)
                            onApply(result, date)
                        }
                    },
                ) { Text(stringResource(R.string.scan_apply)) }
            }
        },
        containerColor = colors.surface,
    )
}

@Composable
private fun ScreenshotFragment(review: BankReview, range: IntRange) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(review, range) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(review, range) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val source = ReceiptImages.decode(context, review.uri, 760) ?: return@runCatching null
                val bounds = review.reading.bounds(range) ?: return@runCatching source.asImageBitmap()
                val top = ((bounds.top - 0.035f) * source.height).toInt().coerceIn(0, source.height - 1)
                val bottom = ((bounds.bottom + 0.035f) * source.height).toInt().coerceIn(top + 1, source.height)
                val part = android.graphics.Bitmap.createBitmap(source, 0, top, source.width, bottom - top)
                if (part !== source) source.recycle()
                part.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, stringResource(R.string.scan_fragment), modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp), contentScale = ContentScale.Fit)
    } ?: Text(range.mapNotNull(review.reading.lines::getOrNull).joinToString("\n"),
        color = AppTheme.colors.muted, maxLines = 5)
}
