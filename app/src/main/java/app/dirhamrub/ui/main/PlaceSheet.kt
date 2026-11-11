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
import app.dirhamrub.ui.components.AppSheet
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
 * Шторка страны (ⓘ рядом со страной в шапке): какие карты здесь принимают, сравнение обменников,
 * шпаргалка цен. Единственный вход в эти разделы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    state: MainUiState,
    place: Pair<String, String>,
    guide: CountryGuide?,
    onGuide: () -> Unit,
    onExchange: () -> Unit,
    onCheat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    AppSheet(onDismissRequest = onDismiss) {
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
            // Только текст и «›»: какие карты принимают, обменники, шпаргалка. Смена страны — касанием страны в шапке.
            Group {
                var first = true
                if (guide != null) {
                    val up = guide.networks[CardNetwork.UNIONPAY]?.acceptance
                    val mir = guide.networks[CardNetwork.MIR]?.acceptance
                    LinkRow(
                        stringResource(R.string.money_guide),
                        stringResource(R.string.trip_guide_sub, mark(up), mark(mir), RuDates.short(guide.updated)),
                        "›",
                        onClick = onGuide,
                    )
                    first = false
                }
                if (wallet.spend != wallet.home) {
                    if (!first) Divider()
                    LinkRow(stringResource(R.string.place_exchange), stringResource(R.string.place_exchange_sub), "›", onClick = onExchange)
                    first = false
                }
                if (!first) Divider()
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
