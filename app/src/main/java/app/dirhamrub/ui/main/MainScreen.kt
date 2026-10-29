package app.dirhamrub.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

@Composable
fun MainScreen(
    state: MainUiState,
    onKey: (Key) -> Unit,
    onSelectCard: (Card) -> Unit,
    onSelectLine: (Line) -> Unit,
    onChip: (BigDecimal) -> Unit,
    onOpenRate: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = AppTheme.colors
    val amounts = state.amounts
    val comparison = state.comparison
    val unit = when (state.active) {
        Line.AED -> "AED"
        Line.RUB -> "₽"
        Line.CARD -> state.selected.symbol
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        TopBar(onOpenCheat)
        // Верх экрана на низких телефонах прокручивается, низ (линия, чипы, клавиатура) всегда на месте.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                BankCardsRow(
                    rates = state.rates,
                    comparison = comparison,
                    selected = state.selected,
                    onSelect = onSelectCard,
                )
                ResultBlock(
                    expression = if (state.expression.isCompound) state.expression.display() + " " + unit else null,
                    rub = Numbers.money(amounts.rub),
                    subtitle = stringResource(
                        if (state.selected == Card.KZT) R.string.result_by_kzt else R.string.result_by_cny,
                        Numbers.money(amounts.aed),
                    ),
                    rate = Numbers.rate(state.rate.rubPerAed, 2),
                    onOpenRate = onOpenRate,
                )
            }
        }
        MetroLine(
            active = state.active,
            aed = if (state.active == Line.AED) state.activeText else Numbers.money(amounts.aed),
            rub = if (state.active == Line.RUB) state.activeText else Numbers.money(amounts.rub),
            card = state.selected,
            cardValue = if (state.active == Line.CARD) state.activeText else Numbers.money(amounts.card),
            onSelect = onSelectLine,
        )
        Spacer(Modifier.height(6.dp))
        Chips(onChip)
        Spacer(Modifier.height(8.dp))
        Keypad(onKey = onKey, onCamera = onOpenCamera)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopBar(onOpenCheat: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = AppTheme.type.title,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onOpenCheat)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_list), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cheat_button), style = AppTheme.type.bodyStrong, color = colors.accent)
        }
    }
}

@Composable
private fun ResultBlock(expression: String?, rub: String, subtitle: String, rate: String, onOpenRate: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val resultA11y = stringResource(R.string.result_a11y, rub)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = expression ?: "",
            style = type.mono.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val heroSize = when {
            rub.length > 13 -> 28.sp
            rub.length > 10 -> 34.sp
            else -> 44.sp
        }
        Text(
            text = "$rub ₽",
            style = type.hero.copy(fontSize = heroSize, lineHeight = heroSize * 1.18f),
            color = colors.ink,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = resultA11y
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Text(
            text = subtitle,
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        val pillA11y = stringResource(R.string.rate_pill_a11y, rate)
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(50))
                .background(colors.accentSoft)
                .clickable(role = Role.Button, onClick = onOpenRate)
                .clearAndSetSemantics {
                    contentDescription = pillA11y
                    role = Role.Button
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.rate_pill, rate), style = type.monoStrong.copy(fontSize = 14.sp), color = colors.accent)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** Быстрые суммы в дирхамах: все семь в один ряд одинаковой ширины; 22 — проезд в метро. */
@Composable
private fun Chips(onChip: (BigDecimal) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (amount in MainViewModel.CHIPS) {
            val metro = amount.toInt() == 22
            val a11y = if (metro) stringResource(R.string.chip_metro_a11y) else stringResource(R.string.chip_a11y, amount.toPlainString())
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onChip(amount) }
                    .clearAndSetSemantics {
                        contentDescription = a11y
                        role = Role.Button
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(amount.toPlainString(), style = type.monoStrong.copy(fontSize = 14.sp), color = colors.ink, maxLines = 1)
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
