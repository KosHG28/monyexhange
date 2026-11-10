package app.dirhamrub.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.exchange.homePerGive
import app.dirhamrub.ui.guide.countryName
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import java.math.BigDecimal

/* Главный экран — один: калькулятор. Всё остальное — в двух шторках: «Карты» (касание строки карты)
   и «Ещё» (кнопка ☰ в шапке). У каждой функции — один вход. */

/** «22,45 ₽/AED» — курс карты в итоговой валюте за единицу (или 100) валюты трат. */
@Composable
private fun rateText(homePerUnit: BigDecimal, spend: Currency, home: Currency): String {
    val unit = Numbers.unitFor(homePerUnit)
    return stringResource(
        R.string.card_rate_short,
        Numbers.rateShort(homePerUnit.multiply(unit, MC)),
        home.symbol,
        if (unit.compareTo(BigDecimal.ONE) == 0) spend.symbol else "${Numbers.moneyWhole(unit)} ${spend.symbol}",
    )
}

/** «22,45 ₽/AED · +2,2 % к ЦБ» (или «· курс ЦБ», «нет курса»). */
@Composable
private fun quoteLine(q: CardQuote?, spend: Currency, home: Currency): String {
    if (q == null) return stringResource(R.string.card_no_rate)
    val vs = q.vsOfficial?.takeIf { q.basis != RateBasis.OFFICIAL }
    val rate = rateText(q.homePerUnit, spend, home)
    return if (vs != null) rate + " · " + stringResource(R.string.picker_vs, signedPercent(vs)) else rate + " · " + basisShort(q)
}

/** Мини-карта: градиент оформления карты. */
@Composable
private fun Swatch(card: BankCard) {
    val style = CardPalette.style(card)
    Box(
        Modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.linearGradient(listOf(style.start, style.end))),
    )
}

/**
 * Выбранная карта одной строкой на калькуляторе: «▮ Карта в юанях ▾ / 22,45 ₽/AED · +2,2 % к ЦБ».
 * Касание — шторка «Карты» (все карты с суммами). Карт нет — «Добавить карту».
 */
@Composable
internal fun CardPicker(state: MainUiState, onOpen: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val card = state.selectedCard
    if (card == null) {
        AddCardHint(onAdd, modifier.padding(horizontal = 16.dp))
        return
    }
    val best = state.quote.best?.takeIf { state.quote.quotes.size > 1 }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.picker_open), onClick = onOpen)
                .padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatch(card)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    card.name + (if (best?.card?.id == card.id) " ★" else "") + "  ▾",
                    style = type.bodyStrong,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(quoteLine(state.selectedQuote, state.wallet.spend, state.wallet.home), style = type.caption, color = colors.muted, maxLines = 1)
            }
        }
    }
}

/**
 * Шторка «Карты»: все карты, которыми можно заплатить, с суммой за текущую покупку — от дешёвой к дорогой,
 * лучшая со звездой и зелёной суммой, выбранная обведена. Касание — выбрать (шторка закрывается),
 * удержание — изменить. Ниже — «+ Карта или наличные» и курс ЦБ с обновлением.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CardsSheet(
    state: MainUiState,
    onSelect: (String) -> Unit,
    onEdit: (BankCard) -> Unit,
    onAdd: () -> Unit,
    onRefreshRates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val amount = state.totals.spend
    val quotes = state.quote
    val best = quotes.best?.takeIf { quotes.quotes.size > 1 }
    val cards = wallet.usable.sortedBy { quotes.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
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
                stringResource(R.string.cards_page_title, Numbers.money(amount), wallet.spend.code),
                style = type.body,
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
            )
            if (cards.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface),
                ) {
                    cards.forEachIndexed { i, card ->
                        val q = quotes.of(card.id)
                        val selected = card.id == wallet.selected?.id
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .then(if (selected) Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(16.dp)) else Modifier)
                                .combinedClickable(
                                    onClick = { onSelect(card.id) },
                                    onLongClick = { onEdit(card) },
                                    onLongClickLabel = stringResource(R.string.card_edit_a11y),
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Swatch(card)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    card.name + if (best?.card?.id == card.id) " ★" else "",
                                    style = type.bodyStrong,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(quoteLine(q, wallet.spend, wallet.home), style = type.caption, color = colors.muted, maxLines = 1)
                            }
                            val sum = q?.let { Numbers.moneyWhole(amount.multiply(it.homePerUnit, MC)) + " " + wallet.home.symbol } ?: "—"
                            Text(
                                (if (q?.basis == RateBasis.ESTIMATE) "≈ " else "") + sum,
                                style = type.number.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                                color = if (best?.card?.id == card.id) colors.good else colors.ink,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.cards_sheet_hint),
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            Tile("＋", stringResource(R.string.cards_page_add), null, onClick = onAdd)
            Spacer(Modifier.height(10.dp))
            OfficialLine(state, onRefreshRates, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * Шторка «Ещё» (☰ в шапке) — всё, что не калькулятор: чем платить в стране (если о ней есть сведения),
 * обменники, шпаргалка, настройки. Больше нигде эти разделы не открываются.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    state: MainUiState,
    guide: CountryGuide?,
    onOpenGuide: () -> Unit,
    onOpenExchange: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val spend = wallet.spend
    val home = wallet.home
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
            if (guide != null) {
                val up = guide.networks[CardNetwork.UNIONPAY]?.acceptance
                val mir = guide.networks[CardNetwork.MIR]?.acceptance
                Tile(
                    "💳",
                    stringResource(R.string.menu_guide, countryName(guide.iso)),
                    stringResource(R.string.trip_guide_sub, mark(up), mark(mir), RuDates.short(guide.updated)),
                    onClick = onOpenGuide,
                )
            }
            if (spend != home) {
                // Лучший из записанных обменников — по тому, во что вам обошлась валюта, которую несёте.
                val best = state.exchangers.filter { it.local == spend }.mapNotNull { ex ->
                    homePerGive(ex.give, home, wallet.cards, state.reference)?.let(ex::homePerLocal)?.let { ex to it }
                }.minByOrNull { it.second }
                val sub = best?.let { (ex, cost) ->
                    stringResource(
                        R.string.trip_exchange_best,
                        ex.name.ifBlank { stringResource(R.string.exchange_unnamed) },
                        spend.code,
                        Numbers.rateShort(cost),
                        home.symbol,
                    )
                } ?: stringResource(R.string.trip_exchange_none)
                Tile("💱", stringResource(R.string.trip_exchange), sub, onClick = onOpenExchange)
            }
            Tile("🧾", stringResource(R.string.cheat_title), stringResource(R.string.cards_page_cheat_sub), onClick = onOpenCheat)
            Tile("⚙", stringResource(R.string.settings_title), stringResource(R.string.menu_settings_sub), onClick = onOpenSettings)
        }
    }
}

private fun mark(a: Acceptance?): String = when (a) {
    Acceptance.YES -> "✓"
    Acceptance.PARTIAL -> "≈"
    Acceptance.NO -> "✗"
    null -> "?"
}

@Composable
private fun Tile(icon: String, title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.bodyStrong, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = type.caption, color = colors.muted)
        }
        Text("›", style = type.title, color = colors.muted)
    }
}
