package app.dirhamrub.ui.cards

import app.dirhamrub.ui.theme.topLight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.domain.Numbers
import app.dirhamrub.ui.theme.AppTheme

/* Поля и блоки редактора карты: блоки-карточки на сером фоне, поле «сумма | валюта» с подписью сверху,
   пара полей (в ряд или стопкой), кнопки «+», пустые состояния. */

/** Блок-карточка с заголовком: «Основное», «UnionPay», «Курс карты». */
@Composable
internal fun EditorBlock(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .topLight()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Text(
            title.uppercase(),
            style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
            color = colors.muted,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { heading() },
        )
        content()
    }
}

/** Подпись над полем: мелко, серым. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.muted, modifier = modifier.padding(start = 2.dp, bottom = 5.dp))
}

/** Подзаголовок внутри блока: «Пополнение», «Покупки по карте». */
@Composable
internal fun SubTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = AppTheme.type.bodyStrong.copy(fontSize = 14.sp), color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = AppTheme.type.caption, color = AppTheme.colors.muted)
    }
}

/**
 * Поле суммы — единый скруглённый контейнер: сверху подпись, внутри слева сумма (разряды пробелом на лету,
 * цифровая клавиатура с запятой), справа — выбор валюты (синим, за тонкой чертой) или серый знак валюты.
 * Ошибка — красная обводка и красный текст вместо подсказки; поле с ошибкой само прокручивается в видимую часть.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AmountField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    error: String? = null,
    /** Серая подсказка в пустом поле (курс ЦБ). */
    placeholder: String? = null,
    /** Справа: выбор валюты ([selectable] — за чертой, синим) или серый знак. */
    trailing: (@Composable () -> Unit)? = null,
    selectable: Boolean = false,
    big: Boolean = false,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) bring.bringIntoView() }
    // Длинная сумма — мельче, чтобы рядом поместилась валюта.
    val size = when {
        big -> 24.sp
        value.length > 12 -> 13.sp
        value.length > 9 -> 15.sp
        else -> 17.sp
    }
    Column(modifier.bringIntoViewRequester(bring)) {
        if (label != null) FieldLabel(label)
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (big) 56.dp else 48.dp)
                .clip(shape)
                .background(colors.surface2)
                .then(if (error != null) Modifier.border(1.5.dp, colors.danger, shape) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(Numbers.cleanInput(it)) },
                singleLine = true,
                textStyle = type.number.copy(fontSize = size, lineHeight = size * 1.25f, color = colors.ink, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = Grouping,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
                // «→» — в парное поле, «✓» — убрать клавиатуру: видна «Сохранить».
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp)
                    .semantics { contentDescription = description },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = type.number.copy(fontSize = size), color = colors.muted.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) {
                if (selectable) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(colors.line),
                    )
                }
                Box(Modifier.padding(horizontal = if (selectable) 4.dp else 14.dp), contentAlignment = Alignment.Center) { trailing() }
            }
        }
        if (error != null) {
            Text(
                error,
                style = type.caption,
                color = colors.danger,
                modifier = Modifier
                    .padding(start = 2.dp, top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Серый знак валюты внутри поля: валюта не выбирается (валюта карты, итоговая). */
@Composable
internal fun FieldSuffix(symbol: String) {
    Text(symbol, style = AppTheme.type.body.copy(fontSize = 16.sp), color = AppTheme.colors.muted, maxLines = 1, softWrap = false)
}

/** Разряды пробелом на лету: «150000,5» показывается как «150 000,5», курсор — там же, где набирали. */
private object Grouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val comma = raw.indexOf(',').let { if (it < 0) raw.length else it }
        val out = StringBuilder()
        // toOut[i] — где в показанной строке стоит символ i исходной (и позиция курсора перед ним).
        val toOut = IntArray(raw.length + 1)
        for (i in raw.indices) {
            if (i in 1 until comma && (comma - i) % 3 == 0) out.append(' ')
            toOut[i] = out.length
            out.append(raw[i])
        }
        toOut[raw.length] = out.length
        val shown = out.toString()
        val toRaw = IntArray(shown.length + 1)
        var r = 0
        // Обратно: первая позиция исходной строки, которая в показанной не левее курсора.
        for (o in 0..shown.length) {
            while (r < raw.length && toOut[r] < o) r++
            toRaw[o] = r
        }
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = toOut[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int): Int = toRaw[offset.coerceIn(0, shown.length)]
            },
        )
    }
}

/** Два поля в ряд, если каждому хватает [PAIR_MIN]; на узком экране — одно под другим. */
@Composable
internal fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= PAIR_MIN * 2 + PAIR_GAP) {
            Row(horizontalArrangement = Arrangement.spacedBy(PAIR_GAP), verticalAlignment = Alignment.Top) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PAIR_GAP)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

private val PAIR_MIN = 140.dp
private val PAIR_GAP = 8.dp

/** «+ Новое пополнение», «+ Чек покупки» — отдельная строка 48 dp, отступ сверху 12 dp: не промахнуться. */
@Composable
internal fun AddAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = AppTheme.colors.accent)
    }
}

/** Пустой раздел: пунктирная рамка, объяснение и крупная кнопка с обводкой («+ Добавить первый чек»). */
@Composable
internal fun EmptyReceipts(text: String, button: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = colors.line,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = AppTheme.type.caption.copy(fontSize = 12.5.sp), color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(button, style = AppTheme.type.bodyStrong, color = colors.accent)
        }
    }
}

/** Итог карты: «Итог по чекам   1 000 ₩ = 62,08 ₽» на зелёной подложке; пока по ЦБ — на серой, с «≈». */
@Composable
internal fun ResultBox(title: String, value: String, sub: String?, exact: Boolean) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (exact) colors.goodSoft else colors.surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppTheme.type.caption, color = if (exact) colors.good else colors.muted, modifier = Modifier.weight(1f))
            Text(value, style = AppTheme.type.number.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = if (exact) colors.good else colors.ink)
        }
        if (sub != null) Text(sub, style = AppTheme.type.caption, color = colors.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

