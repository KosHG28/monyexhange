package app.dirhamrub.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.ui.theme.AppTheme

/**
 * Считать не по чему (нет ни курса карты, ни курса ЦБ) — вместо суммы «—» с мелкой строкой:
 * что случилось и одна кнопка, что с этим сделать.
 *  - курсы ЦБ ещё не скачаны: «Скачиваем…» или «Обновить курсы» (нет связи — так и пишем);
 *  - у ЦБ (и UnionPay) нет курса валюты трат: «Добавить чек» выбранной карте или «Добавить карту».
 */
@Composable
internal fun EmptyRate(
    state: MainUiState,
    onRefreshRates: () -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val spend = state.wallet.spend
    val card = state.selectedCard
    val noRates = state.official == null
    val title: String
    val text: String
    val button: String?
    val action: () -> Unit
    when {
        noRates && state.ratesLoading -> {
            title = stringResource(R.string.empty_loading_title)
            text = stringResource(R.string.empty_loading_text)
            button = null
            action = {}
        }
        noRates -> {
            title = stringResource(if (state.ratesFailed) R.string.empty_offline_title else R.string.empty_rates_title)
            text = stringResource(if (state.ratesFailed) R.string.empty_offline_text else R.string.empty_rates_text)
            button = stringResource(R.string.empty_rates_button)
            action = onRefreshRates
        }
        else -> {
            title = stringResource(R.string.empty_currency_title, spend.code)
            text = stringResource(
                if (card != null) R.string.empty_currency_text_card else R.string.empty_currency_text_no_card,
                spend.code,
            )
            button = stringResource(if (card != null) R.string.empty_add_receipt else R.string.card_add)
            action = { if (card != null) onAddReceipt(card) else onAddCard() }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (noRates) {
                Text("↻", style = type.title.copy(fontSize = 26.sp), color = colors.accent)
            } else {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.accent)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = type.title, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = type.body, color = colors.muted, textAlign = TextAlign.Center)
        if (button != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    .clickable(role = Role.Button, onClick = action)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(button, style = type.bodyStrong, color = colors.onAccent)
            }
        }
    }
}
