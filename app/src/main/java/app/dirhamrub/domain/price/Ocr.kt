package app.dirhamrub.domain.price

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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

/**
 * Наклон строки в градусах (по часовой стрелке, от −90 до 90) по рамкам её слов [words] (слева направо)
 * и верхней стороне её четырёхугольника от OCR ([cornerDx], [cornerDy]).
 *
 * Углы OCR бывают повёрнуты относительно экрана на 90° или 180° — ровный ценник тогда выглядел
 * «почти вертикальным», и сумма не вписывалась. Поэтому главный источник — центры первого и последнего
 * слова (их рамки точно в координатах экрана); углы — только у строки из одного слова, и направление
 * у них берётся без стороны (180° — та же горизонталь). Итог сверяется с рамкой строки: у строки длины L
 * и высоты h высота рамки — L·sin + h·cos, поэтому наклон не может быть больше atan(высота / ширина).
 * Несогласованный угол — 0: лучше ровная сумма, чем повёрнутая не туда. Меньше 2° — тоже 0, чтобы сумма не дрожала.
 */
fun lineAngle(words: List<Box>, cornerDx: Float, cornerDy: Float): Float {
    if (words.isEmpty()) return 0f
    val box = words.reduce(Box::union)
    val first = words.first()
    val last = words.last()
    val dx = last.centerX - first.centerX
    val dy = last.centerY - first.centerY
    val raw = when {
        words.size >= 2 && dx > 0f && hypot(dx, dy) >= WORD_SPAN * hypot(box.width, box.height) -> degrees(dy, dx)
        cornerDx != 0f || cornerDy != 0f -> sideless(degrees(cornerDy, cornerDx))
        else -> return 0f
    }
    if (abs(raw) < MIN_TILT) return 0f
    val limit = degrees(box.height, box.width) + ANGLE_SLACK
    return if (abs(raw) > limit) 0f else raw
}

/**
 * Высота букв слова по его четырёхугольнику от OCR ([corner]), если она правдоподобна: не больше рамки
 * вдоль осей [box] (рамка наклонённого слова только выше букв). Иначе — высота рамки.
 */
fun glyphHeight(corner: Float?, box: Box): Float =
    if (corner != null && corner > 0f && corner <= box.height * HEIGHT_SLACK) corner else box.height

private fun degrees(y: Float, x: Float): Float = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()

/** Направление без стороны: 170° → −10°, −100° → 80°. */
private fun sideless(deg: Float): Float = when {
    deg > 90f -> deg - 180f
    deg <= -90f -> deg + 180f
    else -> deg
}

/** Центры крайних слов — не ближе этой доли диагонали строки, иначе угол по ним неточен. */
private const val WORD_SPAN = 0.35f

/** Запас к пределу наклона по рамке (неровные рамки OCR). */
private const val ANGLE_SLACK = 6f

private const val MIN_TILT = 2f

/** Рамка OCR бывает чуть ниже букв по углам. */
private const val HEIGHT_SLACK = 1.08f

/** Блок OCR — несколько соседних строк. */
data class OcrBlock(val lines: List<OcrLine>)
