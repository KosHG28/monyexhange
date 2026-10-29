package app.dirhamrub.ui.cheat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

/** Шпаргалка: суммы в валюте трат → итоговая валюта по каждой карте и по курсу ЦБ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(quote: WalletQuote, spend: Currency, home: Currency, selectedId: String?, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.cheat_title),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.cheat_subtitle, spend.code, home.symbol), style = type.body, color = colors.muted)
            Spacer(Modifier.height(12.dp))
            // Колонки: сумма, карты (по курсу каждой), ЦБ. Много карт — таблица прокручивается вбок.
            val columns: List<Pair<String, BigDecimal?>> =
                quote.quotes.map { it.card.name to it.homePerUnit } + (stringResource(R.string.cheat_official) to quote.official)
            val highlight = quote.quotes.indexOfFirst { it.card.id == selectedId } + 1
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.paper)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CheatRow(listOf(spend.code) + columns.map { it.first }, highlight, isHeader = true)
                HorizontalDivider(color = colors.line)
                for (amount in MainViewModel.cheat(spend)) {
                    CheatRow(
                        listOf(Numbers.moneyWhole(amount)) + columns.map { (_, rate) ->
                            rate?.let { Numbers.cheat(amount.multiply(it, MC)) } ?: "—"
                        },
                        highlight,
                        isHeader = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheatRow(cells: List<String>, highlight: Int, isHeader: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(Modifier.padding(vertical = if (isHeader) 8.dp else 7.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                text = cell,
                style = when {
                    isHeader -> type.label
                    i == highlight -> type.monoStrong
                    else -> type.mono
                },
                color = when {
                    isHeader -> colors.muted
                    i == highlight -> colors.accent
                    else -> colors.ink
                },
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(if (i == 0) 72.dp else 88.dp)
                    .padding(start = if (i == 0) 0.dp else 8.dp),
            )
        }
    }
}
