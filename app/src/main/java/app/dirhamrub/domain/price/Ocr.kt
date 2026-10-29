package app.dirhamrub.domain.price

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Прямоугольник в координатах превью (пиксели). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = max(0f, width) * max(0f, height)

    fun union(other: Box) = Box(
        min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom),
    )

    fun iou(other: Box): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun centerDistance(other: Box): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return sqrt(dx * dx + dy * dy)
    }

    /** Расстояние между краями (0, если пересекаются). */
    fun gap(other: Box): Float {
        val dx = max(0f, max(other.left - right, left - other.right))
        val dy = max(0f, max(other.top - bottom, top - other.bottom))
        return sqrt(dx * dx + dy * dy)
    }

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/** Слово OCR. */
data class OcrWord(val text: String, val box: Box)

/** Строка OCR — слова слева направо. */
data class OcrLine(val words: List<OcrWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Блок OCR — несколько соседних строк. */
data class OcrBlock(val lines: List<OcrLine>)
