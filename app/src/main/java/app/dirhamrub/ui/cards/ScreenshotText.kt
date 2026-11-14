package app.dirhamrub.ui.cards

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.dirhamrub.data.ReceiptImages
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private OCR bitmap: release only after ML Kit completes, including after the editor is closed. */
suspend fun readScreenshotLines(context: Context, uri: Uri): List<String>? {
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
            transform = { text -> text.textBlocks.flatMap { block -> block.lines.map { it.text } } },
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
