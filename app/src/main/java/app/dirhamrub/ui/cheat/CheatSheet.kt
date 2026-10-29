package app.dirhamrub.ui.cheat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.short
import app.dirhamrub.ui.theme.AppTheme

/** Шпаргалка: AED → рубли по обеим картам рядом и сумма в валюте выбранной карты. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(rates: Map<Card, CardRate>, selected: Card, onDismiss: () -> Unit) {
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
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.cheat_title),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.cheat_subtitle), style = type.body, color = colors.muted)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.paper)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val kzt = rates.getValue(Card.KZT)
                val cny = rates.getValue(Card.CNY)
                val selectedRate = rates.getValue(selected)
                val header = type.label
                CheatRow(
                    cells = listOf(
                        stringResource(R.string.cheat_col_aed),
                        stringResource(R.string.cheat_col_rub, Card.KZT.short()),
                        stringResource(R.string.cheat_col_rub, Card.CNY.short()),
                        selected.symbol,
                    ),
                    style = header,
                    highlight = if (selected == Card.KZT) 1 else 2,
                    isHeader = true,
                )
                HorizontalDivider(color = colors.line)
                for (aed in MainViewModel.CHEAT) {
                    CheatRow(
                        cells = listOf(
                            aed.toPlainString(),
                            Numbers.cheat(MainViewModel.rub(aed, kzt)),
                            Numbers.cheat(MainViewModel.rub(aed, cny)),
                            Numbers.cheat(aed.multiply(selectedRate.cardPerAed, MC)),
                        ),
                        style = type.mono,
                        highlight = if (selected == Card.KZT) 1 else 2,
                        isHeader = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheatRow(cells: List<String>, style: TextStyle, highlight: Int, isHeader: Boolean) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isHeader) 8.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEachIndexed { i, cell ->
            Text(
                text = cell,
                style = if (i == highlight && !isHeader) AppTheme.type.monoStrong else style,
                color = when {
                    isHeader -> colors.muted
                    i == highlight -> colors.accent
                    else -> colors.ink
                },
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(if (i == 0) 0.8f else 1f),
            )
        }
    }
}
