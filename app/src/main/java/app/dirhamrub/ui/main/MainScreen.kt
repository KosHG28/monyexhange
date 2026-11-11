package app.dirhamrub.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.components.TypingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal

/** Подсказка «вы в другой стране»: [iso] — страна сотовой сети, [currency] — её валюта трат. */
data class CountryOffer(val iso: String, val currency: Currency)

/**
 * Главный и единственный экран — «Пересчёт», как в любом конвертере валют: два поля (цена на ценнике
 * и сколько это стоит вам), строка курса между ними, плитки способов оплаты и клавиатура.
 * Отсюда два перехода: клавиша «Камера» и ссылка «Мои деньги ›».
 */
@Composable
fun MainScreen(
    state: MainUiState,
    countryOffer: CountryOffer?,
    tileOffer: Boolean,
    onTileOffer: (accept: Boolean) -> Unit,
    onKey: (Key) -> Unit,
    /** Касание плитки: выбрать способ (касание лучшей — снова считать по лучшей). */
    onSelectCard: (String) -> Unit,
    onEditCard: (BankCard) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    /** Касание поля — считать от него. */
    onSelectLine: (Line) -> Unit,
    /** Касание названия валюты: выбор валюты трат или (`home = true`) итоговой. */
    onOpenCurrencies: (home: Boolean) -> Unit,
    onCountry: (accept: Boolean) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenCamera: () -> Unit,
    /** Центр клавиши «Камера» на экране — из неё раскрывается камера. */
    onCameraPlaced: (Offset) -> Unit = {},
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val quote = state.quote
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Верх на низких телефонах и при крупном шрифте прокручивается, клавиатура всегда на месте.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when {
                countryOffer != null -> CountryBanner(countryOffer, onCountry)
                tileOffer -> TileBanner(onTileOffer)
            }
            Spacer(Modifier.height(10.dp))
            AmountField(state, Line.SPEND, onSelectLine) { onOpenCurrencies(false) }
            // Валюта трат совпадает с итоговой — второе поле и курс не нужны.
            if (wallet.home != wallet.spend) {
                RateLine(state)
                AmountField(state, Line.HOME, onSelectLine) { onOpenCurrencies(true) }
            }
            PayHeader(onOpenMoney)
            val cards = remember(wallet, quote) {
                wallet.usable.sortedBy { quote.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
            }
            if (cards.isEmpty()) {
                NoCards(onAddCard)
            } else {
                PayTiles(state, cards, onSelectCard, onEditCard)
            }
            if (state.homePerUnit == null) {
                // Считать не по чему — понятный блок с одной кнопкой вместо «—».
                Spacer(Modifier.height(8.dp))
                EmptyRate(state, onRefreshRates, onAddReceipt, onAddCard)
            } else {
                DebitLine(state)
                Basis(state, onAddReceipt, onEditCard)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Keypad(onKey = onKey, onCamera = onOpenCamera, onCameraPlaced = onCameraPlaced)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Поле суммы: слева «🇦🇪 Дирхамы ▾» (касание — выбор валюты) и подпись, справа сумма.
 * Активное поле (от него считаем) обведено; в нём — набираемое число и выражение «22 + 15,5 =».
 */
@Composable
private fun AmountField(state: MainUiState, line: Line, onSelectLine: (Line) -> Unit, onPickCurrency: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val currency = if (line == Line.HOME) state.wallet.home else state.wallet.spend
    val active = state.active == line
    val homeActive = state.active == Line.HOME
    val sub = stringResource(
        when {
            line == Line.SPEND && homeActive -> R.string.field_spend_enough
            line == Line.SPEND -> R.string.field_spend
            homeActive -> R.string.field_home_have
            else -> R.string.field_home
        },
    )
    val total = state.totals.of(line)
    val value = if (active) state.activeText else total?.let(Numbers::money) ?: "—"
    val approx = !active && total != null && state.selectedQuote?.basis?.let { it.approximate || it == RateBasis.OFFICIAL } != false
    val a11y = stringResource(R.string.field_a11y, currency.shortRu, sub, (if (approx) "≈ " else "") + value, currency.symbol)
    val activate = stringResource(R.string.field_activate)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(colors.surface)
            .then(if (active) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = activate) { onSelectLine(line) }
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            CurrencyButton(currency, onPickCurrency)
            Text(sub, style = type.caption, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    if (!active) liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.End,
        ) {
            if (active) ExpressionLine(state)
            val style = when {
                value.length > 12 -> type.number.copy(fontSize = 24.sp, lineHeight = 30.sp)
                value.length > 9 -> type.number.copy(fontSize = 30.sp, lineHeight = 36.sp)
                else -> type.number.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Light)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (approx) Text("≈ ", style = style, color = colors.muted, maxLines = 1)
                // Набираемое число «впрыгивает» по цифре; посчитанное — перекатывается.
                if (active) TypingText(text = value, style = style, color = colors.ink) else RollingText(value, style = style, color = colors.ink)
            }
        }
    }
}

/** «22 + 15,5 + 129,5 =» над суммой активного поля; после «=» гаснет. */
@Composable
private fun ExpressionLine(state: MainUiState) {
    val reduceMotion = LocalReduceMotion.current
    // Пока строка гаснет, в ней остаётся последнее выражение.
    val last = remember { arrayOf("") }
    if (state.expression.isCompound) last[0] = state.expression.display() + " ="
    AnimatedVisibility(
        visible = state.expression.isCompound,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(160)) + expandVertically(tween(160)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(200)) + shrinkVertically(tween(240)),
    ) {
        Text(
            last[0],
            style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = AppTheme.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «🇦🇪 Дирхамы ▾» — 48 dp для пальца. */
@Composable
private fun CurrencyButton(currency: Currency, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val a11y = stringResource(R.string.field_currency_a11y, currency.nameRu)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(currency.flag, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(currency.shortRu, style = AppTheme.type.bodyStrong.copy(fontSize = 16.sp), color = colors.ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text("▾", style = AppTheme.type.caption, color = colors.muted)
    }
}

/** «1 AED = 22,19 ₽ · Наличные»; оценка — «≈», чистый ЦБ — «по курсу ЦБ». */
@Composable
private fun RateLine(state: MainUiState) {
    val rate = state.homePerUnit
    val text = if (rate == null) {
        stringResource(R.string.rate_line_none)
    } else {
        val q = state.selectedQuote
        val (unit, value) = rateSides(rate, state.wallet.spend, state.wallet.home)
        val official = q == null || q.basis == RateBasis.OFFICIAL
        val pair = stringResource(if (official || q!!.basis.approximate) R.string.rate_line_approx else R.string.rate_line, unit, value)
        val by = when {
            q == null -> stringResource(R.string.rate_by_cbr)
            q.basis == RateBasis.OFFICIAL -> stringResource(R.string.rate_card_by_cbr, q.card.name)
            else -> q.card.name
        }
        stringResource(R.string.rate_line_by, pair, by)
    }
    Text(
        text,
        style = AppTheme.type.caption.copy(fontSize = 13.sp),
        color = AppTheme.colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** «1 AED» и «22,19 ₽»; у мелких валют — «100 KZT» и «18,54 ₽». */
internal fun rateSides(homePerUnit: BigDecimal, spend: Currency, home: Currency): Pair<String, String> {
    val unit = Numbers.unitFor(homePerUnit)
    return "${Numbers.moneyWhole(unit)} ${spend.code}" to "${Numbers.rateShort(homePerUnit.multiply(unit, MC))} ${home.symbol}"
}

/** «Плачу:» слева и «Мои деньги ›» справа — единственный вход в карты, страну и настройки. */
@Composable
private fun PayHeader(onOpenMoney: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.pay_label),
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        val a11y = stringResource(R.string.money_link_a11y)
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onOpenMoney)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.money_link), style = type.bodyStrong.copy(fontSize = 14.sp), color = colors.accent)
        }
    }
}

/**
 * Плитки способов оплаты, от дешёвого к дорогому: лучшая зелёная с «✓», выбранная обведена.
 * До трёх — поровну по ширине, больше — ряд листается.
 */
@Composable
private fun PayTiles(state: MainUiState, cards: List<BankCard>, onSelect: (String) -> Unit, onEdit: (BankCard) -> Unit) {
    val bestId = state.quote.best?.card?.id?.takeIf { state.quote.quotes.size > 1 }
    val selectedId = state.selectedCard?.id
    if (cards.size <= 3) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (card in cards) {
                PayTile(state, card, card.id == bestId, card.id == selectedId, onSelect, onEdit, Modifier.weight(1f))
            }
        }
    } else {
        LazyRow(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                PayTile(state, card, card.id == bestId, card.id == selectedId, onSelect, onEdit, Modifier.width(112.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayTile(
    state: MainUiState,
    card: BankCard,
    best: Boolean,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (BankCard) -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val sum = tileSum(state, state.quote.of(card.id))
    val shape = RoundedCornerShape(14.dp)
    val a11y = stringResource(if (best) R.string.tile_best_a11y else R.string.tile_a11y, card.name, sum)
    val editLabel = stringResource(R.string.card_edit_a11y)
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (best) colors.goodSoft else colors.surface)
            .then(if (selected) Modifier.border(1.5.dp, if (best) colors.good else colors.accent, shape) else Modifier)
            .combinedClickable(
                onClick = { onSelect(card.id) },
                onLongClick = { onEdit(card) },
                onLongClickLabel = editLabel,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            card.name + if (best) " ✓" else "",
            style = type.caption,
            color = if (best) colors.good else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            sum,
            style = type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = if (best) colors.good else colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Сумма на плитке: покупка в итоговой валюте, а при вводе в итоговой — сколько выйдет в валюте трат. */
private fun tileSum(state: MainUiState, q: CardQuote?): String {
    if (q == null) return "—"
    val approx = if (q.basis.approximate || q.basis == RateBasis.OFFICIAL) "≈ " else ""
    val wallet = state.wallet
    return if (state.active == Line.HOME) {
        val home = state.totals.home ?: return "—"
        val spend = if (q.homePerUnit.signum() == 0) BigDecimal.ZERO else home.divide(q.homePerUnit, MC)
        approx + Numbers.money(spend) + " " + wallet.spend.symbol
    } else {
        approx + Numbers.moneyWhole(state.totals.spend.multiply(q.homePerUnit, MC)) + " " + wallet.home.symbol
    }
}

/**
 * Под плитками: «Спишут с карты 304,76 ¥ · на 43 ₽ дороже, чем «Наличные»». Выбран лучший способ —
 * только «Спишут с карты…» (если у карты своя валюта), иначе ничего.
 */
@Composable
private fun DebitLine(state: MainUiState) {
    val colors = AppTheme.colors
    val card = state.selectedCard
    val debit = state.totals.card?.takeIf { state.showCardLine && card != null }?.let { amount ->
        stringResource(R.string.debit_line, Numbers.money(amount) + " " + card!!.currency.symbol)
    }
    val c = state.quote.compare(card?.id)?.takeIf { !it.selectedIsBest && Numbers.percent(it.percent) != "0" }
    val dearer = c?.let {
        val amount = state.totals.spend
        val approx = if (it.isExact) "" else "≈ "
        val by = if (amount.signum() > 0) {
            approx + Numbers.saving(it.difference(amount)) + " " + state.wallet.home.symbol
        } else {
            approx + Numbers.percent(it.percent) + " %"
        }
        stringResource(if (debit == null) R.string.dearer_first else R.string.dearer, by, it.other.card.name)
    }
    val text = listOfNotNull(debit, dearer).joinToString(" · ")
    if (text.isEmpty()) return
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 13.5.sp),
        color = colors.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

/** Карт нет: «Чем вы платите?» и кнопка — пока считаем по курсу ЦБ. */
@Composable
private fun NoCards(onAdd: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.nocards_title), style = type.bodyStrong, color = colors.ink)
            Text(stringResource(R.string.nocards_text), style = type.caption, color = colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.nocards_add), style = type.bodyStrong.copy(fontSize = 14.sp), color = colors.onAccent)
        }
    }
}
