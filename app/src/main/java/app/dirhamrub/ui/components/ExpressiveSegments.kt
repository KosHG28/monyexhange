package app.dirhamrub.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs

/** Одна непрерывная подложка: пружинящее перемещение и короткое растяжение по направлению выбора. */
@Composable
fun ExpressiveSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val reduce = LocalReduceMotion.current
    val stretch = remember { Animatable(1f) }
    LaunchedEffect(selected, reduce) {
        if (reduce) { stretch.snapTo(1f); return@LaunchedEffect }
        stretch.animateTo(1.08f, tween(90))
        stretch.animateTo(1f, Springs.fastSpatial())
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp)).background(colors.surface2).padding(3.dp)) {
        val cell = maxWidth / labels.size
        val position by animateDpAsState(cell * selected, if (reduce) snap() else spring(dampingRatio = 0.85f, stiffness = 650f), label = "currencyIndicator")
        Box(Modifier.offset(x = position).width(cell).height(48.dp).graphicsLayer { scaleX = stretch.value }
            .clip(RoundedCornerShape(11.dp)).background(colors.accentSoft))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(11.dp))
                    .selectable(selected = selected == i, role = Role.Tab) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, style = AppTheme.type.label, color = if (selected == i) colors.accent else colors.muted, maxLines = 1)
                }
            }
        }
    }
}
