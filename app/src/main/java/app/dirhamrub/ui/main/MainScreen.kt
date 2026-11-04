package app.dirhamrub.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.AppTheme
import java.math.BigDecimal

/** Подсказка «вы в другой стране»: [iso] — страна сотовой сети, [currency] — её валюта трат. */
data class CountryOffer(val iso: String, val currency: Currency)

/** Сколько места нужно верху экрана: карты, сумма, подписи и строка ЦБ. */
private fun topHeight(state: MainUiState, compact: Boolean, fontScale: Float): Dp {
    val hero = ((if (compact) HERO_COMPACT_SP else HERO_SP) * 1.12f * fontScale).dp
    val basis = when {
        basisStale(state) != null || basisAction(state) != null -> ROW
        basisText(state) != null -> TEXT_ROW
        else -> 0.dp
    }
    val compare = if (comparison(state) != null) ROW + 6.dp else 0.dp
    return CARDS_TOP + CARDS_HEIGHT * fontScale.coerceIn(1f, 1.3f) + hero + basis + compare + ROW
}

@Composable
fun MainScreen(
    state: MainUiState,
    countryOffer: CountryOffer?,
    tileOffer: Boolean,
    onTileOffer: (accept: Boolean) -> Unit,
    onKey: (Key) -> Unit,
    onSelectCard: (String) -> Unit,
    onStepCard: (Int) -> Unit,
    onEditCard: (BankCard) -> Unit,
    onAddReceipt: (BankCard) -> Unit,
    onAddCard: () -> Unit,
    onSelectLine: (Line) -> Unit,
    onChip: (BigDecimal) -> Unit,
    onOpenCurrencies: () -> Unit,
    onCountry: (accept: Boolean) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenCheat: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val colors = AppTheme.colors
    val wallet = state.wallet
    // Ввод — в одной из валют, крупно — сумма в итоговой (или в валюте трат, если вводят итоговую),
    // оставшаяся строка (обычно «Спишется с карты») — мелко под полем ввода.
    val lines = buildList {
        add(Line.SPEND)
        if (wallet.home != wallet.spend) add(Line.HOME)
        if (state.showCardLine) add(Line.CARD)
    }
    val heroLine = if (state.active == Line.HOME) Line.SPEND else Line.HOME
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(wallet.spend, wallet.home, onOpenCurrencies, onOpenCheat)
        // Верх экрана на низких телефонах прокручивается, низ (ввод, чипы, клавиатура) всегда на месте.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Крупная сумма мельче, только если иначе группа под картами не помещается без прокрутки.
            val compact = maxHeight < topHeight(state, compact = false, LocalDensity.current.fontScale)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                // Карты сверху, строка ЦБ снизу, сумма с подписями — посередине, отступы поровну.
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    when {
                        countryOffer != null -> CountryBanner(countryOffer, onCountry)
                        tileOffer -> TileBanner(onTileOffer)
                    }
                    BankCardsRow(
                        cards = wallet.cards,
                        quote = state.quote,
                        selectedId = wallet.selected?.id,
                        spend = wallet.spend,
                        home = wallet.home,
                        amount = state.totals.spend,
                        onSelect = onSelectCard,
                        onEdit = onEditCard,
                        onAdd = onAddCard,
                        modifier = Modifier.padding(top = CARDS_TOP),
                    )
                }
                ResultBlock(state, heroLine, compact, onSelectCard, onStepCard, onAddReceipt, onEditCard)
                OfficialLine(state, onRefreshRates, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            InputField(state, lines, onSelectLine)
            for (line in lines) {
                if (line != state.active && line != heroLine) SecondaryLine(state, line, onSelectLine)
            }
            // На коротком экране быстрые суммы уступают место сумме и подсказкам.
            if (LocalConfiguration.current.screenHeightDp >= SHORT_SCREEN_DP) {
                Spacer(Modifier.height(8.dp))
                Chips(wallet.spend, onChip)
            }
            Spacer(Modifier.height(8.dp))
            Keypad(onKey = onKey, onCamera = onOpenCamera)
            Spacer(Modifier.height(8.dp))
        }
    }
}
