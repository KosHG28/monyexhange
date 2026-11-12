package app.dirhamrub.ui.main

import app.dirhamrub.ui.theme.sheen
import app.dirhamrub.ui.theme.parallaxOf
import app.dirhamrub.ui.theme.LocalTilt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
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
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.motion
import java.math.BigDecimal

@Composable
internal fun BankCardView(
    card: BankCard,
    quote: CardQuote?,
    spend: Currency,
    home: Currency,
    sum: String?,
    selected: Boolean,
    best: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    width: Dp,
    height: Dp,
    /** У валюты трат нет курса ЦБ: без чека покупки в ней курса нет — «нужен чек». */
    needsReceipt: Boolean = false,
    /** Переворот и свечение при смене чеков; в предпросмотре редактора чек меняется на каждой цифре — там без них. */
    flipOnChange: Boolean = true,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val style = CardPalette.style(card)
    val dim by animateFloatAsState(if (selected) 1f else 0.82f, motion(), label = "cardDim")
    // Невыбранные карты чуть меньше: выбранная — «впереди».
    val shrink by animateFloatAsState(if (selected) 1f else 0.96f, motion(260), label = "cardScale")
    // Чеки карты изменились (сохранили редактор) — карта переворачивается и коротко светится: курс новый.
    val receipts = Triple(card.transfer, card.purchases, card.manual)
    var seenReceipts by remember(card.id) { mutableStateOf(receipts) }
    val turn = remember(card.id) { Animatable(0f) }
    val glow = remember(card.id) { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(receipts) {
        if (receipts == seenReceipts) return@LaunchedEffect
        seenReceipts = receipts
        if (reduceMotion || !flipOnChange) return@LaunchedEffect
        turn.animateTo(90f, tween(150))
        turn.snapTo(-90f)
        turn.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        glow.snapTo(1f)
        glow.animateTo(0f, tween(800))
    }

    val rate = quote?.let {
        val unit = Numbers.unitFor(it.homePerUnit)
        stringResource(
            R.string.card_rate_short,
            Numbers.rateShort(it.homePerUnit.multiply(unit, MC)),
            home.mark,
            if (unit.compareTo(BigDecimal.ONE) == 0) spend.mark else "${Numbers.moneyWhole(unit)} ${spend.mark}",
        )
    } ?: stringResource(if (needsReceipt) R.string.card_need_receipt else R.string.card_no_rate)
    val vs = quote?.takeIf { it.basis != RateBasis.OFFICIAL }?.vsOfficial
    // Оценочный курс (нет чека или чистый ЦБ) — сумма со знаком «≈», как на главном.
    val approx = if (quote?.basis?.let { it.approximate || it == RateBasis.OFFICIAL } == true) "≈ " else ""
    val sumText = sum?.let { "$approx$it ${home.mark}" } ?: "—"
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, card.name, sumText, rate))
        if (quote != null) append(", ").append(basisLabel(quote))
        if (best != null) append(". ").append(best)
    }
    val editLabel = stringResource(R.string.card_edit_a11y)
    // Узкая карта (три в ряд): сумма мельче, наценка — строкой под курсом, а не рядом.
    val compact = width < COMPACT_CARD_WIDTH
    val sumSize = when {
        compact -> if (sumText.length > 11) 15.sp else 18.sp
        sumText.length > 11 -> 17.sp
        else -> 22.sp
    }

    // Зазор под общую рамку выбранной карты (её рисует ряд): размер и соседи не меняются.
    Box(Modifier.padding(RING_GAP)) {
        Box(
            modifier = Modifier
                .size(width, height)
                .graphicsLayer {
                    alpha = dim
                    scaleX = shrink
                    scaleY = shrink
                    rotationY = turn.value
                    cameraDistance = 12f * density
                }
                .drawWithContent {
                    drawContent()
                    if (glow.value > 0f) {
                        drawRoundRect(
                            color = CardPalette.goodOnCard.copy(alpha = glow.value),
                            cornerRadius = CornerRadius(CARD_RADIUS.toPx()),
                            style = Stroke(2.5.dp.toPx()),
                        )
                    }
                }
                .clip(CARD_SHAPE)
                .background(Brush.linearGradient(listOf(style.start, style.end)))
                .sheen(halfWidth = 46.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = editLabel)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    this.selected = selected
                    role = Role.RadioButton
                },
        ) {
            // Водяной знак валюты карты: при наклоне отстаёт на 6 dp — будто глубже в пластике.
            val mark = card.currency.mark
            val tilt = LocalTilt.current
            Text(
                text = mark,
                style = type.hero.copy(fontSize = if (mark.length > 2) 40.sp else 88.sp, lineHeight = 92.sp, letterSpacing = 0.sp),
                color = style.mark.copy(alpha = 0.2f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = if (mark.length > 2) 4.dp else 24.dp)
                    .graphicsLayer { translationX = parallaxOf(tilt, 6.dp.toPx()) },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 10.dp else 13.dp, vertical = if (compact) 9.dp else 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = type.label.copy(fontSize = if (compact) 11.5.sp else 12.5.sp),
                        color = CardPalette.onCard.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Звезду лучшей карты рисует ряд: при смене лучшей она перелетает к другой карте.
                    if (best != null) Spacer(Modifier.width(STAR_SLOT))
                }
                Spacer(Modifier.weight(1f))
                // Сумма меняется на каждом нажатии — перекатываются только изменившиеся цифры.
                val sumStyle = type.number.copy(fontSize = sumSize, lineHeight = sumSize * 1.15f, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
                Row {
                    if (sum != null) {
                        if (approx.isNotEmpty()) Text(approx, style = sumStyle, color = CardPalette.onCard, maxLines = 1, softWrap = false)
                        RollingText(sum, style = sumStyle, color = CardPalette.onCard)
                        Text(" " + home.mark, style = sumStyle, color = CardPalette.onCard, maxLines = 1, softWrap = false)
                    } else {
                        Text(sumText, style = sumStyle, color = CardPalette.onCard, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(3.dp))
                val rateStyle = type.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp, color = CardPalette.onCard.copy(alpha = 0.88f))
                // Курс не сокращается многоточием: на узкой карте шрифт сам уменьшается, пока строка не влезет.
                val rateText: @Composable (Modifier) -> Unit = { m ->
                    BasicText(
                        text = rate,
                        style = rateStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = RATE_MIN_SP, maxFontSize = 11.5.sp, stepSize = 0.25.sp),
                        modifier = m,
                    )
                }
                val vsChip: @Composable () -> Unit = {
                    if (vs != null) {
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
                if (compact) {
                    rateText(Modifier.fillMaxWidth())
                    if (vs != null) {
                        Spacer(Modifier.height(2.dp))
                        vsChip()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        rateText(Modifier.weight(1f, fill = false))
                        if (vs != null) {
                            Spacer(Modifier.width(5.dp))
                            vsChip()
                        }
                    }
                }
            }
        }
    }
}
