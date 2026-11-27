package app.dirhamrub.ui.main

import app.dirhamrub.ui.components.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.wrapContentSize

import app.dirhamrub.ui.theme.parallaxOf
import app.dirhamrub.ui.theme.LocalTilt
import app.dirhamrub.ui.theme.LocalCardTilt
import app.dirhamrub.ui.theme.cardReflection
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import java.time.Year
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.rateLabel
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.cardMaterial
import app.dirhamrub.ui.theme.forMaterial
import app.dirhamrub.ui.theme.banknoteSurface
import app.dirhamrub.ui.theme.banknoteTheme
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
    /** Насколько видны надписи (перелёт карты: на мини-карте их нет, проявляются по пути). */
    textAlpha: () -> Float = { 1f },
    tripPreview: Boolean = false,
    reflectionTilt: State<Offset>? = null,
    colorSweep: State<Float>? = null,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val cash = card.kind == CardKind.CASH
    val material = card.material
    val palette = CardPalette.style(card)
    val style = if (cash) palette.copy(ink = Color.White) else palette.forMaterial(material)
    val corner = if (cash) 8.dp else CARD_RADIUS
    val cardShape = if (cash) RoundedCornerShape(corner) else CARD_SHAPE
    val surface = when {
        cash -> Modifier.banknoteSurface(banknoteTheme(card.currency), corner, tilt = reflectionTilt)
        reflectionTilt != null -> Modifier.cardReflection(style, material, reflectionTilt, colorSweep, corner)
        else -> Modifier.cardMaterial(style, material, corner, tilt = LocalCardTilt.current)
    }
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

    val approximate = quote?.basis?.let { it != RateBasis.RECEIPTS && it != RateBasis.MANUAL } == true
    val rate = quote?.let { rateLabel(it.homePerUnit, spend, home, approximate) }
        ?: stringResource(if (needsReceipt) R.string.card_need_receipt else R.string.card_no_rate)
    val hasRateEvidence = card.effectiveTransfer?.isValid == true || card.purchases.any { it.spend == spend && it.isValid } ||
        quote?.basis == RateBasis.MANUAL
    val vs = quote?.vsOfficial?.takeIf {
        hasRateEvidence && quote.basis != RateBasis.OFFICIAL && it.setScale(1, java.math.RoundingMode.HALF_UP).signum() != 0
    }
    val approx = if (approximate) "≈ " else ""
    val sumText = sum?.let { "$approx$it ${home.mark}" } ?: "—"
    val a11y = buildString {
        append(stringResource(R.string.card_a11y, card.name, sumText, rate))
        if (quote != null) append(", ").append(basisLabel(quote))
        if (best != null) append(". ").append(best)
    }
    val editLabel = stringResource(R.string.card_edit_a11y)
    // Узкая карта (три в ряд): сумма мельче, наценка — строкой под курсом, а не рядом.
    val compact = width < COMPACT_CARD_WIDTH
    val physicalCard = !cash && !compact
    val inlineChip = physicalCard && LocalDensity.current.fontScale >= 1.3f
    val contactShadow = if (physicalCard) Modifier.shadow(
        elevation = 3.dp, shape = cardShape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.18f),
        spotColor = Color.Black.copy(alpha = 0.28f),
    ) else Modifier
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
                    if (physicalCard || !colors.isDark && reflectionTilt != null) {
                        shape = cardShape
                        shadowElevation = (if (colors.isDark) 7.dp else 11.dp).toPx()
                        ambientShadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.34f) else style.start.copy(alpha = 0.22f)
                        spotShadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.62f) else style.start.copy(alpha = 0.29f)
                    }
                }
                .then(contactShadow)
                .drawWithContent {
                    drawContent()
                    if (glow.value > 0f) {
                        drawRoundRect(
                            color = CardPalette.goodOnCard.copy(alpha = glow.value),
                            cornerRadius = CornerRadius(corner.toPx()),
                            style = Stroke(2.5.dp.toPx()),
                        )
                    }
                }
                .then(if (tripPreview) Modifier else Modifier.clip(cardShape).then(surface))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = editLabel)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    this.selected = selected
                    role = Role.RadioButton
                },
        ) {
            if (!tripPreview) {
                PaymentPreviewFace(card, style.ink, style.chip, sumText, textAlpha)
                return@Box
            }
            Box(Modifier.matchParentSize().tripSharedBounds(TRIP_CARD).clip(cardShape).then(surface))
            // Водяной знак валюты карты: при наклоне отстаёт на 6 dp — будто глубже в пластике.
            val mark = card.currency.mark
            val tilt = LocalTilt.current
            val markY = (if (mark.length > 2) 4.dp else 24.dp) - if (vs != null) 16.dp else 0.dp
            val watermark: @Composable BoxScope.() -> Unit = {
                val markStyle = type.hero.copy(fontSize = if (mark.length > 2) 40.sp else 88.sp,
                    lineHeight = 92.sp, letterSpacing = 0.sp)
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .tripExit(TripExit.DECORATION)
                        .offset(x = 6.dp, y = markY)
                        .graphicsLayer {
                            val angle = reflectionTilt?.value
                            translationX = if (angle == null) parallaxOf(tilt, 6.dp.toPx()) else -angle.x * 6.dp.toPx()
                            translationY = -(angle?.y ?: 0f) * 3.dp.toPx()
                            alpha = textAlpha()
                        },
                ) {
                    Text(mark, style = markStyle, color = Color.Black.copy(alpha = 0.25f),
                        maxLines = 1, modifier = Modifier.offset(y = (-1).dp))
                    Text(mark, style = markStyle, color = Color.White.copy(alpha = 0.26f),
                        maxLines = 1, modifier = Modifier.offset(y = 1.dp))
                    Text(mark, style = markStyle, color = style.mark.copy(alpha = 0.2f), maxLines = 1)
                }
            }
            Box(Modifier.matchParentSize().clip(cardShape), content = watermark)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = textAlpha() }
                    .padding(horizontal = if (compact) 10.dp else 13.dp, vertical = if (compact) 9.dp else 11.dp),
            ) {
                val decoration = Modifier.tripExit(TripExit.DECORATION)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = card.name,
                            style = type.label.copy(fontSize = if (compact) 11.5.sp else 12.5.sp),
                            color = style.ink.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.tripSharedTitle(),
                        )
                    }
                    if (inlineChip) {
                        Spacer(Modifier.width(8.dp))
                        EmvChip(style.chip, decoration.size(width = 22.dp, height = 15.dp))
                    }
                    if (physicalCard) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painterResource(R.drawable.ic_nfc), contentDescription = null,
                            modifier = decoration.size(18.dp), tint = style.ink.copy(alpha = 0.72f),
                        )
                    }
                    // Звезду лучшей карты рисует ряд: при смене лучшей она перелетает к другой карте.
                    if (best != null) Spacer(Modifier.width(STAR_SLOT))
                }
                if (physicalCard && !inlineChip) {
                    Spacer(Modifier.height(7.dp))
                    EmvChip(style.chip, decoration.size(width = 26.dp, height = 19.dp))
                }
                Spacer(Modifier.weight(1f))
                // Сумма меняется на каждом нажатии — перекатываются только изменившиеся цифры.
                val sumStyle = type.number.copy(fontSize = sumSize, lineHeight = sumSize * 1.15f, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
                Row(Modifier.tripSharedBounds(TRIP_HERO)) {
                    if (sum != null) {
                        if (approx.isNotEmpty()) Text(approx, style = sumStyle, color = style.ink, maxLines = 1, softWrap = false)
                        RollingText(sum, style = sumStyle, color = style.ink)
                        Text(" " + home.mark, style = sumStyle, color = style.ink, maxLines = 1, softWrap = false)
                    } else {
                        Text(sumText, style = sumStyle, color = style.ink, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(3.dp))
                val rateStyle = type.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp, color = style.ink.copy(alpha = 0.88f))
                val rateText: @Composable (Modifier) -> Unit = { modifier ->
                    Text(rate, style = rateStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = modifier.tripSharedBounds(TRIP_RATE))
                }
                val vsChip: @Composable () -> Unit = {
                    if (vs != null) {
                        Text(
                            text = if (vs.signum() < 0) stringResource(R.string.card_savings, signedPercent(vs.abs()).removePrefix("+"))
                            else stringResource(R.string.percent_value, signedPercent(vs)),
                            style = type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = when (markupOf(vs)) {
                                Markup.GOOD -> CardPalette.goodOnCard
                                Markup.WARN -> CardPalette.warnOnCard
                                Markup.BAD -> CardPalette.badOnCard
                            },
                            maxLines = 1,
                            modifier = Modifier
                                .tripExit(TripExit.DECORATION)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardPalette.chipScrim.copy(alpha = 0.62f))
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

@Composable
private fun PaymentPreviewFace(
    card: BankCard,
    ink: Color,
    chip: Color,
    sum: String,
    textAlpha: () -> Float,
) {
    val cash = card.kind == CardKind.CASH
    val type = AppTheme.type
    val nameStyle = type.bodyStrong.copy(color = ink, fontSize = 14.sp, lineHeight = 16.sp)
    val designCode = card.currency.code
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = textAlpha() }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                card.name, Modifier.weight(1f), style = nameStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(10.sp, 14.sp, 0.5.sp),
            )
            Spacer(Modifier.width(5.dp))
            if (cash) {
                Text(
                    "№ ${remember { Year.now().value }}·$designCode",
                    style = type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 8.sp),
                    color = ink.copy(alpha = 0.45f), maxLines = 1,
                )
            } else {
                Icon(painterResource(R.drawable.ic_card_contactless), null, Modifier.size(20.dp),
                    tint = ink.copy(alpha = 0.65f))
            }
        }
        if (!cash) {
            Spacer(Modifier.height(5.dp))
            EmvChip(chip, Modifier.size(width = 28.dp, height = 20.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                sum, Modifier.weight(1f), style = type.number.copy(
                    color = ink, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
                ),
                autoSize = TextAutoSize.StepBased(12.sp, 23.sp, 0.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(5.dp))
            val watermark = type.number.copy(
                color = ink.copy(alpha = 0.27f), fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = 0.sp,
                drawStyle = if (cash) Stroke(with(LocalDensity.current) { 0.65.dp.toPx() }) else null,
            )
            Text(designCode, style = watermark, maxLines = 1, modifier = Modifier.offset(x = if (cash) 0.dp else 2.dp))
        }
    }
}
