package app.dirhamrub.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.main.basisLabel
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

/** Шторка по тапу на метку: цена, сумма по каждой карте и по ЦБ, «В калькулятор» и «+ к сумме». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceSheet(
    amount: BigDecimal,
    spend: Currency,
    home: Currency,
    isOld: Boolean,
    quote: WalletQuote,
    selectedId: String?,
    onToCalculator: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${Numbers.money(amount)} ${spend.symbol}",
                    style = type.hero.copy(fontSize = 34.sp, lineHeight = 40.sp),
                    color = if (isOld) colors.muted else colors.ink,
                    textDecoration = if (isOld) TextDecoration.LineThrough else null,
                )
                if (isOld) {
                    Text(
                        stringResource(R.string.price_sheet_old),
                        style = type.label,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val ordered = quote.quotes.sortedByDescending { it.card.id == selectedId }
            for (q in ordered) {
                val isSelected = q.card.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.surface else colors.bg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.card.name, style = type.label, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Numbers.money(amount.multiply(q.cardPerUnit, MC))} ${q.card.currency.symbol} · ${basisLabel(q)}",
                            style = type.mono.copy(fontSize = 12.sp),
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${Numbers.money(amount.multiply(q.homePerUnit, MC))} ${home.symbol}",
                        style = if (isSelected) type.number else type.monoStrong.copy(fontSize = 18.sp),
                        color = if (isSelected) colors.ink else colors.muted,
                    )
                }
            }
            quote.official?.let { o ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (quote.viaUnionPay) R.string.cheat_unionpay else R.string.cheat_official),
                        style = type.label,
                        color = colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${Numbers.money(amount.multiply(o, MC))} ${home.symbol}", style = type.mono, color = colors.muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onToCalculator,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.price_sheet_to_calc), style = type.bodyStrong, color = colors.accent) }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.price_sheet_add), style = type.bodyStrong) }
            }
        }
    }
}
