package app.dirhamrub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.ui.theme.AppTheme

/* Строки экрана настроек: заголовок раздела, группа, переключатель, выбор из вариантов, ссылка. */

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier
            .padding(start = 4.dp, top = 18.dp, bottom = 7.dp)
            .semantics { heading() },
    )
}

/** Карточка раздела: строки друг под другом, между ними — тонкая линия ([Divider]). */
@Composable
internal fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface),
        content = content,
    )
}

@Composable
internal fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.colors.line),
    )
}

/** Заголовок и подпись строки. */
@Composable
internal fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier, titleColor: Color = AppTheme.colors.ink) {
    Column(modifier) {
        Text(title, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium), color = titleColor)
        if (subtitle != null) {
            Text(subtitle, style = AppTheme.type.caption, color = AppTheme.colors.muted)
        }
    }
}

/** Строка с переключателем: нажимается целиком. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.bg,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

/** Выбор одного из вариантов: заголовок и сегменты под ним. */
@Composable
internal fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg)
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) colors.accentSoft else Color.Transparent)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(i) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = if (on) colors.accent else colors.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Строка-ссылка: справа значение («Рубль ›»); без [onClick] — просто сведения. */
@Composable
internal fun LinkRow(
    title: String,
    subtitle: String?,
    value: String?,
    onClick: (() -> Unit)?,
    titleColor: Color = AppTheme.colors.ink,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f), titleColor)
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(value, style = AppTheme.type.body.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1)
        }
    }
}

/** Источник курсов: значок, название, состояние справа (✓ сегодня 19:41) и что в нём. */
@Composable
internal fun SourceRow(badge: String, title: String, status: String, ok: Boolean, subtitle: String) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.paper),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, style = type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, modifier = Modifier.weight(1f))
                Text(status, style = type.caption.copy(fontSize = 13.sp), color = if (ok) colors.good else colors.warn, maxLines = 1)
            }
            Text(subtitle, style = type.caption, color = colors.muted)
        }
    }
}
