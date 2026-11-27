package app.dirhamrub.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.HighFrameRate
import app.dirhamrub.ui.theme.LocalReduceMotion

private val DefaultIndicatorAnimation = spring<Rect>(dampingRatio = 0.78f, stiffness = 420f)

/** Единая подложка перемещается между измеренными позициями, в том числе между рядами чипов. */
@Composable
internal fun SlidingSelection(
    selected: Int,
    color: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 50.dp,
    onIndicatorSettled: ((Int) -> Unit)? = null,
    indicatorAnimation: FiniteAnimationSpec<Rect> = DefaultIndicatorAnimation,
    content: @Composable SlidingSelectionScope.() -> Unit,
) {
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = positions[selected]?.translate(-origin)
    val reduced = LocalReduceMotion.current
    val indicator = if (target != null) {
        animateRectAsState(
            target,
            if (reduced) snap() else indicatorAnimation,
            label = "segment",
            finishedListener = onIndicatorSettled?.let { onSettled ->
                { bounds -> if (bounds == target) onSettled(selected) }
            },
        )
    } else null
    val moving by remember(indicator, target) { derivedStateOf { indicator?.value != target } }
    HighFrameRate(!reduced && moving)
    Box(modifier.selectableGroup()) {
        Box(Modifier.onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
            val bounds = indicator?.value ?: return@drawBehind
            if (bounds.width > 0f && bounds.height > 0f) {
                val radius = minOf(corner.toPx(), bounds.width / 2f, bounds.height / 2f)
                drawRoundRect(color, bounds.topLeft, bounds.size, CornerRadius(radius))
            }
        }) {
            SlidingSelectionScope(positions, origin, indicator).content()
        }
    }
}

internal class SlidingSelectionScope(
    private val positions: MutableMap<Int, Rect>,
    private val origin: Offset,
    private val indicator: State<Rect>?,
) {
    fun position(index: Int): Modifier = Modifier.onGloballyPositioned {
        positions[index] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }

    fun covers(index: Int): Boolean {
        val center = positions[index]?.center ?: return false
        return indicator?.value?.contains(center - origin) == true
    }
}

/** Горизонтальный сегментированный переключатель с фиксированной шириной пунктов. */
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
    trackColor: Color = AppTheme.colors.surface2,
    indicatorColor: Color = AppTheme.colors.accent,
    indicatorText: Color = AppTheme.colors.onAccent,
    showCheck: Boolean = true,
    onIndicatorSettled: ((Int) -> Unit)? = null,
    indicatorAnimation: FiniteAnimationSpec<Rect> = DefaultIndicatorAnimation,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    SlidingSelection(
        selected, indicatorColor, modifier.fillMaxWidth().clip(shape).background(trackColor).padding(3.dp),
        corner = 11.dp, onIndicatorSettled = onIndicatorSettled, indicatorAnimation = indicatorAnimation,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier.weight(1f).heightIn(min = minHeight).then(position(index))
                        .then(optionModifier(index)).clip(RoundedCornerShape(11.dp))
                        .selectable(selected = active, role = role) { onSelect(index) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (active && showCheck) "✓ $label" else label,
                        style = textStyle.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (covers(index)) indicatorText else colors.ink,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
