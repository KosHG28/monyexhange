package app.dirhamrub.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.Currency
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
                        CurrencyTile(c, selected = c == spend, wide = true, modifier = Modifier.weight(1f)) {
                            onSpend(c)
                            onDismiss()
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
            Text(stringResource(R.string.currency_sheet_home_hint), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(8.dp))
            // Итоговых валют больше — сетка по четыре (по три при крупном шрифте или узком экране),
            // выбор выглядит так же, как у валют трат.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) >= WIDE_GRID) 4 else 3
                Column {
                    for (row in Currency.entries.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            for (c in row) {
                                CurrencyTile(c, selected = c == home, wide = false, modifier = Modifier.weight(1f)) { onHome(c) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** С какой ширины (в dp при обычном шрифте) итоговые валюты помещаются по четыре в ряд. */
private val WIDE_GRID = 330.dp

/**
 * Плитка валюты: флаг, код (и знак, если он не совпадает с кодом), в широкой — название.
 * Выбранная — красной рамкой, широкая — ещё и галочкой в углу.
 */
@Composable
internal fun CurrencyTile(c: Currency, selected: Boolean, wide: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val shape = RoundedCornerShape(14.dp)
    val label = if (c.symbol == c.code) c.code else "${c.code} · ${c.symbol}"
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (wide) 56.dp else 48.dp)
                .clip(shape)
                .background(if (selected) colors.accentSoft else colors.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                // В узкой плитке только код — полное название для TalkBack.
                .then(if (wide) Modifier else Modifier.semantics { contentDescription = c.nameRu })
                .padding(horizontal = if (wide) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.flag, fontSize = if (wide) 22.sp else 18.sp)
            Spacer(Modifier.width(if (wide) 10.dp else 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (wide) label else c.code,
                    style = type.bodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = if (selected) colors.accent else colors.ink,
                    maxLines = 1,
                )
                if (wide) Text(c.nameRu, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected && wide) {
            Text(
                "✓",
                style = type.caption.copy(fontSize = 10.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clearAndSetSemantics { },
            )
        }
    }
}
