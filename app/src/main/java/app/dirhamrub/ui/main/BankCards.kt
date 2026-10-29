package app.dirhamrub.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RateSource
import app.dirhamrub.domain.RuDates
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.motion
import app.dirhamrub.ui.title

/** Пропорции банковской карты ISO/IEC 7810 ID-1. */
private const val CARD_RATIO = 1.586f

@Composable
fun BankCardsRow(
    rates: Map<Card, CardRate>,
    selected: Card,
    cheaper: Card?,
    savingsPercent: String,
    onSelect: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (card in listOf(Card.KZT, Card.CNY)) {
            BankCard(
                card = card,
                rate = rates.getValue(card),
                selected = card == selected,
                badge = if (card == cheaper) stringResource(R.string.card_cheaper, savingsPercent) else null,
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
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val scale by animateFloatAsState(if (selected) 1f else 0.93f, motion(), label = "cardScale")
    val lift by animateDpAsState(if (selected) (-4).dp else 2.dp, motion(), label = "cardLift")
    val elevation by animateDpAsState(if (selected) 14.dp else 1.dp, motion(), label = "cardShadow")
    val shape = RoundedCornerShape(14.dp)

    val rateText = Numbers.rate(rate.rubPerAed, 2)
    val dateText = RuDates.short(rate.date)
    val dateLine = stringResource(
        if (rate.source == RateSource.MANUAL) R.string.card_date_manual else R.string.card_date_receipts,
        dateText,
    )
    val title = card.title()
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, title, rateText, dateLine))
        if (badge != null) append(". ").append(badge)
    }

    val gradient = when (card) {
        Card.KZT -> Brush.linearGradient(listOf(CardPalette.tengeStart, CardPalette.tengeEnd))
        Card.CNY -> Brush.linearGradient(listOf(CardPalette.yuanStart, CardPalette.yuanEnd))
    }
    val chipColor = if (card == Card.CNY) CardPalette.gold else CardPalette.silver
    val markColor = if (card == Card.CNY) CardPalette.gold else CardPalette.tengeMark

    Box(
        modifier = modifier
            .padding(top = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift.toPx()
            },
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
                color = markColor.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 18.dp)
                    .clearAndSetSemantics {},
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clearAndSetSemantics {},
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = type.label,
                        color = CardPalette.onCard.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                CardChip(chipColor)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.card_rate, rateText),
                    style = type.title.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = CardPalette.onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateLine,
                    style = type.caption.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    color = CardPalette.onCard.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge != null) {
            Text(
                text = badge,
                style = type.caption.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = if (colors.isDark) colors.bg else Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-10).dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.good)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** Чип банковской карты. */
@Composable
private fun CardChip(color: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 19.dp)) {
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(color = color, cornerRadius = r)
        val stroke = 1.dp.toPx()
        val line = color.copy(alpha = 0.55f).let { Color(it.red * 0.7f, it.green * 0.7f, it.blue * 0.6f, 1f) }
        drawLine(line, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
        drawLine(line, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
        drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
        drawRoundRect(
            color = line,
            topLeft = Offset(size.width / 3f, size.height / 4f),
            size = Size(size.width / 3f, size.height / 2f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
    }
}
