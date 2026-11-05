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

/**
 * Слово OCR: [box] — рамка вдоль осей экрана, [height] — высота самих букв «как на ценнике»
 * (у наклонённой строки рамка выше букв).
 */
data class OcrWord(val text: String, val box: Box, val height: Float = box.height)

/** Строка OCR — слова слева направо; [angle] — наклон строки в градусах (по часовой стрелке). */
data class OcrLine(val words: List<OcrWord>, val angle: Float = 0f) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/**
 * Ширина наклонённой на [angleDeg] строки «как на ценнике» по ширине её рамки вдоль осей [boxWidth]
 * и высоте букв [height]: рамка = w·cos + h·sin. null — строка почти вертикальная (больше ~72°).
 */
fun orientedWidth(boxWidth: Float, height: Float, angleDeg: Float): Float? {
    val r = Math.toRadians(kotlin.math.abs(angleDeg).toDouble())
    val c = kotlin.math.cos(r)
    if (c < MIN_COS) return null
    val w = (boxWidth - height * kotlin.math.sin(r)) / c
    return maxOf(w.toFloat(), height)
}

private const val MIN_COS = 0.3

/** Блок OCR — несколько соседних строк. */
data class OcrBlock(val lines: List<OcrLine>)
