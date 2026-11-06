package app.dirhamrub.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import app.dirhamrub.domain.wallet.cbrPublishes
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.motion
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private val CARD_HEIGHT = 100.dp
private val ADD_WIDTH = 52.dp
private val CARD_RADIUS = 18.dp
private val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS)
/** Зазор между картой и обводкой выбранной: обводка рисуется в нём и не сдвигает соседей. */
private val RING_GAP = 4.dp

/** Место под звезду лучшей карты в строке названия (звезду рисует ряд). */
private val STAR_SLOT = 16.dp

/** Насколько рамка доползает к соседней карте, пока палец тянет сумму. */
private const val SWIPE_RING_SHARE = 0.35f
private const val STAR_FLIGHT_MS = 520
private val STAR_ARC = 26.dp
private val RATE_MIN_SP = 9.sp

/**
 * Карты пользователя в ряд (прокручивается) и плитка «+»: ширина карт — от ширины экрана,
 * чтобы две карты и «+» помещались целиком.
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
    /** Свайп по сумме: −1…1 — насколько палец уже потянул к соседней карте (рамка ползёт навстречу). */
    swipePreview: State<Float>? = null,
) {
    if (cards.isEmpty()) {
        AddCardHint(onAdd, modifier.padding(horizontal = 16.dp))
        return
    }
    val listState = rememberLazyListState()
    // Карту выбрали свайпом по сумме или в камере — докручиваем ряд, чтобы она была видна целиком.
    LaunchedEffect(selectedId) {
        val index = cards.indexOfFirst { it.id == selectedId }
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset) {
            listState.animateScrollToItem(index)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 16 dp поля + 2 карты + «+» + зазоры по 10 dp; обводке выбранной — по RING_GAP с каждой стороны.
        // При крупном системном шрифте карты шире и выше, иначе название и курс обрезаются
        // (тогда «+» и вторая карта уходят вправо — ряд прокручивается).
        val scale = cardScale()
        val cardWidth = (((maxWidth - 16.dp * 2 - 10.dp * 2 - ADD_WIDTH) / 2) * scale).coerceIn(140.dp, minOf(240.dp, maxWidth * 0.62f))
        // Рамка выбранной карты — одна на весь ряд: при выборе другой карты она переезжает к ней
        // с лёгким пружинящим докатом, а не гаснет на одной и загорается на другой.
        val selectedIndex = cards.indexOfFirst { it.id == selectedId }
        val ringAt = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
        val reduceMotion = LocalReduceMotion.current
        LaunchedEffect(selectedIndex) {
            if (selectedIndex < 0) return@LaunchedEffect
            if (reduceMotion) {
                ringAt.snapTo(selectedIndex.toFloat())
            } else {
                ringAt.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f))
            }
        }
        val ringColor = AppTheme.colors.accent
        // Звезда лучшей карты: при смене лучшей перелетает по дуге к новой.
        val best = quote.best?.takeIf { quote.quotes.size > 1 && quote.bestSavingsPercent?.let(Numbers::percent)?.let { p -> p != "0" } == true }
        val bestIndex = cards.indexOfFirst { it.id == best?.card?.id }
        var starFrom by remember { mutableIntStateOf(bestIndex) }
        var starTo by remember { mutableIntStateOf(bestIndex) }
        val starFlight = remember { Animatable(1f) }
        LaunchedEffect(bestIndex) {
            if (bestIndex == starTo) return@LaunchedEffect
            starFrom = if (starTo >= 0) starTo else bestIndex
            starTo = bestIndex
            if (reduceMotion || starFrom < 0 || bestIndex < 0) {
                starFlight.snapTo(1f)
            } else {
                starFlight.snapTo(0f)
                starFlight.animateTo(1f, tween(STAR_FLIGHT_MS, easing = FastOutSlowInEasing))
            }
        }
        val measurer = rememberTextMeasurer()
        val starStyle = AppTheme.type.label.copy(color = CardPalette.star)
        val star = remember(measurer, starStyle) { measurer.measure("★", starStyle) }
        Box {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp - RING_GAP),
                horizontalArrangement = Arrangement.spacedBy(10.dp - RING_GAP * 2),
            ) {
                cardItems(cards, quote, selectedId, spend, home, amount, cardWidth, onSelect, onEdit)
                item(key = "add") { AddCardTile(onAdd, CARD_HEIGHT * scale) }
            }
            Canvas(Modifier.matchParentSize()) {
                val info = listState.layoutInfo
                val items = info.visibleItemsInfo
                val step = (cardWidth + RING_GAP * 2 + 10.dp - RING_GAP * 2).toPx()
                // Левый край карты на дробной позиции: между соседними — по их местам, за краями — шагом ряда.
                fun leftAt(pos: Float): Float? {
                    val i0 = floor(pos).toInt().coerceIn(0, cards.lastIndex)
                    val a = items.firstOrNull { it.index == i0 } ?: return null
                    val b = items.firstOrNull { it.index == i0 + 1 }?.takeIf { i0 + 1 <= cards.lastIndex }
                    val gap = if (b != null) (b.offset - a.offset).toFloat() else step
                    return a.offset + gap * (pos - i0) - info.viewportStartOffset
                }
                val cardBox = items.firstOrNull { it.index in cards.indices }?.size?.toFloat() ?: return@Canvas
                if (selectedIndex >= 0) {
                    val preview = swipePreview?.value ?: 0f
                    val left = leftAt(ringAt.value + preview * SWIPE_RING_SHARE)
                    if (left != null) {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(left + stroke / 2f, stroke / 2f),
                            size = Size(cardBox - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(22.dp.toPx()),
                            style = Stroke(stroke),
                        )
                    }
                }
                if (starTo >= 0) {
                    val p = starFlight.value
                    val fromLeft = leftAt(starFrom.toFloat())
                    val toLeft = leftAt(starTo.toFloat())
                    if (fromLeft != null && toLeft != null) {
                        // Правый верхний угол карты (в зазоре под рамку), с поправкой на уменьшенную невыбранную карту.
                        fun anchor(left: Float, index: Int): Offset {
                            val s = if (index == selectedIndex) 1f else 0.96f
                            val cx = left + cardBox / 2f
                            val cy = size.height / 2f
                            val x = left + cardBox - (RING_GAP + 13.dp).toPx() - star.size.width
                            val y = (RING_GAP + 11.dp).toPx()
                            return Offset(cx + (x - cx) * s, cy + (y - cy) * s)
                        }
                        val a = anchor(fromLeft, starFrom)
                        val b = anchor(toLeft, starTo)
                        val arc = sin(PI.toFloat() * p)
                        val at = Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p - arc * STAR_ARC.toPx())
                        val center = Offset(at.x + star.size.width / 2f, at.y + star.size.height / 2f)
                        withTransform({
                            rotate(360f * p, center)
                            scale(1f + 0.8f * arc, 1f + 0.8f * arc, center)
                        }) {
                            drawText(star, topLeft = at)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.cardItems(
    cards: List<BankCard>,
    quote: WalletQuote,
    selectedId: String?,
    spend: Currency,
    home: Currency,
    amount: BigDecimal,
    cardWidth: Dp,
    onSelect: (String) -> Unit,
    onEdit: (BankCard) -> Unit,
) {
    val best = quote.best?.takeIf { quote.quotes.size > 1 }
    val bestPercent = quote.bestSavingsPercent?.let(Numbers::percent)?.takeIf { it != "0" }
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
            width = cardWidth,
            height = CARD_HEIGHT * cardScale(),
            needsReceipt = quote.official == null && !(cbrPublishes(spend) && cbrPublishes(home)),
        )
    }
}

/** Во сколько раз карты больше обычного: по системному размеру шрифта, от 1 до 1,3. */
@Composable
internal fun cardScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MAX_CARD_SCALE)

private const val MAX_CARD_SCALE = 1.3f

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
    width: Dp,
    height: Dp,
    /** У валюты трат нет курса ЦБ: без чека покупки в ней курса нет — «нужен чек». */
    needsReceipt: Boolean = false,
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
        if (reduceMotion) return@LaunchedEffect
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
            home.symbol,
            if (unit.compareTo(BigDecimal.ONE) == 0) spend.symbol else "${Numbers.moneyWhole(unit)} ${spend.symbol}",
        )
    } ?: stringResource(if (needsReceipt) R.string.card_need_receipt else R.string.card_no_rate)
    val vs = quote?.takeIf { it.basis != RateBasis.OFFICIAL }?.vsOfficial
    // Оценочный курс (нет чека покупки в валюте трат) — сумма со знаком «≈».
    val approx = if (quote?.basis == RateBasis.ESTIMATE) "≈ " else ""
    val sumText = sum?.let { "$approx$it ${home.symbol}" } ?: "—"
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, card.name, sumText, rate))
        if (quote != null) append(", ").append(basisLabel(quote))
        if (best != null) append(". ").append(best)
    }
    val editLabel = stringResource(R.string.card_edit_a11y)
    val sumSize = if (sumText.length > 11) 17.sp else 22.sp

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
                        Text(" " + home.symbol, style = sumStyle, color = CardPalette.onCard, maxLines = 1, softWrap = false)
                    } else {
                        Text(sumText, style = sumStyle, color = CardPalette.onCard, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Курс не сокращается многоточием: на узкой карте шрифт сам уменьшается, пока строка не влезет.
                    BasicText(
                        text = rate,
                        style = type.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp, color = CardPalette.onCard.copy(alpha = 0.88f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = RATE_MIN_SP, maxFontSize = 11.5.sp, stepSize = 0.25.sp),
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
private fun AddCardTile(onAdd: () -> Unit, height: Dp) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.card_add)
    Box(Modifier.padding(RING_GAP)) {
        Box(
            modifier = Modifier
                .size(ADD_WIDTH, height)
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
