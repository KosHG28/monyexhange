package app.dirhamrub.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal
import kotlin.math.abs

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
            // Курсы есть, но этой валюты у ЦБ нет: считаем только по чекам и своему курсу.
            val noCbr = noOfficialForSpend(state)
            val missing = if (rates?.cross(wallet.spend, Currency.RUB) == null) wallet.spend else wallet.home
            Text(
                when {
                    noCbr -> noRefText(state, R.string.official_no_currency, R.string.official_no_ref).let { (res, code) -> stringResource(res, code) }
                    state.ratesLoading -> stringResource(R.string.official_loading_short)
                    rates == null -> stringResource(R.string.official_missing_short)
                    // Курсы старые, без этой валюты (сохранены прежней версией) и свежие не скачались.
                    else -> stringResource(R.string.official_not_loaded, missing.code)
                },
                style = style,
                color = if (noCbr) colors.warn else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            // У ЦБ нет валюты трат — справочный курс UnionPay (к доллару) × ЦБ (доллар): подпись и дата — UnionPay.
            val via = state.quote.viaUnionPay
            val date = RuDates.short(state.activeUnionPay?.date?.takeIf { via } ?: rates.date)
            val offline = if (state.ratesFailed) stringResource(R.string.official_offline) else ""
            val full = Triple(
                stringResource(if (via) R.string.official_line_head_unionpay else R.string.official_line_head, date, rate.first),
                arrow?.let { stringResource(R.string.official_trend_week, it) },
                offline,
            )
            val short = Triple(
                stringResource(if (via) R.string.official_line_head_unionpay_short else R.string.official_line_head_short, date, rate.first),
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
internal fun RefreshMark(loading: Boolean, failed: Boolean, reduceMotion: Boolean) {
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

internal const val FLASH_MS = 1000
