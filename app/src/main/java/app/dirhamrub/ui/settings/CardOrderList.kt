package app.dirhamrub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette

/**
 * Карты и наличные в порядке ряда на главном экране. Порядок меняют, перетаскивая за «≡»
 * (TalkBack — действиями «Выше»/«Ниже»), нажатие на строку открывает редактор карты.
 */
@Composable
internal fun CardOrderList(cards: List<BankCard>, onReorder: (List<String>) -> Unit, onEdit: (BankCard) -> Unit) {
    var order by remember(cards) { mutableStateOf(cards) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val offset = remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW.toPx() }
    val up = stringResource(R.string.settings_card_up)
    val down = stringResource(R.string.settings_card_down)
    val handle = stringResource(R.string.settings_card_drag)

    fun move(from: Int, to: Int): List<BankCard> = order.toMutableList().apply { add(to, removeAt(from)) }

    Column {
        order.forEachIndexed { index, card ->
            key(card.id) {
                val dragged = dragging == card.id
                if (index > 0) Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW)
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) offset.floatValue else 0f }
                        .background(if (dragged) AppTheme.colors.paper else AppTheme.colors.surface)
                        .clickable(role = Role.Button) { onEdit(card) }
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(up) { onReorder(move(index, index - 1).map { it.id }); true }.takeIf { index > 0 },
                                CustomAccessibilityAction(down) { onReorder(move(index, index + 1).map { it.id }); true }
                                    .takeIf { index < order.lastIndex },
                            )
                        }
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CardPalette.style(card).start),
                    )
                    Spacer(Modifier.width(12.dp))
                    RowText(card.name, subtitleOf(card), Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = handle }
                            .pointerInput(card.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = card.id
                                        offset.floatValue = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset.floatValue += amount.y
                                        val i = order.indexOfFirst { it.id == card.id }
                                        // Дотянули до середины соседней строки — меняемся местами.
                                        if (offset.floatValue > rowPx / 2 && i < order.lastIndex) {
                                            order = move(i, i + 1)
                                            offset.floatValue -= rowPx
                                        } else if (offset.floatValue < -rowPx / 2 && i > 0) {
                                            order = move(i, i - 1)
                                            offset.floatValue += rowPx
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset.floatValue = 0f
                                        onReorder(order.map { it.id })
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset.floatValue = 0f
                                        order = cards
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("≡", style = AppTheme.type.body.copy(fontSize = 20.sp), color = AppTheme.colors.muted)
                    }
                }
            }
        }
    }
}

/** «KZT · по чекам», «CNY · UnionPay, расчёт в CNY», «Только когда тратите AED». */
@Composable
private fun subtitleOf(card: BankCard): String = when {
    card.kind == CardKind.CASH -> stringResource(R.string.settings_card_cash, card.currency.code)
    card.unionPay -> stringResource(R.string.settings_card_unionpay, card.currency.code, card.settlementCurrency.code)
    card.transfer != null || card.purchases.isNotEmpty() -> stringResource(R.string.settings_card_receipts, card.currency.code)
    else -> stringResource(R.string.settings_card_plain, card.currency.code)
}

private val ROW = 64.dp
