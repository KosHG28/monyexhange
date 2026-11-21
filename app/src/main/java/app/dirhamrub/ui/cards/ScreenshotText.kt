package app.dirhamrub.ui.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import app.dirhamrub.data.ReceiptImages
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ScreenshotReading(val lines: List<String>, val shapes: List<RectF?>) {
    fun bounds(range: IntRange): RectF? {
        val matches = range.mapNotNull(shapes::getOrNull)
        val first = matches.firstOrNull() ?: return null
        return RectF(first).apply { matches.drop(1).forEach(::union) }
    }
}

/** Private OCR bitmap: release only after ML Kit completes, including after the editor is closed. */
internal suspend fun readBankScreenshot(context: Context, uri: Uri): ScreenshotReading? {
    var bitmap: Bitmap? = null
    var recognizer: TextRecognizer? = null
    var submitted = false
    try {
        val decoded = withContext(Dispatchers.IO) {
            runCatching { ReceiptImages.decode(context, uri, OCR_MAX_SIDE) }.getOrNull().also { bitmap = it }
        } ?: return null
        val client = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull() ?: return null
        recognizer = client
        submitted = true
        return awaitOwnedTask(
            start = { client.process(InputImage.fromBitmap(decoded, 0)) },
            release = {
                runCatching { client.close() }
                decoded.recycle()
                bitmap = null
            },
            transform = { text ->
                val rows = text.textBlocks.flatMap { it.lines }
                    .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                ScreenshotReading(
                    rows.map { it.text },
                    rows.map { row -> row.boundingBox?.let { b ->
                        RectF(
                            b.left.toFloat() / decoded.width, b.top.toFloat() / decoded.height,
                            b.right.toFloat() / decoded.width, b.bottom.toFloat() / decoded.height,
                        )
                    } },
                )
            },
        )
    } finally {
        if (!submitted) {
            runCatching { recognizer?.close() }
            bitmap?.recycle()
        }
    }
}

// Preserve full resolution for normal screenshots (including QHD/4K), bound huge camera imports.
private const val OCR_MAX_SIDE = 4096
