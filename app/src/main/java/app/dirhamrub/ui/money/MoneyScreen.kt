package app.dirhamrub.ui.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.dirhamrub.ui.components.fadingEdges
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.components.CollapsingBar
import app.dirhamrub.ui.components.LargeTitle
import app.dirhamrub.ui.components.rateLabel
import app.dirhamrub.ui.components.rememberCollapsingTitle
import app.dirhamrub.ui.main.EmvChip
import app.dirhamrub.ui.main.MainUiState
import app.dirhamrub.ui.main.moved
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.banknoteSurface
import app.dirhamrub.ui.theme.banknoteTheme
import app.dirhamrub.ui.theme.cardMaterial
import app.dirhamrub.ui.theme.forMaterial
import app.dirhamrub.ui.theme.motion
import app.dirhamrub.ui.theme.rememberReflectionTilt
import app.dirhamrub.ui.theme.surfaceOutline

class MoneyActions(
    val onBack: () -> Unit,
    val onEditCard: (BankCard) -> Unit,
    val onAddCard: () -> Unit,
    val onAddCash: () -> Unit,
    val onReorder: (List<String>) -> Unit,
)

@Composable
fun MoneyScreen(state: MainUiState, actions: MoneyActions, reflectLight: Boolean) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val quote = state.quote
    val cards = remember(wallet.cards, wallet.manualPaymentOrder, quote) {
        if (wallet.manualPaymentOrder) wallet.cards else
            wallet.cards.sortedWith(compareBy(nullsLast()) { quote.of(it.id)?.homePerUnit })
    }
    val ids = remember(cards) { cards.map { it.id } }
    val tilt = rememberReflectionTilt(active = reflectLight && cards.isNotEmpty())
    val byId = remember(cards) { cards.associateBy { it.id } }
    val drag = remember(ids) { WalletDrag(ids) }
    val bestId = quote.best?.card?.id?.takeIf { quote.quotes.size > 1 }
    val up = stringResource(R.string.payment_move_up)
    val down = stringResource(R.string.payment_move_down)
    val title = stringResource(R.string.cards_title)
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val scroll = rememberLazyListState()
        val collapsing = rememberCollapsingTitle(scroll)
        CollapsingBar(title, actions.onBack, collapsing)
        val order = drag.order
        val held = drag.held
        val scale by animateFloatAsState(if (held == null) 1f else 1.02f, motion(170), label = "walletCardLift")
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            LazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxSize().fadingEdges(scroll, edge = 0.dp, bottomEdge = 24.dp)
                    .walletDragGesture(drag, scroll, ids.size > 1, actions.onReorder),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            ) {
                item { LargeTitle(title, collapsing) }
                if (order.isNotEmpty()) item { Spacer(Modifier.height(4.dp)) }
                itemsIndexed(order, key = { _, id -> id }) { index, id ->
                    val card = byId.getValue(id)
                    val accessible = Modifier.semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(up) { actions.onReorder(order.moved(index, index - 1)); true })
                            if (index < order.lastIndex) add(CustomAccessibilityAction(down) { actions.onReorder(order.moved(index, index + 1)); true })
                        }
                    }
                    Column {
                        if (held == id) {
                            Box(Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { }) {
                                WalletCard(state, card, quote.of(id), id == bestId, false, tilt, Modifier) {}
                            }
                        } else {
                            WalletCard(state, card, quote.of(id), id == bestId, false, tilt, accessible) { actions.onEditCard(card) }
                        }
                        Spacer(Modifier.height(if (index == order.lastIndex) 14.dp else 10.dp))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AddTile(R.drawable.ic_card, R.string.money_add_card, R.string.money_add_card_sub, Modifier.weight(1f), actions.onAddCard)
                        AddTile(R.drawable.ic_cash, R.string.money_add_cash, R.string.money_add_cash_sub, Modifier.weight(1f), actions.onAddCash)
                    }
                }
                if (ids.size > 1) item {
                    Text(stringResource(R.string.money_order_hint), style = AppTheme.type.caption, color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp))
                }
            }
            if (held != null) {
                val card = byId.getValue(held)
                WalletCard(state, card, quote.of(held), held == bestId, true, tilt,
                    Modifier.padding(horizontal = 14.dp).clearAndSetSemantics { }
                        .graphicsLayer {
                            translationY = drag.overlayY
                            scaleX = scale
                            scaleY = scale
                        },
                ) {}
            }
        }
    }
}

@Composable
private fun WalletCard(
    state: MainUiState,
    card: BankCard,
    quote: CardQuote?,
    best: Boolean,
    held: Boolean,
    tilt: State<Offset>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val type = AppTheme.type
    val cash = card.kind == CardKind.CASH
    val wallet = state.wallet
    val exact = quote?.basis == RateBasis.RECEIPTS || quote?.basis == RateBasis.MANUAL
    val date = card.transfer?.takeIf { it.isValid }?.date?.let(RuDates::short)?.let {
        stringResource(if (cash) R.string.money_exchanged else R.string.money_topped_up, it)
    }
    val status = when {
        quote != null -> null
        !card.canPay(wallet.spend) -> stringResource(R.string.money_only_in, card.currency.code)
        else -> stringResource(R.string.money_no_rate, wallet.spend.code)
    }
    val rate = quote?.let { rateLabel(it.homePerUnit, wallet.spend, wallet.home, approximate = !exact) }
    val corner = 16.dp
    val shape = RoundedCornerShape(corner)
    val style = CardPalette.style(card).forMaterial(card.material)
    val ink = if (cash) Color.White else style.ink
    val surface = if (cash) Modifier.banknoteSurface(banknoteTheme(card.currency), corner, tilt = tilt)
    else Modifier.cardMaterial(style, card.material, corner, tilt = tilt)
    Row(
        modifier.fillMaxWidth().heightIn(min = 84.dp)
            .clip(shape).then(surface)
            .then(if (held) Modifier.border(1.dp, ink.copy(alpha = 0.42f), shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.card_edit_a11y), onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!cash) {
            EmvChip(style.ink, Modifier.size(width = 20.dp, height = 14.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(card.name, style = type.bodyStrong, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = date ?: status ?: card.currency.code
            Text(subtitle, style = type.caption, color = ink.copy(alpha = 0.87f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (rate != null || date != null && status != null) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(0.93f), horizontalAlignment = Alignment.End) {
                if (rate != null) {
                    Text(rate, style = type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 18.sp), color = ink,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                } else if (status != null) {
                    Text(status, style = type.caption, color = ink.copy(alpha = 0.87f), textAlign = TextAlign.End)
                }
                if (best) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.pay_best),
                        style = type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = Color(0xFFB8F5D7),
                        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xCC153A2C))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTile(icon: Int, title: Int, subtitle: Int, modifier: Modifier, onAdd: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 104.dp).clip(shape).background(colors.surface).surfaceOutline(shape)
            .clickable(role = Role.Button, onClick = onAdd).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = colors.accent)
        Text(stringResource(title), style = type.bodyStrong, color = colors.ink)
        Text(stringResource(subtitle), style = type.caption, color = colors.muted)
    }
}
