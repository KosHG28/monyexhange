package app.dirhamrub.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.guide.countryFlag
import app.dirhamrub.ui.guide.countryName
import app.dirhamrub.ui.main.MainUiState
import app.dirhamrub.ui.main.PayIcon
import app.dirhamrub.ui.settings.Divider
import app.dirhamrub.ui.settings.Group
import app.dirhamrub.ui.settings.LinkRow
import app.dirhamrub.ui.settings.SectionTitle
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import java.math.BigDecimal

/** Что можно сделать с экрана «Карты и наличные». */
class MoneyActions(
    val onBack: () -> Unit,
    val onEditCard: (BankCard) -> Unit,
    val onAddCard: () -> Unit,
)

/**
 * «Карты и наличные» (из настроек): все способы оплаты, в том числе наличные в других валютах, которых нет
 * на главном; курс каждого, дата пополнения; касание — изменить, ниже — добавить.
 */
@Composable
fun MoneyScreen(state: MainUiState, actions: MoneyActions) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val back = stringResource(R.string.settings_back)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = actions.onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.cards_title),
                style = type.title.copy(fontSize = 21.sp),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp),
        ) {
            // От дешёвого к дорогому, как на главном; без курса — в конце.
            val cards = wallet.cards.sortedBy { state.quote.of(it.id)?.homePerUnit ?: BigDecimal(Long.MAX_VALUE) }
            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Group {
                    cards.forEachIndexed { i, card ->
                        if (i > 0) Divider()
                        CardRow(state, card, state.quote.of(card.id)) { actions.onEditCard(card) }
                    }
                }
            }
            AddRow(actions.onAddCard)
        }
    }
}

/**
 * «Наличные дирхамы / точно · обменяли 7 ноя      22,19 ₽» — название, «1 AED стоит» справа,
 * под названием — точно ли посчитан курс и дата последнего пополнения.
 */
@Composable
private fun CardRow(state: MainUiState, card: BankCard, q: CardQuote?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val wallet = state.wallet
    val exact = q != null && (q.basis == RateBasis.RECEIPTS || q.basis == RateBasis.MANUAL)
    val date = card.transfer?.takeIf { it.isValid }?.date?.let(RuDates::short)?.let {
        stringResource(if (card.kind == CardKind.CASH) R.string.money_exchanged else R.string.money_topped_up, it)
    }
    val accuracy = when {
        q == null && !card.canPay(wallet.spend) -> stringResource(R.string.money_only_in, card.currency.code)
        q == null -> stringResource(R.string.money_no_rate, wallet.spend.code)
        exact -> ""
        else -> stringResource(R.string.money_refine)
    }
    val rate = q?.let {
        val unit = Numbers.unitFor(it.homePerUnit)
        stringResource(
            R.string.money_rate,
            "${Numbers.moneyWhole(unit)} ${wallet.spend.code}",
            (if (exact) "" else "≈ ") + "${Numbers.rateShort(it.homePerUnit.multiply(unit, MC))} ${wallet.home.symbol}",
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.card_edit_a11y), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PayIcon(card, best = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Точный курс — только дата; примерный — «уточнить ›» (≈ стоит у курса справа).
            Row {
                if (accuracy.isNotEmpty()) {
                    Text(accuracy, style = type.caption, color = if (q == null) colors.muted else colors.accent, maxLines = 1)
                }
                if (date != null) {
                    Text((if (accuracy.isNotEmpty()) " · " else "") + date, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (rate != null) {
            Spacer(Modifier.width(10.dp))
            Text(rate, style = type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1)
        }
    }
}

@Composable
private fun AddRow(onAdd: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(R.string.money_add), style = AppTheme.type.bodyStrong, color = colors.accent)
    }
}
