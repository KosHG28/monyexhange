package app.dirhamrub.ui.camera

import app.dirhamrub.domain.price.Box
import app.dirhamrub.domain.price.OcrBlock
import app.dirhamrub.domain.price.OcrLine
import app.dirhamrub.domain.price.OcrWord
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.atan2

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
                    words += OcrWord(
                        element.text,
                        Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
                    )
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

    private const val MIN_TILT = 2f
}
