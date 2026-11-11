package app.dirhamrub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.AppTheme

/**
 * Сетка валют-чипов с флагами, выбранная — красная. Все валюты видны сразу, без прокрутки вбок:
 * в ряду столько чипов, сколько помещается при текущем размере шрифта, ширина у всех одинаковая.
 */
@Composable
fun CurrencyChips(
    options: List<Currency>,
    selected: Currency,
    onSelect: (Currency) -> Unit,
    modifier: Modifier = Modifier,
    /** «Все валюты…» под сеткой — открыть полный выбор с поиском. */
    onMore: (() -> Unit)? = null,
    moreLabel: String = "",
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = ((maxWidth + CHIP_GAP) / (CHIP_MIN * fontScale + CHIP_GAP)).toInt().coerceIn(3, options.size.coerceAtLeast(3))
        Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            for (row in options.chunked(perRow)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                    for (c in row) CurrencyChip(c, c == selected, Modifier.weight(1f)) { onSelect(c) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (onMore != null) {
                val colors = AppTheme.colors
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, colors.line, RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClick = onMore)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(moreLabel, style = AppTheme.type.bodyStrong.copy(fontSize = 14.sp), color = colors.accent, maxLines = 1)
                }
            }
        }
    }
}

private val CHIP_GAP = 6.dp
private val CHIP_MIN = 62.dp

@Composable
fun CurrencyChip(currency: Currency, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = currency.nameRu
                this.selected = selected
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${currency.flag} ${currency.code}",
            style = AppTheme.type.monoStrong.copy(fontSize = 14.sp),
            color = if (selected) colors.onAccent else colors.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Компактный выбор валюты: «₽ ▾» (или чипом «🇦🇪 AED ▾», если [chip]), по тапу — меню со всеми валютами.
 */
@Composable
fun CurrencyDropdown(
    selected: Currency,
    options: List<Currency>,
    onSelect: (Currency) -> Unit,
    description: String,
    chip: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(if (chip) 50 else 20))
                .clickable(role = Role.DropdownList, onClickLabel = description) { open = true }
                .padding(horizontal = if (chip) 0.dp else 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (chip) {
                Text(
                    "${selected.flag} ${selected.code} ▾",
                    style = type.monoStrong.copy(fontSize = 14.sp),
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.bg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Text("${selected.mark} ▾", style = type.mono, color = colors.accent, maxLines = 1, softWrap = false)
            }
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
