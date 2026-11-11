package app.dirhamrub.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.CardComparison
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.ui.components.RollingText
import app.dirhamrub.ui.main.Markup
import app.dirhamrub.ui.main.basisShort
import app.dirhamrub.ui.main.markupOf
import app.dirhamrub.ui.main.signedPercent
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import java.math.BigDecimal

/** Что показывает карточка фокуса; data class — чтобы сравнивать содержимое между кадрами. */
internal data class FocusInfo(
    val id: Long,
    val value: BigDecimal,
    val isOld: Boolean,
    val unsure: Boolean,
    val homeAmount: BigDecimal?,
    val officialAmount: BigDecimal?,
)

/**
 * Карточка цены в фокусе: распознанная цена и карта, крупно сумма в итоговой валюте («≈» — только у оценки),
 * как посчитан курс и наценка к ЦБ, снизу — сравнение с самой дешёвой из остальных карт.
 * Тап — шторка со всеми картами, «+ В список» — добавить к отмеченному, тап по сравнению — выбрать ту карту.
 */
@Composable
internal fun FocusCard(
    info: FocusInfo,
    spend: Currency,
    home: Currency,
    cardName: String?,
    cardQuote: CardQuote?,
    /** Справочный курс — UnionPay (у ЦБ нет валюты трат): подписи «UnionPay» вместо «ЦБ». */
    viaUnionPay: Boolean,
    comparison: CardComparison?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onSelectCard: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val price = "${Numbers.money(info.value)} ${spend.symbol}" + if (info.unsure) "?" else ""
    val head = (if (info.isOld) stringResource(R.string.camera_focus_old, price) else price) +
        (cardName?.let { " · $it" } ?: "")
    // «≈» — у оценки: курс без чека покупки в валюте трат, по ЦБ, или сама цена — догадка.
    val exact = !info.unsure && (cardQuote?.basis == RateBasis.RECEIPTS || cardQuote?.basis == RateBasis.MANUAL)
    val homeText = info.homeAmount?.let { (if (exact) "" else "≈ ") + "${Numbers.cheat(it)} ${home.symbol}" } ?: "—"
    val details = buildAnnotatedString {
        if (cardQuote != null) {
            append(basisShort(cardQuote))
            val vs = cardQuote.vsOfficial?.takeIf { cardQuote.basis != RateBasis.OFFICIAL }
            if (vs != null) {
                append(" · ")
                val tint = when (markupOf(vs)) {
                    Markup.GOOD -> colors.good
                    Markup.WARN -> colors.warn
                    Markup.BAD -> colors.danger
                }
                withStyle(SpanStyle(color = tint, fontWeight = FontWeight.Medium)) {
                    append(stringResource(if (viaUnionPay) R.string.card_vs_unionpay else R.string.card_vs_official, signedPercent(vs)))
                }
            }
        } else if (info.homeAmount != null) {
            append(stringResource(if (viaUnionPay) R.string.card_basis_unionpay else R.string.card_basis_official))
        }
        info.officialAmount?.let {
            append(if (length > 0) " · " else "")
            append(stringResource(if (viaUnionPay) R.string.camera_focus_unionpay else R.string.camera_focus_cbr, Numbers.cheat(it), home.symbol))
        }
    }
    val open = stringResource(R.string.camera_focus_open)
    val a11y = "$head, $homeText" + if (details.isNotEmpty()) ", $details" else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClickLabel = open, onClick = onOpen)
            .semantics { contentDescription = a11y }
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Text(head, style = type.label.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Сменилась карта или цена — перекатываются только изменившиеся цифры.
            val amountStyle = type.number.copy(fontSize = 30.sp, lineHeight = 36.sp)
            Row(Modifier.weight(1f)) {
                val amount = info.homeAmount
                if (amount == null) {
                    Text("—", style = amountStyle, color = colors.ink, maxLines = 1)
                } else {
                    if (!exact) Text("≈ ", style = amountStyle, color = colors.ink, maxLines = 1, softWrap = false)
                    RollingText(Numbers.cheat(amount), style = amountStyle, color = colors.ink)
                    Text(" " + home.symbol, style = amountStyle, color = colors.ink, maxLines = 1, softWrap = false)
                }
            }
            val add = stringResource(R.string.camera_focus_add)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentSoft)
                    .clickable(role = Role.Button, onClickLabel = add, onClick = onAdd)
                    .semantics { contentDescription = add }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.camera_focus_add_short), style = type.bodyStrong, color = colors.accent, maxLines = 1)
            }
        }
        if (details.isNotEmpty()) {
            Text(details, style = type.caption.copy(fontSize = 13.sp), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (comparison != null && info.homeAmount != null && !info.isOld) {
            FocusComparison(info.value, home, comparison, onSelectCard)
        }
    }
}

/**
 * Строка сравнения в карточке фокуса: выбранная карта выгоднее всех — «✓ Выгоднее всего · «Карта в тенге» — 4 160 ₽»,
 * иначе — «● «Карта в тенге» дешевле на 142 ₽ ›», тап выбирает ту карту.
 */
@Composable
internal fun FocusComparison(value: BigDecimal, home: Currency, c: CardComparison, onSelectCard: (String) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val style = type.caption.copy(fontSize = 13.sp)
    val other = c.other
    val dot = CardPalette.style(other.card).start
    HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
    if (c.selectedIsBest) {
        val otherAmount = "${Numbers.cheat(value.multiply(other.homePerUnit, MC))} ${home.symbol}"
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.good, fontWeight = FontWeight.SemiBold)) { append("✓ ") }
                append(stringResource(R.string.camera_focus_best_head))
                append(" · ")
                withStyle(SpanStyle(color = dot)) { append("● ") }
                append(stringResource(R.string.camera_focus_best_other, other.card.name, otherAmount))
            },
            style = style,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    } else {
        val diff = (if (c.isExact) "" else "≈ ") + Numbers.saving(c.difference(value))
        val text = stringResource(R.string.camera_focus_cheaper, other.card.name, diff, home.symbol)
        val a11y = stringResource(R.string.camera_focus_cheaper_a11y, other.card.name, diff, home.symbol)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onSelectCard(other.card.id) }
                .clearAndSetSemantics {
                    contentDescription = a11y
                    role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = dot)) { append("● ") }
                    append(text)
                },
                style = style.copy(fontWeight = FontWeight.Medium),
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", style = type.bodyStrong, color = colors.accent, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
