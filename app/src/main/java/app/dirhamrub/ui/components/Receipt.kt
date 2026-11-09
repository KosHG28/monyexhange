package app.dirhamrub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.ui.theme.AppTheme

/** Чек со скруглённым верхом и зубчатым нижним краем. */
val ReceiptShape = GenericShape { size, _ ->
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

@Composable
fun ReceiptSection(title: String, modifier: Modifier = Modifier) {
    Text(title, style = AppTheme.type.label, color = AppTheme.colors.muted, modifier = modifier.padding(vertical = 4.dp))
}

/**
 * Строка чека «Отправил ........ [15 000] ₽»: подпись, точечные отточия, поле ввода, хвост
 * (валюта или выбор валюты). Поле и хвост — постоянной ширины, чтобы поля всех строк стояли
 * ровной колонкой, а «₽ ▾» и «¥» — на одной линии.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptLine(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    last: Boolean = false,
    /** Серая подсказка в пустом поле (например, курс ЦБ). */
    placeholder: String? = null,
    /** Поле нужно заполнить: подпись красным, пояснение под строкой, шторка прокручивается к нему. */
    error: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) bring.bringIntoView() }
    Column(Modifier.bringIntoViewRequester(bring)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = type.mono, color = if (error != null) colors.accent else colors.ink)
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
                description = description,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                onDone = { focus.clearFocus() },
                placeholder = placeholder,
                modifier = Modifier.width(RECEIPT_FIELD),
            )
            Box(
                Modifier
                    .width(RECEIPT_TRAILING)
                    .padding(start = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) { trailing() }
        }
        if (error != null) {
            Text(
                error,
                style = type.caption,
                color = colors.accent,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Ширина поля суммы в чеке: «1 234 567,89» помещается. */
val RECEIPT_FIELD = 136.dp

/** Колонка валюты справа от поля: «₽ ▾», «AED», «сум». */
val RECEIPT_TRAILING = 50.dp

/** Числовое поле с системной клавиатурой numberDecimal. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    description: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    /** Серая подсказка в пустом поле — например, текущий курс по чекам. */
    placeholder: String? = null,
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
        modifier = modifier.semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = style, color = colors.muted.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
                }
                inner()
            }
        },
    )
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = AppTheme.colors.line
    Canvas(
        modifier
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
