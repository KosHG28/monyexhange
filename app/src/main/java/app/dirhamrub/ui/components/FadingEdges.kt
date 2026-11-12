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
    .edgeHaptic(state)
    // Отдельный буфер нужен только для смешивания краёв: когда прокручивать некуда, рисуем как обычно.
    .graphicsLayer {
        compositingStrategy = if (state.canScrollBackward || state.canScrollForward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
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
