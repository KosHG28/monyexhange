package app.dirhamrub.ui.camera

import app.dirhamrub.domain.price.Box
import app.dirhamrub.domain.price.OcrBlock
import app.dirhamrub.domain.price.OcrLine
import app.dirhamrub.domain.price.OcrWord
import com.google.mlkit.vision.text.Text

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
                if (words.isNotEmpty()) lines += OcrLine(words)
            }
            if (lines.isNotEmpty()) blocks += OcrBlock(lines)
        }
        return blocks
    }
}
