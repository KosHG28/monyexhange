package app.dirhamrub.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import kotlin.math.abs

/** Что сейчас крупно: смена ключа (карта, валюты) анимируется, набор цифр — нет. */
private data class HeroKey(val cardId: String?, val spend: Currency, val home: Currency, val line: Line)

private class HeroContent(val key: HeroKey, val cardIndex: Int, val text: AnnotatedString, val size: Int)

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
    val swipe = if (canSwipe) {
        Modifier.pointerInput(Unit) {
            val threshold = 56.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (abs(total) > threshold) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        // Влево — следующая карта, как в ряду карт.
                        step(if (total < 0) 1 else -1)
                    }
                },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    total += dx
                },
            )
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(swipe)
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
            key = HeroKey(card?.id, wallet.spend, wallet.home, heroLine),
            cardIndex = cardIndex,
            text = buildAnnotatedString {
                if (estimate && heroValue != null) withStyle(SpanStyle(color = colors.muted)) { append("≈ ") }
                append(number)
                if (heroValue != null) {
                    append(" ")
                    withStyle(SpanStyle(color = colors.muted)) { append(heroCurrency.symbol) }
                }
            },
            size = heroSize,
        )
        val resultA11y = stringResource(R.string.result_a11y, heroPlain)
        val nextLabel = stringResource(R.string.card_next)
        val prevLabel = stringResource(R.string.card_prev)
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = hero,
            contentKey = { it.key },
            transitionSpec = {
                when {
                    reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                    // Другая карта — сумма уезжает вбок, в сторону свайпа.
                    targetState.key.cardId != initialState.key.cardId -> {
                        val dir = if (targetState.cardIndex >= initialState.cardIndex) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(160)))
                    }
                    // Другие валюты — сверху вниз.
                    else -> (slideInVertically(tween(260)) { -it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)))
                }.using(SizeTransform(clip = false))
            },
            label = "hero",
            modifier = Modifier
                .fillMaxWidth()
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
            Text(
                text = h.text,
                style = type.hero.copy(fontSize = h.size.sp, lineHeight = (h.size * 1.12f).sp),
                color = colors.ink,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Basis(state, onAddReceipt)
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
private fun Basis(state: MainUiState, onAddReceipt: (BankCard) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
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
    val approx = if (c.isExact) "" else "≈"
    val diff = if (amount.signum() > 0) {
        stringResource(R.string.compare_amount, approx + Numbers.cheat(c.difference(amount)), state.wallet.home.symbol, percent)
    } else {
        stringResource(R.string.compare_percent, approx + percent)
    }
    val best = c.selectedIsBest
    val text = if (best) stringResource(R.string.compare_best, diff, c.other.card.name) else stringResource(R.string.compare_other, diff, c.other.card.name)
    val a11y = if (best) text else stringResource(R.string.compare_other_a11y, c.other.card.name, diff)
    Box(
        modifier = Modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(50))
            .background(if (best) colors.goodSoft else colors.accentSoft)
            .then(if (best) Modifier else Modifier.clickable(role = Role.Button) { onSelectCard(c.other.card.id) })
            .clearAndSetSemantics {
                contentDescription = a11y
                if (!best) role = Role.Button
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = if (best) colors.good else colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * «ЦБ: 100 ₸ = 17,65 ₽ ↑0,4 % · 30 окт ↻» — курс ЦБ на ту же единицу, изменение за неделю
 * (вверх — валюта трат подорожала, жёлтым; вниз — подешевела, зелёным), дата и обновление по тапу.
 */
@Composable
internal fun OfficialLine(state: MainUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val official = state.quote.official
    val rates = state.official
    val trend = state.weekTrend?.takeIf { !state.ratesLoading && Numbers.percent(it.abs()) != "0" }
    val trendText = trend?.let { (if (it.signum() > 0) "↑" else "↓") + stringResource(R.string.compare_percent, Numbers.percent(it.abs())) }
    val head: String
    val tail: String
    when {
        state.ratesLoading -> {
            head = stringResource(R.string.official_loading_short)
            tail = ""
        }
        official == null || rates == null -> {
            head = stringResource(R.string.official_missing_short)
            tail = ""
        }
        else -> {
            val u = Numbers.unitFor(official)
            val unitLabel = if (u.compareTo(BigDecimal.ONE) == 0) "1 ${wallet.spend.symbol}" else "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}"
            head = stringResource(R.string.official_rate, unitLabel, Numbers.rate(official.multiply(u, MC), 2), wallet.home.symbol)
            tail = " · " + RuDates.short(rates.date) + if (state.ratesFailed) stringResource(R.string.official_offline) else ""
        }
    }
    val text = buildAnnotatedString {
        append(head)
        if (trendText != null && trend != null) {
            append(" ")
            withStyle(SpanStyle(color = if (trend.signum() > 0) colors.warn else colors.good, fontWeight = FontWeight.Medium)) { append(trendText) }
        }
        append(tail)
    }
    val a11y = buildString {
        append(head)
        if (trend != null) append(", ").append(stringResource(R.string.official_trend_a11y, signedPercent(trend)))
        append(tail)
    }
    val refresh = stringResource(R.string.official_refresh)
    Row(
        modifier = modifier
            .heightIn(min = ROW)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = refresh, enabled = !state.ratesLoading, onClick = onRefresh)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = type.body.copy(fontSize = 13.sp), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(
            "↻",
            style = type.bodyStrong,
            color = when {
                state.ratesLoading -> colors.line
                state.ratesFailed -> colors.warn
                else -> colors.accent
            },
        )
    }
}
