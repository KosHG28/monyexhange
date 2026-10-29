package app.dirhamrub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.AppTheme

/** Ряд валют-чипов с флагами (прокручивается), выбранная — красная. */
@Composable
fun CurrencyChips(
    options: List<Currency>,
    selected: Currency,
    onSelect: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in options) CurrencyChip(c, c == selected) { onSelect(c) }
    }
}

@Composable
fun CurrencyChip(currency: Currency, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = currency.nameRu
                this.selected = selected
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${currency.flag} ${currency.code}",
            style = AppTheme.type.monoStrong.copy(fontSize = 14.sp),
            color = if (selected) colors.onAccent else colors.ink,
        )
    }
}

/** Компактный выбор валюты: показывает «₽ ▾», по тапу — меню со всеми валютами. */
@Composable
fun CurrencyDropdown(
    selected: Currency,
    options: List<Currency>,
    onSelect: (Currency) -> Unit,
    description: String,
) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.DropdownList, onClickLabel = description) { open = true }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("${selected.symbol} ▾", style = AppTheme.type.mono, color = colors.accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in options) {
                DropdownMenuItem(
                    text = { Text("${c.flag} ${c.code} · ${c.nameRu}", style = AppTheme.type.body) },
                    onClick = {
                        open = false
                        onSelect(c)
                    },
                )
            }
        }
    }
}
