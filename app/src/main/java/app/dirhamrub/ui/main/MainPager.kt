package app.dirhamrub.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.exchange.homePerGive
import app.dirhamrub.ui.guide.countryFlag
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Экраны главного: «Карты», «Счёт» (калькулятор), «Поездка». */
private const val PAGE_CARDS = 0
private const val PAGE_CALC = 1
private const val PAGE_TRIP = 2

/** Что нужно экранам «Карты» и «Поездка» помимо калькулятора. */
class PagerActions(
    val onSelectCard: (String) -> Unit,
    val onEditCard: (BankCard) -> Unit,
    val onAddCard: () -> Unit,
    val onRefreshRates: () -> Unit,
    val onOpenCheat: () -> Unit,
    val onOpenGuide: () -> Unit,
    val onOpenExchange: () -> Unit,
    val onOpenCamera: () -> Unit,
    val onOpenBoard: () -> Unit,
)

/**
 * Главный экран — три листаемых экрана с подписями сверху: «Карты · Счёт · Поездка».
 * Калькулятор посередине и открывается первым; «Назад» с соседнего экрана возвращает к нему.
 * [calculator] получает «открыть экран Карты» — для строки выбранной карты.
 */
@Composable
fun MainPager(
    state: MainUiState,
    guide: CountryGuide?,
    actions: PagerActions,
    /** С какого экрана начать: 0 — «Карты», 1 — «Счёт», 2 — «Поездка» (снимки CI). */
    startPage: Int = PAGE_CALC,
    calculator: @Composable (onOpenCards: () -> Unit) -> Unit,
) {
    val colors = AppTheme.colors
    val pager = rememberPagerState(initialPage = startPage.coerceIn(PAGE_CARDS, PAGE_TRIP)) { 3 }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    fun go(page: Int) {
        scope.launch { if (reduceMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    BackHandler(enabled = pager.currentPage != PAGE_CALC) { go(PAGE_CALC) }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PageTabs(pager.currentPage, ::go)
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            when (page) {
                PAGE_CARDS -> CardsPage(state, actions) { id ->
                    actions.onSelectCard(id)
                    go(PAGE_CALC)
                }
                PAGE_CALC -> calculator { go(PAGE_CARDS) }
                else -> TripPage(state, guide, actions)
            }
        }
    }
}

@Composable
private fun PageTabs(current: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    val titles = listOf(R.string.page_cards, R.string.page_calc, R.string.page_trip)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.Center,
    ) {
        titles.forEachIndexed { i, title ->
            val selected = i == current
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.surface else colors.bg)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(title),
                    style = AppTheme.type.bodyStrong.copy(fontSize = 14.sp),
                    color = if (selected) colors.ink else colors.muted,
                )
            }
        }
    }
}

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

/** «22,45 ₽/AED · +2,2 % к ЦБ» (или «· по ЦБ», «· нет курса»). */
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
 * Касание — экран «Карты» (там все карты с суммами). Карт нет — «Добавить карту».
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
 * Экран «Карты»: все карты, которыми можно заплатить, с суммой за текущую покупку — от дешёвой к дорогой,
 * лучшая со звездой, выбранная обведена; касание — выбрать (и назад к счёту), удержание — изменить.
 * Ниже — «+ Карта или наличные», курс ЦБ и шпаргалка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardsPage(state: MainUiState, actions: PagerActions, onPick: (String) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val amount = state.totals.spend
    val quotes = state.quote
    val best = quotes.best?.takeIf { quotes.quotes.size > 1 }
    val cards = wallet.usable.sortedBy { quotes.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            stringResource(R.string.cards_page_title, Numbers.money(amount), wallet.spend.code),
            style = type.body,
            color = colors.muted,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp).semantics { heading() },
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
                                onClick = { onPick(card.id) },
                                onLongClick = { actions.onEditCard(card) },
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
                            (if (q?.basis?.let { it == RateBasis.ESTIMATE } == true) "≈ " else "") + sum,
                            style = type.number.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                            color = if (best?.card?.id == card.id) colors.good else colors.ink,
                        )
                    }
                }
            }
        }
        Tile("＋", stringResource(R.string.cards_page_add), null, onClick = actions.onAddCard)
        Spacer(Modifier.height(14.dp))
        OfficialLine(state, actions.onRefreshRates, Modifier.align(Alignment.CenterHorizontally))
        Tile("🧾", stringResource(R.string.cheat_title), stringResource(R.string.cards_page_cheat_sub), onClick = actions.onOpenCheat)
    }
}

/**
 * Экран «Поездка»: всё «для страны» — чем платить (справочник), обменники (лучший), камера для ценников
 * и для табло обменника, шпаргалка.
 */
@Composable
private fun TripPage(state: MainUiState, guide: CountryGuide?, actions: PagerActions) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val spend = wallet.spend
    val home = wallet.home
    // Страна — из справочника, иначе единственная страна с этой валютой (у доллара и евро — просто валюта).
    val country = guide?.iso?.let(Countries::byIso) ?: Countries.all.filter { it.currency == spend }.singleOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (country?.let { countryFlag(it.iso) + " " + it.nameRu } ?: (spend.flag + " " + spend.nameRu)),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text("${spend.code} → ${home.code}", style = type.caption, color = colors.muted)
        }
        // Чем платить.
        if (guide != null) {
            val up = guide.networks[CardNetwork.UNIONPAY]?.acceptance
            val mir = guide.networks[CardNetwork.MIR]?.acceptance
            Tile(
                "💳",
                stringResource(R.string.trip_guide),
                stringResource(R.string.trip_guide_sub, mark(up), mark(mir), RuDates.short(guide.updated)),
                onClick = actions.onOpenGuide,
            )
        } else {
            Tile("💳", stringResource(R.string.trip_guide), stringResource(R.string.trip_guide_none), onClick = null)
        }
        // Обменники: лучший по вашей валюте.
        if (spend != home) {
            val best = state.exchangers.filter { it.local == spend }.mapNotNull { ex ->
                homePerGive(ex.give, home, wallet.cards, state.reference)?.let(ex::homePerLocal)?.let { ex to it }
            }.minByOrNull { it.second }
            val sub = best?.let { (ex, cost) ->
                stringResource(R.string.trip_exchange_best, ex.name.ifBlank { stringResource(R.string.exchange_unnamed) }, spend.code, Numbers.rateShort(cost), home.symbol)
            } ?: stringResource(R.string.trip_exchange_none)
            Tile("💱", stringResource(R.string.trip_exchange), sub, onClick = actions.onOpenExchange)
            Tile("🔍", stringResource(R.string.trip_board), stringResource(R.string.trip_board_sub), onClick = actions.onOpenBoard)
        }
        Tile("📷", stringResource(R.string.trip_camera), stringResource(R.string.trip_camera_sub), onClick = actions.onOpenCamera)
        Tile("🧾", stringResource(R.string.cheat_title), stringResource(R.string.cards_page_cheat_sub), onClick = actions.onOpenCheat)
    }
}

private fun mark(a: Acceptance?): String = when (a) {
    Acceptance.YES -> "✓"
    Acceptance.PARTIAL -> "≈"
    Acceptance.NO -> "✗"
    null -> "?"
}

@Composable
private fun Tile(icon: String, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.bodyStrong, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = type.caption, color = colors.muted)
        }
        if (onClick != null) Text("›", style = type.title, color = colors.muted)
    }
}
