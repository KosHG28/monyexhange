package app.dirhamrub.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.theme.AppTheme

/** Где тратите (10 валют сеткой) и в какой валюте считать итог (все валюты). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySheet(
    spend: Currency,
    home: Currency,
    onSpend: (Currency) -> Unit,
    onHome: (Currency) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.currency_sheet_spend),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            for (row in Currency.SPEND.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    for (c in row) {
                        val selected = c == spend
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.accentSoft else colors.surface)
                                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(14.dp))
                                .clickable(role = Role.RadioButton) {
                                    onSpend(c)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.flag, fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${c.code} · ${c.symbol}",
                                    style = type.monoStrong.copy(fontSize = 14.sp),
                                    color = if (selected) colors.accent else colors.ink,
                                )
                                Text(c.nameRu, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.currency_sheet_home),
                style = type.title.copy(fontSize = 16.sp),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            CurrencyChips(options = Currency.entries, selected = home, onSelect = onHome)
        }
    }
}
