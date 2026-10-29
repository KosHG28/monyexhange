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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

@Composable
fun MainScreen(
    state: MainUiState,
    onKey: (Key) -> Unit,
    onSelectCard: (String) -> Unit,
    onEditCard: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    onSelectLine: (Line) -> Unit,
    onChip: (BigDecimal) -> Unit,
    onOpenCurrencies: () -> Unit,
    onRefreshRates: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val totals = state.totals
    val unit = when (state.active) {
        Line.SPEND -> wallet.spend.symbol
        Line.HOME -> wallet.home.symbol
        Line.CARD -> state.selectedCard?.currency?.symbol.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        TopBar(wallet.spend, wallet.home, onOpenCurrencies, onOpenCheat)
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
                    cards = wallet.cards,
                    quote = state.quote,
                    selectedId = wallet.selected?.id,
                    spend = wallet.spend,
                    home = wallet.home,
                    amount = totals.spend,
                    onSelect = onSelectCard,
                    onEdit = onEditCard,
                    onAdd = onAddCard,
                )
                ResultBlock(state, unit, onEditCard, onAddCard, onRefreshRates)
            }
        }
        MetroLine(
            active = state.active,
            spend = wallet.spend,
            spendValue = valueText(state, Line.SPEND),
            home = wallet.home,
            homeValue = valueText(state, Line.HOME),
            card = state.selectedCard,
            cardValue = valueText(state, Line.CARD),
            showCard = state.showCardLine,
            onSelect = onSelectLine,
        )
        Spacer(Modifier.height(6.dp))
        Chips(wallet.spend, onChip)
        Spacer(Modifier.height(8.dp))
        Keypad(onKey = onKey, onCamera = onOpenCamera)
        Spacer(Modifier.height(8.dp))
    }
}

private fun valueText(state: MainUiState, line: Line): String =
    if (state.active == line) state.activeText else state.totals.of(line)?.let(Numbers::money) ?: "—"

/** Шапка: «🇦🇪 AED → ₽» (выбор валют) и шпаргалка. */
@Composable
private fun TopBar(spend: Currency, home: Currency, onOpenCurrencies: () -> Unit, onOpenCheat: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val a11y = stringResource(R.string.currency_button_a11y, spend.nameRu, home.nameRu)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(50))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onOpenCurrencies)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                    heading()
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spend.flag, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${spend.code} → ${home.symbol}",
                style = type.title.copy(fontSize = 17.sp),
                color = colors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text("▾", style = type.body, color = colors.muted)
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.app_name),
            style = type.label,
            color = colors.muted,
            maxLines = 1,
            modifier = Modifier.padding(end = 4.dp),
        )
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

@Composable
private fun ResultBlock(
    state: MainUiState,
    unit: String,
    onEditCard: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    onRefreshRates: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val home = state.totals.home
    val rate = state.homePerUnit
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (state.expression.isCompound) state.expression.display() + " " + unit else "",
            style = type.mono.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val heroText = home?.let { "${Numbers.money(it)} ${wallet.home.symbol}" } ?: "—"
        val heroSize = when {
            heroText.length > 15 -> 26.sp
            heroText.length > 12 -> 32.sp
            else -> 44.sp
        }
        val resultA11y = stringResource(R.string.result_a11y, heroText)
        Text(
            text = heroText,
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
        val spendText = "${Numbers.money(state.totals.spend)} ${wallet.spend.symbol}"
        val card = state.selectedCard
        Text(
            text = when {
                rate == null -> stringResource(R.string.result_no_rate)
                card != null && state.selectedQuote != null -> stringResource(R.string.result_by_card, spendText, card.name)
                else -> stringResource(R.string.result_by_official, spendText)
            },
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        if (rate != null) {
            val u = Numbers.unitFor(rate)
            val unitLabel = if (u.compareTo(BigDecimal.ONE) == 0) "1 ${wallet.spend.symbol}" else "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}"
            val rateText = Numbers.rate(rate.multiply(u, MC), 2)
            val pillA11y = stringResource(R.string.rate_pill_a11y, unitLabel, rateText, wallet.home.symbol)
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentSoft)
                    .clickable(role = Role.Button) { if (card != null) onEditCard(card) else onAddCard() }
                    .clearAndSetSemantics {
                        contentDescription = pillA11y
                        role = Role.Button
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rate_pill, unitLabel, rateText, wallet.home.symbol), style = type.monoStrong.copy(fontSize = 14.sp), color = colors.accent)
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            }
        }
        OfficialLine(state, onRefreshRates)
    }
}

/** «ЦБ на 29 окт: 1 AED = 22,19 ₽» и обновление курсов. */
@Composable
private fun OfficialLine(state: MainUiState, onRefresh: () -> Unit) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val official = state.official
    val perUnit = state.quote.official
    val text = when {
        state.ratesLoading -> stringResource(R.string.official_loading)
        official == null -> stringResource(R.string.official_missing)
        state.ratesFailed -> stringResource(R.string.official_failed, RuDates.short(official.date))
        perUnit != null -> {
            val u = Numbers.unitFor(perUnit)
            stringResource(
                R.string.official_line,
                RuDates.short(official.date),
                "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}",
                Numbers.rate(perUnit.multiply(u, MC), 2),
                wallet.home.symbol,
            )
        }
        else -> stringResource(R.string.official_missing)
    }
    val refresh = stringResource(R.string.official_refresh)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = refresh, enabled = !state.ratesLoading, onClick = onRefresh)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AppTheme.type.caption.copy(fontSize = 12.5.sp), color = colors.muted, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("↻", style = AppTheme.type.bodyStrong, color = if (state.ratesLoading) colors.line else colors.accent)
    }
}

/** Быстрые суммы в валюте трат: семь в один ряд; у дирхама 22 — проезд в метро. */
@Composable
private fun Chips(spend: Currency, onChip: (BigDecimal) -> Unit) {
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
                    .border(1.dp, colors.line, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onChip(amount) }
                    .clearAndSetSemantics {
                        contentDescription = a11y
                        role = Role.Button
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = type.monoStrong.copy(fontSize = 13.sp), color = colors.ink, maxLines = 1)
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
