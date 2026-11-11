package app.dirhamrub.ui.exchange

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.AppSheet
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ExchangeMath
import app.dirhamrub.domain.wallet.Exchanger
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.ReceiptCheck
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.components.NumberField
import app.dirhamrub.ui.main.signedPercent
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Что несут в обменник (кроме местной валюты). */
val EXCHANGE_GIVE = listOf(Currency.RUB, Currency.USD, Currency.EUR, Currency.CNY)

/** Сколько итоговой валюты стоил 1 [give]: как купили для наличных раньше, иначе по ЦБ; итоговая — 1. */
fun homePerGive(give: Currency, home: Currency, cards: List<BankCard>, official: OfficialRates?): BigDecimal? {
    if (give == home) return BigDecimal.ONE
    cards.asSequence()
        .filter { it.kind == CardKind.CASH }
        .mapNotNull { it.transfer }
        .filter { it.sentCurrency == give }
        .mapNotNull { t -> t.bought?.takeIf { it.second == home }?.first }
        .firstOrNull()
        ?.let { return it }
    return official?.cross(give, home)
}

/**
 * Обменники для местной валюты [local]: что несёте и почём это купили, сколько меняете;
 * лучшая карта для сравнения, обменники по выгоде («+12 % к карте», «невыгодно» при разнице курсов > 3 %),
 * сколько местной валюты даст лучший вместо худшего; «+ Добавить» (вручную или снимком табло).
 * «Я обменял здесь» — наличные с чеком обмена ([onExchanged] открывает их в редакторе).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeSheet(
    local: Currency,
    home: Currency,
    cards: List<BankCard>,
    quote: WalletQuote,
    official: OfficialRates?,
    exchangers: List<Exchanger>,
    onSave: (Exchanger) -> Unit,
    onDelete: (String) -> Unit,
    onExchanged: (BankCard) -> Unit,
    newCard: () -> BankCard,
    onDismiss: () -> Unit,
    /** Снимок табло (камера в режиме «Обменник»); null — недоступно. */
    onScan: ((give: Currency) -> Unit)? = null,
    /** Распознанный камерой курс — открыть сразу форму с ним. */
    prefill: Exchanger? = null,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    val gives = EXCHANGE_GIVE.filter { it != local }.ifEmpty { listOf(Currency.USD) }
    var give by rememberSaveable { mutableStateOf((prefill?.give ?: gives.first { it != home || gives.size == 1 }).code) }
    val giveCurrency = Currency.byCode(give) ?: gives.first()
    val defaultBought = homePerGive(giveCurrency, home, cards, official)
    var boughtText by rememberSaveable(give) { mutableStateOf(defaultBought?.let { Numbers.plain(it, 4) }.orEmpty()) }
    var amountText by rememberSaveable(give) { mutableStateOf(if (giveCurrency == home) "10000" else "500") }
    val bought = if (giveCurrency == home) BigDecimal.ONE else Numbers.parse(boughtText)?.takeIf { it.signum() > 0 }
    val amount = Numbers.parse(amountText)?.takeIf { it.signum() > 0 }
    var adding by rememberSaveable { mutableStateOf(prefill != null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val ranked = ExchangeMath.ranked(exchangers, giveCurrency, local)
    val bestCard = quote.quotes.filter { it.card.kind == CardKind.CARD }.minByOrNull { it.homePerUnit }
    val cbrBuy = official?.cross(giveCurrency, local)

    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.exchange_title, giveCurrency.symbol, local.code),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            // Что несёте.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in gives) {
                    val selected = c == giveCurrency
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.accent else colors.surface)
                            .clickable(role = Role.RadioButton) { give = c.code; expanded = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${c.flag} ${c.code}", style = type.bodyStrong.copy(fontSize = 13.5.sp), color = if (selected) colors.onAccent else colors.ink)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (giveCurrency != home) {
                    Field(
                        stringResource(R.string.exchange_bought, giveCurrency.symbol, home.symbol),
                        boughtText,
                        { boughtText = it },
                        Modifier.weight(1f),
                    ) { focus.clearFocus() }
                }
                Field(stringResource(R.string.exchange_amount, giveCurrency.symbol), amountText, { amountText = it }, Modifier.weight(1f)) {
                    focus.clearFocus()
                }
            }
            // Карта для сравнения и ЦБ.
            Spacer(Modifier.height(10.dp))
            if (bestCard != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text("💳 " + bestCard.card.name, style = type.bodyStrong, color = colors.ink)
                        Text(rateLine(local, bestCard.homePerUnit, home), style = type.body.copy(fontSize = 18.sp), color = colors.ink)
                    }
                }
            }
            if (cbrBuy != null) {
                Text(
                    stringResource(R.string.exchange_cbr, giveCurrency.symbol, Numbers.rateShort(cbrBuy), local.code),
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            // Обменники.
            if (ranked.isEmpty() && !adding) {
                Text(
                    stringResource(R.string.exchange_empty),
                    style = type.body,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                )
            }
            ranked.forEachIndexed { i, ex ->
                val cost = bought?.let(ex::homePerLocal)
                val vsCard = if (cost != null && bestCard != null) ExchangeMath.percentVs(cost, bestCard.homePerUnit) else null
                val open = expanded == ex.id
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (i == 0 && ranked.size > 1) colors.goodSoft else colors.surface)
                        .then(if (i == 0 && ranked.size > 1) Modifier.border(1.5.dp, colors.good, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable { expanded = if (open) null else ex.id }
                        .animateContentSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name.ifBlank { stringResource(R.string.exchange_unnamed) }, style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                        when {
                            ex.expensive -> Tag(stringResource(R.string.exchange_expensive), colors.accentSoft, colors.accent)
                            vsCard != null && vsCard.signum() > 0 -> Tag(stringResource(R.string.exchange_vs_card, signedPercent(vsCard)), colors.paper, colors.warn)
                            vsCard != null -> Tag(stringResource(R.string.exchange_better_card), colors.goodSoft, colors.good)
                        }
                    }
                    Text(subLine(ex), style = type.caption, color = colors.muted)
                    Text(
                        (cost?.let { rateLine(local, it, home) } ?: "—") + "  ·  " +
                            stringResource(R.string.exchange_per_give, giveCurrency.symbol, Numbers.rateShort(ex.buy), local.code),
                        style = type.body,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (open) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(stringResource(R.string.exchange_done_here), primary = true, Modifier.weight(1f)) {
                                val base = cards.firstOrNull { it.kind == CardKind.CASH && it.currency == local }
                                onExchanged(ExchangeMath.cashAfter(ex, amount ?: BigDecimal(100), bought, home, LocalDate.now(), base, newCard()))
                            }
                            Pill(stringResource(R.string.exchange_delete), primary = false, Modifier.width(110.dp)) { onDelete(ex.id) }
                        }
                    }
                }
            }
            if (ranked.size > 1 && amount != null) {
                val extra = ExchangeMath.extraLocal(ranked.first(), ranked.last(), amount)
                val extraHome = bestCard?.homePerUnit?.let { extra.multiply(it, MC) }
                Text(
                    stringResource(R.string.exchange_extra, ranked.first().name.ifBlank { "№1" }, ranked.last().name.ifBlank { "№${ranked.size}" }, Numbers.money(amount), giveCurrency.symbol) +
                        " +" + Numbers.moneyWhole(extra) + " " + local.code + (extraHome?.let { " ≈ " + Numbers.moneyWhole(it) + " " + home.symbol } ?: ""),
                    style = type.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.good,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
            if (adding) {
                AddForm(giveCurrency, local, cbrBuy, prefill?.takeIf { it.give == giveCurrency }, onCancel = { adding = false }) {
                    onSave(it)
                    adding = false
                }
            } else {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.exchange_add), primary = onScan == null, Modifier.weight(1f)) { adding = true }
                    if (onScan != null) Pill(stringResource(R.string.exchange_scan), primary = true, Modifier.weight(1f)) { onScan(giveCurrency) }
                }
            }
            Text(
                stringResource(R.string.exchange_tip),
                style = type.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
    }
}

/** Форма нового обменника: название, покупка, продажа (необязательно); сверка покупки с ЦБ — от опечатки. */
@Composable
private fun AddForm(give: Currency, local: Currency, cbrBuy: BigDecimal?, prefill: Exchanger?, onCancel: () -> Unit, onSave: (Exchanger) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    var name by remember(prefill) { mutableStateOf(prefill?.name.orEmpty()) }
    var buyText by remember(prefill) { mutableStateOf(prefill?.buy?.let { Numbers.plain(it, 6) }.orEmpty()) }
    var sellText by remember(prefill) { mutableStateOf(prefill?.sell?.let { Numbers.plain(it, 6) }.orEmpty()) }
    val buy = Numbers.parse(buyText)?.takeIf { it.signum() > 0 }
    val sell = Numbers.parse(sellText)?.takeIf { it.signum() > 0 }
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text(stringResource(if (prefill != null) R.string.exchange_recognized else R.string.exchange_new), style = type.bodyStrong, color = colors.ink)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(stringResource(R.string.exchange_name)) },
            placeholder = { Text(stringResource(R.string.exchange_name_hint)) },
            singleLine = true,
            textStyle = type.body,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.exchange_buy, give.symbol, local.code), buyText, { buyText = it }, Modifier.weight(1f)) { focus.clearFocus() }
            Field(stringResource(R.string.exchange_sell), sellText, { sellText = it }, Modifier.weight(1f)) { focus.clearFocus() }
        }
        if (buy != null) {
            val spread = Exchanger("", "", give, local, buy, sell).spreadPercent
            val typo = cbrBuy?.let { ReceiptCheck.deviationPercent(buy, BigDecimal.ONE, it) }?.takeIf { it.abs() > ReceiptCheck.LIMIT_PERCENT }
            when {
                typo != null -> Note("⚠ " + stringResource(R.string.exchange_typo, signedPercent(typo), Numbers.rateShort(cbrBuy)), colors.warn)
                sell != null && sell < buy -> Note("⚠ " + stringResource(R.string.exchange_swapped), colors.warn)
                spread != null && spread > Exchanger.EXPENSIVE_SPREAD -> Note(stringResource(R.string.exchange_spread_bad, Numbers.percent(spread)), colors.danger)
                spread != null -> Note("✓ " + stringResource(R.string.exchange_spread_ok, Numbers.percent(spread)), colors.good)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.exchange_cancel), primary = false, Modifier.weight(1f), onClick = onCancel)
            Pill(stringResource(R.string.exchange_save), primary = buy != null, Modifier.weight(1f)) {
                if (buy != null) {
                    onSave(Exchanger(UUID.randomUUID().toString(), name.trim(), give, local, buy, sell, System.currentTimeMillis()))
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, onDone: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = AppTheme.type.caption, color = colors.muted, maxLines = 1)
        NumberField(value, onChange, AppTheme.type.monoStrong.copy(fontSize = 17.sp), label, onDone, Modifier.fillMaxWidth().heightIn(min = 32.dp))
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = AppTheme.type.caption.copy(fontSize = 13.sp), color = color, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Pill(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (primary) Modifier.background(colors.accent) else Modifier.border(1.5.dp, colors.line, RoundedCornerShape(14.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = if (primary) colors.onAccent else colors.ink, maxLines = 1)
    }
}

/** «1 AED = 25,23 ₽». */
private fun rateLine(local: Currency, homePerLocal: BigDecimal, home: Currency): String {
    val unit = Numbers.unitFor(homePerLocal)
    val unitText = if (unit.compareTo(BigDecimal.ONE) == 0) "1" else Numbers.moneyWhole(unit)
    return "$unitText ${local.code} = ${Numbers.rateShort(homePerLocal.multiply(unit, MC))} ${home.symbol}"
}

/** «сегодня 14:20 · разница 0,5 %». */
@Composable
private fun subLine(ex: Exchanger): String {
    val t = Instant.ofEpochMilli(ex.at).atZone(ZoneId.systemDefault())
    val day = t.toLocalDate()
    val today = LocalDate.now()
    val time = "%02d:%02d".format(t.hour, t.minute)
    val whenText = when (day) {
        today -> stringResource(R.string.exchange_today, time)
        today.minusDays(1) -> stringResource(R.string.exchange_yesterday, time)
        else -> RuDates.short(day) + " " + time
    }
    val spread = ex.spreadPercent?.let { stringResource(R.string.exchange_spread_short, Numbers.percent(it)) }
    return listOfNotNull(whenText, spread).joinToString(" · ")
}
