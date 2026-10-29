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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Pricing
import app.dirhamrub.domain.Source
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.title
import java.math.BigDecimal

/** Шторка по тапу на метку: цена (AED или ₸), рубли по обеим картам, «В калькулятор» и «+ к сумме». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceSheet(
    amount: BigDecimal,
    source: Source,
    isOld: Boolean,
    rates: Map<Card, CardRate>,
    selected: Card,
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
                    text = "${Numbers.money(amount)} ${source.code}",
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
            for (card in listOf(selected, selected.other)) {
                val rub = amount.multiply(Pricing.rubPerUnit(card, source, rates), MC)
                val inCard = amount.multiply(Pricing.cardPerUnit(card, source, rates), MC)
                val isSelected = card == selected
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
                        Text(card.title(), style = type.label, color = colors.muted)
                        Text("${Numbers.money(inCard)} ${card.symbol}", style = type.mono.copy(fontSize = 13.sp), color = colors.muted)
                    }
                    Text(
                        "${Numbers.money(rub)} ₽",
                        style = if (isSelected) type.number else type.monoStrong.copy(fontSize = 18.sp),
                        color = if (isSelected) colors.ink else colors.muted,
                    )
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
