package app.dirhamrub.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Мягкое затухание у краёв прокрутки вместо резкого среза: сверху — когда есть что прокрутить назад,
 * снизу — когда есть что прокрутить вперёд. Ставить перед `verticalScroll` (на окно, а не на содержимое).
 */
fun Modifier.fadingEdges(state: ScrollableState, edge: Dp = 24.dp, bottomEdge: Dp = edge): Modifier = this
    .edgeHaptic(state)
    // Separate only the edges that will mix with the scroll content.
    .graphicsLayer {
        val fadesTop = edge > 0.dp && state.canScrollBackward
        val fadesBottom = bottomEdge > 0.dp && state.canScrollForward
        compositingStrategy = if (fadesTop || fadesBottom) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithCache {
        val topHeight = edge.toPx().coerceAtMost(size.height / 3f)
        val bottomHeight = bottomEdge.toPx().coerceAtMost(size.height / 3f)
        val top = if (topHeight > 0f) {
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = topHeight)
        } else null
        val bottom = if (bottomHeight > 0f) {
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - bottomHeight, endY = size.height)
        } else null
        onDrawWithContent {
            drawContent()
            if (top != null && state.canScrollBackward) {
                drawRect(brush = top, size = Size(size.width, topHeight), blendMode = BlendMode.DstIn)
            }
            if (bottom != null && state.canScrollForward) {
                drawRect(
                    brush = bottom,
                    topLeft = Offset(0f, size.height - bottomHeight),
                    size = Size(size.width, bottomHeight),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

/** Вертикальная прокрутка с затуханием у краёв ([fadingEdges]). */
fun Modifier.fadingScroll(state: ScrollState, bottomEdge: Dp = 24.dp, topEdge: Dp = 24.dp): Modifier =
    fadingEdges(state, edge = topEdge, bottomEdge = bottomEdge).verticalScroll(state)

/** Затухание у скрытых краёв горизонтально прокручиваемого ряда. */
fun Modifier.fadingHorizontalEdges(state: ScrollableState, edge: Dp = 14.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.canScrollBackward || state.canScrollForward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithCache {
        val width = edge.toPx().coerceAtMost(size.width / 3f)
        val left = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = width)
        val right = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - width, endX = size.width)
        val ltr = layoutDirection == LayoutDirection.Ltr
        onDrawWithContent {
            drawContent()
            val fadeLeft = if (ltr) state.canScrollBackward else state.canScrollForward
            val fadeRight = if (ltr) state.canScrollForward else state.canScrollBackward
            if (fadeLeft) drawRect(brush = left, size = Size(width, size.height), blendMode = BlendMode.DstIn)
            if (fadeRight) {
                drawRect(brush = right, topLeft = Offset(size.width - width, 0f), size = Size(width, size.height), blendMode = BlendMode.DstIn)
            }
        }
    }

/**
 * Упёрлись в край прокрутки (пальцем или разлётом) — один мягкий щелчок вибрацией; снова — только после того,
 * как список сдвинулся. Если всё и так помещается (прокручивать нечего) — тихо. Ставить перед прокручиваемым.
 */
fun Modifier.edgeHaptic(state: ScrollableState): Modifier = composed {
    val haptics = rememberHaptics()
    val connection = remember(haptics, state) {
        object : NestedScrollConnection {
            private var ticked = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) {
                    ticked = false
                } else if (available.y != 0f && !ticked && (state.canScrollForward || state.canScrollBackward)) {
                    ticked = true
                    haptics.play(Haptic.EDGE)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                ticked = false
                return Velocity.Zero
            }
        }
    }
    nestedScroll(connection)
}
