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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
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
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.abs

/** Подсказка «вы в другой стране»: [iso] — страна сотовой сети, [currency] — её валюта трат. */
data class CountryOffer(val iso: String, val currency: Currency)

/** Ниже этой высоты верхней части экрана крупная сумма мельче — чтобы карты, сумма и подсказки поместились. */
private val COMPACT_TOP = 330.dp

@Composable
fun MainScreen(
    state: MainUiState,
    countryOffer: CountryOffer?,
    onKey: (Key) -> Unit,
    onSelectCard: (String) -> Unit,
    onStepCard: (Int) -> Unit,
    onEditCard: (BankCard) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    onSelectLine: (Line) -> Unit,
    onChip: (BigDecimal) -> Unit,
    onOpenCurrencies: () -> Unit,
    onCountry: (accept: Boolean) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    // Ввод — в одной из валют, крупно — сумма в итоговой (или в валюте трат, если вводят итоговую),
    // оставшаяся строка (обычно «Спишется с карты») — мелко под полем ввода.
    val lines = buildList {
        add(Line.SPEND)
        if (wallet.home != wallet.spend) add(Line.HOME)
        if (state.showCardLine) add(Line.CARD)
    }
    val heroLine = if (state.active == Line.HOME) Line.SPEND else Line.HOME
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(wallet.spend, wallet.home, onOpenCurrencies, onOpenCheat)
        // Верх экрана на низких телефонах прокручивается, низ (ввод, чипы, клавиатура) всегда на месте.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < COMPACT_TOP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    if (countryOffer != null) CountryBanner(countryOffer, onCountry)
                    BankCardsRow(
                        cards = wallet.cards,
                        quote = state.quote,
                        selectedId = wallet.selected?.id,
                        spend = wallet.spend,
                        home = wallet.home,
                        amount = state.totals.spend,
                        onSelect = onSelectCard,
                        onEdit = onEditCard,
                        onAdd = onAddCard,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                ResultBlock(state, heroLine, compact, onSelectCard, onStepCard, onAddReceipt, onRefreshRates)
                Spacer(Modifier.height(4.dp))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            InputField(state, lines, onSelectLine)
            for (line in lines) {
                if (line != state.active && line != heroLine) SecondaryLine(state, line, onSelectLine)
            }
            Spacer(Modifier.height(8.dp))
            Chips(wallet.spend, onChip)
            Spacer(Modifier.height(8.dp))
            Keypad(onKey = onKey, onCamera = onOpenCamera)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun valueText(state: MainUiState, line: Line): String =
    if (state.active == line) state.activeText else state.totals.of(line)?.let(Numbers::money) ?: "—"

private fun Line.currency(state: MainUiState): Currency? = when (this) {
    Line.SPEND -> state.wallet.spend
    Line.HOME -> state.wallet.home
    Line.CARD -> state.selectedCard?.currency
}

/** «Казахстан» по коду страны; если система не знает код — сам код. */
private fun countryName(iso: String): String =
    runCatching { Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.forLanguageTag("ru")) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(iso, ignoreCase = true) }
        ?: iso

/** «🇰🇿 Казахстан: считать в KZT?» — [Да] [×]. Страна — из сотовой сети, без интернета. */
@Composable
private fun CountryBanner(offer: CountryOffer, onCountry: (accept: Boolean) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val c = offer.currency
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.flag, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.country_offer, countryName(offer.iso), c.code),
            style = type.body.copy(fontSize = 14.sp),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val yes = stringResource(R.string.country_offer_yes, c.code)
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) { onCountry(true) }
                .semantics { contentDescription = yes }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.country_offer_switch), style = type.bodyStrong.copy(fontSize = 14.sp), color = colors.accent)
        }
        val close = stringResource(R.string.country_offer_close)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = close) { onCountry(false) }
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Шапка: «🇦🇪 AED → ₽ ▾» (выбор валют) и шпаргалка. */
@Composable
private fun TopBar(spend: Currency, home: Currency, onOpenCurrencies: () -> Unit, onOpenCheat: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val a11y = stringResource(R.string.currency_button_a11y, spend.nameRu, home.nameRu)
        val text = buildAnnotatedString {
            append(spend.code)
            withStyle(SpanStyle(color = colors.muted, fontWeight = FontWeight.Normal)) { append("  →  ") }
            append(home.symbol)
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surface)
                .clickable(role = Role.Button, onClick = onOpenCurrencies)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                    heading()
                }
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spend.flag, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(text, style = type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("▾", style = type.caption, color = colors.muted)
        }
        Spacer(Modifier.weight(1f))
        val cheat = stringResource(R.string.cheat_button)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = cheat, onClick = onOpenCheat)
                .semantics { contentDescription = cheat },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_table), contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** Что сейчас крупно: смена ключа (карта, валюты) анимируется, набор цифр — нет. */
private data class HeroKey(val cardId: String?, val spend: Currency, val home: Currency, val line: Line)

private class HeroContent(val key: HeroKey, val cardIndex: Int, val text: AnnotatedString, val size: Int)

/**
 * Крупная сумма, чем посчитана, какая карта выгоднее и курс ЦБ.
 * Свайп по блоку влево/вправо — соседняя карта.
 */
@Composable
private fun ResultBlock(
    state: MainUiState,
    heroLine: Line,
    compact: Boolean,
    onSelectCard: (String) -> Unit,
    onStepCard: (Int) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onRefreshRates: () -> Unit,
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
        val base = if (compact) 48 else 60
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
        Comparison(state, onSelectCard)
        OfficialLine(state, onRefreshRates)
    }
}

/** «Карта в юанях · оценка · добавить чек ›»: по какой карте и насколько точно посчитано. */
@Composable
private fun Basis(state: MainUiState, onAddReceipt: (BankCard) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val card = state.selectedCard
    val q = state.selectedQuote
    val style = type.body.copy(fontSize = 14.sp)
    val text = when {
        state.homePerUnit == null -> stringResource(R.string.result_no_rate)
        card != null && q != null -> stringResource(R.string.result_card, card.name, basisShort(q))
        else -> stringResource(R.string.result_by_official)
    }
    // Оценка (нет чека покупки в валюте трат) — предлагаем добавить чек, курс станет точным.
    val hint = card?.takeIf { q?.basis == RateBasis.ESTIMATE }
    val hintA11y = stringResource(R.string.add_receipt_a11y, state.wallet.spend.nameRu)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (hint != null) Modifier.clickable(role = Role.Button, onClickLabel = hintA11y) { onAddReceipt(hint) } else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = style,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (hint != null) {
            Text(stringResource(R.string.add_receipt), style = style.copy(fontWeight = FontWeight.Medium), color = colors.accent, maxLines = 1)
        }
    }
}

/**
 * Какой картой платить: у выгодной — «На 13 ₽ (2,7 %) дешевле, чем «Карта в тенге»» зелёным;
 * у остальных — «Дешевле на 13 ₽ (2,7 %) — «Карта в юанях» ›», тап выбирает выгодную карту.
 */
@Composable
private fun Comparison(state: MainUiState, onSelectCard: (String) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val c = state.quote.compare(state.selectedCard?.id) ?: return
    val percent = Numbers.percent(c.percent)
    if (percent == "0") return
    val amount = state.totals.spend
    val approx = if (c.isExact) "" else "≈"
    val diff = if (amount.signum() > 0) {
        stringResource(R.string.compare_amount, approx + Numbers.cheat(c.difference(amount)), state.wallet.home.symbol, percent)
    } else {
        stringResource(R.string.compare_percent, approx + percent)
    }
    val style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    if (c.selectedIsBest) {
        // Та же высота, что у кнопки ниже: при свайпе между картами ничего не прыгает.
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.compare_best, diff, c.other.card.name),
                style = style,
                color = colors.good,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        val a11y = stringResource(R.string.compare_other_a11y, c.other.card.name, diff)
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accentSoft)
                .clickable(role = Role.Button) { onSelectCard(c.other.card.id) }
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.compare_other, diff, c.other.card.name),
                style = style,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** «ЦБ: 100 ₸ = 17,65 ₽ · 30 окт ↻» — курс ЦБ на ту же единицу, дата и обновление по тапу. */
@Composable
private fun OfficialLine(state: MainUiState, onRefresh: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val official = state.quote.official
    val rates = state.official
    val text = when {
        state.ratesLoading -> stringResource(R.string.official_loading_short)
        official == null || rates == null -> stringResource(R.string.official_missing_short)
        else -> {
            val u = Numbers.unitFor(official)
            val unitLabel = if (u.compareTo(BigDecimal.ONE) == 0) "1 ${wallet.spend.symbol}" else "${Numbers.moneyWhole(u)} ${wallet.spend.symbol}"
            val line = stringResource(
                R.string.official_row,
                unitLabel,
                Numbers.rate(official.multiply(u, MC), 2),
                wallet.home.symbol,
                RuDates.short(rates.date),
            )
            if (state.ratesFailed) line + stringResource(R.string.official_offline) else line
        }
    }
    val refresh = stringResource(R.string.official_refresh)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = refresh, enabled = !state.ratesLoading, onClick = onRefresh)
            .semantics { contentDescription = text }
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

/** Поле ввода: валюта (тап — вводить в другой), выражение «22 + 15,5 =» и сумма. */
@Composable
private fun InputField(state: MainUiState, lines: List<Line>, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val line = state.active
    val currency = line.currency(state) ?: state.wallet.spend
    val label = if (line == Line.CARD) state.selectedCard?.name.orEmpty() else currency.shortRu
    val value = valueText(state, line)
    val next = lines.getOrNull((lines.indexOf(line) + 1) % lines.size)?.takeIf { it != line }
    val a11y = stringResource(R.string.input_a11y, label, value, currency.symbol)
    val switchLabel = stringResource(R.string.input_switch)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(max = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (next != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = switchLabel) { onSelectLine(next) }
                    } else {
                        Modifier
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = a11y
                    if (next != null) role = Role.Button
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = type.label, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (next != null) Text(" ⇅", style = type.label, color = colors.accent, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (state.expression.isCompound) state.expression.display() + " =" else "",
            style = type.body.copy(fontSize = 14.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = if (value.length > 12) type.number.copy(fontSize = 24.sp, lineHeight = 30.sp) else type.number,
            color = colors.ink,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = currency.symbol,
            style = type.body.copy(fontSize = 18.sp),
            color = colors.muted,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** «Спишется с карты  3 438,58 ₸» — тап переносит ввод в эту валюту. */
@Composable
private fun SecondaryLine(state: MainUiState, line: Line, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val currency = line.currency(state) ?: return
    val label = if (line == Line.CARD) stringResource(R.string.debit_card) else currency.shortRu
    val value = valueText(state, line)
    val a11y = stringResource(R.string.line_a11y, label, value, currency.symbol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.input_here)) { onSelectLine(line) }
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.body.copy(fontSize = 13.5.sp), color = colors.muted, maxLines = 1, modifier = Modifier.weight(1f))
        Text("$value ${currency.symbol}", style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
    }
}

/** Быстрые суммы в валюте трат: семь в один ряд; у дирхама 22 — проезд в метро. */
@Composable
private fun Chips(spend: Currency, onChip: (BigDecimal) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (amount in MainViewModel.chips(spend)) {
            val metro = spend == Currency.AED && amount.toInt() == 22
            val label = compact(amount)
            val a11y = if (metro) stringResource(R.string.chip_metro_a11y) else stringResource(R.string.chip_a11y, amount.toPlainString(), spend.code)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface)
                    .clickable(role = Role.Button) { onChip(amount) }
                    .clearAndSetSemantics {
                        contentDescription = a11y
                        role = Role.Button
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = type.label.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1)
                if (metro) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "M",
                        style = type.caption.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.accent)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** «500», «2K», «50K», «1M». */
private fun compact(amount: BigDecimal): String = when {
    amount >= BigDecimal(1_000_000) -> Numbers.rate(amount.divide(BigDecimal(1_000_000)), 1) + "M"
    amount >= BigDecimal(1000) -> Numbers.rate(amount.divide(BigDecimal(1000)), 1) + "K"
    else -> amount.toPlainString()
}
