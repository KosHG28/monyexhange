package app.dirhamrub.ui.camera

import app.dirhamrub.domain.price.Box
import app.dirhamrub.domain.price.OcrBlock
import app.dirhamrub.domain.price.OcrLine
import app.dirhamrub.domain.price.OcrWord
import app.dirhamrub.domain.price.glyphHeight
import app.dirhamrub.domain.price.lineAngle
import com.google.mlkit.vision.text.Text
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
                    words += OcrWord(element.text, box, glyphHeight(heightOf(element), box))
                }
                if (words.isNotEmpty()) lines += OcrLine(words, angleOf(line, words))
            }
            if (lines.isNotEmpty()) blocks += OcrBlock(lines)
        }
        return blocks
    }

    /**
     * Наклон строки: по центрам слов, у строки из одного слова — по верхней стороне её углов от ML Kit;
     * всё сверяется с рамками ([lineAngle]) — углы ML Kit бывают повёрнуты относительно экрана.
     */
    private fun angleOf(line: Text.Line, words: List<OcrWord>): Float {
        val p = line.cornerPoints
        val dx = if (p != null && p.size >= 2) (p[1].x - p[0].x).toFloat() else 0f
        val dy = if (p != null && p.size >= 2) (p[1].y - p[0].y).toFloat() else 0f
        return lineAngle(words.map { it.box }, dx, dy)
    }

    /** Высота слова по его углам: среднее левой и правой стороны (у наклонного слова рамка выше букв). */
    private fun heightOf(element: Text.Element): Float? {
        val p = element.cornerPoints ?: return null
        if (p.size < 4) return null
        fun dist(a: android.graphics.Point, b: android.graphics.Point) =
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
        return (dist(p[1], p[2]) + dist(p[0], p[3])) / 2f
    }
}
