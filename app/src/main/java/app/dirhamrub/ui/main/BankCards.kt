package app.dirhamrub.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
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

/** Пропорции банковской карты ISO/IEC 7810 ID-1. */
private const val CARD_RATIO = 1.586f

/**
 * Карты пользователя в ряд (прокручивается, по две на экране) и плитка «Добавить карту».
 * На каждой — цена текущей покупки в итоговой валюте, курс и разница с ЦБ; у лучшей — бейдж.
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
        AddCardHint(onAdd, modifier)
        return
    }
    val best = quote.best?.takeIf { quote.quotes.size > 1 }
    val bestPercent = quote.bestSavingsPercent?.let(Numbers::percent)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth: Dp = (maxWidth - 8.dp) / 2
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards, key = { it.id }) { card ->
                val q = quote.of(card.id)
                BankCardView(
                    card = card,
                    quote = q,
                    spend = spend,
                    home = home,
                    sum = q?.let { Numbers.moneyWhole(amount.multiply(it.homePerUnit, MC)) },
                    selected = card.id == selectedId,
                    badge = if (best != null && q == best && bestPercent != null && bestPercent != "0") {
                        stringResource(R.string.card_best, bestPercent)
                    } else {
                        null
                    },
                    onClick = { if (card.id == selectedId) onEdit(card) else onSelect(card.id) },
                    onLongClick = { onEdit(card) },
                    modifier = Modifier.width(cardWidth),
                )
            }
            item(key = "add") { AddCardTile(onAdd, Modifier.width(cardWidth)) }
        }
    }
}

/** «22,45 ₽ за AED · по чекам · +4,7 % к ЦБ» — как посчитан курс карты. */
@Composable
fun basisLabel(q: CardQuote): String {
    val basis = stringResource(
        when (q.basis) {
            RateBasis.RECEIPTS -> R.string.card_basis_receipts
            RateBasis.ESTIMATE -> R.string.card_basis_estimate
            RateBasis.OFFICIAL -> R.string.card_basis_official
            RateBasis.MANUAL -> R.string.card_basis_manual
        },
    )
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
    badge: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val style = CardPalette.style(card.style)
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, motion(), label = "cardScale")
    val dim by animateFloatAsState(if (selected) 1f else 0.62f, motion(), label = "cardDim")
    val elevation by animateDpAsState(if (selected) 12.dp else 0.dp, motion(), label = "cardShadow")
    val ring by animateFloatAsState(if (selected) 1f else 0f, motion(), label = "cardRing")
    val shape = RoundedCornerShape(16.dp)

    val rateLine = quote?.let {
        val unit = Numbers.unitFor(it.homePerUnit)
        stringResource(
            R.string.card_rate_line,
            Numbers.rate(it.homePerUnit.multiply(unit, MC), 2),
            home.symbol,
            if (unit.compareTo(BigDecimal.ONE) == 0) spend.symbol else "${Numbers.moneyWhole(unit)} ${spend.symbol}",
        )
    } ?: stringResource(R.string.card_no_rate)
    val basis = quote?.let { basisLabel(it) }
    val sumText = sum?.let { "$it ${home.symbol}" } ?: "—"
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, card.name, sumText, rateLine))
        if (basis != null) append(", ").append(basis)
        if (badge != null) append(". ").append(badge)
    }
    val sumSize = if (sumText.length > 10) 16.sp else 20.sp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim
            }
            // Обводка выбранной карты — с зазором цвета фона.
            .border(2.dp, colors.accent.copy(alpha = ring), RoundedCornerShape(20.dp))
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.linearGradient(listOf(style.start, style.end)))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                style = type.hero.copy(fontSize = if (mark.length > 2) 44.sp else 92.sp, lineHeight = 96.sp),
                color = style.mark.copy(alpha = 0.22f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = if (mark.length > 2) 4.dp else 22.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = type.label.copy(fontSize = 12.sp),
                        color = CardPalette.onCard.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) EditMark(style.end)
                }
                Spacer(Modifier.height(4.dp))
                if (badge != null) {
                    Text(
                        text = badge,
                        style = type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = if (colors.isDark) colors.bg else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.good)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                } else {
                    CardChip(style.chip)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = sumText,
                    style = type.hero.copy(fontSize = sumSize, lineHeight = sumSize * 1.15f),
                    color = CardPalette.onCard,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = rateLine,
                    style = type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                    color = CardPalette.onCard.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (basis != null) {
                    Text(
                        text = basis,
                        style = type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = CardPalette.onCard.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Плитка «+ Добавить карту» того же размера, что карта. */
@Composable
private fun AddCardTile(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.card_add)
    Box(modifier.padding(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onAdd)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val line = colors.line
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = line,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                Text(label, style = AppTheme.type.label, color = colors.accent)
            }
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

/** Белый кружок с карандашом у выбранной карты: тап по ней открывает редактор. */
@Composable
private fun EditMark(tint: Color) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
    }
}

/** Чип банковской карты. */
@Composable
private fun CardChip(color: Color) {
    Canvas(Modifier.size(width = 24.dp, height = 17.dp)) {
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(color = color, cornerRadius = r)
        val stroke = 1.dp.toPx()
        val line = Color(color.red * 0.7f, color.green * 0.7f, color.blue * 0.6f, 1f)
        drawLine(line, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
        drawLine(line, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
        drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
        drawRoundRect(
            color = line,
            topLeft = Offset(size.width / 3f, size.height / 4f),
            size = Size(size.width / 3f, size.height / 2f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
    }
}
