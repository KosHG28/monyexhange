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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import app.dirhamrub.domain.wallet.RateBasis
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
    // Ввод — в одной из валют, крупно — сумма в итоговой (или в валюте трат, если вводят итоговую),
    // оставшаяся строка (обычно «Спишется с карты») — мелко под полем ввода.
    val lines = buildList {
        add(Line.SPEND)
        if (wallet.home != wallet.spend) add(Line.HOME)
        if (state.showCardLine) add(Line.CARD)
    }
    val heroLine = if (state.active == Line.HOME) Line.SPEND else Line.HOME
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(wallet.spend, wallet.home, onOpenCurrencies, onOpenCheat)
        // Верх экрана на низких телефонах прокручивается, низ (ввод, чипы, клавиатура) всегда на месте.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                BankCardsRow(
                    cards = wallet.cards,
                    quote = state.quote,
                    selectedId = wallet.selected?.id,
                    spend = wallet.spend,
                    home = wallet.home,
                    amount = state.totals.spend,
                    onSelect = onSelectCard,
                    onEdit = onEditCard,
                    onAdd = onAddCard,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ResultBlock(state, heroLine, onEditCard, onAddCard, onRefreshRates)
                Spacer(Modifier.height(12.dp))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            InputField(state, lines, onSelectLine)
            for (line in lines) {
                if (line != state.active && line != heroLine) SecondaryLine(state, line, onSelectLine)
            }
            Spacer(Modifier.height(10.dp))
            Chips(wallet.spend, onChip)
            Spacer(Modifier.height(10.dp))
            Keypad(onKey = onKey, onCamera = onOpenCamera)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun valueText(state: MainUiState, line: Line): String =
    if (state.active == line) state.activeText else state.totals.of(line)?.let(Numbers::money) ?: "—"

private fun Line.currency(state: MainUiState): Currency? = when (this) {
    Line.SPEND -> state.wallet.spend
    Line.HOME -> state.wallet.home
    Line.CARD -> state.selectedCard?.currency
}

/** Шапка: «🇦🇪 AED → ₽ ▾» (выбор валют) и шпаргалка. */
@Composable
private fun TopBar(spend: Currency, home: Currency, onOpenCurrencies: () -> Unit, onOpenCheat: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val a11y = stringResource(R.string.currency_button_a11y, spend.nameRu, home.nameRu)
        val text = buildAnnotatedString {
            append(spend.code)
            withStyle(SpanStyle(color = colors.muted, fontWeight = FontWeight.Normal)) { append("  →  ") }
            append(home.symbol)
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surface)
                .clickable(role = Role.Button, onClick = onOpenCurrencies)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                    heading()
                }
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spend.flag, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(text, style = type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("▾", style = type.caption, color = colors.muted)
        }
        Spacer(Modifier.weight(1f))
        val cheat = stringResource(R.string.cheat_button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = cheat, onClick = onOpenCheat)
                .semantics { contentDescription = cheat },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_table), contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** Крупная сумма, чем посчитана, и одна строка курса со сверкой с ЦБ. */
@Composable
private fun ResultBlock(
    state: MainUiState,
    heroLine: Line,
    onEditCard: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    onRefreshRates: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val heroCurrency = heroLine.currency(state) ?: wallet.home
    val heroValue = state.totals.of(heroLine)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val number = heroValue?.let(Numbers::money) ?: "—"
        val heroPlain = "$number ${heroCurrency.symbol}"
        val heroSize = when {
            heroPlain.length > 15 -> 38.sp
            heroPlain.length > 11 -> 48.sp
            else -> 60.sp
        }
        val resultA11y = stringResource(R.string.result_a11y, heroPlain)
        Text(
            text = buildAnnotatedString {
                append(number)
                if (heroValue != null) {
                    append(" ")
                    withStyle(SpanStyle(color = colors.muted)) { append(heroCurrency.symbol) }
                }
            },
            style = type.hero.copy(fontSize = heroSize, lineHeight = heroSize * 1.12f),
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
        val inputCurrency = state.active.currency(state) ?: wallet.spend
        val inputText = "${state.totals.of(state.active)?.let(Numbers::money) ?: "—"} ${inputCurrency.symbol}"
        val card = state.selectedCard
        val q = state.selectedQuote
        Text(
            text = when {
                state.homePerUnit == null -> stringResource(R.string.result_no_rate)
                card != null && q != null -> stringResource(R.string.result_by_card, inputText, card.name, basisShort(q))
                else -> stringResource(R.string.result_by_official, inputText)
            },
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        RateLine(state, onEdit = { if (card != null) onEditCard(card) else onAddCard() }, onRefresh = onRefreshRates)
    }
}

/**
 * «1 ¥ = 12,75 ₽ · ЦБ 12,08 +5,5 % ↻»: курс выбранной карты, курс ЦБ на ту же единицу и наценка.
 * Тап по курсу — редактор карты, по ↻ — обновить курсы ЦБ.
 */
@Composable
private fun RateLine(state: MainUiState, onEdit: () -> Unit, onRefresh: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val rate = state.homePerUnit
    val official = state.quote.official
    val q = state.selectedQuote
    val byOfficial = q == null || q.basis == RateBasis.OFFICIAL
    val u = Numbers.unitFor(rate ?: official ?: BigDecimal.ONE)
    val unitLabel = if (u.compareTo(BigDecimal.ONE) == 0) "1 ${wallet.spend.symbol}" else "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}"
    val date = state.official?.let { RuDates.short(it.date) }
    val officialText = when {
        state.ratesLoading -> stringResource(R.string.official_loading_short)
        official == null -> stringResource(R.string.official_missing_short)
        byOfficial -> stringResource(R.string.official_on, date.orEmpty())
        else -> stringResource(R.string.official_short, Numbers.rate(official.multiply(u, MC), 2))
    }
    val vs = q?.vsOfficial?.takeIf { !byOfficial && !state.ratesLoading }
    val vsColor = vs?.let {
        when (markupOf(it)) {
            Markup.GOOD -> colors.good
            Markup.WARN -> colors.warn
            Markup.BAD -> colors.accent
        }
    }
    val rateText = rate?.let { stringResource(R.string.rate_pill, unitLabel, Numbers.rate(it.multiply(u, MC), 2), wallet.home.symbol) }
    val a11y = buildString {
        if (rateText != null) append(stringResource(R.string.rate_pill_a11y, rateText)).append(". ")
        append(officialText)
        if (vs != null) append(", ").append(stringResource(R.string.card_vs_official, signedPercent(vs)))
        if (state.ratesFailed && date != null) append(". ").append(stringResource(R.string.official_failed, date))
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, colors.line, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onEdit)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val small = type.body.copy(fontSize = 14.sp)
            if (rateText != null) {
                Text(rateText, style = small.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
                Text("  ·  ", style = small, color = colors.muted, maxLines = 1)
            }
            Text(officialText, style = small, color = colors.muted, maxLines = 1)
            if (vs != null && vsColor != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.percent_value, signedPercent(vs)),
                    style = small.copy(fontWeight = FontWeight.Medium),
                    color = vsColor,
                    maxLines = 1,
                )
            }
        }
        val refresh = stringResource(R.string.official_refresh)
        Box(
            modifier = Modifier
                .size(44.dp, 48.dp)
                .clickable(role = Role.Button, onClickLabel = refresh, enabled = !state.ratesLoading, onClick = onRefresh)
                .semantics { contentDescription = refresh },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "↻",
                style = type.bodyStrong,
                color = when {
                    state.ratesLoading -> colors.line
                    state.ratesFailed -> colors.warn
                    else -> colors.accent
                },
            )
        }
    }
}

/** Поле ввода: валюта (тап — вводить в другой), выражение «22 + 15,5 =» и сумма. */
@Composable
private fun InputField(state: MainUiState, lines: List<Line>, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val line = state.active
    val currency = line.currency(state) ?: state.wallet.spend
    val label = if (line == Line.CARD) state.selectedCard?.name.orEmpty() else currency.shortRu
    val value = valueText(state, line)
    val next = lines.getOrNull((lines.indexOf(line) + 1) % lines.size)?.takeIf { it != line }
    val a11y = stringResource(R.string.input_a11y, label, value, currency.symbol)
    val switchLabel = stringResource(R.string.input_switch)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(max = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (next != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = switchLabel) { onSelectLine(next) }
                    } else {
                        Modifier
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = a11y
                    if (next != null) role = Role.Button
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = type.label, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (next != null) Text(" ⇅", style = type.label, color = colors.accent, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (state.expression.isCompound) state.expression.display() + " =" else "",
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = if (value.length > 12) type.number.copy(fontSize = 24.sp, lineHeight = 30.sp) else type.number,
            color = colors.ink,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = currency.symbol,
            style = type.body.copy(fontSize = 18.sp),
            color = colors.muted,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** «Спишется с карты  3 438,58 ₸» — тап переносит ввод в эту валюту. */
@Composable
private fun SecondaryLine(state: MainUiState, line: Line, onSelectLine: (Line) -> Unit) {
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
        Text("$value ${currency.symbol}", style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
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
