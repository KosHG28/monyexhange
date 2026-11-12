package app.dirhamrub.ui.onboarding

import app.dirhamrub.ui.theme.fieldGlow
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.ui.cards.ScreenshotButton
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.CardStyle
import app.dirhamrub.ui.theme.sheen
import java.math.BigDecimal
import java.math.RoundingMode

/* Шаги знакомства 1–2. Пример — Турция: ценник в лирах, итог в рублях. */

private val EXAMPLE_SPEND = Currency.TRY
private val EXAMPLE_PRICE = BigDecimal(2500)

/** Рублей за лиру, пока курсов ЦБ ещё нет (первый запуск без сети). */
private val EXAMPLE_CBR = BigDecimal("1.89")

/** Реальные курсы карт примера к курсу ЦБ: карта в тенге (+2,3 %) и юаневая UnionPay (+3,1 %). */
private val BEST_MARKUP = BigDecimal("1.023")
private val SECOND_MARKUP = BigDecimal("1.031")

/** Прокручиваемое содержимое шага и кнопка внизу экрана. */
@Composable
internal fun ColumnScope.StepLayout(button: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .fadingScroll(rememberScrollState()),
        content = content,
    )
    Spacer(Modifier.height(12.dp))
    button()
    Spacer(Modifier.height(16.dp))
}

/**
 * 1. Сколько на самом деле стоит покупка: кусок главного экрана — ценник, лучшая карта зелёным, вторая — белым
 * с разницей в рублях, ниже курс ЦБ. Курс ЦБ лиры — настоящий, если уже скачан.
 */
@Composable
internal fun ColumnScope.ValueStep(official: OfficialRates?, enter: (Int) -> Modifier, onNext: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val cbr = official?.cross(EXAMPLE_SPEND, Currency.RUB)?.takeIf { it.signum() > 0 } ?: EXAMPLE_CBR
    val base = EXAMPLE_PRICE.multiply(cbr, MC)
    val best = base.multiply(BEST_MARKUP, MC).setScale(0, RoundingMode.HALF_UP)
    val second = base.multiply(SECOND_MARKUP, MC).setScale(0, RoundingMode.HALF_UP)
    val rub = Currency.RUB.mark
    StepLayout(button = { PrimaryButton(stringResource(R.string.onboarding_next), enter(6), onClick = onNext) }) {
        Title(stringResource(R.string.onboarding_value_title), enter(0))
        Lead(stringResource(R.string.onboarding_value_lead), enter(1))
        // Поле цены — как на главном: подпись сверху, сумма справа тонким начертанием.
        Column(
            enter(2)
                .fillMaxWidth()
                .fieldGlow(corner = 18.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.onboarding_value_price), style = type.caption, color = colors.muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.weight(1f))
                Text(Numbers.moneyWhole(EXAMPLE_PRICE), style = type.number.copy(fontWeight = FontWeight.Light), color = colors.ink)
                Text(
                    " " + EXAMPLE_SPEND.mark,
                    style = type.body.copy(fontSize = 15.sp),
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WayRow(
            style = CardPalette.style(0),
            badge = stringResource(R.string.onboarding_badge_visa),
            name = stringResource(R.string.card_default_name, Currency.KZT.code),
            sub = stringResource(R.string.onboarding_value_best),
            sum = "≈ ${Numbers.moneyWhole(best)} $rub",
            best = true,
            modifier = enter(3),
        )
        Spacer(Modifier.height(8.dp))
        WayRow(
            style = CardPalette.style(1),
            badge = stringResource(R.string.onboarding_badge_unionpay),
            name = stringResource(R.string.card_default_name, Currency.CNY.code),
            sub = stringResource(R.string.onboarding_value_more, "${Numbers.moneyWhole(second.subtract(best))} $rub"),
            sum = "≈ ${Numbers.moneyWhole(second)} $rub",
            best = false,
            modifier = enter(4),
        )
        val unit = Numbers.unitFor(cbr)
        Text(
            stringResource(
                R.string.onboarding_value_cbr,
                "${Numbers.moneyWhole(unit)} ${EXAMPLE_SPEND.mark}",
                "${Numbers.rateShort(cbr.multiply(unit, MC))} $rub",
            ),
            style = type.caption.copy(fontSize = 13.sp),
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = enter(5)
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

/** Строка способа оплаты примера: мини-карта с платёжной системой, название, подпись и сумма. */
@Composable
private fun WayRow(style: CardStyle, badge: String, name: String, sub: String, sum: String, best: Boolean, modifier: Modifier) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (best) colors.goodSoft else colors.surface)
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 42.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(style.start, style.end)))
                .sheen(halfWidth = 9.dp),
        ) {
            Box(
                Modifier
                    .padding(start = 5.dp, top = 5.dp)
                    .size(width = 9.dp, height = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.chip),
            )
            Text(
                badge,
                style = type.caption.copy(fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold),
                color = CardPalette.onCard,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
            Text(sub, style = type.caption.copy(fontSize = 12.5.sp), color = if (best) colors.good else colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            sum,
            style = type.number.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
            color = if (best) colors.good else colors.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 2. Точный курс — из скриншотов банка: кнопка скриншота (как в настройке карты, здесь только показ) и какие
 * чеки нужны каждому способу оплаты — строки с контурными значками, как в «Чем платить».
 */
@Composable
internal fun ColumnScope.RulesStep(home: Currency, enter: (Int) -> Modifier, onNext: () -> Unit) {
    val colors = AppTheme.colors
    StepLayout(button = { PrimaryButton(stringResource(R.string.onboarding_next), enter(4), onClick = onNext) }) {
        Title(stringResource(R.string.onboarding_rules_title), enter(0))
        Lead(stringResource(R.string.onboarding_rules_lead), enter(1))
        Box(enter(2)) { ScreenshotButton(null, home, null) }
        Spacer(Modifier.height(12.dp))
        Column(
            enter(3)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface),
        ) {
            val homeTitle = if (home == Currency.RUB) {
                stringResource(R.string.onboarding_rule_home_t)
            } else {
                stringResource(R.string.onboarding_rule_home_other_t, home.code)
            }
            RuleRow(R.drawable.ic_card, homeTitle, stringResource(R.string.onboarding_rule_home_s), divider = false)
            RuleRow(R.drawable.ic_card_currency, stringResource(R.string.onboarding_rule_currency_t), stringResource(R.string.onboarding_rule_currency_s), divider = true)
            RuleRow(R.drawable.ic_cash, stringResource(R.string.onboarding_rule_cash_t), stringResource(R.string.onboarding_rule_cash_s), divider = true)
        }
    }
}

@Composable
private fun RuleRow(@DrawableRes icon: Int, title: String, sub: String, divider: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    // Разделитель — от текста, не от края: значки стоят столбиком.
    if (divider) {
        Box(
            Modifier
                .padding(start = 50.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            Text(sub, style = type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
