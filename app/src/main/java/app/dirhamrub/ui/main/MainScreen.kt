package app.dirhamrub.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import kotlinx.coroutines.delay

import app.dirhamrub.diag.RecompositionProbe
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import app.dirhamrub.ui.theme.fieldGlow
import app.dirhamrub.ui.theme.surfaceOutline
import app.dirhamrub.ui.theme.banknoteSurface
import app.dirhamrub.ui.theme.banknoteTheme
import app.dirhamrub.ui.theme.cardMaterial
import app.dirhamrub.ui.theme.forMaterial
import app.dirhamrub.ui.theme.HighFrameRate
import app.dirhamrub.ui.components.*
import app.dirhamrub.ui.components.TypingText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.zIndex
import app.dirhamrub.ui.theme.payRowSurface
import app.dirhamrub.ui.theme.PayRowShadow
import app.dirhamrub.ui.theme.paymentRowShadow
import androidx.compose.animation.core.Animatable
import app.dirhamrub.ui.theme.Springs
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import app.dirhamrub.ui.components.flightOrigin
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.rateLabel
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.domain.InputLimit
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
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    states: StateFlow<MainUiState>,
    place: Pair<String, String>,
    countryOffer: CountryOffer?,
    tileOffer: Boolean,
    onTileOffer: (accept: Boolean) -> Unit,
    onKey: (Key) -> Unit,
    /** Касание карточки: выбрать способ (касание лучшей — снова считать по лучшей). */
    onSelectCard: (String) -> Unit,
    onReorderPayments: (List<String>) -> Unit,
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
) {
    val state by states.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val wallet = state.wallet
    val quote = state.quote
    val short = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
    val compactAdd = wallet.cards.size >= 2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)),
    ) {
        Header(place.first, place.second, onOpenCountries, onOpenPlace, onOpenSettings)
        // Итог и поле цены всегда на месте; прокручивается только то, что ниже (карточки, курс ЦБ),
        // с мягким затуханием у краёв. Клавиатура тоже всегда на месте.
        Column(Modifier.padding(horizontal = 16.dp)) {
            when {
                countryOffer != null -> CountryBanner(countryOffer, onCountry)
                tileOffer -> TileBanner(onTileOffer)
            }
            Hero(state, short, compactAdd, onSelectLine)
            PriceField(state)
            val hint = when (state.inputLimit) {
                InputLimit.WHOLE -> stringResource(R.string.key_limit_whole)
                InputLimit.FRACTION -> stringResource(R.string.key_limit_fraction, state.fractionDigits)
                InputLimit.TERMS -> stringResource(R.string.key_limit_terms)
                InputLimit.MULTIPLE_ZEROS -> stringResource(R.string.key_limit_multiple_zeros)
                null -> state.pendingConversion?.let { stringResource(R.string.key_pending_conversion, it.currency.code) }
            }
            if (hint != null) Text(
                hint, color = colors.muted, style = AppTheme.type.caption,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val paymentsScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingScroll(paymentsScroll)
                .padding(horizontal = 16.dp),
        ) {
            if (state.homePerUnit == null) {
                // Считать не по чему — понятный блок с одной кнопкой вместо «—».
                Spacer(Modifier.height(8.dp))
                EmptyRate(state, onRefreshRates, onAddReceipt, onAddCard)
            }
            val cards = remember(wallet, quote) {
                if (wallet.manualPaymentOrder) wallet.usable else
                    wallet.usable.sortedBy { quote.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
            }
            Spacer(Modifier.height(16.dp))
            if (cards.isEmpty()) {
                Text(
                    stringResource(R.string.nocards_text),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            } else {
                PayList(state, cards, paymentsScroll, onSelectCard, onEditCard, onReorderPayments)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fetchedAt = if (quote.viaUnionPay) state.unionPayFetchedAt else state.officialFetchedAt
                CbrLine(
                    quote.official, quote.viaUnionPay, wallet.spend, wallet.home, state.ratesLoading,
                    fetchedAt, LocalDate.now().toEpochDay(), ZoneId.systemDefault().id,
                    Modifier.weight(1f).padding(end = if (compactAdd) 8.dp else 0.dp), onRefreshRates,
                )
                if (compactAdd) {
                    RoundAction(
                        R.drawable.ic_plus, stringResource(R.string.money_add_short), 28.dp, 16.dp, onAddCard,
                        modifier = Modifier.tripMainEnter(TripEnter.ADD),
                        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!compactAdd) Box(Modifier.tripMainEnter(TripEnter.ADD)) { AddButton(onAddCard) }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(6.dp))
            Keypad(
                onKey = onKey, onCamera = onOpenCamera, onCameraPlaced = onCameraPlaced,
                showTripleZero = state.inputCurrency.magnitude >= 4 || state.inputCurrency.digits == 0,
                showDoubleZero = state.inputCurrency.digits == 0,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Выбор страны, памятка по оплате в этой стране и настройки. */
@Composable
private fun Header(flag: String, name: String, onCountries: () -> Unit, onPlace: () -> Unit, onSettings: () -> Unit) {
    RecompositionProbe("Header")
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeA11y = stringResource(R.string.place_a11y, name)
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f, fill = false).tripSharedBounds(TRIP_COUNTRY)
                    .heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = placeA11y, onClick = onCountries)
                    .clearAndSetSemantics {
                        contentDescription = placeA11y
                        role = Role.Button
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(flag, fontSize = 18.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    name,
                    style = AppTheme.type.bodyStrong.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp), tint = colors.muted)
            }
            RoundAction(
                R.drawable.ic_card, stringResource(R.string.place_tools_a11y, name), 32.dp, 18.dp, onPlace,
                modifier = Modifier.tripMainEnter(TripEnter.HEADER, 0),
                background = colors.surface2,
                tint = colors.ink,
            )
        }
        RoundAction(
            R.drawable.ic_tune, stringResource(R.string.settings_title), 38.dp, 22.dp, onSettings,
            modifier = Modifier.tripMainEnter(TripEnter.HEADER, 1),
            background = colors.surface,
            tint = colors.ink,
        )
    }
}

@Composable
private fun RoundAction(
    icon: Int,
    label: String,
    diameter: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color,
    tint: Color,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(diameter).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(iconSize), tint = tint)
        }
    }
}

/** Сумма для показа по настройке округления: «3 706», «3 710», «3 706,06»; меньше 10 — с копейками. */
private fun big(value: BigDecimal, rounding: Rounding, currency: Currency): String =
    Numbers.total(value, rounding, currency.digits.coerceAtLeast(2))

/** Приблизительный ли курс выбранного способа (оценка, UnionPay, ЦБ или карт нет вовсе). */
private fun approxNow(state: MainUiState): Boolean =
    state.selectedQuote?.basis?.let { it.approximate || it == RateBasis.OFFICIAL }
        ?: (state.wallet.spend != state.wallet.home)

/**
 * Итог — самые крупные цифры экрана, без подписей: «67 ₽», «≈ 72 ₽». Ничего не набрано — тусклый «0 ₽».
 * Длинное число уменьшает шрифт, чтобы остаться в одну строку. Касание — ввод в рублях (и обратно).
 */
@Composable
private fun Hero(state: MainUiState, short: Boolean, airy: Boolean, onSelectLine: (Line) -> Unit) {
    val colors = AppTheme.colors
    val homeInput = state.active == Line.HOME
    val currency = if (homeInput) state.wallet.spend else state.wallet.home
    val value = if (homeInput) state.displaySpend else state.totals.home
    val empty = state.expression.isEmpty || value == null
    val approx = !empty && approxNow(state)
    val number = when {
        value == null && (!state.expression.isEmpty || state.pendingConversion != null) -> "—"
        empty -> "0"
        else -> big(value, state.settings.rounding, currency)
    }
    // Самая крупная строка — единственное место, где валюта знаком у любой валюты, у которой он есть («800 ֏»):
    // крупно знак смотрится лучше кода. Везде ещё — код ([Currency.mark]); TalkBack читает код.
    val sign = currency.sign
    val approxColor = colors.muted.copy(alpha = 0.55f)
    val signColor = if (empty) colors.ink else colors.muted.copy(alpha = 0.85f)
    val text = buildAnnotatedString {
        if (approx) withStyle(SpanStyle(color = approxColor, fontSize = APPROX_SCALE.em)) { append("≈ ") }
        append(number)
        withStyle(SpanStyle(color = signColor, fontSize = SIGN_SCALE.em, fontWeight = FontWeight.Light)) { append(" " + sign) }
    }
    val a11y = stringResource(R.string.hero_a11y, (if (approx) "≈ " else "") + number, currency.mark)
    val switchLabel = stringResource(if (homeInput) R.string.hero_switch_spend else R.string.hero_switch_home, state.wallet.spend.code)
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.hero_copy)
    val copiedLabel = stringResource(R.string.hero_copied)
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    var copyCount by remember { mutableIntStateOf(0) }
    val copy: (() -> Unit)? = if (empty) null else ({
        context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
            clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, number.filterNot(Char::isWhitespace).replace('−', '-')))
            haptics.play(Haptic.TICK)
            copied = true
            copyCount++
        }
    })
    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            delay(1100)
            copied = false
        }
    }
    val maxSize = if (short) 72f else 92f
    // Сумма сменилась — цифры на миг «наливаются»: становятся плотнее и возвращаются к тонким.
    val pour = remember { Animatable(0f) }
    val reducePour = LocalReduceMotion.current
    HighFrameRate(pour.isRunning)
    LaunchedEffect(number, empty, reducePour) {
        if (empty || reducePour) {
            pour.snapTo(0f)
            return@LaunchedEffect
        }
        pour.animateTo(1f, tween(POUR_IN_MS))
        pour.animateTo(0f, Springs.effects())
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height((if (short) 104.dp else 136.dp) + if (airy) (if (short) 8.dp else 18.dp) else 0.dp)
            .tripClip(RoundedCornerShape(20.dp))
            .combinedClickable(role = Role.Button, onClickLabel = switchLabel, onLongClickLabel = copyLabel, onLongClick = copy,
                onClick = { onSelectLine(if (homeInput) Line.SPEND else Line.HOME) })
            .clearAndSetSemantics {
                contentDescription = if (copied) "$a11y. $copiedLabel" else a11y
                liveRegion = LiveRegionMode.Polite
                if (copy != null) onLongClick(copyLabel) { copy(); true }
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
            Row(Modifier.then(if (LocalTripSharedTransition.current?.primaryCardId != null) Modifier.tripSharedBounds(TRIP_HERO) else Modifier.tripMainEnter()).alpha(if (empty) 0.25f else 1f), verticalAlignment = Alignment.CenterVertically) {
                if (approx) {
                    HeroPart("≈", style, size * APPROX_SCALE, approxColor)
                    Spacer(Modifier.width((size * 0.075f).dp))
                }
                HeroPart(number, style, size, colors.ink, pour = pour.value)
                Spacer(Modifier.width((size * 0.09f).dp))
                HeroPart(sign, style.copy(fontWeight = FontWeight.Light), size * SIGN_SCALE, signColor)
            }
        }
        AnimatedVisibility(copied, modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reducePour) EnterTransition.None else fadeIn(tween(100)),
            exit = if (reducePour) ExitTransition.None else fadeOut(tween(135))) {
            Text(copiedLabel, style = AppTheme.type.label, color = colors.ink,
                modifier = Modifier.clip(CircleShape).background(colors.surface2).padding(horizontal = 12.dp, vertical = 5.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** Знаки «≈» и «₽» — мельче цифр. */
private const val APPROX_SCALE = 0.67f
private const val SIGN_SCALE = 0.6f

@Composable
private fun HeroPart(text: String, style: TextStyle, sizeSp: Float, color: androidx.compose.ui.graphics.Color, pour: Float = 0f) {
    val partStyle = style.copy(
        fontSize = sizeSp.sp,
        lineHeight = sizeSp.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    Box {
        Text(text, style = partStyle, color = color, maxLines = 1, softWrap = false)
        // «Наливается»: поверх — те же цифры обводкой того же начертания; ширина обводки — до 3 % кегля.
        if (pour > 0.01f) {
            val width = with(LocalDensity.current) { (sizeSp * POUR_STROKE * pour).sp.toPx() }
            Text(text, style = partStyle.copy(drawStyle = Stroke(width = width)), color = color, maxLines = 1, softWrap = false)
        }
    }
}

private const val POUR_IN_MS = 90
private const val POUR_STROKE = 0.03f

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
 * Поле ввода: «Цена на ценнике» слева вверху, справа набираемая сумма
 * и код валюты; выражение «1 200 + 300 =» — мелко под суммой. При вводе в рублях — «Сколько у вас».
 */
@Composable
private fun PriceField(state: MainUiState) {
    RecompositionProbe("PriceField")
    val colors = AppTheme.colors
    val type = AppTheme.type
    val homeInput = state.active == Line.HOME
    val currency = if (homeInput) state.wallet.home else state.wallet.spend
    val label = stringResource(if (homeInput) R.string.field_home_input else R.string.field_price)
    val value = if (state.pendingConversion != null && state.expression.isEmpty) "—" else state.activeText
    val a11y = stringResource(R.string.field_a11y, label, value, currency.code)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .tripSharedBounds(TRIP_PRICE)
            .fillMaxWidth()
            // Мягкое свечение цветом заливки поля и тонкая нейтральная обводка — без синего.
            .fieldGlow(corner = 18.dp)
            .clip(shape)
            .background(colors.surface)
            .surfaceOutline(shape)
            .padding(start = 16.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Text(label, style = type.caption, color = colors.muted, maxLines = 1, modifier = Modifier.tripMainEnter(TripEnter.PRICE_LABEL))
        val size = when {
            value.length > 13 -> 20.sp
            value.length > 10 -> 25.sp
            else -> 32.sp
        }
        Row(Modifier.align(Alignment.End).tripSharedBounds(TRIP_VALUE), verticalAlignment = Alignment.CenterVertically) {
            TypingText(value, style = type.number.copy(fontSize = size, lineHeight = size * 1.2f),
                color = colors.ink, fractionColor = colors.muted, empty = state.expression.isEmpty)
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
 * Способы оплаты, от дешёвого к дорогому. Лучший — мягкий зелёный фон и зелёная сумма, «выгоднее всего»;
 * выбранный (по нему итог) — синяя обводка. Касание — выбрать; касание выбранного или удержание — изменить.
 * Ничего не набрано — вместо сумм курс каждого способа («22,40 ₽ / 100 AMD»).
 */
@Composable
private fun PayList(
    state: MainUiState,
    cards: List<BankCard>,
    scroll: ScrollState,
    onSelect: (String) -> Unit,
    onEdit: (BankCard) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val quote = state.quote
    val best = quote.best?.takeIf { quote.quotes.size > 1 }
    val selectedId = state.selectedCard?.id
    val haptics = rememberHaptics()
    // Самая выгодная сменилась (другая валюта, новый чек) — короткое нарастание и щелчок. При открытии — тихо.
    val bestId = best?.card?.id
    var seenBest by remember { mutableStateOf(bestId) }
    LaunchedEffect(bestId) {
        if (bestId != null && seenBest != null && bestId != seenBest) haptics.play(Haptic.BEST)
        seenBest = bestId
    }
    val ids = remember(cards) { cards.map { it.id } }
    val byId = remember(cards) { cards.associateBy { it.id } }
    val drag = remember(ids, state.wallet.spend) { PaymentDrag(ids) }
    val movable = ids.size > 1
    val gesture = Modifier.paymentDragGesture(drag, scroll, movable, onReorder)
    val editLabel = stringResource(R.string.card_edit_a11y)
    val up = stringResource(R.string.payment_move_up)
    val down = stringResource(R.string.payment_move_down)
    val displayed = drag.order
    val primary = LocalTripSharedTransition.current?.primaryCardId
    Column(Modifier.fillMaxWidth().selectableGroup().then(gesture), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((ordinal, id) in displayed.withIndex()) {
            val card = byId.getValue(id)
            val q = quote.of(card.id)
            val selected = card.id == selectedId
            val actions = buildList {
                add(CustomAccessibilityAction(editLabel) { onEdit(card); true })
                if (ordinal > 0) add(CustomAccessibilityAction(up) { onReorder(displayed.moved(ordinal, ordinal - 1)); true })
                if (ordinal < displayed.lastIndex) add(CustomAccessibilityAction(down) { onReorder(displayed.moved(ordinal, ordinal + 1)); true })
            }
            key(id) { Box(
                Modifier.zIndex(if (drag.dragged == id) 2f else if (selected) 1f else 0f)
                    .paymentDragSlot(drag, id),
            ) { PayRow(
                state = state,
                card = card,
                q = q,
                best = best,
                selected = selected,
                dragged = drag.dragged == id,
                entranceIndex = displayed.take(ordinal).count { it != primary },
                modifier = Modifier.graphicsLayer { translationY = drag.translationOf(id) },
                actions = actions,
                onClick = {
                    if (selected) {
                        onEdit(card)
                    } else {
                        haptics.play(Haptic.SELECT)
                        onSelect(card.id)
                    }
                },
                onLongClick = if (movable) null else ({
                    haptics.play(Haptic.TICK)
                    onEdit(card)
                }),
            ) } }
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
    dragged: Boolean,
    entranceIndex: Int,
    modifier: Modifier,
    actions: List<CustomAccessibilityAction>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val isPrimary = card.id == LocalTripSharedTransition.current?.primaryCardId
    val isBest = best != null && best.card.id == card.id
    val homeInput = state.active == Line.HOME
    val amount = state.totals.spend
    // Отрицательный итог («100 − 250») картам не сумма: показываем их курсы.
    val hasAmount = !state.expression.isEmpty && if (homeInput) state.expression.value.signum() > 0 else amount.signum() > 0
    val rounding = state.settings.rounding
    val approx = q != null && (q.basis.approximate || q.basis == RateBasis.OFFICIAL)
    val network = networkName(card.paymentNetwork)
    val sub: String
    val right: String
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
            val digits = wallet.spend.digits.coerceAtLeast(2)
            val less = bestSpend?.let { Numbers.rounded(it, rounding, digits).subtract(Numbers.rounded(spend, rounding, digits)) }
                ?.takeIf { !isBest && it.signum() > 0 }
            sub = when {
                isBest -> stringResource(R.string.pay_best_most)
                less != null -> join(network, stringResource(R.string.pay_less, Numbers.saving(less, digits) + " " + wallet.spend.code)) ?: ""
                else -> network ?: ""
            }
            right = (if (approx) "≈ " else "") + big(spend, state.settings.rounding, wallet.spend) + " " + wallet.spend.code
        }
        hasAmount -> {
            val digits = wallet.home.digits.coerceAtLeast(2)
            val dearer = best?.let {
                Numbers.rounded(amount.multiply(q.homePerUnit, MC), rounding, digits)
                    .subtract(Numbers.rounded(amount.multiply(it.homePerUnit, MC), rounding, digits))
            }?.takeIf { !isBest && it.signum() > 0 }
            sub = when {
                isBest -> stringResource(R.string.pay_best)
                dearer != null -> join(network, stringResource(R.string.pay_dearer, Numbers.saving(dearer, digits) + " " + wallet.home.mark)) ?: ""
                else -> network ?: ""
            }
            right = (if (approx) "≈ " else "") + big(amount.multiply(q.homePerUnit, MC), state.settings.rounding, wallet.home) + " " + wallet.home.mark
        }
        else -> {
            // Ничего не набрано: курс способа и когда пополняли.
            sub = join(network, lastTopUp(card)) ?: ""
            right = rateLabel(q.homePerUnit, wallet.spend, wallet.home, approximate = approx)
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val reduce = LocalReduceMotion.current
    val grabTint = if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.accent.copy(alpha = 0.07f)
    val grab by animateFloatAsState(if (dragged) 1f else 0f,
        if (reduce) snap() else tween(if (dragged) 120 else 180), label = "paymentGrab")
    val bg by animateColorAsState(
        colors.payRowSurface(selected, isBest),
        if (reduce) snap() else tween(180), label = "paySurface",
    )
    val elevation by animateDpAsState(
        if (selected && !colors.isDark) 4.dp else 0.dp,
        if (reduce) snap() else tween(180), label = "payElevation",
    )
    val shadowAlpha = animateFloatAsState(if (selected && !colors.isDark) 1f else 0f,
        if (reduce) snap() else tween(180), label = "paySurroundShadow")
    val a11y = stringResource(R.string.pay_row_a11y, card.name, sub, right)
    val editLabel = stringResource(R.string.card_edit_a11y)
    // Выбранный способ пружинисто «приподнимается»: чуть крупнее, с мягкой тенью (Material 3 Expressive).
    val lift by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "payLift",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .tripMainEnter(if (isPrimary) TripEnter.PRIMARY_DETAILS else TripEnter.PAYMENT, entranceIndex)
            .graphicsLayer {
                val s = (1f + LIFT_SCALE * lift) * (1f + (if (reduce) 0f else 0.02f) * grab)
                scaleX = s
                scaleY = s
                shadowElevation = if (colors.isDark) 0f else elevation.toPx()
                spotShadowColor = PayRowShadow.spot
                ambientShadowColor = PayRowShadow.ambient
                this.shape = shape
            }
            .paymentRowShadow(enabled = !colors.isDark, opacity = { shadowAlpha.value })
            .clip(shape)
            .background(bg)
            .surfaceOutline(shape)
            .drawWithContent {
                val amount = grab
                if (amount > 0f) drawRect(grabTint.copy(alpha = grabTint.alpha * amount))
                drawContent()
                if (amount > 0f) {
                    val half = 0.5.dp.toPx()
                    inset(half) {
                        drawRoundRect(color = colors.accent.copy(alpha = 0.38f * amount),
                            cornerRadius = CornerRadius(16.dp.toPx() - half), style = Stroke(half * 2f))
                    }
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = editLabel)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.RadioButton
                this.selected = selected
                customActions = actions
            }
            .padding(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PayIcon(card, flight = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (isPrimary) Modifier.tripSharedTitle() else Modifier)
            if (sub.isNotEmpty()) {
                Text(sub, style = type.caption, color = if (isBest && hasAmount) colors.good else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        // Связь строки с крупным итогом: у выбранного способа (по нему итог сверху) — галочка и сумма плотным цветом
        // (синим, у самого выгодного — зелёным); у остальных — сумма серая, обычным начертанием.
        val mark = if (isBest) colors.good else colors.accent
        SelectedMark(mark, lift)
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            // Длинная сумма (донги, рупии) — мельче, чтобы не прилипать к краю.
            val size = when {
                right.length > 12 -> 14.sp
                right.length > 9 -> 16.sp
                else -> 19.sp
            }
            Text(
                right,
                style = type.number.copy(fontSize = size, lineHeight = size * 1.2f, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) mark else colors.muted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Галочка выбранного способа: кружок цвета суммы с белой «✓». */
@Composable
private fun SelectedMark(color: androidx.compose.ui.graphics.Color, progress: Float) {
    Canvas(Modifier.size(18.dp).graphicsLayer {
        scaleX = progress
        scaleY = progress
        alpha = progress.coerceIn(0f, 1f)
    }) {
        drawCircle(color)
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.27f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.69f)
            lineTo(w * 0.74f, w * 0.34f)
        }
        drawPath(path, androidx.compose.ui.graphics.Color.White, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Значок способа: у наличных — купюра, у карты — мини-карта в цветах её оформления с подписью системы.
 * [flight] — на главном: отсюда карта «перелетает» в настройку карты ([flightOrigin]).
 */
@Composable
internal fun PayIcon(card: BankCard, flight: Boolean = false) {
    RecompositionProbe("PayIcon")
    val shared = flight && card.id == LocalTripSharedTransition.current?.primaryCardId
    val sharedModifier = if (shared) Modifier.tripSharedBounds(TRIP_CARD) else Modifier
    val cash = card.kind == CardKind.CASH
    val palette = CardPalette.style(card)
    val style = if (cash) palette else palette.forMaterial(card.material)
    val finish = if (cash) {
        Modifier.banknoteSurface(banknoteTheme(card.currency), 3.dp, miniature = true)
    } else {
        Modifier.cardMaterial(style, card.material, 5.dp)
    }
    Box(
        Modifier.then(if (flight) Modifier.flightOrigin(card.id) else Modifier)
            .size(width = 38.dp, height = 26.dp),
    ) {
        Box(Modifier.matchParentSize().then(sharedModifier).clip(RoundedCornerShape(if (cash) 3.dp else 5.dp)).then(finish))
        if (!cash) {
            EmvChip(style.ink, Modifier.align(Alignment.CenterStart).padding(start = 5.dp).size(8.dp, 6.dp))
        }
        ((if (cash) null else networkMark(card.paymentNetwork)) ?: card.currency.code).let { mark ->
            Text(
                mark,
                style = AppTheme.type.caption.copy(fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold),
                color = if (cash) Color.White else style.ink,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 2.dp)
                    .then(if (shared) Modifier.tripMainEnter(TripEnter.BADGE) else Modifier),
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
private fun CbrLine(rate: BigDecimal?, viaUnionPay: Boolean, spend: Currency, home: Currency,
    loading: Boolean, fetchedAtMillis: Long, todayEpochDay: Long, zone: String,
    modifier: Modifier, onRefresh: () -> Unit,
) {
    RecompositionProbe("CbrLine")
    val colors = AppTheme.colors
    val text = when {
        loading -> stringResource(R.string.cbr_loading)
        rate == null -> stringResource(R.string.cbr_none)
        else -> {
            val label = stringResource(if (viaUnionPay) R.string.cbr_label_unionpay else R.string.cbr_label)
            val pair = rateLabel(rate, spend, home, approximate = viaUnionPay)
            val time = fetchedAt(fetchedAtMillis, todayEpochDay, zone)
            if (time == null) stringResource(R.string.cbr_line, label, pair) else stringResource(R.string.cbr_line_time, label, pair, time)
        }
    }
    val refresh = stringResource(R.string.official_refresh)
    Text(
        text,
        style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
        color = colors.muted,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .then(if (LocalTripSharedTransition.current?.primaryCardId != null) Modifier.tripSharedBounds(TRIP_RATE) else Modifier.tripMainEnter())
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = refresh, onClick = onRefresh)
            .padding(vertical = 8.dp),
    )
}

/** «сегодня 09:40», «вчера 18:02», «3 ноя»; ещё не скачивали — null. */
@Composable
private fun fetchedAt(at: Long, todayEpochDay: Long, zone: String): String? {
    if (at <= 0L) return null
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.of(zone))
    val time = "%02d:%02d".format(t.hour, t.minute)
    val today = LocalDate.ofEpochDay(todayEpochDay)
    return when (val day = t.toLocalDate()) {
        today -> stringResource(R.string.cbr_today, time)
        today.minusDays(1) -> stringResource(R.string.cbr_yesterday, time)
        else -> RuDates.short(day)
    }
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

/** Насколько приподнимается выбранная строка способа оплаты. */
private const val LIFT_SCALE = 0.022f
