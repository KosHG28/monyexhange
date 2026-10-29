package app.dirhamrub.ui.rate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Defaults
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RateSource
import app.dirhamrub.domain.Receipt
import app.dirhamrub.domain.RuDates
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.title
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Шторка «Курс»: сверху ручной курс, ниже — бумажный чек с переводом и покупкой,
 * итог считается на лету. Здесь можно системную клавиатуру (numberDecimal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateSheet(
    card: Card,
    rate: CardRate,
    onManualRate: (BigDecimal) -> Boolean,
    onReceipt: (Receipt) -> Boolean,
    onResetReceipt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    fun close() {
        focus.clearFocus()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    var manual by rememberSaveable(card) { mutableStateOf(Numbers.plain(rate.rubPerAed, 4)) }
    var rubSent by rememberSaveable(card) { mutableStateOf(field(rate.receipt.rubSent)) }
    var cardGot by rememberSaveable(card) { mutableStateOf(field(rate.receipt.cardGot)) }
    var cardPaid by rememberSaveable(card) { mutableStateOf(field(rate.receipt.cardPaid)) }
    var aedPaid by rememberSaveable(card) { mutableStateOf(field(rate.receipt.aedPaid)) }

    val receipt = remember(rubSent, cardGot, cardPaid, aedPaid) {
        val values = listOf(rubSent, cardGot, cardPaid, aedPaid).map { Numbers.parse(it) }
        if (values.any { it == null }) null
        else Receipt(values[0]!!, values[1]!!, values[2]!!, values[3]!!).takeIf { it.isValid }
    }
    val manualValue = Numbers.parse(manual)?.takeIf { it.signum() > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.rate_title, card.title()),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            val since = RuDates.long(rate.date)
            Text(
                text = stringResource(
                    if (rate.source == RateSource.MANUAL) R.string.rate_current_manual else R.string.rate_current_receipts,
                    since,
                ),
                style = type.body,
                color = colors.muted,
            )
            Spacer(Modifier.height(14.dp))

            // Ручной курс
            Text(stringResource(R.string.rate_manual_title), style = type.label, color = colors.muted)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rate_manual_prefix), style = type.monoStrong.copy(fontSize = 18.sp), color = colors.ink)
                Spacer(Modifier.width(8.dp))
                val manualLabel = stringResource(R.string.rate_manual_title)
                NumberField(
                    value = manual,
                    onValueChange = { manual = it },
                    style = type.monoStrong.copy(fontSize = 20.sp),
                    description = manualLabel,
                    onDone = { if (manualValue != null && onManualRate(manualValue)) close() },
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.rate_manual_suffix), style = type.monoStrong.copy(fontSize = 18.sp), color = colors.ink)
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (manualValue != null && onManualRate(manualValue)) close() },
                    enabled = manualValue != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(stringResource(R.string.rate_manual_apply), style = type.bodyStrong) }
            }

            Spacer(Modifier.height(18.dp))

            // Бумажный чек
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReceiptShape)
                    .background(colors.paper)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            ) {
                Text(
                    text = stringResource(R.string.receipt_title).uppercase(),
                    style = type.monoStrong.copy(fontSize = 14.sp, letterSpacing = 2.sp),
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                ReceiptSection(stringResource(R.string.receipt_transfer))
                ReceiptLine(stringResource(R.string.receipt_sent), rubSent, { rubSent = it }, "₽")
                ReceiptLine(stringResource(R.string.receipt_got), cardGot, { cardGot = it }, card.symbol)
                Spacer(Modifier.height(8.dp))
                ReceiptSection(stringResource(R.string.receipt_purchase))
                ReceiptLine(stringResource(R.string.receipt_paid), cardPaid, { cardPaid = it }, card.symbol)
                ReceiptLine(stringResource(R.string.receipt_for), aedPaid, { aedPaid = it }, "AED", last = true)
                Spacer(Modifier.height(10.dp))
                DashedDivider()
                Spacer(Modifier.height(10.dp))
                val rubPerAed = receipt?.rubPerAed
                if (receipt != null && rubPerAed != null) {
                    Text(
                        text = stringResource(R.string.receipt_result, Numbers.rate(rubPerAed)),
                        style = type.monoStrong.copy(fontSize = 22.sp, lineHeight = 28.sp),
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val transfer = Numbers.transferRate(receipt.cardPerRub!!, card.symbol)
                    val purchase = "1 AED = ${Numbers.rate(receipt.cardPerAed!!)} ${card.symbol}"
                    Text(
                        text = "$transfer · $purchase",
                        style = type.mono.copy(fontSize = 13.sp),
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.receipt_invalid),
                        style = type.body,
                        color = colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (receipt != null && onReceipt(receipt)) close() },
                enabled = receipt != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.receipt_apply), style = type.bodyStrong.copy(fontSize = 16.sp)) }
            TextButton(
                onClick = {
                    val d = Defaults.receipt(card)
                    rubSent = field(d.rubSent)
                    cardGot = field(d.cardGot)
                    cardPaid = field(d.cardPaid)
                    aedPaid = field(d.aedPaid)
                    manual = Numbers.plain(Defaults.rate(card).rubPerAed, 4)
                    onResetReceipt()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.receipt_reset, RuDates.long(Defaults.DATE)),
                    style = type.bodyStrong,
                    color = colors.accent,
                )
            }
        }
    }
}

/** Значение для поля чека: «2 757,48». */
private fun field(value: BigDecimal): String = Numbers.groupInput(Numbers.plain(value))

@Composable
private fun ReceiptSection(title: String) {
    Text(title, style = AppTheme.type.label, color = AppTheme.colors.muted, modifier = Modifier.padding(vertical = 4.dp))
}

/** «Отправил ........ 15 000 ₽» — подпись, точечные отточия, поле, валюта. */
@Composable
private fun ReceiptLine(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    last: Boolean = false,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.mono, color = colors.ink)
        val dotColor = colors.muted
        Canvas(
            Modifier
                .weight(1f)
                .height(14.dp)
                .padding(horizontal = 6.dp),
        ) {
            val step = 5.dp.toPx()
            val r = 1.dp.toPx()
            var x = r
            while (x < size.width) {
                drawCircle(dotColor, r, Offset(x, size.height - r))
                x += step
            }
        }
        NumberField(
            value = value,
            onValueChange = onValueChange,
            style = type.monoStrong.copy(fontSize = 17.sp),
            description = "$label, $unit",
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
            onDone = { focus.clearFocus() },
            modifier = Modifier.widthIn(min = 96.dp, max = 150.dp),
        )
        Text(
            unit,
            style = type.mono,
            color = colors.muted,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(34.dp),
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    description: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
) {
    val colors = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }) },
        singleLine = true,
        textStyle = style.copy(color = colors.ink, textAlign = TextAlign.End),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { inner() }
        },
    )
}

@Composable
private fun DashedDivider() {
    val color = AppTheme.colors.line
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
        )
    }
}

/** Чек со скруглённым верхом и зубчатым нижним краем. */
private val ReceiptShape = GenericShape { size, _ ->
    val tooth = 14f * (size.width / 360f).coerceIn(1f, 3f)
    val depth = tooth * 0.6f
    val radius = 24f
    moveTo(0f, radius)
    quadraticTo(0f, 0f, radius, 0f)
    lineTo(size.width - radius, 0f)
    quadraticTo(size.width, 0f, size.width, radius)
    lineTo(size.width, size.height - depth)
    val count = (size.width / tooth).toInt().coerceAtLeast(1)
    val step = size.width / count
    var x = size.width
    for (i in 0 until count) {
        lineTo(x - step / 2f, size.height)
        x -= step
        lineTo(x, size.height - depth)
    }
    close()
}
