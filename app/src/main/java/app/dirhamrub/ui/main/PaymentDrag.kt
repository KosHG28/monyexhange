package app.dirhamrub.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import kotlin.math.abs

internal class PaymentDrag(private val initial: List<String>) {
    private val bounds = mutableStateMapOf<String, Rect>()
    private var slots = emptyList<Float>()
    private var centerY by mutableFloatStateOf(0f)
    private var pointerY = 0f
    var viewport = Rect.Zero
    var dragged by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf(initial)
        private set

    fun place(id: String, rect: Rect) { bounds[id] = rect }

    fun start(at: Offset): Boolean {
        val id = initial.firstOrNull { bounds[it]?.contains(at) == true } ?: return false
        slots = initial.mapNotNull { bounds[it]?.center?.y }
        if (slots.size != initial.size) return false
        centerY = bounds.getValue(id).center.y
        pointerY = at.y
        dragged = id
        return true
    }

    fun dragBy(dy: Float) {
        val id = dragged ?: return
        pointerY += dy
        centerY = (centerY + dy).coerceIn(slots.first(), slots.last())
        val to = slots.indices.minBy { abs(slots[it] - centerY) }
        val from = order.indexOf(id)
        if (from != to) order = order.moved(from, to)
    }

    fun translationOf(id: String): Float =
        if (dragged == id) centerY - (bounds[id]?.center?.y ?: centerY) else 0f

    fun edgeScroll(edge: Float, step: Float): Float = when {
        viewport.height <= 0f -> 0f
        pointerY < viewport.top + edge -> -step * ((viewport.top + edge - pointerY) / edge).coerceAtMost(1f)
        pointerY > viewport.bottom - edge -> step * ((pointerY - viewport.bottom + edge) / edge).coerceAtMost(1f)
        else -> 0f
    }

    fun finish(): List<String>? {
        val changed = order.takeIf { it != initial }
        dragged = null
        return changed
    }

    fun cancel() {
        dragged = null
        order = initial
    }
}

internal fun List<String>.moved(from: Int, to: Int): List<String> = toMutableList().apply { add(to, removeAt(from)) }

internal fun frameScrollDistance(previous: Long, now: Long, pixelsPerSecond: Float): Float =
    ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.032f) * pixelsPerSecond

/** The list owns the gesture and scrolls the common viewport while a card is held near an edge. */
@Composable
internal fun Modifier.paymentDragGesture(
    drag: PaymentDrag,
    scroll: ScrollState,
    enabled: Boolean,
    onReorder: (List<String>) -> Unit,
): Modifier {
    if (!enabled) return this
    val haptics = rememberHaptics()
    val commit by rememberUpdatedState(onReorder)
    val density = LocalDensity.current
    val edge = with(density) { 34.dp.toPx() }
    val speed = with(density) { 420.dp.toPx() }
    LaunchedEffect(drag, drag.dragged, edge, speed) {
        if (drag.dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (drag.dragged != null) {
            withFrameNanos { now ->
                val step = frameScrollDistance(previous, now, speed)
                previous = now
                val delta = drag.edgeScroll(edge, step)
                if (delta != 0f) drag.dragBy(scroll.dispatchRawDelta(delta))
            }
        }
    }
    return this.onGloballyPositioned {
        val full = it.boundsInWindow(clipBounds = false)
        drag.viewport = it.boundsInWindow().translate(-full.topLeft)
    }.pointerInput(drag, haptics) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (drag.start(it)) haptics.play(Haptic.TICK) },
            onDrag = { change, delta ->
                if (drag.dragged != null) {
                    change.consume()
                    drag.dragBy(delta.y)
                }
            },
            onDragCancel = drag::cancel,
            onDragEnd = { drag.finish()?.let(commit) },
        )
    }
}

internal fun Modifier.paymentDragSlot(drag: PaymentDrag, id: String): Modifier = onPlaced { coordinates ->
    val origin = coordinates.positionInParent()
    drag.place(id, Rect(origin, origin + Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
}
