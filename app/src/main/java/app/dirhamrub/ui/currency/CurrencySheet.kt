package app.dirhamrub.ui.currency

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.CurrencySearch
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.Region
import app.dirhamrub.domain.wallet.cbrPublishes
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.theme.AppTheme

/**
 * Выбор валют: «Где тратите» — страны (около 240) с поиском по стране, городу, валюте, коду и знаку,
 * сверху — страна по сотовой сети и недавние валюты, ниже — все страны по частям света с курсом ЦБ
 * или пометкой «без ЦБ»; «Итог считать в» — рубль и валюты с курсом ЦБ (без него итог не посчитать).
 * Без [onHome] вкладки итоговой валюты нет (знакомство).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySheet(
    spend: Currency,
    home: Currency,
    onSpend: (Currency) -> Unit,
    onHome: ((Currency) -> Unit)?,
    onDismiss: () -> Unit,
    recent: List<Currency> = emptyList(),
    country: String? = null,
    official: OfficialRates? = null,
) {
    val colors = AppTheme.colors
    var homeTab by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        val cascade = rememberCascade()
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
        ) {
            if (onHome != null) {
                Tabs(homeTab) {
                    homeTab = it
                    query = ""
                }
            } else {
                Text(
                    stringResource(R.string.currency_sheet_spend),
                    style = AppTheme.type.title,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(10.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(if (homeTab) R.string.currency_search_currency else R.string.currency_search_hint),
            )
            Spacer(Modifier.height(4.dp))
            val texts = SheetTexts(
                here = stringResource(R.string.currency_here),
                recent = stringResource(R.string.currency_recent),
                frequent = stringResource(R.string.currency_frequent),
                all = stringResource(R.string.currency_all),
                found = stringResource(R.string.currency_found),
                nothing = stringResource(R.string.currency_nothing),
                noCbr = stringResource(R.string.currency_no_cbr),
                homeHint = stringResource(R.string.currency_sheet_home_hint) + ". " + stringResource(R.string.currency_home_only_cbr),
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (homeTab && onHome != null) {
                    homeItems(query, home, official, cascade, texts, onHome)
                } else {
                    spendItems(query, spend, home, recent, country, official, cascade, texts) {
                        onSpend(it)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/**
 * Выбор одной валюты из [options] с поиском — валюта карты в редакторе, итоговая в знакомстве.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    title: String,
    selected: Currency,
    options: List<Currency>,
    onPick: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
        ) {
            Text(title, style = AppTheme.type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            SearchField(query, { query = it }, stringResource(R.string.currency_search_currency))
            val nothing = stringResource(R.string.currency_nothing)
            val found = CurrencySearch.currencies(query, options)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            ) {
                if (found.isEmpty()) item { Note(nothing) }
                items(found, key = { it.code }) { c ->
                    CurrencyRow(c, selected = c == selected) {
                        onPick(c)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** Подписи разделов — строки берутся в composable-области, а не в LazyListScope. */
private class SheetTexts(
    val here: String,
    val recent: String,
    val frequent: String,
    val all: String,
    val found: String,
    val nothing: String,
    val noCbr: String,
    val homeHint: String,
)

private fun LazyListScope.spendItems(
    query: String,
    spend: Currency,
    home: Currency,
    recent: List<Currency>,
    country: String?,
    official: OfficialRates?,
    cascade: State<Float>,
    texts: SheetTexts,
    onPick: (Currency) -> Unit,
) {
    if (query.isNotBlank()) {
        val found = CurrencySearch.countries(query)
        item(key = "found") { Section(texts.found.format(found.size)) }
        if (found.isEmpty()) item(key = "nothing") { Note(texts.nothing) }
        items(found, key = { "f-" + it.iso }) { c -> CountryRow(c, c.currency == spend, home, official, texts.noCbr) { onPick(c.currency) } }
        return
    }
    var index = 0
    // Страна по сотовой сети — одной кнопкой (дома, в России, не показываем).
    val here = country?.trim()?.uppercase()?.let { Countries.byIso(it) }?.takeIf { it.iso != "RU" }
    if (here != null) {
        val i = index++
        item(key = "here") {
            Column(Modifier.cascade(cascade, i)) {
                Section(texts.here)
                HereCard(here, selected = here.currency == spend, home, official) { onPick(here.currency) }
            }
        }
    }
    val last = recent.ifEmpty { listOf(spend) }
    val i = index++
    item(key = "recent") {
        Column(Modifier.cascade(cascade, i)) {
            Section(texts.recent)
            CurrencyChips(options = last, selected = spend, onSelect = onPick)
        }
    }
    val byRegion = Countries.all.groupBy { it.region }
    for (region in Region.entries) {
        val list = byRegion[region] ?: continue
        val h = index++
        item(key = "r-" + region.name) { RegionHeader(region.title, Modifier.cascade(cascade, h)) }
        for (c in list) {
            val r = index++
            item(key = "c-" + c.iso) {
                CountryRow(c, c.currency == spend, home, official, texts.noCbr, Modifier.cascade(cascade, r)) { onPick(c.currency) }
            }
        }
    }
}

private fun LazyListScope.homeItems(
    query: String,
    home: Currency,
    official: OfficialRates?,
    cascade: State<Float>,
    texts: SheetTexts,
    onPick: (Currency) -> Unit,
) {
    // Итог — только в рубле и валютах с курсом ЦБ: иначе карты не с чем сравнить и нечем пересчитать.
    val allowed = Currency.entries
        .filter { cbrPublishes(it, official) }
        .sortedWith(compareBy<Currency> { it != Currency.RUB }.thenBy { it.nameRu })
    if (query.isNotBlank()) {
        val found = CurrencySearch.currencies(query, allowed)
        item(key = "found") { Section(texts.found.format(found.size)) }
        if (found.isEmpty()) item(key = "nothing") { Note(texts.nothing) }
        items(found, key = { "f-" + it.code }) { c -> CurrencyRow(c, c == home) { onPick(c) } }
        return
    }
    item(key = "hint") { Note(texts.homeHint, Modifier.cascade(cascade, 0)) }
    item(key = "frequent") {
        Column(Modifier.cascade(cascade, 1)) {
            Section(texts.frequent)
            CurrencyChips(options = (Currency.FREQUENT.filter { it in allowed } + home).distinct(), selected = home, onSelect = onPick)
        }
    }
    item(key = "all") { Section(texts.all, Modifier.cascade(cascade, 2)) }
    var index = 3
    for (c in allowed) {
        val i = index++
        item(key = "h-" + c.code) { CurrencyRow(c, c == home, Modifier.cascade(cascade, i)) { onPick(c) } }
    }
}

/** «IDR · Rp» или просто «AED», если знак — это код. */
internal fun label(c: Currency): String = if (c.symbol == c.code) c.code else "${c.code} · ${c.symbol}"

/** «10 000 Rp = 57,69 ₽» по курсу ЦБ; null — у итоговой валюты или если у ЦБ курса нет. */
internal fun rateText(c: Currency, home: Currency, official: OfficialRates?): String? {
    if (c == home) return null
    val rate = official?.cross(c, home) ?: return null
    val unit = Numbers.unitFor(rate)
    return "${Numbers.moneyWhole(unit)} ${c.symbol} = ${Numbers.rateShort(rate.multiply(unit, MC))} ${home.symbol}"
}

/** У ЦБ нет курса: валюты нет ни в известном списке, ни в загруженных курсах. */
internal fun noCbr(c: Currency, home: Currency, official: OfficialRates?): Boolean =
    c != home && !cbrPublishes(c, official)
