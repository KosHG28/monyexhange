package app.dirhamrub.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Rounding
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.sign
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Подсказка «вы в другой стране»: [iso] — страна сотовой сети, [currency] — её валюта трат. */
data class CountryOffer(val iso: String, val currency: Currency)

/** Где тратим: флаг и название страны ([Countries.placeFor]); валюта многих стран без выбранной — название валюты («Евро»). */
fun placeOf(spend: Currency, chosen: String?, networkIso: String?): Pair<String, String> {
    val country = Countries.placeFor(spend, chosen, networkIso)
    return if (country != null) country.flag to country.nameRu else spend.flag to spend.shortRu
}

/**
 * Главный и единственный экран. Сверху вниз: страна и настройки; огромный итог в рублях (по выбранному
 * способу, без подписей); поле «Цена на ценнике»; карточки способов оплаты (касание — выбрать);
 * курс ЦБ; «＋ Добавить»; клавиатура с камерой. Считается на лету.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    place: Pair<String, String>,
    countryOffer: CountryOffer?,
    tileOffer: Boolean,
    onTileOffer: (accept: Boolean) -> Unit,
    onKey: (Key) -> Unit,
    /** Касание карточки: выбрать способ (касание лучшей — снова считать по лучшей). */
    onSelectCard: (String) -> Unit,
    onEditCard: (BankCard) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    /** Касание итога — ввод в другой валюте (рубли ↔ валюта трат). */
    onSelectLine: (Line) -> Unit,
    /** Касание страны в шапке — полноэкранный выбор страны. */
    onOpenCountries: () -> Unit,
    /** ⓘ рядом со страной — шторка «всё о стране». */
    onOpenPlace: () -> Unit,
    onOpenSettings: () -> Unit,
    onCountry: (accept: Boolean) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenCamera: () -> Unit,
    /** Центр клавиши камеры на экране — из неё раскрывается камера. */
    onCameraPlaced: (Offset) -> Unit = {},
    /** Главный закрыт другим экраном (настройки, страны, камера): курсор не мигает впустую. */
    covered: Boolean = false,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    val quote = state.quote
    val short = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(place, onOpenCountries, onOpenPlace, onOpenSettings)
        // Итог и поле цены всегда на месте; прокручивается только то, что ниже (карточки, курс ЦБ),
        // с мягким затуханием у краёв. Клавиатура тоже всегда на месте.
        Column(Modifier.padding(horizontal = 16.dp)) {
            when {
                countryOffer != null -> CountryBanner(countryOffer, onCountry)
                tileOffer -> TileBanner(onTileOffer)
            }
            Hero(state, short, onSelectLine)
            PriceField(state, covered)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.homePerUnit == null) {
                // Считать не по чему — понятный блок с одной кнопкой вместо «—».
                Spacer(Modifier.height(8.dp))
                EmptyRate(state, onRefreshRates, onAddReceipt, onAddCard)
            }
            val cards = remember(wallet, quote) {
                wallet.usable.sortedBy { quote.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
            }
            Spacer(Modifier.height(10.dp))
            if (cards.isEmpty()) {
                Text(
                    stringResource(R.string.nocards_text),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            } else {
                PayList(state, cards, onSelectCard, onEditCard)
            }
            CbrLine(state, onRefreshRates)
            AddButton(onAddCard)
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(6.dp))
            Keypad(onKey = onKey, onCamera = onOpenCamera, onCameraPlaced = onCameraPlaced)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** «🇦🇲 Армения ⌄» (сменить страну), рядом ⓘ (всё о стране), справа — настройки. У каждой кнопки одно действие. */
@Composable
private fun Header(place: Pair<String, String>, onCountries: () -> Unit, onPlace: () -> Unit, onSettings: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeA11y = stringResource(R.string.place_a11y, place.second)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onCountries)
                    .clearAndSetSemantics {
                        contentDescription = placeA11y
                        role = Role.Button
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Без подложки: страна, ⓘ и настройки лежат прямо на фоне, зоны касания — 48 dp.
                Row(
                    Modifier.padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(place.first, fontSize = 17.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        place.second,
                        style = AppTheme.type.bodyStrong.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                }
            }
            val tools = stringResource(R.string.place_tools_a11y, place.second)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = tools, onClick = onPlace)
                    .semantics { contentDescription = tools },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.ink, modifier = Modifier.size(23.dp))
            }
        }
        val settings = stringResource(R.string.settings_title)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = settings, onClick = onSettings)
                .semantics { contentDescription = settings },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tune), contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
        }
    }
}

/** Сумма для показа по настройке округления: «3 706», «3 710», «3 706,06»; меньше 10 — с копейками. */
private fun big(value: BigDecimal, rounding: Rounding): String = Numbers.total(value, rounding)

/** Приблизительный ли курс выбранного способа (оценка, UnionPay, ЦБ или карт нет вовсе). */
private fun approxNow(state: MainUiState): Boolean =
    state.selectedQuote?.basis?.let { it.approximate || it == RateBasis.OFFICIAL } != false

/**
 * Итог — самые крупные цифры экрана, без подписей: «67 ₽», «≈ 72 ₽». Ничего не набрано — тусклый «0 ₽».
 * Длинное число уменьшает шрифт, чтобы остаться в одну строку. Касание — ввод в рублях (и обратно).
 */
@Composable
private fun Hero(state: MainUiState, short: Boolean, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val homeInput = state.active == Line.HOME
    val currency = if (homeInput) state.wallet.spend else state.wallet.home
    val value = if (homeInput) state.totals.spend else state.totals.home
    val empty = state.expression.isEmpty || value == null
    val approx = !empty && approxNow(state)
    val number = if (empty) "0" else big(value!!, state.settings.rounding)
    // Самая крупная строка — единственное место, где валюта знаком у любой валюты, у которой он есть («800 ֏»):
    // крупно знак смотрится лучше кода. Везде ещё — код ([Currency.mark]); TalkBack читает код.
    val sign = currency.sign
    val text = buildAnnotatedString {
        if (approx) withStyle(SpanStyle(color = colors.muted, fontSize = 0.6.em)) { append("≈ ") }
        append(number)
        withStyle(SpanStyle(color = if (empty) colors.ink else colors.muted, fontSize = 0.62.em)) { append(" " + sign) }
    }
    val a11y = stringResource(R.string.hero_a11y, (if (approx) "≈ " else "") + number, currency.mark)
    val switchLabel = stringResource(if (homeInput) R.string.hero_switch_spend else R.string.hero_switch_home, state.wallet.spend.code)
    val maxSize = if (short) 72f else 92f
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (short) 104.dp else 136.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = switchLabel) { onSelectLine(if (homeInput) Line.SPEND else Line.HOME) }
            .clearAndSetSemantics {
                contentDescription = a11y
                liveRegion = LiveRegionMode.Polite
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        // Плейсхолдер — то же число с заливкой, полупрозрачное, плотным начертанием (тонкое на прозрачности выглядит контуром).
        val style = AppTheme.type.hero.copy(
            color = colors.ink,
            fontWeight = if (empty) FontWeight.Medium else FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        FitSize(text, style, maxSize, 40f) { size ->
            // «≈» и «₽» — по центру высоты цифр, а не по базовой линии: строки обрезаны до высоты шрифта
            // и выровнены по центру ряда.
            Row(Modifier.alpha(if (empty) 0.25f else 1f), verticalAlignment = Alignment.CenterVertically) {
                if (approx) {
                    HeroPart("≈", style, size * SIGN_SCALE, colors.muted)
                    Spacer(Modifier.width((size * 0.12f).dp))
                }
                HeroPart(number, style, size, colors.ink)
                Spacer(Modifier.width((size * 0.12f).dp))
                HeroPart(sign, style, size * SIGN_SCALE, if (empty) colors.ink else colors.muted)
            }
        }
    }
}

/** Знаки «≈» и «₽» — мельче цифр. */
private const val SIGN_SCALE = 0.6f

@Composable
private fun HeroPart(text: String, style: TextStyle, sizeSp: Float, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = style.copy(
            fontSize = sizeSp.sp,
            lineHeight = sizeSp.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** Размер шрифта, при котором [text] помещается в одну строку по ширине: от [maxSp] вниз до [minSp]. */
@Composable
private fun FitSize(text: AnnotatedString, style: TextStyle, maxSp: Float, minSp: Float, content: @Composable (Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val limit = constraints.maxWidth
        val size = remember(text, limit, style, maxSp) {
            var s = maxSp
            while (s > minSp && measurer.measure(text, style.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > limit) s -= 2f
            s
        }
        content(size)
    }
}

/**
 * Поле ввода: «Цена на ценнике» слева вверху, справа набираемая сумма с курсором вплотную к последней цифре
 * и код валюты; выражение «1 200 + 300 =» — мелко под суммой. При вводе в рублях — «Сколько у вас».
 */
@Composable
private fun PriceField(state: MainUiState, covered: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val homeInput = state.active == Line.HOME
    val currency = if (homeInput) state.wallet.home else state.wallet.spend
    val label = stringResource(if (homeInput) R.string.field_home_input else R.string.field_price)
    val value = state.activeText
    val a11y = stringResource(R.string.field_a11y, label, value, currency.code)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, colors.accent, shape)
            .padding(start = 16.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Text(label, style = type.caption, color = colors.muted, maxLines = 1)
        val size = when {
            value.length > 13 -> 20.sp
            value.length > 10 -> 25.sp
            else -> 32.sp
        }
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = type.number.copy(fontSize = size, lineHeight = size * 1.2f), color = colors.ink, maxLines = 1, softWrap = false)
            Caret(size.value, paused = covered)
            Spacer(Modifier.width(6.dp))
            Text(currency.code, style = type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = colors.muted, maxLines = 1)
        }
        if (state.expression.isCompound) {
            Text(
                state.expression.display() + " =",
                style = type.caption.copy(fontSize = 12.5.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Мигающий курсор сразу за последней цифрой; без анимаций и под закрывающим главный экраном — просто черта.
 * Прозрачность меняется в слое — без рекомпозиций.
 */
@Composable
private fun Caret(fontSp: Float, paused: Boolean) {
    val still = paused || LocalReduceMotion.current
    val blink = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "caret")
            .animateFloat(1f, 0f, infiniteRepeatable(tween(530, delayMillis = 300), RepeatMode.Reverse), label = "caretAlpha")
    }
    Box(
        Modifier
            .padding(start = 2.dp)
            .size(width = 2.dp, height = (fontSp * 0.95f).dp)
            .graphicsLayer { alpha = blink?.value ?: 1f }
            .background(AppTheme.colors.accent, RoundedCornerShape(1.dp)),
    )
}

/**
 * Способы оплаты, от дешёвого к дорогому. Лучший — мягкий зелёный фон и зелёная сумма, «выгоднее всего»;
 * выбранный (по нему итог) — синяя обводка. Касание — выбрать; касание выбранного или удержание — изменить.
 * Ничего не набрано — вместо сумм курс каждого способа («22,40 ₽ / 100 AMD»).
 */
@Composable
private fun PayList(state: MainUiState, cards: List<BankCard>, onSelect: (String) -> Unit, onEdit: (BankCard) -> Unit) {
    val quote = state.quote
    val best = quote.best?.takeIf { quote.quotes.size > 1 }
    val selectedId = state.selectedCard?.id
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (card in cards) {
            val q = quote.of(card.id)
            val selected = card.id == selectedId
            PayRow(
                state = state,
                card = card,
                q = q,
                best = best,
                selected = selected,
                onClick = { if (selected) onEdit(card) else onSelect(card.id) },
                onLongClick = { onEdit(card) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayRow(
    state: MainUiState,
    card: BankCard,
    q: CardQuote?,
    best: CardQuote?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val isBest = best != null && best.card.id == card.id
    val homeInput = state.active == Line.HOME
    val amount = state.totals.spend
    // Отрицательный итог («100 − 250») картам не сумма: показываем их курсы.
    val hasAmount = !state.expression.isEmpty && amount.signum() > 0
    val rounding = state.settings.rounding
    val approx = q != null && (q.basis.approximate || q.basis == RateBasis.OFFICIAL)
    val network = networkName(card.paymentNetwork)
    val sub: String
    val right: String
    var rightSub: String? = null
    when {
        q == null -> {
            sub = stringResource(R.string.money_no_rate, wallet.spend.code)
            right = "—"
        }
        hasAmount && homeInput -> {
            // Ввод в рублях: сколько валюты трат выйдет этим способом и насколько меньше, чем лучшим.
            val home = state.totals.home ?: BigDecimal.ZERO
            val spend = if (q.homePerUnit.signum() == 0) BigDecimal.ZERO else home.divide(q.homePerUnit, MC)
            val bestSpend = best?.homePerUnit?.takeIf { it.signum() != 0 }?.let { home.divide(it, MC) }
            // Разница — из показанных сумм, чтобы сходилась с ними на экране.
            val less = bestSpend?.let { Numbers.rounded(it, rounding).subtract(Numbers.rounded(spend, rounding)) }
                ?.takeIf { !isBest && it.signum() > 0 }
            sub = when {
                isBest -> stringResource(R.string.pay_best_most)
                less != null -> join(network, stringResource(R.string.pay_less, Numbers.saving(less) + " " + wallet.spend.code)) ?: ""
                else -> network ?: ""
            }
            right = (if (approx) "≈ " else "") + big(spend, state.settings.rounding) + " " + wallet.spend.code
        }
        hasAmount -> {
            val dearer = best?.let {
                Numbers.rounded(amount.multiply(q.homePerUnit, MC), rounding).subtract(Numbers.rounded(amount.multiply(it.homePerUnit, MC), rounding))
            }?.takeIf { !isBest && it.signum() > 0 }
            sub = when {
                isBest -> stringResource(R.string.pay_best)
                dearer != null -> join(network, stringResource(R.string.pay_dearer, Numbers.saving(dearer) + " " + wallet.home.mark)) ?: ""
                else -> network ?: ""
            }
            right = (if (approx) "≈ " else "") + big(amount.multiply(q.homePerUnit, MC), state.settings.rounding) + " " + wallet.home.mark
        }
        else -> {
            // Ничего не набрано: курс способа и когда пополняли.
            sub = join(network, lastTopUp(card)) ?: ""
            val unit = Numbers.unitFor(q.homePerUnit)
            right = (if (approx) "≈ " else "") + Numbers.rateShort(q.homePerUnit.multiply(unit, MC)) + " " + wallet.home.mark
            rightSub = Numbers.moneyWhole(unit) + " " + wallet.spend.code
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        isBest -> colors.goodSoft
        selected -> colors.accentSoft
        else -> colors.surface
    }
    val a11y = stringResource(R.string.pay_row_a11y, card.name, sub, right)
    val editLabel = stringResource(R.string.card_edit_a11y)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(bg)
            // Синяя обводка — только у выбранного вручную; лучший и так выделен зелёным.
            .then(if (selected && !isBest) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = editLabel)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PayIcon(card, isBest)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Text(sub, style = type.caption, color = if (isBest && hasAmount) colors.good else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            // Длинная сумма (донги, рупии) — мельче, чтобы не прилипать к краю.
            val size = when {
                right.length > 12 -> 14.sp
                right.length > 9 -> 16.sp
                else -> 19.sp
            }
            Text(
                right,
                style = type.number.copy(fontSize = size, lineHeight = size * 1.2f, fontWeight = if (isBest) FontWeight.Medium else FontWeight.Normal),
                color = if (isBest) colors.good else colors.ink,
                maxLines = 1,
                softWrap = false,
            )
            rightSub?.let { Text(it, style = type.caption, color = colors.muted, maxLines = 1) }
        }
    }
}

/** Значок способа: у наличных — купюра, у карты — мини-карта в цветах её оформления с подписью системы. */
@Composable
internal fun PayIcon(card: BankCard, best: Boolean) {
    val colors = AppTheme.colors
    if (card.kind == CardKind.CASH) {
        Box(Modifier.size(width = 38.dp, height = 26.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_cash), contentDescription = null, tint = if (best) colors.good else colors.ink, modifier = Modifier.size(28.dp))
        }
        return
    }
    val style = CardPalette.style(card)
    Box(
        Modifier
            .size(width = 38.dp, height = 26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.linearGradient(listOf(style.start, style.end))),
    ) {
        Box(
            Modifier
                .padding(start = 5.dp, top = 6.dp)
                .size(width = 8.dp, height = 6.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(style.chip),
        )
        networkMark(card.paymentNetwork)?.let { mark ->
            Text(
                mark,
                style = AppTheme.type.caption.copy(fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold),
                color = CardPalette.onCard,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun networkName(n: CardNetwork?): String? = when (n) {
    CardNetwork.UNIONPAY, CardNetwork.UNIONPAY_FOREIGN -> stringResource(R.string.network_unionpay)
    CardNetwork.MIR -> stringResource(R.string.network_mir)
    CardNetwork.VISA_MC_FOREIGN -> stringResource(R.string.network_visa_mc)
    null -> null
}

@Composable
private fun networkMark(n: CardNetwork?): String? = when (n) {
    CardNetwork.UNIONPAY, CardNetwork.UNIONPAY_FOREIGN -> stringResource(R.string.network_unionpay_short)
    CardNetwork.MIR -> stringResource(R.string.network_mir_short)
    CardNetwork.VISA_MC_FOREIGN -> stringResource(R.string.network_visa_mc_short)
    null -> null
}

/** «обмен 9 ноя» у наличных, «пополнение 8 ноя» у карты; без пополнения — ничего. */
@Composable
private fun lastTopUp(card: BankCard): String? {
    val date = card.transfer?.takeIf { it.isValid }?.date ?: return null
    return stringResource(if (card.kind == CardKind.CASH) R.string.money_exchanged else R.string.money_topped_up, RuDates.short(date))
}

private fun join(a: String?, b: String?): String? = listOfNotNull(a?.takeIf { it.isNotEmpty() }, b?.takeIf { it.isNotEmpty() })
    .joinToString(" · ")
    .ifEmpty { null }

/** «Курс ЦБ: 100 AMD = 22,00 ₽ · сегодня 09:40» — мелко серым под списком; касание — обновить курсы. */
@Composable
private fun CbrLine(state: MainUiState, onRefresh: () -> Unit) {
    val colors = AppTheme.colors
    val rate = state.quote.official
    val text = when {
        state.ratesLoading -> stringResource(R.string.cbr_loading)
        rate == null -> stringResource(R.string.cbr_none)
        else -> {
            val (unit, value) = rateSides(rate, state.wallet.spend, state.wallet.home)
            val label = stringResource(if (state.quote.viaUnionPay) R.string.cbr_label_unionpay else R.string.cbr_label)
            val pair = "$unit = $value"
            val time = fetchedAt(state.officialFetchedAt)
            if (time == null) stringResource(R.string.cbr_line, label, pair) else stringResource(R.string.cbr_line_time, label, pair, time)
        }
    }
    val refresh = stringResource(R.string.official_refresh)
    Text(
        text,
        style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
        color = colors.muted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = refresh, onClick = onRefresh)
            .padding(vertical = 8.dp),
    )
}

/** «сегодня 09:40», «вчера 18:02», «3 ноя»; ещё не скачивали — null. */
@Composable
private fun fetchedAt(at: Long): String? {
    if (at <= 0L) return null
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val time = "%02d:%02d".format(t.hour, t.minute)
    val today = LocalDate.now()
    return when (val day = t.toLocalDate()) {
        today -> stringResource(R.string.cbr_today, time)
        today.minusDays(1) -> stringResource(R.string.cbr_yesterday, time)
        else -> RuDates.short(day)
    }
}

/** «1 AMD» и «22,19 ₽»; у дешёвых валют — «100 AMD», «1 000 VND». */
internal fun rateSides(homePerUnit: BigDecimal, spend: Currency, home: Currency): Pair<String, String> {
    val unit = Numbers.unitFor(homePerUnit)
    return "${Numbers.moneyWhole(unit)} ${spend.code}" to "${Numbers.rateShort(homePerUnit.multiply(unit, MC))} ${home.mark}"
}

/** Призрачная кнопка «＋ Добавить карту или наличные» — без рамки и подложки. */
@Composable
private fun AddButton(onAdd: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onAdd),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.money_add_short), style = AppTheme.type.bodyStrong.copy(fontWeight = FontWeight.Medium), color = colors.accent)
    }
}
