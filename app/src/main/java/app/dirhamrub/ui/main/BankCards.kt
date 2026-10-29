package app.dirhamrub.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.motion
import java.math.BigDecimal

private val CARD_WIDTH = 164.dp
private val CARD_HEIGHT = 100.dp
private val CARD_SHAPE = RoundedCornerShape(18.dp)
/** Зазор между картой и обводкой выбранной: обводка рисуется в нём и не сдвигает соседей. */
private val RING_GAP = 4.dp

/**
 * Карты пользователя в ряд (прокручивается, видно две с половиной) и плитка «+».
 * На каждой — цена текущей покупки в итоговой валюте, курс и наценка к ЦБ; у лучшей — звезда.
 * Тап выбирает карту, тап по выбранной или долгое нажатие — редактор.
 */
@Composable
fun BankCardsRow(
    cards: List<BankCard>,
    quote: WalletQuote,
    selectedId: String?,
    spend: Currency,
    home: Currency,
    amount: BigDecimal,
    onSelect: (String) -> Unit,
    onEdit: (BankCard) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        AddCardHint(onAdd, modifier.padding(horizontal = 16.dp))
        return
    }
    val best = quote.best?.takeIf { quote.quotes.size > 1 }
    val bestPercent = quote.bestSavingsPercent?.let(Numbers::percent)?.takeIf { it != "0" }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp - RING_GAP),
        horizontalArrangement = Arrangement.spacedBy(10.dp - 2 * RING_GAP),
    ) {
        items(cards, key = { it.id }) { card ->
            val q = quote.of(card.id)
            BankCardView(
                card = card,
                quote = q,
                spend = spend,
                home = home,
                sum = q?.let { Numbers.moneyWhole(amount.multiply(it.homePerUnit, MC)) },
                selected = card.id == selectedId,
                best = if (best != null && q == best && bestPercent != null) stringResource(R.string.card_best_a11y, bestPercent) else null,
                onClick = { if (card.id == selectedId) onEdit(card) else onSelect(card.id) },
                onLongClick = { onEdit(card) },
            )
        }
        item(key = "add") { AddCardTile(onAdd) }
    }
}

/** Насколько курс карты дороже ЦБ: до 3 % — хорошо, до 7 % — заметно, дальше — дорого. */
enum class Markup { GOOD, WARN, BAD }

fun markupOf(vsOfficial: BigDecimal): Markup = when {
    vsOfficial < BigDecimal(3) -> Markup.GOOD
    vsOfficial < BigDecimal(7) -> Markup.WARN
    else -> Markup.BAD
}

/** «по чекам», «оценка», «курс ЦБ», «вручную». */
@Composable
fun basisShort(q: CardQuote): String = stringResource(
    when (q.basis) {
        RateBasis.RECEIPTS -> R.string.card_basis_receipts
        RateBasis.ESTIMATE -> R.string.card_basis_estimate
        RateBasis.OFFICIAL -> R.string.card_basis_official
        RateBasis.MANUAL -> R.string.card_basis_manual
    },
)

/** «22,45 ₽ за AED · по чекам · +4,7 % к ЦБ» — как посчитан курс карты. */
@Composable
fun basisLabel(q: CardQuote): String {
    val basis = basisShort(q)
    val vs = q.vsOfficial?.takeIf { q.basis != RateBasis.OFFICIAL }?.let { signedPercent(it) }
    return if (vs == null) basis else basis + " · " + stringResource(R.string.card_vs_official, vs)
}

/** «+4,7», «−1,2», «0». */
fun signedPercent(value: BigDecimal): String {
    val text = Numbers.percent(value.abs())
    return when {
        text == "0" -> "0"
        value.signum() > 0 -> "+$text"
        else -> "−$text"
    }
}

@Composable
private fun BankCardView(
    card: BankCard,
    quote: CardQuote?,
    spend: Currency,
    home: Currency,
    sum: String?,
    selected: Boolean,
    best: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val style = CardPalette.style(card.style)
    val dim by animateFloatAsState(if (selected) 1f else 0.82f, motion(), label = "cardDim")
    val ring by animateFloatAsState(if (selected) 1f else 0f, motion(), label = "cardRing")

    val rate = quote?.let {
        val unit = Numbers.unitFor(it.homePerUnit)
        stringResource(
            R.string.card_rate_short,
            Numbers.rate(it.homePerUnit.multiply(unit, MC), 2),
            home.symbol,
            if (unit.compareTo(BigDecimal.ONE) == 0) spend.symbol else "${Numbers.moneyWhole(unit)} ${spend.symbol}",
        )
    } ?: stringResource(R.string.card_no_rate)
    val vs = quote?.takeIf { it.basis != RateBasis.OFFICIAL }?.vsOfficial
    val sumText = sum?.let { "$it ${home.symbol}" } ?: "—"
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, card.name, sumText, rate))
        if (quote != null) append(", ").append(basisLabel(quote))
        if (best != null) append(". ").append(best)
    }
    val editLabel = stringResource(R.string.card_edit_a11y)
    val sumSize = if (sumText.length > 10) 17.sp else 22.sp

    // Обводка выбранной карты — в постоянном зазоре: размер и соседи не меняются.
    Box(
        Modifier
            .border(2.dp, colors.accent.copy(alpha = ring), RoundedCornerShape(22.dp))
            .padding(RING_GAP),
    ) {
        Box(
            modifier = Modifier
                .size(CARD_WIDTH, CARD_HEIGHT)
                .graphicsLayer { alpha = dim }
                .clip(CARD_SHAPE)
                .background(Brush.linearGradient(listOf(style.start, style.end)))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = editLabel)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    this.selected = selected
                    role = Role.RadioButton
                },
        ) {
            // Водяной знак валюты карты.
            val mark = card.currency.symbol
            Text(
                text = mark,
                style = type.hero.copy(fontSize = if (mark.length > 2) 40.sp else 88.sp, lineHeight = 92.sp, letterSpacing = 0.sp),
                color = style.mark.copy(alpha = 0.2f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = if (mark.length > 2) 4.dp else 24.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = type.label.copy(fontSize = 12.5.sp),
                        color = CardPalette.onCard.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (best != null) {
                        Text("★", style = type.label, color = CardPalette.star, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = sumText,
                    style = type.number.copy(fontSize = sumSize, lineHeight = sumSize * 1.15f, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                    color = CardPalette.onCard,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rate,
                        style = type.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
                        color = CardPalette.onCard.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (vs != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.percent_value, signedPercent(vs)),
                            style = type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = when (markupOf(vs)) {
                                Markup.GOOD -> CardPalette.goodOnCard
                                Markup.WARN -> CardPalette.warnOnCard
                                Markup.BAD -> CardPalette.badOnCard
                            },
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardPalette.chipScrim)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Узкая плитка «+» в конце ряда: показывает, что карт может быть больше. */
@Composable
private fun AddCardTile(onAdd: () -> Unit) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.card_add)
    Box(Modifier.padding(RING_GAP)) {
        Box(
            modifier = Modifier
                .size(64.dp, CARD_HEIGHT)
                .clip(CARD_SHAPE)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onAdd)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val line = colors.line
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = line,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.muted, modifier = Modifier.size(26.dp))
        }
    }
}

/** Пустой кошелёк: широкая плитка с объяснением. */
@Composable
private fun AddCardHint(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.card_add), style = AppTheme.type.bodyStrong, color = colors.ink)
            Text(stringResource(R.string.card_add_hint), style = AppTheme.type.caption, color = colors.muted, textAlign = TextAlign.Start)
        }
    }
}
