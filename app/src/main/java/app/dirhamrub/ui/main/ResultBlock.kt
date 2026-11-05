package app.dirhamrub.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardComparison
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.StaleReceipt
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.motion
import java.math.BigDecimal
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Что сейчас крупно: смена валют или строки — сумма уезжает целиком; набор и смена карты —
 * перекатываются только изменившиеся цифры ([RollingText]).
 */
private data class HeroKey(val spend: Currency, val home: Currency, val line: Line)

private class HeroContent(val key: HeroKey, val approx: Boolean, val number: String, val symbol: String?, val size: Int)

/**
 * Крупная сумма, подпись (только если курс не по чекам) и какая карта выгоднее.
 * Свайп по блоку влево/вправо — соседняя карта.
 */
@Composable
internal fun ResultBlock(
    state: MainUiState,
    heroLine: Line,
    compact: Boolean,
    onSelectCard: (String) -> Unit,
    onStepCard: (Int) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onEditCard: (BankCard) -> Unit,
    /** Свайп по сумме для ряда карт: −1…1 — насколько палец уже потянул к соседней карте. */
    swipe: MutableFloatState? = null,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val card = state.selectedCard
    val q = state.selectedQuote
    val cardIndex = wallet.cards.indexOfFirst { it.id == card?.id }
    val step by rememberUpdatedState(onStepCard)
    val view = LocalView.current
    val canSwipe = wallet.cards.size > 1
    // Сумма идёт за пальцем (с сопротивлением), рамка в ряду карт ползёт к соседней. Дотянули — карта
    // сменилась, мало — сумма пружинит обратно. Без анимаций — как раньше: только смена карты.
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val drag = remember { mutableFloatStateOf(0f) }
    val index by rememberUpdatedState(cardIndex)
    val last by rememberUpdatedState(wallet.cards.lastIndex)
    val swipeModifier = if (canSwipe) {
        Modifier.pointerInput(Unit) {
            val threshold = 56.dp.toPx()
            var total = 0f
            fun settle() {
                val fromDrag = drag.floatValue
                val fromPreview = swipe?.floatValue ?: 0f
                scope.launch {
                    animate(fromDrag, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)) { v, _ -> drag.floatValue = v }
                }
                if (swipe != null) {
                    scope.launch { animate(fromPreview, 0f, animationSpec = tween(320)) { v, _ -> swipe.floatValue = v } }
                }
            }
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (abs(total) > threshold) {
                        val dir = if (total < 0) 1 else -1
                        if (index + dir in 0..last) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        // Влево — следующая карта, как в ряду карт.
                        step(dir)
                    }
                    settle()
                },
                onDragCancel = { settle() },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    total += dx
                    if (!reduceMotion) {
                        // Влево — к следующей карте; у крайней карты тянется туже.
                        val toward = (-total / threshold).coerceIn(-1f, 1f)
                        val possible = index + (if (toward > 0) 1 else -1) in 0..last
                        drag.floatValue = total * (if (possible) DRAG_FOLLOW else DRAG_EDGE)
                        swipe?.floatValue = if (possible) toward else 0f
                    }
                },
            )
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(swipeModifier)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val heroCurrency = heroLine.currency(state) ?: wallet.home
        val heroValue = state.totals.of(heroLine)
        val estimate = q?.basis == RateBasis.ESTIMATE
        val number = heroValue?.let(Numbers::money) ?: "—"
        val heroPlain = (if (estimate && heroValue != null) "≈ " else "") + "$number ${heroCurrency.symbol}"
        val base = if (compact) HERO_COMPACT_SP else HERO_SP
        val heroSize = when {
            heroPlain.length > 15 -> base * 5 / 8
            heroPlain.length > 11 -> base * 4 / 5
            else -> base
        }
        val hero = HeroContent(
            key = HeroKey(wallet.spend, wallet.home, heroLine),
            approx = estimate && heroValue != null,
            number = number,
            symbol = heroCurrency.symbol.takeIf { heroValue != null },
            size = heroSize,
        )
        val resultA11y = stringResource(R.string.result_a11y, heroPlain)
        val nextLabel = stringResource(R.string.card_next)
        val prevLabel = stringResource(R.string.card_prev)
        AnimatedContent(
            targetState = hero,
            contentKey = { it.key },
            transitionSpec = {
                when {
                    reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                    // Другие валюты — сверху вниз.
                    else -> (slideInVertically(tween(260)) { -it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)))
                }.using(SizeTransform(clip = false))
            },
            label = "hero",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = drag.floatValue
                    alpha = 1f - (abs(drag.floatValue) / size.width).coerceAtMost(0.4f)
                }
                .semantics {
                    contentDescription = resultA11y
                    liveRegion = LiveRegionMode.Polite
                    if (canSwipe) {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(nextLabel) { onStepCard(1); true }.takeIf { cardIndex < wallet.cards.lastIndex },
                            CustomAccessibilityAction(prevLabel) { onStepCard(-1); true }.takeIf { cardIndex > 0 },
                        )
                    }
                },
        ) { h ->
            val style = type.hero.copy(fontSize = h.size.sp, lineHeight = (h.size * 1.12f).sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (h.approx) Text("≈ ", style = style, color = colors.muted, maxLines = 1, softWrap = false)
                RollingText(h.number, style = style, color = colors.ink)
                if (h.symbol != null) Text(" " + h.symbol, style = style, color = colors.muted, maxLines = 1, softWrap = false)
            }
        }
        Basis(state, onAddReceipt, onEditCard)
        comparison(state)?.let { c ->
            Spacer(Modifier.height(6.dp))
            Comparison(state, c, onSelectCard)
        }
    }
}

/**
 * «≈ оценка · добавить чек ›» — только когда курс не по чекам: тап открывает редактор с пустым чеком покупки.
 * При курсе по чекам подписи нет: выбранная карта и так видна в рамке.
 */
@Composable
private fun Basis(state: MainUiState, onAddReceipt: (BankCard) -> Unit, onEditCard: (BankCard) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val card = state.selectedCard
    val stale = basisStale(state)
    if (stale != null && card != null) {
        StaleLine(stale, onOpen = { onEditCard(card) })
        return
    }
    val basis = basisText(state) ?: return
    val style = type.body.copy(fontSize = 14.sp)
    val q = state.selectedQuote
    val text = if (state.homePerUnit == null || q == null) stringResource(R.string.result_no_rate) else basisShort(q)
    val action = basisAction(state)
    if (action == null) {
        Text(text, style = style, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        return
    }
    val hintA11y = stringResource(R.string.add_receipt_a11y, state.wallet.spend.nameRu)
    Row(
        modifier = Modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = hintA11y) { onAddReceipt(action) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (if (basis == RateBasis.ESTIMATE) "≈ " else "") + text,
            style = style,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(stringResource(R.string.add_receipt), style = style.copy(fontWeight = FontWeight.Medium), color = colors.accent, maxLines = 1)
    }
}

/** «Чек от 12 окт · ЦБ с тех пор +3,1 % · обновить ›» — тап открывает карту, чтобы обновить чек. */
@Composable
private fun StaleLine(stale: StaleReceipt, onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val style = AppTheme.type.body.copy(fontSize = 14.sp)
    val date = RuDates.short(stale.date)
    val change = signedPercent(stale.cbrChange)
    val a11y = stringResource(R.string.stale_receipt_a11y, date, change)
    Row(
        modifier = Modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.stale_receipt, date, change),
            style = style,
            color = colors.warn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(stringResource(R.string.stale_receipt_action), style = style.copy(fontWeight = FontWeight.Medium), color = colors.accent, maxLines = 1)
    }
}

/**
 * Какой картой платить. Выбрана выгодная — зелёная плашка «На 13 ₽ (2,7 %) дешевле, чем «Карта в тенге»»;
 * иначе — кнопка «Дешевле на 13 ₽ (2,7 %) — «Карта в юанях» ›», тап выбирает выгодную карту.
 * Обе одной высоты: при свайпе между картами ничего не прыгает.
 */
@Composable
private fun Comparison(state: MainUiState, c: CardComparison, onSelectCard: (String) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val percent = Numbers.percent(c.percent)
    val amount = state.totals.spend
    val approx = if (c.isExact) "" else "≈ "
    val diff = if (amount.signum() > 0) {
        // Целыми рублями, как суммы на картах; копейки — только у разницы меньше 10.
        stringResource(R.string.compare_amount, approx + Numbers.saving(c.difference(amount)), state.wallet.home.symbol, percent)
    } else {
        stringResource(R.string.compare_percent, approx + percent)
    }
    val best = c.selectedIsBest
    val text = if (best) stringResource(R.string.compare_best, diff, c.other.card.name) else stringResource(R.string.compare_other, diff, c.other.card.name)
    val a11y = if (best) text else stringResource(R.string.compare_other_a11y, c.other.card.name, diff)
    // Сменилась карта — плашка плавно перекрашивается, меняет ширину и текст, а не прыгает.
    val reduceMotion = LocalReduceMotion.current
    val bg by animateColorAsState(if (best) colors.goodSoft else colors.accentSoft, motion(280), label = "compareBg")
    val fg by animateColorAsState(if (best) colors.good else colors.accent, motion(280), label = "compareFg")
    Box(
        modifier = Modifier
            .heightIn(min = ROW)
            .animateContentSize(if (reduceMotion) snap() else tween(280))
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (best) Modifier else Modifier.clickable(role = Role.Button) { onSelectCard(c.other.card.id) })
            .clearAndSetSemantics {
                contentDescription = a11y
                if (!best) role = Role.Button
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Текст сменяется растворением только при смене «выгоднее / дороже»; при наборе суммы — на месте.
        AnimatedContent(
            targetState = best to text,
            contentKey = { it.first },
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(200, delayMillis = 80)) togetherWith fadeOut(tween(120))
                }.using(SizeTransform(clip = false))
            },
            label = "compareText",
        ) { (_, t) ->
            Text(
                text = t,
                style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * «ЦБ на 30 окт: 100 ₸ = 17,65 ₽ · ↑0,4 % за неделю ↻» — курс ЦБ на ту же единицу и его изменение за неделю
 * (вверх — валюта трат подорожала, жёлтым; вниз — подешевела, зелёным). Изменение — у курса ЦБ, оно одно
 * для всех карт (наценка карты — на самой карте). Не помещается — короче: «ЦБ 30 окт: … · ↑0,4 %/нед.».
 * Тап — обновить: значок крутится, новый курс перекатывается и коротко подсвечивается.
 */
@Composable
internal fun OfficialLine(state: MainUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val official = state.quote.official
    val rates = state.official
    val reduceMotion = LocalReduceMotion.current
    val style = type.body.copy(fontSize = 13.sp)
    val refresh = stringResource(R.string.official_refresh)
    val trend = state.weekTrend?.takeIf { Numbers.percent(it.abs()) != "0" }
    val arrow = trend?.let { (if (it.signum() > 0) "↑" else "↓") + Numbers.percent(it.abs()) }
    val trendColor = if (trend != null && trend.signum() > 0) colors.warn else colors.good

    // Подсветка нового курса: тот же курс валют, другое значение.
    val rate = if (official != null && rates != null) {
        val u = Numbers.unitFor(official)
        val unitLabel = if (u.compareTo(BigDecimal.ONE) == 0) "1 ${wallet.spend.symbol}" else "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}"
        unitLabel to Numbers.rateShort(official.multiply(u, MC))
    } else {
        null
    }
    val flash = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<Triple<Currency, Currency, String>?>(null) }
    LaunchedEffect(rate?.second, wallet.spend, wallet.home) {
        val now = rate?.let { Triple(wallet.spend, wallet.home, it.second) }
        val before = shown
        shown = now
        if (now != null && before != null && before.first == now.first && before.second == now.second && before.third != now.third && !reduceMotion) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_MS))
        }
    }

    Row(
        modifier = modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = refresh, enabled = !state.ratesLoading, onClick = onRefresh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rate == null || rates == null) {
            Text(
                stringResource(if (state.ratesLoading) R.string.official_loading_short else R.string.official_missing_short),
                style = style,
                color = colors.muted,
                maxLines = 1,
            )
        } else {
            val date = RuDates.short(rates.date)
            val offline = if (state.ratesFailed) stringResource(R.string.official_offline) else ""
            val full = Triple(
                stringResource(R.string.official_line_head, date, rate.first),
                arrow?.let { stringResource(R.string.official_trend_week, it) },
                offline,
            )
            val short = Triple(
                stringResource(R.string.official_line_head_short, date, rate.first),
                arrow?.let { stringResource(R.string.official_trend_week_short, it) },
                offline,
            )
            val tail = { t: Triple<String, String?, String> -> " ${wallet.home.symbol}" + (t.second?.let { " · $it" } ?: "") + t.third }
            // Полная строка, если помещается вместе со значком обновления; иначе короткая.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = LocalConfiguration.current.screenWidthDp.dp - 68.dp
            val fullWidth = with(density) { measurer.measure(full.first + rate.second + tail(full), style).size.width.toDp() }
            val line = if (fullWidth <= width) full else short
            val a11y = line.first + rate.second + " " + wallet.home.symbol +
                (trend?.let { ", " + stringResource(R.string.official_trend_a11y, signedPercent(it)) } ?: "") + line.third
            Row(
                Modifier.clearAndSetSemantics { contentDescription = a11y },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.first, style = style, color = colors.muted, maxLines = 1, softWrap = false)
                RollingText(
                    rate.second,
                    style = style,
                    color = lerp(colors.muted, colors.warn, flash.value),
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.warn.copy(alpha = 0.28f * flash.value)),
                )
                Text(
                    buildAnnotatedString {
                        append(" " + wallet.home.symbol)
                        if (line.second != null) {
                            append(" · ")
                            withStyle(SpanStyle(color = trendColor, fontWeight = FontWeight.Medium)) { append(line.second) }
                        }
                        append(line.third)
                    },
                    style = style,
                    color = colors.muted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RefreshMark(state.ratesLoading, state.ratesFailed, reduceMotion)
    }
}

/** «↻»: пока курсы обновляются — крутится (без анимации — просто бледнеет). */
@Composable
private fun RefreshMark(loading: Boolean, failed: Boolean, reduceMotion: Boolean) {
    val colors = AppTheme.colors
    val color = when {
        loading && reduceMotion -> colors.line
        failed -> colors.warn
        else -> colors.accent
    }
    val mark = @Composable { m: Modifier -> Text("↻", style = AppTheme.type.bodyStrong, color = color, modifier = m) }
    if (loading && !reduceMotion) {
        // Бесконечная анимация есть, только пока идёт загрузка.
        val spin = rememberInfiniteTransition(label = "refresh")
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "refreshAngle")
        mark(Modifier.graphicsLayer { rotationZ = angle })
    } else {
        mark(Modifier)
    }
}

private const val FLASH_MS = 1000

/** Насколько сумма следует за пальцем при свайпе (и у крайней карты, где листать некуда). */
private const val DRAG_FOLLOW = 0.55f
private const val DRAG_EDGE = 0.2f
