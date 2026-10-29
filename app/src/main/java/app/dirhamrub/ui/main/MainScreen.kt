package app.dirhamrub.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import app.dirhamrub.domain.Source
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

@Composable
fun MainScreen(
    state: MainUiState,
    onKey: (Key) -> Unit,
    onSelectCard: (Card) -> Unit,
    onSelectLine: (Line) -> Unit,
    onSelectSource: (Source) -> Unit,
    onChip: (BigDecimal) -> Unit,
    onOpenRate: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = AppTheme.colors
    val amounts = state.amounts
    val comparison = state.comparison
    val unit = when (state.active) {
        Line.SOURCE -> state.source.code
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
        TopBar(state.source, onSelectSource, onOpenCheat)
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
                    source = state.source,
                    comparison = comparison,
                    selected = state.selected,
                    onSelect = onSelectCard,
                )
                ResultBlock(
                    expression = if (state.expression.isCompound) state.expression.display() + " " + unit else null,
                    rub = Numbers.money(amounts.rub),
                    subtitle = stringResource(
                        if (state.selected == Card.KZT) R.string.result_by_kzt else R.string.result_by_cny,
                        Numbers.money(amounts.source),
                        state.source.code,
                    ),
                    rateLabel = unitLabel(state.source),
                    rate = Numbers.rate(state.rubPerUnit.multiply(MainViewModel.unitLabel(state.source)), 2),
                    onOpenRate = onOpenRate,
                )
            }
        }
        MetroLine(
            active = state.active,
            source = state.source,
            sourceValue = if (state.active == Line.SOURCE) state.activeText else Numbers.money(amounts.source),
            rub = if (state.active == Line.RUB) state.activeText else Numbers.money(amounts.rub),
            card = state.selected,
            cardValue = if (state.active == Line.CARD) state.activeText else Numbers.money(amounts.card),
            showCard = state.showCardLine,
            onSelect = onSelectLine,
        )
        Spacer(Modifier.height(6.dp))
        Chips(state.source, onChip)
        Spacer(Modifier.height(8.dp))
        Keypad(onKey = onKey, onCamera = onOpenCamera)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopBar(source: Source, onSelectSource: (Source) -> Unit, onOpenCheat: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (source == Source.KZT) R.string.title_kzt else R.string.app_name),
            style = AppTheme.type.title.copy(fontSize = 17.sp),
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        SourceSwitch(source, onSelectSource)
        val cheat = stringResource(R.string.cheat_button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = cheat, onClick = onOpenCheat)
                .semantics { contentDescription = cheat },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_list), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
    }
}

/** Переключатель валюты цен: дирхамы или тенге. */
@Composable
private fun SourceSwitch(source: Source, onSelect: (Source) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val name = stringResource(if (source == Source.KZT) R.string.source_kzt_name else R.string.source_aed_name)
    val a11y = stringResource(R.string.source_switch_a11y, name)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(50))
            .padding(3.dp)
            .selectableGroup()
            .semantics { contentDescription = a11y },
    ) {
        for (option in Source.entries) {
            val selected = option == source
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.code,
                    style = if (option == Source.KZT) type.title.copy(fontSize = 16.sp) else type.monoStrong.copy(fontSize = 13.sp),
                    color = if (selected) colors.onAccent else colors.muted,
                )
            }
        }
    }
}

/** «AED» или «100 ₸» — за сколько единиц валюты цен показан курс. */
@Composable
private fun unitLabel(source: Source): String =
    if (source == Source.KZT) "100 ₸" else source.code

@Composable
private fun ResultBlock(
    expression: String?,
    rub: String,
    subtitle: String,
    rateLabel: String,
    rate: String,
    onOpenRate: () -> Unit,
) {
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
        val pillA11y = stringResource(R.string.rate_pill_a11y, rateLabel, rate)
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
            Text(stringResource(R.string.rate_pill, rateLabel, rate), style = type.monoStrong.copy(fontSize = 14.sp), color = colors.accent)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** Быстрые суммы в дирхамах: все семь в один ряд одинаковой ширины; 22 — проезд в метро. */
@Composable
private fun Chips(source: Source, onChip: (BigDecimal) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (amount in MainViewModel.chips(source)) {
            val metro = source == Source.AED && amount.toInt() == 22
            val label = if (amount >= BigDecimal(1000)) Numbers.moneyWhole(amount.divide(BigDecimal(1000))) + "K" else amount.toPlainString()
            val a11y = if (metro) stringResource(R.string.chip_metro_a11y) else stringResource(R.string.chip_a11y, amount.toPlainString(), source.code)
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
                Text(label, style = type.monoStrong.copy(fontSize = 14.sp), color = colors.ink, maxLines = 1)
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
