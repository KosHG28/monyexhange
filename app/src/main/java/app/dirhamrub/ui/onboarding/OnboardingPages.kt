package app.dirhamrub.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.currency.CurrencySheet
import app.dirhamrub.ui.currency.CurrencyTile
import app.dirhamrub.ui.main.signedPercent
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.CardStyle
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.math.BigDecimal

/* Страницы знакомства 1–4. Пример — один ценник в дирхамах и итог в рублях, без конкретных карт. */

private val EXAMPLE_PRICE = BigDecimal(167)
private val EXAMPLE_CBR = BigDecimal("21.97")

/** Способ оплаты в примере: курс в рублях за дирхам и оформление. */
private class ExampleWay(val name: Int, val sub: Int, val rate: BigDecimal, val style: CardStyle)

/** 1. Зачем: один ценник — разные суммы, смотря чем платить. */
@Composable
internal fun WhyPage() {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Title(stringResource(R.string.onboarding_why_title))
    Lead(stringResource(R.string.onboarding_why_lead))
    // Ценник.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.onboarding_why_tag), style = type.caption, color = colors.muted)
            Text("${Numbers.moneyWhole(EXAMPLE_PRICE)} AED", style = type.number.copy(fontSize = 30.sp), color = colors.ink)
        }
    }
    val ways = listOf(
        ExampleWay(R.string.onboarding_why_card1, R.string.onboarding_why_rate_sub, BigDecimal("22.45"), CardPalette.style(4)),
        ExampleWay(R.string.onboarding_why_cash, R.string.onboarding_why_cash_sub, BigDecimal("22.70"), CardPalette.cash),
        ExampleWay(R.string.onboarding_why_card2, R.string.onboarding_why_rate_sub, BigDecimal("23.24"), CardPalette.style(5)),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface),
    ) {
        ExampleRow(null, stringResource(R.string.onboarding_why_cbr), stringResource(R.string.onboarding_why_cbr_sub), EXAMPLE_CBR, null, best = false)
        ways.forEachIndexed { i, w ->
            val rate = "${Numbers.rateShort(w.rate)} ₽"
            ExampleRow(w.style, stringResource(w.name), stringResource(w.sub, rate), w.rate, EXAMPLE_CBR, best = i == 0)
        }
    }
    Lead(stringResource(R.string.onboarding_why_foot), Modifier.padding(top = 4.dp))
}

@Composable
private fun ExampleRow(style: CardStyle?, name: String, sub: String, rate: BigDecimal, cbr: BigDecimal?, best: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (best) colors.goodSoft else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 30.dp, height = 20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (style != null) Brush.linearGradient(listOf(style.start, style.end)) else Brush.linearGradient(listOf(colors.paper, colors.paper))),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = type.body, color = colors.ink)
            Text(sub, style = type.caption, color = colors.muted)
        }
        val amount = "${Numbers.moneyWhole(EXAMPLE_PRICE.multiply(rate, MC))} ₽"
        Text(amount, style = type.monoStrong.copy(fontSize = 18.sp), color = if (cbr == null) colors.muted else colors.ink)
        if (cbr != null) {
            val pct = rate.divide(cbr, MC).subtract(BigDecimal.ONE).multiply(BigDecimal(100))
            Text(
                " ${signedPercent(pct)} %",
                style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (best) colors.good else colors.warn,
            )
        }
    }
}

/** 2. Как считаем: три способа платить — у каждого своя цепочка и свои чеки. */
@Composable
internal fun HowPage(home: Currency) {
    Title(stringResource(R.string.onboarding_how_title))
    Lead(stringResource(R.string.onboarding_how_lead))
    val homeSym = home.mark
    WayCard(
        icon = "💳",
        title = homeCardName(home),
        sub = stringResource(R.string.onboarding_how_home_sub),
        nodes = listOf(homeSym, stringResource(R.string.onboarding_pipe_price)),
        text = stringResource(R.string.onboarding_how_home_text),
    )
    WayCard(
        icon = "💳",
        title = stringResource(R.string.onboarding_how_currency),
        sub = stringResource(R.string.onboarding_how_currency_sub),
        nodes = listOf(homeSym, stringResource(R.string.onboarding_pipe_card), stringResource(R.string.onboarding_pipe_price)),
        text = stringResource(R.string.onboarding_how_currency_text),
    )
    WayCard(
        icon = "💵",
        title = stringResource(R.string.onboarding_how_cash),
        sub = stringResource(R.string.onboarding_how_cash_sub),
        nodes = listOf(stringResource(R.string.onboarding_pipe_cash_from, homeSym), stringResource(R.string.onboarding_pipe_local)),
        text = stringResource(R.string.onboarding_how_cash_text),
    )
    val colors = AppTheme.colors
    Text(
        rich(stringResource(R.string.onboarding_how_none)),
        style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = colors.ink,
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, colors.warn, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** «Рублёвая карта» или «Карта в KZT», если итог считается не в рублях. */
@Composable
internal fun homeCardName(home: Currency): String =
    if (home == Currency.RUB) stringResource(R.string.onboarding_home_card_rub) else stringResource(R.string.onboarding_home_card_other, home.code)

@Composable
private fun WayCard(icon: String, title: String, sub: String, nodes: List<String>, text: String) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(
        Modifier
            .padding(top = 9.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.paper),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 17.sp) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
                Text(sub, style = type.caption, color = colors.muted)
            }
        }
        Pipe(nodes, Modifier.padding(top = 9.dp))
        Text(rich(text), style = type.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = colors.muted, modifier = Modifier.padding(top = 7.dp))
    }
}

/**
 * Цепочка денег «₽ → валюта карты → ценник». С анимациями по ней бежит подсветка — деньги «перетекают»
 * от узла к узлу; без анимаций — просто цепочка.
 */
@Composable
private fun Pipe(nodes: List<String>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val parts = nodes.size * 2 - 1
    val active = if (LocalReduceMotion.current) {
        null
    } else {
        val phase = rememberInfiniteTransition(label = "pipe").animateFloat(
            initialValue = 0f,
            targetValue = parts + 1f,
            animationSpec = infiniteRepeatable(tween(PIPE_STEP_MS * (parts + 1), easing = LinearEasing)),
            label = "phase",
        )
        // Перерисовка — только когда подсветка переходит к следующему звену, а не каждый кадр.
        val index by remember { derivedStateOf { phase.value.toInt() } }
        index
    }
    val description = nodes.joinToString(" → ")
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        nodes.forEachIndexed { i, node ->
            if (i > 0) {
                val on = active == i * 2 - 1
                Text(
                    "→",
                    style = type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) colors.accent else colors.muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            val on = active == i * 2
            Text(
                node,
                style = type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = if (on) colors.accent else colors.ink,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors.accentSoft else colors.bg)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

private const val PIPE_STEP_MS = 520

/** 3. Что учитываем — и что нет. */
@Composable
internal fun WhatPage() {
    val colors = AppTheme.colors
    Title(stringResource(R.string.onboarding_what_title))
    Spacer(Modifier.padding(top = 6.dp))
    WhatItem("✓", colors.good, colors.goodSoft, R.string.onboarding_what_pay_t, R.string.onboarding_what_pay_s)
    WhatItem("✓", colors.good, colors.goodSoft, R.string.onboarding_what_topup_t, R.string.onboarding_what_topup_s)
    WhatItem("✓", colors.good, colors.goodSoft, R.string.onboarding_what_cash_t, R.string.onboarding_what_cash_s)
    WhatItem("≈", colors.warn, colors.surface, R.string.onboarding_what_est_t, R.string.onboarding_what_est_s)
    WhatItem("✗", colors.accent, colors.accentSoft, R.string.onboarding_what_no_t, R.string.onboarding_what_no_s)
}

@Composable
private fun WhatItem(mark: String, color: Color, back: Color, title: Int, sub: Int) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(back),
            contentAlignment = Alignment.Center,
        ) { Text(mark, style = type.bodyStrong, color = color) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            Text(rich(stringResource(sub)), style = type.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = colors.muted)
        }
    }
}

/** 4. Откуда и куда считаем: валюта ценников и валюта итога — с живым примером. */
@Composable
internal fun CurrenciesPage(
    spend: Currency,
    home: Currency,
    official: OfficialRates?,
    onSpend: (Currency) -> Unit,
    onHome: (Currency) -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    var picker by remember { mutableStateOf(false) }
    Title(stringResource(R.string.onboarding_cur_title))
    Label(stringResource(R.string.onboarding_cur_spend))
    // Частые валюты трат плитками; выбранная другая (по сети или из списка) — первой.
    for (row in (listOf(spend) + Currency.POPULAR).distinct().take(6).chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            for (c in row) CurrencyTile(c, selected = c == spend, wide = true, modifier = Modifier.weight(1f)) { onSpend(c) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    // Остальные ~150 валют — в окне с поиском по стране, городу и валюте.
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { picker = true }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(R.string.onboarding_other_country), style = type.bodyStrong, color = colors.accent)
    }
    if (picker) CurrencySheet(spend = spend, home = home, onSpend = onSpend, onHome = null, onDismiss = { picker = false }, official = official)
    Label(stringResource(R.string.onboarding_cur_home))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(12.dp),
    ) {
        CurrencyChips(options = (Currency.FREQUENT + home).distinct(), selected = home, onSelect = onHome)
        Text(
            rich(stringResource(R.string.onboarding_cur_home_hint)),
            style = type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        // Живой пример: быстрая сумма в валюте трат → итог по курсу ЦБ (пока карт нет).
        val rate = official?.cross(spend, home)
        if (rate != null && spend != home) {
            val amount = QuickSums.chips(spend)[4]
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.onboarding_cur_preview, "${Numbers.moneyWhole(amount)} ${spend.mark}"), style = type.body, color = colors.muted, modifier = Modifier.weight(1f))
                Text(
                    "≈ ${Numbers.moneyWhole(amount.multiply(rate, MC))} ${home.mark}",
                    style = type.number.copy(fontSize = 20.sp),
                    color = colors.ink,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
    Lead(stringResource(R.string.onboarding_cur_foot), Modifier.padding(top = 2.dp))
}

@Composable
internal fun Label(text: String) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
