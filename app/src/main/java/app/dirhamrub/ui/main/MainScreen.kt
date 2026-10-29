package app.dirhamrub.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.Comparison
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.ui.short
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.motion
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
                    selected = state.selected,
                    cheaper = comparison.cheaper,
                    savingsPercent = Numbers.percent(comparison.savingsPercent),
                    onSelect = onSelectCard,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                ResultBlock(
                    expression = if (state.expression.isCompound) state.expression.display() + " " + unit else null,
                    rub = Numbers.money(amounts.rub),
                    rate = Numbers.rate(state.rate.rubPerAed, 2),
                    onOpenRate = onOpenRate,
                )
                ComparisonBlock(comparison)
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
private fun ResultBlock(expression: String?, rub: String, rate: String, onOpenRate: () -> Unit) {
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

/** «Юани 8 507 ₽ · Тенге 8 808 ₽ · экономия 301 ₽» и две полоски пропорционально сумме. */
@Composable
private fun ComparisonBlock(comparison: Comparison) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val first = comparison.cheaper ?: Card.KZT
    val order = listOf(first, first.other)
    val rub = comparison.rubByCard
    val max = rub.values.maxOrNull()?.takeIf { it.signum() > 0 }

    val parts = order.map { card -> stringResource(R.string.compare_item, card.short(), Numbers.moneyWhole(rub.getValue(card))) }
    val savings = Numbers.moneyWhole(comparison.savingsRub)
    val text = buildString {
        append(parts.joinToString(" · "))
        if (comparison.cheaper != null && savings != "0") {
            append(" · ")
            append(stringResource(R.string.compare_savings, savings))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = type.label, color = colors.muted, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        for (card in order) {
            val fraction = if (max == null) 0f else rub.getValue(card).toFloat() / max.toFloat()
            val color = if (card == comparison.cheaper) colors.good else colors.muted
            ComparisonBar(card.symbol, fraction, color)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ComparisonBar(symbol: String, fraction: Float, color: Color) {
    val colors = AppTheme.colors
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), motion(300), label = "bar")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, style = AppTheme.type.caption, color = colors.muted, modifier = Modifier.width(16.dp))
        Box(
            Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.line),
        ) {
            if (animated > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun Chips(onChip: (BigDecimal) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.chips_label), style = type.label, color = colors.muted, modifier = Modifier.padding(end = 2.dp))
        for (amount in MainViewModel.CHIPS) {
            val label = if (amount.toInt() == 22) stringResource(R.string.chip_metro) else amount.toPlainString()
            val a11y = stringResource(R.string.chip_a11y, label)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onChip(amount) }
                    .clearAndSetSemantics {
                        contentDescription = a11y
                        role = Role.Button
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = type.monoStrong.copy(fontSize = 14.sp), color = colors.ink)
            }
        }
    }
}
