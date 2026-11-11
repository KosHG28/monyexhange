package app.dirhamrub.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Мягкое затухание у краёв прокрутки вместо резкого среза: сверху — когда есть что прокрутить назад,
 * снизу — когда есть что прокрутить вперёд. Ставить перед `verticalScroll` (на окно, а не на содержимое).
 */
fun Modifier.fadingEdges(state: ScrollableState, edge: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = edge.toPx().coerceAtMost(size.height / 3f)
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h),
                size = Size(size.width, h),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height),
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Вертикальная прокрутка с затуханием у краёв ([fadingEdges]). */
fun Modifier.fadingScroll(state: ScrollState): Modifier = fadingEdges(state).verticalScroll(state)
