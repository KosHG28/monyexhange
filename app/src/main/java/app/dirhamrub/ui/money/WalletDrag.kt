package app.dirhamrub.ui.money

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.main.moved
import app.dirhamrub.ui.main.frameScrollDistance
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import kotlin.math.abs

internal class WalletDrag(private val initial: List<String>) {
    var order by mutableStateOf(initial)
        private set
    var held by mutableStateOf<String?>(null)
        private set
    private var center by mutableFloatStateOf(0f)
    private var height = 0
    private var pointer = 0f

    val overlayY: Float get() = center - height / 2f

    fun start(y: Float, scroll: LazyListState): Boolean {
        val item = scroll.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key in order && y >= it.offset && y < it.offset + it.size
        } ?: return false
        height = item.size
        center = item.offset + item.size / 2f
        pointer = y
        held = item.key as String
        return true
    }

    fun move(dy: Float, scroll: LazyListState) {
        pointer += dy
        val view = scroll.layoutInfo
        center = (center + dy).coerceIn(view.viewportStartOffset.toFloat(), view.viewportEndOffset.toFloat())
        reorder(scroll)
    }

    fun reorder(scroll: LazyListState) {
        val id = held ?: return
        val from = order.indexOf(id)
        val layout = scroll.layoutInfo
        val target = layout.visibleItemsInfo.filter { it.key != id && it.key in order }
            .minByOrNull { abs(center - (it.offset + it.size / 2f)) } ?: return
        val to = order.indexOf(target.key)
        val targetCenter = target.offset + target.size / 2f
        if (to < from && center > targetCenter || to > from && center < targetCenter) return
        val first = scroll.firstVisibleItemIndex
        if (layout.visibleItemsInfo.any { it.index == first && (it.key == id || it.key == target.key) }) {
            scroll.requestScrollToItem(first, scroll.firstVisibleItemScrollOffset)
        }
        order = order.moved(from, to)
    }

    fun scrollDelta(scroll: LazyListState, edge: Float, step: Float): Float {
        val layout = scroll.layoutInfo
        val start = layout.viewportStartOffset + edge
        val end = layout.viewportEndOffset - edge
        return when {
            pointer < start -> -step * ((start - pointer) / edge).coerceAtMost(1f)
            pointer > end -> step * ((pointer - end) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    fun finish(): List<String>? {
        held = null
        return order.takeIf { it != initial }
    }

    fun cancel() {
        held = null
        order = initial
    }
}

@Composable
internal fun Modifier.walletDragGesture(drag: WalletDrag, scroll: LazyListState, enabled: Boolean, commit: (List<String>) -> Unit): Modifier {
    if (!enabled) return this
    val onCommit by rememberUpdatedState(commit)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val edge = with(density) { 34.dp.toPx() }
    val speed = with(density) { 420.dp.toPx() }
    LaunchedEffect(drag, drag.held, scroll, edge, speed) {
        if (drag.held == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (drag.held != null) {
            withFrameNanos { now ->
                val step = frameScrollDistance(previous, now, speed)
                previous = now
                val delta = drag.scrollDelta(scroll, edge, step)
                if (delta != 0f && scroll.dispatchRawDelta(delta) != 0f) drag.reorder(scroll)
            }
        }
    }
    return pointerInput(drag, scroll, haptics) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (drag.start(it.y, scroll)) haptics.play(Haptic.TICK) },
            onDrag = { change, delta ->
                if (drag.held != null) {
                    change.consume()
                    drag.move(delta.y, scroll)
                }
            },
            onDragCancel = drag::cancel,
            onDragEnd = { drag.finish()?.let(onCommit) },
        )
    }
}
