package app.dirhamrub.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
