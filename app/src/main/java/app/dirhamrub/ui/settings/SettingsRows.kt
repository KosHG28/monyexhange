package app.dirhamrub.ui.settings

import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import app.dirhamrub.ui.theme.topLight
import app.dirhamrub.ui.components.ButtonGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/**
 * Карточка раздела в стиле Android 16 (Material 3 Expressive): строки — отдельные плитки, снаружи крупное
 * скругление (24 dp), между строками — зазор 2 dp цвета фона с маленькими скруглениями (6 dp) у каждой плитки
 * ([Divider]). Светлая полоса сверху — в тёмной теме ([topLight]).
 */
@Composable
internal fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_RADIUS))
            .background(AppTheme.colors.surface)
            .topLight(),
        content = content,
    )
}

/**
 * Зазор между плитками группы: полоса цвета фона и «срезы» углов над и под ней — у каждой плитки свои
 * маленькие скругления, как в настройках Android 16.
 */
@Composable
internal fun Divider() {
    val bg = AppTheme.colors.bg
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(GAP),
    ) {
        drawRect(bg)
        val r = INNER_RADIUS.toPx()
        val top = 0f
        val bottom = size.height
        for ((x, dx) in listOf(0f to 1f, size.width to -1f)) {
            // Над зазором — нижние углы верхней плитки, под ним — верхние углы нижней.
            for ((y, dy) in listOf(top to -1f, bottom to 1f)) {
                val p = Path().apply {
                    moveTo(x, y)
                    lineTo(x + dx * r, y)
                    quadraticTo(x, y, x, y + dy * r)
                    close()
                }
                drawPath(p, bg)
            }
        }
    }
}

private val GROUP_RADIUS = 24.dp
private val INNER_RADIUS = 6.dp
private val GAP = 2.dp

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
        AppSwitch(checked)
    }
}

/** Выбор одного из вариантов: заголовок и группа кнопок Expressive под ним. */
@Composable
internal fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
        Spacer(Modifier.height(8.dp))
        ButtonGroup(
            options = options,
            selected = selected,
            onSelect = onSelect,
            textStyle = type.caption.copy(fontSize = 13.sp),
        )
    }
}

/**
 * Строка-ссылка: справа значение («🇷🇺 RUB») и системный шеврон «›», если строка открывает что-то;
 * без [onClick] — просто сведения (значение без шеврона).
 */
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
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text("›", style = AppTheme.type.title.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
    }
}

/**
 * Источник курсов: текстовый значок («ЦБ», «7д»), название, справа — когда скачан («✓ сегодня 19:41», зелёным;
 * жёлтым при [warn]) или [trailing] (переключатель UnionPay). Данных нет — строка полупрозрачная ([enabled]),
 * без «—»: это состояние, а не выбор.
 */
@Composable
internal fun SourceRow(
    badge: String,
    title: String,
    status: String?,
    subtitle: String,
    warn: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, style = type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            Text(subtitle, style = type.caption, color = colors.muted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else if (status != null) {
            Spacer(Modifier.width(10.dp))
            Text(status, style = type.caption.copy(fontSize = 13.sp), color = if (warn) colors.warn else colors.good, maxLines = 1)
        }
    }
}

/**
 * Переключатель в цветах приложения; нажимается вся строка ([toggleable] у неё). Включили / выключили —
 * отклик «вкл» / «выкл» (при открытии экрана — тихо).
 */
@Composable
internal fun AppSwitch(checked: Boolean) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    var seen by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != seen) haptics.play(if (checked) Haptic.TOGGLE_ON else Haptic.TOGGLE_OFF)
        seen = checked
    }
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
