package app.dirhamrub.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.cards.ScreenshotButton
import app.dirhamrub.ui.cards.FieldSuffix
import app.dirhamrub.ui.cards.FieldPair
import app.dirhamrub.ui.cards.AmountField
import androidx.compose.ui.text.input.ImeAction
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.FirstCard
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PayWay
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.approximate
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.currency.CurrencyPickerScreen
import app.dirhamrub.ui.main.signedPercent
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.LocalDate

/** Частые валюты карт (кроме итоговой): остальные — в «Все валюты…». */
private val CARD_CURRENCIES = listOf(Currency.USD, Currency.EUR, Currency.CNY, Currency.KZT, Currency.AED, Currency.TRY)

/** Что несут в обменник (кроме итоговой валюты — она есть всегда). */
private val EXCHANGE_FROM = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

/**
 * 5. Чем будете платить: сверху способ, ниже — только нужные ему поля и курс, который из них выходит.
 * «Готово» сохраняет карту (или наличные) — даже без чека: тогда курс по ЦБ с пометкой «≈».
 */
@Composable
internal fun PayStep(
    spend: Currency,
    home: Currency,
    official: OfficialRates?,
    unionPay: UnionPayRates?,
    newCard: () -> BankCard,
    onFinish: (BankCard?) -> Unit,
    onOpenEditor: (BankCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val template = remember { newCard() }
    var way by rememberSaveable { mutableStateOf(PayWay.HOME_CARD) }
    var cardCurrency by rememberSaveable { mutableStateOf(CARD_CURRENCIES.first { it != home }) }
    var sentCurrency by rememberSaveable { mutableStateOf(home) }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var bought by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }

    val cardName = stringResource(R.string.card_default_name, if (way == PayWay.HOME_CARD) home.code else cardCurrency.code)
    val cashName = stringResource(R.string.cash_default_name, spend.code)
    val draft = FirstCard.build(
        way, template, home, spend, cardCurrency, sentCurrency,
        Numbers.parse(first), Numbers.parse(second), Numbers.parse(bought), LocalDate.now(),
    )?.let { it.copy(name = if (it.kind == CardKind.CASH) cashName else cardName) }

    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingScroll(rememberScrollState()),
        ) {
            Title(stringResource(R.string.onboarding_pay_title))
            Lead(stringResource(R.string.onboarding_pay_lead))
            WaySwitch(way, home) {
                if (it != way) {
                    way = it
                    first = ""
                    second = ""
                    bought = ""
                }
            }
            Spacer(Modifier.height(12.dp))
            when (way) {
                PayWay.HOME_CARD -> if (spend == home) {
                    Note(stringResource(R.string.onboarding_pay_same))
                } else {
                    Fields(stringResource(R.string.onboarding_pay_home_receipt)) {
                        // Главный быстрый путь — скриншот уведомления: сверху, заметной кнопкой.
                        draft?.let { d ->
                            ScreenshotButton(null, home) { onOpenEditor(d) }
                            Spacer(Modifier.height(12.dp))
                        }
                        val price = stringResource(R.string.onboarding_pay_price)
                        val paid = stringResource(R.string.onboarding_pay_paid)
                        FieldPair(
                            first = { m -> AmountField(price, first, { first = it }, price, m, imeAction = ImeAction.Next, trailing = { FieldSuffix(spend.mark) }) },
                            second = { m -> AmountField(paid, second, { second = it }, paid, m, trailing = { FieldSuffix(home.mark) }) },
                        )
                        Live(rate(second, first), spend, home, official?.cross(spend, home))
                    }
                }
                PayWay.CURRENCY_CARD -> {
                    Label(stringResource(R.string.onboarding_pay_card_currency))
                    CurrencyChips(
                        options = (CARD_CURRENCIES.filter { it != home } + cardCurrency).distinct(),
                        selected = cardCurrency,
                        onSelect = {
                            if (it != cardCurrency) second = ""
                            cardCurrency = it
                        },
                        onMore = { picker = true },
                        moreLabel = stringResource(R.string.currency_more),
                    )
                    Spacer(Modifier.height(12.dp))
                    Fields(stringResource(R.string.onboarding_pay_topup)) {
                        draft?.let { d ->
                            ScreenshotButton(null, cardCurrency) { onOpenEditor(d) }
                            Spacer(Modifier.height(12.dp))
                        }
                        val sent = stringResource(R.string.onboarding_pay_sent)
                        val got = stringResource(R.string.onboarding_pay_got)
                        FieldPair(
                            first = { m -> AmountField(sent, first, { first = it }, sent, m, imeAction = ImeAction.Next, trailing = { FieldSuffix(home.mark) }) },
                            second = { m -> AmountField(got, second, { second = it }, got, m, trailing = { FieldSuffix(cardCurrency.mark) }) },
                        )
                        Live(rate(first, second), cardCurrency, home, official?.cross(cardCurrency, home))
                    }
                }
                PayWay.CASH -> Fields(stringResource(R.string.onboarding_pay_exchange)) {
                    val gave = stringResource(R.string.onboarding_pay_gave)
                    val received = stringResource(R.string.onboarding_pay_received)
                    val pick = stringResource(R.string.editor_pick_currency)
                    FieldPair(
                        first = { m ->
                            AmountField(
                                gave, first, { first = it }, gave, m,
                                imeAction = ImeAction.Next,
                                trailing = {
                                    CurrencyDropdown(
                                        sentCurrency,
                                        (listOf(home) + EXCHANGE_FROM).filter { it != spend }.distinct(),
                                        {
                                            if (it != sentCurrency) bought = ""
                                            sentCurrency = it
                                        },
                                        pick,
                                    )
                                },
                                selectable = true,
                            )
                        },
                        second = { m ->
                            AmountField(
                                received, second, { second = it }, received, m,
                                imeAction = if (sentCurrency == home) ImeAction.Done else ImeAction.Next,
                                trailing = { FieldSuffix(spend.mark) },
                            )
                        },
                    )
                    if (sentCurrency != home) {
                        val label = stringResource(R.string.receipt_bought, "1 ${sentCurrency.mark}")
                        Spacer(Modifier.height(8.dp))
                        AmountField(
                            label, bought, { bought = it }, label,
                            placeholder = official?.cross(sentCurrency, home)?.let(Numbers::rate),
                            trailing = { FieldSuffix(home.mark) },
                        )
                    }
                    // Курс наличных в итоговой валюте: отданное за полученное × почём купили отданное.
                    val perLocal = rate(first, second)?.let { r ->
                        if (sentCurrency == home) r else (Numbers.parse(bought) ?: official?.cross(sentCurrency, home))?.let { r.multiply(it, MC) }
                    }
                    Live(perLocal, spend, home, official?.cross(spend, home))
                }
                PayWay.NOTHING -> Note(stringResource(R.string.onboarding_pay_nothing))
            }
            // Что выйдет уже сейчас: быстрая сумма в валюте трат по этой карте (или по ЦБ).
            val amount = QuickSums.chips(spend)[4]
            val q = draft?.let { RateEngine.quote(it, spend, home, official, unionPay) }
            val perUnit = q?.homePerUnit ?: official?.cross(spend, home)
            if (perUnit != null && spend != home) {
                val approx = q == null || q.basis.approximate || q.basis == RateBasis.OFFICIAL
                Text(
                    stringResource(
                        R.string.onboarding_pay_now,
                        "${Numbers.moneyWhole(amount)} ${spend.mark}",
                        if (approx) "≈" else "=",
                        "${Numbers.moneyWhole(amount.multiply(perUnit, MC))} ${home.mark}",
                    ),
                    style = type.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = colors.ink,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.paper)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            if (way != PayWay.NOTHING && way != PayWay.CASH && first.isBlank() && second.isBlank()) {
                Note(stringResource(R.string.onboarding_pay_no_receipt))
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(stringResource(R.string.onboarding_pay_done)) { onFinish(draft) }
        Spacer(Modifier.height(16.dp))
    }
    if (picker) {
        CurrencyPickerScreen(
            title = stringResource(R.string.onboarding_pay_card_currency),
            selected = cardCurrency,
            options = Currency.entries.filter { it != home },
            onPick = {
                if (it != cardCurrency) second = ""
                cardCurrency = it
            },
            onDismiss = { picker = false },
        )
    }
}

/** Отношение двух набранных сумм [top] / [bottom] (курс), если обе заполнены. */
private fun rate(top: String, bottom: String): BigDecimal? {
    val t = Numbers.parse(top)?.takeIf { it.signum() > 0 } ?: return null
    val b = Numbers.parse(bottom)?.takeIf { it.signum() > 0 } ?: return null
    return t.divide(b, MC)
}

/** Способ оплаты: 2×2 — карта в итоговой валюте, в другой валюте, наличные, пока ничего. */
@Composable
private fun WaySwitch(way: PayWay, home: Currency, onChange: (PayWay) -> Unit) {
    val labels = listOf(
        PayWay.HOME_CARD to "💳  " + homeCardName(home),
        PayWay.CURRENCY_CARD to "💳  " + stringResource(R.string.onboarding_pay_currency),
        PayWay.CASH to "💵  " + stringResource(R.string.onboarding_pay_cash),
        PayWay.NOTHING to "⏭  " + stringResource(R.string.onboarding_pay_nothing_short),
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in labels.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((w, label) in row) {
                    val on = w == way
                    val colors = AppTheme.colors
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) colors.accentSoft else colors.surface)
                            .border(1.5.dp, if (on) colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
                            .selectable(selected = on, role = Role.RadioButton) { onChange(w) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                            color = if (on) colors.accent else colors.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/** Бумажка с полями чека: заголовок («Как пополняли — из уведомления банка») и строки. */
@Composable
private fun Fields(title: String, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Text(title, style = AppTheme.type.caption.copy(fontSize = 12.5.sp), color = colors.muted, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** «✓ 1 AED = 22,45 ₽ · ЦБ 21,97 ₽ (+2,2 %)» — курс из набранного, рядом курс ЦБ. */
@Composable
private fun Live(homePerUnit: BigDecimal?, unit: Currency, home: Currency, cbr: BigDecimal?) {
    homePerUnit ?: return
    val u = Numbers.unitFor(homePerUnit)
    val mine = "${Numbers.moneyWhole(u)} ${unit.mark} = ${Numbers.rate(homePerUnit.multiply(u, MC))} ${home.mark}"
    val text = if (cbr != null && cbr.signum() > 0) {
        val pct = homePerUnit.divide(cbr, MC).subtract(BigDecimal.ONE).multiply(BigDecimal(100))
        stringResource(R.string.onboarding_pay_live_cbr, mine, "${Numbers.rate(cbr.multiply(u, MC))} ${home.mark}", signedPercent(pct))
    } else {
        stringResource(R.string.onboarding_pay_live, mine)
    }
    Text(
        text,
        style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = AppTheme.colors.good,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

