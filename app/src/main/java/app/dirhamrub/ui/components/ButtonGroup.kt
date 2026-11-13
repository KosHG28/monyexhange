package app.dirhamrub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs

/**
 * Группа кнопок Material 3 Expressive: отдельные «таблетки» с зазором 4 dp. Выбранная — синяя, полностью круглая,
 * с «✓»; остальные — со скруглёнными внешними углами и внутренними 8 dp. Нажатая на миг шире (+25 %), соседи
 * уступают — пружиной. Без анимаций в системе — сразу на месте.
 */
@Composable
fun ButtonGroup(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    minHeight: Dp = 48.dp,
    textStyle: TextStyle = AppTheme.type.bodyStrong,
    optionModifier: @Composable (Int) -> Modifier = { Modifier },
) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    var pressed by remember { mutableIntStateOf(-1) }
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val source = remember { MutableInteractionSource() }
            val isPressed by source.collectIsPressedAsState()
            LaunchedEffect(isPressed) {
                if (isPressed) pressed = i else if (pressed == i) pressed = -1
            }
            val widthTarget = when {
                pressed == i -> PRESSED_WEIGHT
                pressed >= 0 -> YIELD_WEIGHT
                else -> 1f
            }
            val weight by animateFloatAsState(widthTarget, if (reduceMotion) snap() else Springs.fastSpatial(), label = "groupWeight")
            val round by animateFloatAsState(if (on) 1f else 0f, if (reduceMotion) snap() else Springs.fastSpatial(), label = "groupRound")
            val background by animateColorAsState(if (on) colors.accent else colors.surface2, if (reduceMotion) snap() else Springs.effects(), label = "groupBg")
            val first = i == 0
            val last = i == options.lastIndex
            Box(
                Modifier
                    .weight(weight.coerceAtLeast(0.2f))
                    .then(optionModifier(i))
                    .heightIn(min = minHeight)
                    .graphicsLayer {
                        val full = size.height / 2f
                        val inner = INNER.toPx()
                        val shared = (inner + (full - inner) * round).coerceAtLeast(0f)
                        val start = if (first) full else shared
                        val end = if (last) full else shared
                        shape = RoundedCornerShape(topStart = start, topEnd = end, bottomEnd = end, bottomStart = start)
                        clip = true
                    }
                    .background(background)
                    .selectable(
                        selected = on,
                        interactionSource = source,
                        indication = LocalIndication.current,
                        role = role,
                    ) { onSelect(i) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (on) "✓ $label" else label,
                    style = textStyle.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) colors.onAccent else colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val GAP = 4.dp
private val INNER = 8.dp
private const val PRESSED_WEIGHT = 1.25f
private const val YIELD_WEIGHT = 0.9f
