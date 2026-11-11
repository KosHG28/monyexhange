package app.dirhamrub.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.dirhamrub.ui.guide.acceptanceColor
import app.dirhamrub.ui.guide.acceptanceIcon
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
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.AppSheet
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.ui.settings.Divider
import app.dirhamrub.ui.settings.Group
import app.dirhamrub.ui.settings.LinkRow
import app.dirhamrub.ui.theme.AppTheme

/**
 * Шторка страны (ⓘ рядом со страной в шапке): какие карты здесь принимают и шпаргалка цен. Единственный вход в эти разделы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    state: MainUiState,
    place: Pair<String, String>,
    guide: CountryGuide?,
    onGuide: () -> Unit,
    onCheat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                place.first + "  " + place.second,
                style = AppTheme.type.title.copy(fontSize = 20.sp),
                color = colors.ink,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { heading() },
            )
            // Валюта страны: название, код и знак — по нему узнают цены на ценниках («Армянский драм (AMD · ֏)»).
            val c = wallet.spend
            Text(
                if (c.symbol == c.code) {
                    stringResource(R.string.place_currency_code, c.nameRu, c.code)
                } else {
                    stringResource(R.string.place_currency, c.nameRu, c.code, c.symbol)
                },
                style = AppTheme.type.body.copy(fontSize = 13.5.sp),
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
            )
            // Только текст и «›»: какие карты принимают, шпаргалка. Смена страны — касанием страны в шапке.
            Group {
                if (guide != null) {
                    GuideRow(guide, onGuide)
                    Divider()
                }
                LinkRow(stringResource(R.string.money_cheat), stringResource(R.string.cards_page_cheat_sub), null, onClick = onCheat)
            }
        }
    }
}

/** «Какие карты здесь принимают» и под ним «UnionPay ≈ · МИР ✕ · обновлено 10 ноя» — значки цветом, как в справочнике. */
@Composable
private fun GuideRow(guide: CountryGuide, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val up = guide.networks[CardNetwork.UNIONPAY]?.acceptance
    val mir = guide.networks[CardNetwork.MIR]?.acceptance
    val updated = stringResource(R.string.trip_guide_updated, RuDates.short(guide.updated))
    val title = stringResource(R.string.money_guide)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sub = type.caption.copy(color = colors.muted)
                Text(stringResource(R.string.network_unionpay) + " ", style = sub, maxLines = 1)
                Mark(up)
                Text(" · " + stringResource(R.string.network_mir) + " ", style = sub, maxLines = 1)
                Mark(mir)
                Text(" · $updated", style = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("›", style = type.title.copy(fontWeight = FontWeight.Normal), color = colors.muted)
    }
}

@Composable
private fun Mark(a: Acceptance?) {
    Icon(
        painterResource(acceptanceIcon(a)),
        contentDescription = stringResource(
            when (a) {
                Acceptance.YES -> R.string.guide_mark_yes
                Acceptance.PARTIAL -> R.string.guide_mark_partial
                Acceptance.NO -> R.string.guide_mark_no
                null -> R.string.guide_mark_unknown
            },
        ),
        tint = acceptanceColor(a),
        modifier = Modifier.size(14.dp),
    )
}
