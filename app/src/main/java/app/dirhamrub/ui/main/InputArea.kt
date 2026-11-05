package app.dirhamrub.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.components.TypingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal

/**
 * Поле ввода: переключатель валюты «AED | ₽ | ¥» (во что вводим), выражение «3 × 22 + 15 =» и сумма.
 * Если валюта одна (траты и итог совпадают, карты в третьей валюте нет) — просто подпись.
 */
@Composable
internal fun InputField(state: MainUiState, lines: List<Line>, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val line = state.active
    val currency = line.currency(state) ?: state.wallet.spend
    val value = valueText(state, line)
    val a11y = stringResource(R.string.input_a11y, lineName(state, line), value, currency.symbol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 5.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lines.size > 1) {
            InputSwitch(state, lines, onSelectLine)
        } else {
            Text(lineName(state, line), style = type.label, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 11.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = a11y },
            horizontalAlignment = Alignment.End,
        ) {
            // «=» — строка выражения опускается в результат и гаснет.
            val reduceMotion = LocalReduceMotion.current
            // Пока строка гаснет, в ней остаётся последнее выражение (держатель без состояния — без лишних перерисовок).
            val lastExpression = remember { arrayOf("") }
            if (state.expression.isCompound) lastExpression[0] = state.expression.display() + " ="
            AnimatedVisibility(
                visible = state.expression.isCompound,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(160)) + expandVertically(tween(160)),
                exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(240)) { it } + fadeOut(tween(200)) + shrinkVertically(tween(240)),
            ) {
                Text(
                    text = lastExpression[0],
                    style = type.body.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // Новая цифра «впрыгивает», стёртая гаснет, результат «=» поднимается снизу.
                TypingText(
                    text = value,
                    style = if (value.length > 11) type.number.copy(fontSize = 24.sp, lineHeight = 30.sp) else type.number,
                    color = colors.ink,
                )
                Text(
                    text = currency.symbol,
                    style = type.body.copy(fontSize = 16.sp),
                    color = colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** Переключатель «AED | ₽ | ¥»: выбранная валюта подсвечена, у каждой кнопки 48 dp для пальца. */
@Composable
private fun InputSwitch(state: MainUiState, lines: List<Line>, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val label = stringResource(R.string.input_switch)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg)
            .semantics { contentDescription = label },
    ) {
        for (line in lines) {
            val selected = line == state.active
            val symbol = line.currency(state)?.symbol.orEmpty()
            val name = stringResource(R.string.input_here_a11y, lineName(state, line))
            Box(
                modifier = Modifier
                    .size(width = if (symbol.length > 2) 56.dp else 48.dp, height = 48.dp)
                    .selectable(selected = selected, role = Role.Tab) { onSelectLine(line) }
                    .semantics { contentDescription = name }
                    .padding(4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.accentSoft else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    style = type.label.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) colors.accent else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** «Спишется с карты  3 438,58 ₸» — тап переносит ввод в эту валюту. */
@Composable
internal fun SecondaryLine(state: MainUiState, line: Line, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val currency = line.currency(state) ?: return
    val label = if (line == Line.CARD) stringResource(R.string.debit_card) else currency.shortRu
    val value = valueText(state, line)
    val a11y = stringResource(R.string.line_a11y, label, value, currency.symbol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.input_here)) { onSelectLine(line) }
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.body.copy(fontSize = 13.5.sp), color = colors.muted, maxLines = 1, modifier = Modifier.weight(1f))
        val valueStyle = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        // Вводимая строка меняется на каждом нажатии — здесь цифры перекатываются.
        RollingText(value, style = valueStyle, color = colors.ink)
        Text(" " + currency.symbol, style = valueStyle, color = colors.ink, maxLines = 1)
    }
}

/** Быстрые суммы в валюте трат: семь в один ряд; у дирхама 22 — проезд в метро. */
@Composable
internal fun Chips(spend: Currency, onChip: (BigDecimal) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (amount in MainViewModel.chips(spend)) {
            val metro = spend == Currency.AED && amount.toInt() == 22
            val label = compact(amount)
            val a11y = if (metro) stringResource(R.string.chip_metro_a11y) else stringResource(R.string.chip_a11y, amount.toPlainString(), spend.code)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface)
                    .clickable(role = Role.Button) { onChip(amount) }
                    .clearAndSetSemantics {
                        contentDescription = a11y
                        role = Role.Button
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = type.label.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1)
                if (metro) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "M",
                        style = type.caption.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.accent)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** «500», «2K», «50K», «1M». */
private fun compact(amount: BigDecimal): String = when {
    amount >= BigDecimal(1_000_000) -> Numbers.rate(amount.divide(BigDecimal(1_000_000)), 1) + "M"
    amount >= BigDecimal(1000) -> Numbers.rate(amount.divide(BigDecimal(1000)), 1) + "K"
    else -> amount.toPlainString()
}
