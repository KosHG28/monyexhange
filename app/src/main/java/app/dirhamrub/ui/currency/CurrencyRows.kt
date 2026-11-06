package app.dirhamrub.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.Country
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.ui.theme.AppTheme

/** «Где тратите | Итог считать в». */
@Composable
internal fun Tabs(homeTab: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(4.dp),
    ) {
        for (tab in listOf(false, true)) {
            val selected = tab == homeTab
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.accentSoft else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) { onChange(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (tab) R.string.currency_sheet_home else R.string.currency_sheet_spend),
                    style = type.bodyStrong,
                    color = if (selected) colors.accent else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Поле поиска; системная клавиатура — только после касания поля. */
@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = type.body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(40)) },
                singleLine = true,
                textStyle = type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.currency_search_clear),
                tint = colors.muted,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") }
                    .padding(12.dp),
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
internal fun Section(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        color = AppTheme.colors.muted,
        modifier = modifier
            .padding(top = 14.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun RegionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = AppTheme.type.bodyStrong,
        color = AppTheme.colors.ink,
        modifier = modifier
            .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = AppTheme.colors.muted,
        modifier = modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
    )
}

/** «Вы сейчас здесь»: страна по сотовой сети одной кнопкой. */
@Composable
internal fun HereCard(country: Country, selected: Boolean, home: Currency, official: OfficialRates?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val c = country.currency
    val sub = listOfNotNull(c.nameRu, rateText(c, home, official)).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentSoft)
            .border(if (selected) 2.dp else 1.dp, colors.accent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${country.nameRu}, $sub"
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(country.flag, fontSize = 28.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${country.nameRu} · ${label(c)}",
                style = type.bodyStrong.copy(fontSize = 16.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(sub, style = type.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Строка страны: флаг, страна, валюта с кодом и знаком; справа — курс ЦБ, справочный курс
 * с пометкой «UnionPay» или «без ЦБ» («без курса»).
 */
@Composable
internal fun CountryRow(
    country: Country,
    selected: Boolean,
    home: Currency,
    official: OfficialRates?,
    noCbrLabel: String,
    unionPayLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = country.currency
    val rate = rateText(c, home, official)
    val note = unionPayLabel.takeIf { rate != null && official?.viaUnionPay(c, home) == true }
    val sub = "${c.nameRu} · ${label(c)}"
    PickRow(
        flag = country.flag,
        title = country.nameRu,
        sub = sub,
        right = rate,
        rightNote = note,
        noCbr = rate == null && noCbr(c, home, official),
        noCbrLabel = noCbrLabel,
        selected = selected,
        a11y = listOfNotNull(country.nameRu, sub, rate, note, noCbrLabel.takeIf { rate == null && noCbr(c, home, official) })
            .joinToString(", "),
        modifier = modifier,
        onClick = onClick,
    )
}

/** Строка валюты: флаг главной страны, название, код и знак. */
@Composable
internal fun CurrencyRow(c: Currency, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PickRow(
        flag = c.flag,
        title = c.nameRu,
        sub = label(c),
        right = null,
        noCbr = false,
        noCbrLabel = "",
        selected = selected,
        a11y = "${c.nameRu}, ${c.code}",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
internal fun PickRow(
    flag: String,
    title: String,
    sub: String,
    right: String?,
    noCbr: Boolean,
    noCbrLabel: String,
    selected: Boolean,
    a11y: String,
    modifier: Modifier,
    /** Мелкая подпись под курсом справа — откуда курс («UnionPay»). */
    rightNote: String? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flag, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.width(34.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = type.body.copy(fontWeight = FontWeight.Medium),
                color = if (selected) colors.accent else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(sub, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            right != null -> Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(right, style = type.caption, color = colors.muted, textAlign = TextAlign.End, maxLines = 1)
                if (rightNote != null) {
                    Text(rightNote, style = type.caption.copy(fontSize = 11.sp), color = colors.muted, maxLines = 1)
                }
            }
            noCbr -> Text(
                noCbrLabel,
                style = type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = colors.warn,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.warn.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Плитка валюты: флаг, код (и знак, если он не совпадает с кодом), в широкой — название.
 * Выбранная — красной рамкой, широкая — ещё и галочкой в углу.
 */
@Composable
internal fun CurrencyTile(c: Currency, selected: Boolean, wide: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val shape = RoundedCornerShape(14.dp)
    val label = if (c.symbol == c.code) c.code else "${c.code} · ${c.symbol}"
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (wide) 56.dp else 48.dp)
                .clip(shape)
                .background(if (selected) colors.accentSoft else colors.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                // В узкой плитке только код — полное название для TalkBack.
                .then(if (wide) Modifier else Modifier.semantics { contentDescription = c.nameRu })
                .padding(horizontal = if (wide) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(c.flag, fontSize = if (wide) 22.sp else 18.sp)
            Spacer(Modifier.width(if (wide) 10.dp else 8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (wide) label else c.code,
                    style = type.bodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = if (selected) colors.accent else colors.ink,
                    maxLines = 1,
                )
                if (wide) Text(c.nameRu, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected && wide) {
            Text(
                "✓",
                style = type.caption.copy(fontSize = 10.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clearAndSetSemantics { },
            )
        }
    }
}
