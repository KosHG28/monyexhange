package app.dirhamrub.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
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
import app.dirhamrub.ui.components.AppSheet
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
    /** Открыть сразу на итоговой валюте (касание нижнего поля пересчёта). */
    startOnHome: Boolean = false,
) {
    val colors = AppTheme.colors
    var homeTab by rememberSaveable { mutableStateOf(startOnHome && onHome != null) }
    var query by rememberSaveable { mutableStateOf("") }
    AppSheet(onDismissRequest = onDismiss) {
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
                // С курсами UnionPay «без ЦБ» — почти у всех стран: тогда помечаем только те, где курса нет совсем.
                noCbr = stringResource(if (official?.derived.isNullOrEmpty()) R.string.currency_no_cbr else R.string.currency_no_rate),
                unionPay = stringResource(R.string.currency_unionpay),
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
 * «Где вы тратите?» — полноэкранный выбор страны (касание страны в шапке главного): стрелка назад, поиск,
 * страна по сотовой сети и недавние, ниже — все страны по частям света с курсом. Выбрали — сразу назад.
 * Итоговая валюта здесь не выбирается — только в настройках.
 */
@Composable
fun CountryScreen(
    spend: Currency,
    home: Currency,
    onSpend: (Currency) -> Unit,
    onBack: () -> Unit,
    recent: List<Currency> = emptyList(),
    country: String? = null,
    official: OfficialRates? = null,
) {
    val colors = AppTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val cascade = rememberCascade()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.settings_back)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.country_title),
                style = AppTheme.type.title.copy(fontSize = 21.sp),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(4.dp))
        SearchField(value = query, onValueChange = { query = it }, hint = stringResource(R.string.currency_search_hint))
        Spacer(Modifier.height(4.dp))
        val texts = SheetTexts(
            here = stringResource(R.string.currency_here),
            recent = stringResource(R.string.currency_recent),
            frequent = stringResource(R.string.currency_frequent),
            all = stringResource(R.string.currency_all),
            found = stringResource(R.string.currency_found),
            nothing = stringResource(R.string.currency_nothing),
            noCbr = stringResource(if (official?.derived.isNullOrEmpty()) R.string.currency_no_cbr else R.string.currency_no_rate),
            unionPay = stringResource(R.string.currency_unionpay),
            homeHint = "",
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            spendItems(query, spend, home, recent, country, official, cascade, texts) {
                onSpend(it)
                onBack()
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
    AppSheet(onDismissRequest = onDismiss) {
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
    val unionPay: String,
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
        items(found, key = { "f-" + it.iso }) { c -> CountryRow(c, c.currency == spend, home, official, texts.noCbr, texts.unionPay) { onPick(c.currency) } }
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
                CountryRow(c, c.currency == spend, home, official, texts.noCbr, texts.unionPay, Modifier.cascade(cascade, r)) { onPick(c.currency) }
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

/**
 * «10 000 Rp = 57,69 ₽» по курсу ЦБ, «100 Rs ≈ 24,65 ₽» — по справочному курсу UnionPay;
 * null — у итоговой валюты или если курса нет.
 */
internal fun rateText(c: Currency, home: Currency, official: OfficialRates?): String? {
    if (c == home || official == null) return null
    val rate = official.cross(c, home) ?: return null
    val unit = Numbers.unitFor(rate)
    val sign = if (official.viaUnionPay(c, home)) "≈" else "="
    return "${Numbers.moneyWhole(unit)} ${c.symbol} $sign ${Numbers.rateShort(rate.multiply(unit, MC))} ${home.symbol}"
}

/** Курса нет: валюты нет ни в известном списке ЦБ, ни в загруженных курсах ЦБ, ни у UnionPay. */
internal fun noCbr(c: Currency, home: Currency, official: OfficialRates?): Boolean =
    c != home && !cbrPublishes(c, official) && (official == null || c !in official.derived)
