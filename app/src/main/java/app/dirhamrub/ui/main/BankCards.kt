package app.dirhamrub.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Comparison
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RateSource
import app.dirhamrub.domain.RuDates
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.motion
import app.dirhamrub.ui.title

/** Пропорции банковской карты ISO/IEC 7810 ID-1. */
private const val CARD_RATIO = 1.586f

/**
 * Две мини-карты: на каждой — цена текущей покупки в рублях по этой карте и её курс.
 * Выбранная — в красной обводке с галочкой, на выгодной — зелёный бейдж с экономией.
 */
@Composable
fun BankCardsRow(
    rates: Map<Card, CardRate>,
    comparison: Comparison,
    selected: Card,
    onSelect: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = Numbers.percent(comparison.savingsPercent)
    val savings = Numbers.moneyWhole(comparison.savingsRub)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (card in listOf(Card.KZT, Card.CNY)) {
            val badge = when {
                card != comparison.cheaper -> null
                savings == "0" -> stringResource(R.string.card_cheaper, percent)
                else -> stringResource(R.string.card_savings, savings, percent)
            }
            BankCard(
                card = card,
                rate = rates.getValue(card),
                rub = Numbers.moneyWhole(comparison.rubByCard.getValue(card)),
                selected = card == selected,
                badge = badge,
                onClick = { onSelect(card) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BankCard(
    card: Card,
    rate: CardRate,
    rub: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, motion(), label = "cardScale")
    val dim by animateFloatAsState(if (selected) 1f else 0.62f, motion(), label = "cardDim")
    val elevation by animateDpAsState(if (selected) 12.dp else 0.dp, motion(), label = "cardShadow")
    val ring by animateFloatAsState(if (selected) 1f else 0f, motion(), label = "cardRing")
    val shape = RoundedCornerShape(16.dp)
    val ringShape = RoundedCornerShape(20.dp)

    val rateText = Numbers.rate(rate.rubPerAed, 2)
    val dateLine = stringResource(
        if (rate.source == RateSource.MANUAL) R.string.card_date_manual else R.string.card_date_receipts,
        RuDates.short(rate.date),
    )
    val title = card.title()
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, title, rub, rateText, dateLine))
        if (badge != null) append(". ").append(badge)
    }

    val gradient = when (card) {
        Card.KZT -> Brush.linearGradient(listOf(CardPalette.tengeStart, CardPalette.tengeEnd))
        Card.CNY -> Brush.linearGradient(listOf(CardPalette.yuanStart, CardPalette.yuanEnd))
    }
    val chipColor = if (card == Card.CNY) CardPalette.gold else CardPalette.silver
    val markColor = if (card == Card.CNY) CardPalette.gold else CardPalette.tengeMark
    val sumSize = if (rub.length > 9) 17.sp else 21.sp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim
            }
            // Обводка выбранной карты — с зазором цвета фона, как на макете.
            .border(2.dp, colors.accent.copy(alpha = ring), ringShape)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(gradient)
                .semantics { contentDescription = a11y }
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        ) {
            // Водяной знак валюты.
            Text(
                text = card.symbol,
                style = type.hero.copy(fontSize = 96.sp, lineHeight = 96.sp),
                color = markColor.copy(alpha = if (card == Card.CNY) 0.25f else 0.18f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 22.dp)
                    .clearAndSetSemantics {},
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp, vertical = 9.dp)
                    .clearAndSetSemantics {},
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = type.label.copy(fontSize = 12.sp),
                        color = CardPalette.onCard.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) CheckMark(if (card == Card.CNY) CardPalette.yuanStart else CardPalette.tengeEnd)
                }
                Spacer(Modifier.height(5.dp))
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
                    CardChip(chipColor)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$rub ₽",
                    style = type.hero.copy(fontSize = sumSize, lineHeight = sumSize * 1.15f),
                    color = CardPalette.onCard,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = stringResource(R.string.card_rate_line, rateText, dateLine),
                    style = type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                    color = CardPalette.onCard.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Белый кружок с галочкой у выбранной карты. */
@Composable
private fun CheckMark(tick: Color) {
    Canvas(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.White),
    ) {
        val w = size.width
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.28f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.73f, w * 0.36f)
        }
        drawPath(path, tick, style = stroke)
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
