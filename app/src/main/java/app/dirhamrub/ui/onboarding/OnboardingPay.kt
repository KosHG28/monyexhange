package app.dirhamrub.ui.onboarding


import app.dirhamrub.ui.components.*
import java.math.BigDecimal
import app.dirhamrub.ui.components.ButtonGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.FirstCard
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PayWay
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.noun
import app.dirhamrub.ui.cards.AmountField
import app.dirhamrub.ui.cards.FieldPair
import app.dirhamrub.ui.cards.FieldSuffix
import app.dirhamrub.ui.cards.ScreenshotButton
import app.dirhamrub.ui.components.CurrencyDropdown
import app.dirhamrub.ui.main.BankCardView
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import java.time.LocalDate

/** Частые валюты карт: в «Списали» у валютной карты (кроме итоговой и валюты трат). */
private val CARD_CURRENCIES = listOf(Currency.USD, Currency.EUR, Currency.CNY, Currency.KZT, Currency.AED, Currency.TRY)

/** Что несут в обменник (кроме итоговой валюты — она есть всегда). */
private val EXCHANGE_FROM = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

private val PREVIEW_WIDTH = 190.dp
private val PREVIEW_HEIGHT = 100.dp

/**
 * 3. Настройте под поездку: страна (касание — список стран) и мини-конструктор первого способа оплаты —
 * превью карты, тип (рублёвая, валютная, наличные), скриншот или «Цена / Списали» (у наличных — обмен).
 * Чек есть — «Начать», нет — «Продолжить без чека»: карта сохраняется и считается по ЦБ с «≈».
 */
@Composable
internal fun ColumnScope.SetupStep(
    spend: Currency,
    home: Currency,
    place: Pair<String, String>,
    official: OfficialRates?,
    unionPay: UnionPayRates?,
    newCard: () -> BankCard,
    onCountry: () -> Unit,
    onFinish: (BankCard?, BigDecimal?) -> Unit,
    onOpenEditor: (BankCard) -> Unit,
    enter: (Int) -> Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val template = remember { newCard() }
    val haptics = rememberHaptics()
    var way by rememberSaveable { mutableStateOf(PayWay.HOME_CARD) }
    var cardCurrency by rememberSaveable { mutableStateOf(CARD_CURRENCIES.first { it != home && it != spend }) }
    var sentCurrency by rememberSaveable { mutableStateOf(home) }
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var bought by rememberSaveable { mutableStateOf("") }
    // Сменили страну — суммы были в прежней валюте: поля чистые, валюта карты — не валюта трат.
    var seenSpend by rememberSaveable { mutableStateOf(spend) }
    LaunchedEffect(spend) {
        if (spend != seenSpend) {
            seenSpend = spend
            first = ""
            second = ""
            bought = ""
            if (cardCurrency == spend || cardCurrency == home) cardCurrency = CARD_CURRENCIES.first { it != home && it != spend }
            if (sentCurrency == spend) sentCurrency = home
        }
    }

    val cardName = stringResource(R.string.card_default_name, if (way == PayWay.HOME_CARD) home.code else cardCurrency.code)
    val cashName = stringResource(R.string.cash_default_name, spend.code)
    val draft = FirstCard.build(
        way, template, home, spend, cardCurrency, sentCurrency,
        Numbers.parse(first), Numbers.parse(second), Numbers.parse(bought), LocalDate.now(),
    )?.let { it.copy(name = if (it.kind == CardKind.CASH) cashName else cardName) }
    val hasReceipt = draft != null && (draft.purchases.isNotEmpty() || draft.transfer != null)
    val quote = draft?.let { RateEngine.quote(it, spend, home, official, unionPay) }

    val price = Numbers.parse(first)?.takeIf { it.signum() > 0 && way != PayWay.CASH } ?: QuickSums.chips(spend)[4]
    val preview: @Composable (() -> Float) -> Unit = { textAlpha ->
        draft?.let {
            BankCardView(
                card = it, quote = quote, spend = spend, home = home,
                sum = quote?.let { q -> Numbers.moneyWhole(price.multiply(q.homePerUnit, MC)) },
                selected = true, best = null, onClick = {}, onLongClick = {},
                width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT,
                needsReceipt = official?.cross(spend, home) == null,
                flipOnChange = false, textAlpha = textAlpha, tripPreview = true,
            )
        }
    }

    StepLayout(
        button = {
            PrimaryButton(
                stringResource(if (hasReceipt) R.string.onboarding_start else R.string.onboarding_without_receipt),
                enter(6).tripExit(TripExit.FOOTER),
                soft = false,
            ) {
                haptics.play(if (hasReceipt) Haptic.CONFIRM else Haptic.TICK)
                onFinish(draft, Numbers.parse(first)?.takeIf { way != PayWay.CASH && it.signum() >= 0 })
            }
        },
    ) {
        Title(stringResource(R.string.onboarding_setup_title), enter(0).tripExit(TripExit.TITLE))
        Lead(stringResource(R.string.onboarding_setup_lead), enter(1).tripExit(TripExit.TITLE))
        CountryCard(place, spend, enter(2), onCountry)
        Label(stringResource(R.string.onboarding_setup_way), enter(3).tripExit(TripExit.TITLE))
        Column(
            enter(4).tripOutgoing(320)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                preview { 1f }
            }
            Spacer(Modifier.height(10.dp))
            TypeSwitch(way, home) {
                if (it != way) {
                    way = it
                    first = ""
                    second = ""
                    bought = ""
                }
            }
            Spacer(Modifier.height(10.dp))
            when (way) {
                PayWay.HOME_CARD, PayWay.NOTHING -> if (spend == home) {
                    Note(stringResource(R.string.onboarding_pay_same))
                } else {
                    draft?.let { d -> Box(Modifier.tripExit(TripExit.SCREENSHOT)) { ScreenshotButton(null, home) { onOpenEditor(d) } } }
                    Spacer(Modifier.height(10.dp))
                    PurchaseFields(first, { first = it }, second, { second = it }, spend) { FieldSuffix(home.mark) }
                }
                PayWay.CURRENCY_CARD -> {
                    draft?.let { d -> Box(Modifier.tripExit(TripExit.SCREENSHOT)) { ScreenshotButton(null, cardCurrency) { onOpenEditor(d) } } }
                    Spacer(Modifier.height(10.dp))
                    val pick = stringResource(R.string.onboarding_pay_card_currency)
                    PurchaseFields(first, { first = it }, second, { second = it }, spend, selectable = true) {
                        CurrencyDropdown(
                            cardCurrency,
                            (CARD_CURRENCIES + cardCurrency).filter { it != home && it != spend }.distinct(),
                            {
                                if (it != cardCurrency) second = ""
                                cardCurrency = it
                            },
                            pick,
                        )
                    }
                }
                PayWay.CASH -> {
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
                }
            }
            // Чек набран — «Ваш курс» сразу под полями.
            if (hasReceipt && quote != null && spend != home) {
                val unit = Numbers.unitFor(quote.homePerUnit)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
                    Text(stringResource(R.string.onboarding_pay_live), style = type.caption.copy(fontSize = 13.sp), color = colors.muted, modifier = Modifier.weight(1f))
                    Text(
                        "${Numbers.moneyWhole(unit)} ${spend.mark} = ${Numbers.rate(quote.homePerUnit.multiply(unit, MC))} ${home.mark}",
                        style = type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.good,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.onboarding_setup_later),
            style = type.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = colors.muted,
            modifier = enter(5).tripExit(TripExit.FOOTER).padding(start = 2.dp, end = 2.dp, top = 10.dp),
        )
    }
}

/** Страна поездки: флаг, название, валюта («Лира · TRY») и «Изменить» — открывает список стран. */
@Composable
private fun CountryCard(place: Pair<String, String>, spend: Currency, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val sub = "${spend.noun} · ${spend.code}"
    val a11y = stringResource(R.string.onboarding_country_a11y, place.second, sub)
    Row(
        modifier
            .tripOutgoing(200)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = a11y }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).tripSharedBounds(TRIP_COUNTRY), verticalAlignment = Alignment.CenterVertically) {
            Text(place.first, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.second, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
                Text(sub, style = type.caption.copy(fontSize = 12.5.sp), color = colors.muted, maxLines = 1, modifier = Modifier.tripOutgoing(200))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.onboarding_country_change), style = type.bodyStrong, color = colors.accent, modifier = Modifier.tripOutgoing(200))
    }
}

/** «Цена» в валюте трат и «Списали» — справа валюта списания ([trailing]: знак или выбор валюты карты). */
@Composable
private fun PurchaseFields(
    first: String,
    onFirst: (String) -> Unit,
    second: String,
    onSecond: (String) -> Unit,
    spend: Currency,
    selectable: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val price = stringResource(R.string.onboarding_pay_price)
    val paid = stringResource(R.string.onboarding_pay_paid)
    FieldPair(
        first = { m -> AmountField(price, first, onFirst, price, m, imeAction = ImeAction.Next, trailing = { FieldSuffix(spend.code) }, tripPrice = true) },
        second = { m -> AmountField(paid, second, onSecond, paid, m.tripExit(TripExit.SCREENSHOT), trailing = trailing, selectable = selectable) },
    )
}

/** Тип первого способа: рублёвая (карта в итоговой валюте), валютная, наличные — группа кнопок Expressive. */
@Composable
private fun TypeSwitch(way: PayWay, home: Currency, onChange: (PayWay) -> Unit) {
    val labels = listOf(
        PayWay.HOME_CARD to if (home == Currency.RUB) {
            stringResource(R.string.onboarding_type_home_rub)
        } else {
            stringResource(R.string.onboarding_type_home_other, home.code)
        },
        PayWay.CURRENCY_CARD to stringResource(R.string.onboarding_type_currency),
        PayWay.CASH to stringResource(R.string.onboarding_type_cash),
    )
    val ways = labels.map { it.first }
    ButtonGroup(
        options = labels.map { it.second },
        selected = ways.indexOf(if (way == PayWay.NOTHING) PayWay.HOME_CARD else way),
        onSelect = { onChange(ways[it]) },
        textStyle = AppTheme.type.body.copy(fontSize = 14.sp),
        optionModifier = { Modifier.tripExit(TripExit.CHIPS, it) },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
