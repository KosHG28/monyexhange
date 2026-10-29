package app.dirhamrub.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Source
import app.dirhamrub.ui.short
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.motion

/** Одна станция «линии метро»: строка валюты. */
data class MetroStation(val line: Line, val label: String, val unit: String, val value: String)

/**
 * Строки валюты цен (AED или ₸), рублей и валюты карты — на красной линии со станциями,
 * как схема Red Line. Активная станция залита, ввод идёт в неё. Строку карты прячем,
 * если карта в той же валюте, что и цены.
 */
@Composable
fun MetroLine(
    active: Line,
    source: Source,
    sourceValue: String,
    rub: String,
    card: Card,
    cardValue: String,
    showCard: Boolean,
    onSelect: (Line) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceLabel = stringResource(if (source == Source.KZT) R.string.line_kzt else R.string.line_aed)
    val stations = buildList {
        add(MetroStation(Line.SOURCE, sourceLabel, source.code, sourceValue))
        add(MetroStation(Line.RUB, stringResource(R.string.line_rub), "₽", rub))
        if (showCard) add(MetroStation(Line.CARD, card.short(), card.symbol, cardValue))
    }
    Column(modifier.fillMaxWidth()) {
        stations.forEachIndexed { index, station ->
            StationRow(
                station = station,
                active = station.line == active,
                first = index == 0,
                last = index == stations.lastIndex,
                onClick = { onSelect(station.line) },
            )
        }
    }
}

@Composable
private fun StationRow(
    station: MetroStation,
    active: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val bg by animateColorAsState(if (active) colors.surface else Color.Transparent, motion(), label = "stationBg")
    val a11y = buildString {
        append(stringResource(R.string.line_a11y, station.label, station.value, station.unit))
        if (active) append(", ").append(stringResource(R.string.line_a11y_active))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = a11y
                selected = active
                role = Role.RadioButton
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationMark(active = active, first = first, last = last, color = colors.accent, fill = colors.surface)
        Text(
            text = station.label,
            style = type.label,
            color = if (active) colors.ink else colors.muted,
            modifier = Modifier.width(78.dp),
            maxLines = 1,
        )
        // Все суммы одним шрифтом: активная крупнее и ярче, остальные приглушены.
        Text(
            text = station.value,
            style = if (active) type.number.copy(fontSize = 24.sp, lineHeight = 30.sp) else type.number.copy(fontSize = 17.sp, lineHeight = 22.sp),
            color = if (active) colors.ink else colors.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        // Курсор занимает место и у неактивных строк, чтобы валюты стояли в одной колонке.
        Box(
            Modifier
                .padding(start = 2.dp)
                .width(2.dp)
                .height(26.dp)
                .background(if (active) colors.accent else Color.Transparent),
        )
        Text(
            text = station.unit,
            style = type.label,
            color = colors.muted,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .padding(start = 6.dp, end = 8.dp)
                .width(34.dp),
        )
    }
}

@Composable
private fun StationMark(active: Boolean, first: Boolean, last: Boolean, color: Color, fill: Color) {
    val inner by animateColorAsState(if (active) color else fill, motion(), label = "stationFill")
    Canvas(
        Modifier
            .width(44.dp)
            .fillMaxHeight(),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val lineWidth = 6.dp.toPx()
        // Строки идут вплотную, поэтому отрезки соседних станций сливаются в одну линию.
        val top = if (first) cy else 0f
        val bottom = if (last) cy else size.height
        drawLine(color, Offset(cx, top), Offset(cx, bottom), lineWidth)
        val r = 9.dp.toPx()
        drawCircle(fill, r, Offset(cx, cy))
        drawCircle(inner, r - 3.5.dp.toPx(), Offset(cx, cy))
        drawCircle(color, r, Offset(cx, cy), style = Stroke(3.5.dp.toPx()))
    }
    Spacer(Modifier.width(2.dp))
}
