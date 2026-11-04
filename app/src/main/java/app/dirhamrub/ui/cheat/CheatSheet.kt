package app.dirhamrub.ui.cheat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.dirhamrub.ui.theme.CardPalette
import java.math.BigDecimal

/** Колонка шпаргалки: карта (с цветом её оформления) или ЦБ. */
private class CheatColumn(val title: String, val dot: Color?, val rate: BigDecimal?, val selected: Boolean, val best: Boolean)

/**
 * Шпаргалка: суммы в валюте трат → итоговая валюта по каждой карте и по курсу ЦБ.
 * Колонки подписаны цветной точкой карты; выбранная — жирным, самая дешёвая — зелёным;
 * текущая сумма с главного экрана — отдельной подсвеченной строкой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(
    quote: WalletQuote,
    spend: Currency,
    home: Currency,
    selectedId: String?,
    amount: BigDecimal,
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
            val best = quote.best?.takeIf { quote.quotes.size > 1 }
            // Колонки: карты (по курсу каждой) и ЦБ. Много карт — таблица прокручивается вбок.
            val columns = quote.quotes.map {
                CheatColumn(it.card.name, CardPalette.style(it.card).start, it.homePerUnit, it.card.id == selectedId, it == best)
            } + CheatColumn(stringResource(R.string.cheat_official), null, quote.official, selected = false, best = false)
            val amounts = MainViewModel.cheat(spend)
            val current = amount.takeIf { it.signum() > 0 }
            val rows = (amounts + listOfNotNull(current?.takeIf { a -> amounts.none { it.compareTo(a) == 0 } })).sorted()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.paper)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                HeaderRow(spend.code, columns)
                HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 12.dp))
                for (value in rows) {
                    ValueRow(value, columns, isCurrent = current != null && value.compareTo(current) == 0)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(first: String, columns: List<CheatColumn>) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(first, style = type.label, color = colors.muted, modifier = Modifier.width(FIRST_WIDTH))
        for (c in columns) {
            // Точка цвета карты — в начале названия, а не у края колонки.
            Text(
                text = buildAnnotatedString {
                    if (c.dot != null) withStyle(SpanStyle(color = c.dot)) { append("● ") }
                    append(c.title)
                },
                style = if (c.selected) type.label.copy(fontWeight = FontWeight.SemiBold) else type.label,
                color = if (c.selected) colors.ink else colors.muted,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(COLUMN_WIDTH)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ValueRow(value: BigDecimal, columns: List<CheatColumn>, isCurrent: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .background(if (isCurrent) colors.accentSoft else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = Numbers.cheat(value),
            style = if (isCurrent) type.monoStrong else type.mono,
            color = if (isCurrent) colors.accent else colors.ink,
            maxLines = 1,
            modifier = Modifier.width(FIRST_WIDTH),
        )
        for (c in columns) {
            Text(
                text = c.rate?.let { Numbers.cheat(value.multiply(it, MC)) } ?: "—",
                style = if (c.selected || isCurrent) type.monoStrong else type.mono,
                color = when {
                    c.best -> colors.good
                    c.rate == null -> colors.muted
                    else -> colors.ink
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .width(COLUMN_WIDTH)
                    .padding(start = 8.dp),
            )
        }
    }
}

private val FIRST_WIDTH = 72.dp
private val COLUMN_WIDTH = 96.dp
