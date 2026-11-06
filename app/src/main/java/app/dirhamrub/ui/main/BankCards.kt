package app.dirhamrub.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.domain.wallet.cbrPublishes
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private val CARD_HEIGHT = 100.dp
internal val ADD_WIDTH = 52.dp
internal val CARD_RADIUS = 18.dp
internal val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS)
/** Зазор между картой и обводкой выбранной: обводка рисуется в нём и не сдвигает соседей. */
internal val RING_GAP = 4.dp

/** Место под звезду лучшей карты в строке названия (звезду рисует ряд). */
internal val STAR_SLOT = 16.dp

/** Насколько рамка доползает к соседней карте, пока палец тянет сумму. */
private const val SWIPE_RING_SHARE = 0.35f
private const val STAR_FLIGHT_MS = 520
private val STAR_ARC = 26.dp
internal val RATE_MIN_SP = 9.sp

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
