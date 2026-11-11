package app.dirhamrub.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.ui.exchange.homePerGive
import app.dirhamrub.ui.settings.Divider
import app.dirhamrub.ui.settings.Group
import app.dirhamrub.ui.settings.LinkRow
import app.dirhamrub.ui.theme.AppTheme

/**
 * Шторка страны (касание «🇦🇲 Армения ⌄» в шапке): сменить страну и валюту, какие карты здесь принимают,
 * где менять деньги, шпаргалка цен. Единственный вход в эти разделы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    state: MainUiState,
    place: Pair<String, String>,
    guide: CountryGuide?,
    onChange: () -> Unit,
    onGuide: () -> Unit,
    onExchange: () -> Unit,
    onCheat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                place.first + "  " + place.second,
                style = AppTheme.type.title.copy(fontSize = 20.sp),
                color = colors.ink,
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 12.dp)
                    .semantics { heading() },
            )
            Group {
                LinkRow(
                    stringResource(R.string.place_change),
                    "${wallet.spend.nameRu} · ${wallet.spend.code}",
                    "›",
                    onClick = onChange,
                )
                if (guide != null) {
                    Divider()
                    val up = guide.networks[CardNetwork.UNIONPAY]?.acceptance
                    val mir = guide.networks[CardNetwork.MIR]?.acceptance
                    LinkRow(
                        stringResource(R.string.money_guide),
                        stringResource(R.string.trip_guide_sub, mark(up), mark(mir), RuDates.short(guide.updated)),
                        "›",
                        onClick = onGuide,
                    )
                }
                if (wallet.spend != wallet.home) {
                    Divider()
                    // Лучший из записанных обменников — по тому, во что вам обошлась валюта, которую несёте.
                    val best = state.exchangers.filter { it.local == wallet.spend }.mapNotNull { ex ->
                        homePerGive(ex.give, wallet.home, wallet.cards, state.reference)?.let(ex::homePerLocal)?.let { ex to it }
                    }.minByOrNull { it.second }
                    val sub = best?.let { (ex, cost) ->
                        stringResource(
                            R.string.trip_exchange_best,
                            ex.name.ifBlank { stringResource(R.string.exchange_unnamed) },
                            wallet.spend.code,
                            Numbers.rateShort(cost),
                            wallet.home.symbol,
                        )
                    } ?: stringResource(R.string.trip_exchange_none)
                    LinkRow(stringResource(R.string.money_exchange), sub, "›", onClick = onExchange)
                }
                Divider()
                LinkRow(stringResource(R.string.money_cheat), stringResource(R.string.cards_page_cheat_sub), "›", onClick = onCheat)
            }
        }
    }
}

private fun mark(a: Acceptance?): String = when (a) {
    Acceptance.YES -> "✓"
    Acceptance.PARTIAL -> "≈"
    Acceptance.NO -> "✗"
    null -> "?"
}
