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
import app.dirhamrub.domain.Converter
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Pricing
import app.dirhamrub.domain.Source
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.short
import app.dirhamrub.ui.theme.AppTheme

/** Шпаргалка: цены (AED или ₸) → рубли по обеим картам рядом и сумма в валюте выбранной карты. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(rates: Map<Card, CardRate>, source: Source, selected: Card, onDismiss: () -> Unit) {
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
                // Колонка валюты карты не нужна, если карта в той же валюте, что и цены.
                val showCard = !(source == Source.KZT && selected == Card.KZT)
                val highlight = if (selected == Card.KZT) 1 else 2
                CheatRow(
                    cells = buildList {
                        add(source.code)
                        add(stringResource(R.string.cheat_col_rub, Card.KZT.short()))
                        add(stringResource(R.string.cheat_col_rub, Card.CNY.short()))
                        if (showCard) add(selected.symbol)
                    },
                    style = type.label,
                    highlight = highlight,
                    isHeader = true,
                )
                HorizontalDivider(color = colors.line)
                for (amount in MainViewModel.cheat(source)) {
                    CheatRow(
                        cells = buildList {
                            add(Numbers.moneyWhole(amount))
                            add(Numbers.cheat(Converter.rub(amount, Card.KZT, source, rates)))
                            add(Numbers.cheat(Converter.rub(amount, Card.CNY, source, rates)))
                            if (showCard) add(Numbers.cheat(amount.multiply(Pricing.cardPerUnit(selected, source, rates), MC)))
                        },
                        style = type.mono,
                        highlight = highlight,
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
