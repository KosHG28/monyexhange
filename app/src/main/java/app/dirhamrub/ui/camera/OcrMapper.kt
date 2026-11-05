package app.dirhamrub.ui.camera

import app.dirhamrub.domain.price.Box
import app.dirhamrub.domain.price.OcrBlock
import app.dirhamrub.domain.price.OcrLine
import app.dirhamrub.domain.price.OcrWord
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Результат ML Kit → модель OCR из domain (рамки уже в координатах превью). */
object OcrMapper {
    fun map(text: Text?): List<OcrBlock> {
        if (text == null) return emptyList()
        val blocks = ArrayList<OcrBlock>(text.textBlocks.size)
        for (block in text.textBlocks) {
            val lines = ArrayList<OcrLine>(block.lines.size)
            for (line in block.lines) {
                val words = ArrayList<OcrWord>(line.elements.size)
                for (element in line.elements) {
                    val r = element.boundingBox ?: continue
                    val box = Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
                    words += OcrWord(element.text, box, heightOf(element) ?: box.height)
                }
                if (words.isNotEmpty()) lines += OcrLine(words, angleOf(line))
            }
            if (lines.isNotEmpty()) blocks += OcrBlock(lines)
        }
        return blocks
    }

    /**
     * Наклон строки по её углам (уже в координатах экрана): от верхнего левого к верхнему правому.
     * Почти горизонтальную строку считаем ровной, чтобы вписанная сумма не дрожала.
     */
    private fun angleOf(line: Text.Line): Float {
        val p = line.cornerPoints ?: return 0f
        if (p.size < 2) return 0f
        val deg = Math.toDegrees(atan2((p[1].y - p[0].y).toDouble(), (p[1].x - p[0].x).toDouble())).toFloat()
        return if (abs(deg) < MIN_TILT) 0f else deg
    }

    /** Высота слова по его углам: среднее левой и правой стороны (у наклонного слова рамка выше букв). */
    private fun heightOf(element: Text.Element): Float? {
        val p = element.cornerPoints ?: return null
        if (p.size < 4) return null
        fun dist(a: android.graphics.Point, b: android.graphics.Point) =
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
        return (dist(p[1], p[2]) + dist(p[0], p[3])) / 2f
    }

    private const val MIN_TILT = 2f
}
