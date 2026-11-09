package app.dirhamrub.ui.cheat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.components.sweep
import app.dirhamrub.ui.main.MainViewModel
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import java.math.BigDecimal

/** Колонка шпаргалки: карта (с цветом её оформления) или ЦБ. */
private class CheatColumn(val title: String, val dot: Color?, val rate: BigDecimal?, val selected: Boolean, val best: Boolean)

/**
 * Шпаргалка: суммы в валюте трат → итоговая валюта по каждой карте и по курсу ЦБ.
 * Над таблицей — легенда «● Название карты», в шапке колонок только цветные точки (названия не обрезаются);
 * выбранная карта — жирным, самая дешёвая — зелёным;
 * текущая сумма с главного экрана — отдельной подсвеченной строкой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(
    quote: WalletQuote,
    spend: Currency,
    home: Currency,
    selectedId: String?,
    amount: BigDecimal,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        // Строки шпаргалки появляются каскадом, текущая сумма подсвечивается пробегающей полосой.
        val cascade = rememberCascade()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.cheat_title),
                style = type.title,
                color = colors.ink,
                modifier = Modifier
                    .cascade(cascade, 0)
                    .semantics { heading() },
            )
            Text(
                stringResource(if (quote.viaUnionPay) R.string.cheat_subtitle_unionpay else R.string.cheat_subtitle, spend.code, home.symbol),
                style = type.body,
                color = colors.muted,
                modifier = Modifier.cascade(cascade, 1),
            )
            Spacer(Modifier.height(12.dp))
            val best = quote.best?.takeIf { quote.quotes.size > 1 }
            // Колонки: карты (по курсу каждой) и ЦБ. Много карт — таблица прокручивается вбок.
            val columns = quote.quotes.map {
                CheatColumn(it.card.name, CardPalette.style(it.card).start, it.homePerUnit, it.card.id == selectedId, it == best)
            } + CheatColumn(
                stringResource(if (quote.viaUnionPay) R.string.cheat_unionpay else R.string.cheat_official),
                null,
                quote.official,
                selected = false,
                best = false,
            )
            val amounts = MainViewModel.cheat(spend)
            val current = amount.takeIf { it.signum() > 0 }
            val rows = (amounts + listOfNotNull(current?.takeIf { a -> amounts.none { it.compareTo(a) == 0 } })).sorted()
            Legend(columns.filter { it.dot != null }, Modifier.cascade(cascade, 2))
            Spacer(Modifier.height(10.dp))
            CheatTable(spend.code, rows, current, columns, cascade)
        }
    }
}

/**
 * Таблица по ширине экрана: первая колонка — по самому длинному числу, колонки карт и ЦБ делят остальное
 * поровну. Если колонкам тесно (много карт), первая колонка стоит на месте, а остальные прокручиваются вбок.
 * Обе части из строк одной высоты, поэтому ряды совпадают; TalkBack читает строку целиком.
 */
@Composable
private fun CheatTable(first: String, rows: List<BigDecimal>, current: BigDecimal?, columns: List<CheatColumn>, cascade: State<Float>) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labels = rows.map(Numbers::cheat)
    val cells = rows.map { value -> columns.map { c -> c.rate?.let { Numbers.cheat(value.multiply(it, MC)) } ?: "—" } }
    // Цифры моноширинные: самая длинная строка — самая широкая.
    fun widthOf(text: String): Dp = with(density) { measurer.measure(text, type.monoStrong).size.width.toDp() }
    val firstWidth = widthOf(labels.maxBy { it.length }) + 2.dp
    val valueWidth = widthOf(cells.flatten().maxBy { it.length }) + CELL_GAP
    val headerHeight = with(density) { type.label.lineHeight.toDp() } + 16.dp
    val rowHeight = with(density) { type.mono.lineHeight.toDp() } + 14.dp

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paper)
            .padding(vertical = 8.dp),
    ) {
        val free = maxWidth - EDGE * 2 - firstWidth
        val fits = free >= maxOf(valueWidth, MIN_COLUMN) * columns.size
        val columnWidth = if (fits) free / columns.size else maxOf(valueWidth, SCROLL_COLUMN)
        Row {
            // Первая колонка: суммы в валюте трат.
            Column(Modifier.width(EDGE + firstWidth)) {
                Box(
                    Modifier
                        .height(headerHeight)
                        .fillMaxWidth()
                        .cascade(cascade, 2)
                        .padding(start = EDGE, bottom = 8.dp),
                    contentAlignment = Alignment.BottomStart,
                ) { Text(first, style = type.label, color = colors.muted, modifier = Modifier.clearAndSetSemantics { }) }
                HorizontalDivider(color = colors.line, modifier = Modifier.padding(start = EDGE))
                rows.forEachIndexed { i, value ->
                    val isCurrent = current != null && value.compareTo(current) == 0
                    Box(
                        Modifier
                            .height(rowHeight)
                            .fillMaxWidth()
                            .cascade(cascade, 3 + i)
                            .then(if (isCurrent) Modifier.sweep(cascade, 3 + i, colors.accentSoft) else Modifier)
                            .padding(start = EDGE)
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = labels[i],
                            style = if (isCurrent) type.monoStrong else type.mono,
                            color = if (isCurrent) colors.accent else colors.ink,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            // Карты и ЦБ: по ширине экрана или прокрутка вбок.
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState(), enabled = !fits),
            ) {
                Column(Modifier.width(columnWidth * columns.size + EDGE)) {
                    Row(
                        Modifier
                            .height(headerHeight)
                            .cascade(cascade, 2)
                            .semantics(mergeDescendants = true) { },
                    ) {
                        for (c in columns) HeaderCell(c, columnWidth)
                    }
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(end = EDGE))
                    rows.forEachIndexed { i, value ->
                        val isCurrent = current != null && value.compareTo(current) == 0
                        val a11y = labels[i] + " " + first + ": " +
                            columns.indices.joinToString(", ") { j -> columns[j].title + " " + cells[i][j] }
                        Row(
                            Modifier
                                .height(rowHeight)
                                .fillMaxWidth()
                                .cascade(cascade, 3 + i)
                                .then(if (isCurrent) Modifier.sweep(cascade, 3 + i, colors.accentSoft) else Modifier)
                                .padding(end = EDGE)
                                .clearAndSetSemantics { contentDescription = a11y },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            columns.forEachIndexed { j, c ->
                                Text(
                                    text = cells[i][j],
                                    style = if (c.selected || isCurrent) type.monoStrong else type.mono,
                                    color = when {
                                        c.best -> colors.good
                                        c.rate == null -> colors.muted
                                        else -> colors.ink
                                    },
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(columnWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Шапка колонки: у карты — только её цветная точка (название — в легенде), у ЦБ — подпись. */
@Composable
private fun HeaderCell(c: CheatColumn, width: Dp) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = CELL_GAP, bottom = 8.dp)
            .clearAndSetSemantics { contentDescription = c.title },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (c.dot != null) {
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(if (c.selected) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(c.dot),
            )
        } else {
            Text(c.title, style = type.label, color = colors.muted, textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

/** Легенда над таблицей: «● Карта в тенге  ● Карта в юанях», выбранная — жирным. Пункты не разрываются. */
@Composable
private fun Legend(cards: List<CheatColumn>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    if (cards.isEmpty()) return
    Text(
        text = buildAnnotatedString {
            cards.forEachIndexed { i, c ->
                if (i > 0) append("   ")
                withStyle(SpanStyle(color = c.dot ?: colors.muted)) { append("●\u00A0") }
                withStyle(SpanStyle(color = if (c.selected) colors.ink else colors.muted, fontWeight = if (c.selected) FontWeight.SemiBold else null)) {
                    append(c.title.replace(' ', '\u00A0'))
                }
            }
        },
        style = AppTheme.type.label,
        modifier = modifier.clearAndSetSemantics { },
    )
}

/** Поля таблицы по краям. */
private val EDGE = 12.dp

/** Зазор между колонками. */
private val CELL_GAP = 8.dp

/** Уже — колонкам тесно для названий карт: таблица прокручивается вбок. */
private val MIN_COLUMN = 64.dp

/** Ширина колонки в прокручиваемой таблице. */
private val SCROLL_COLUMN = 88.dp
