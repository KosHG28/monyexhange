package app.dirhamrub.ui.cards

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Текст со скриншота построчно — тем же ML Kit, что и в камере, на устройстве, без интернета.
 * null — картинку не открыть или распознать не удалось.
 */
suspend fun readScreenshotLines(context: Context, uri: Uri): List<String>? {
    val image = withContext(Dispatchers.IO) { runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() } ?: return null
    val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull() ?: return null
    return try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text -> cont.resume(text.textBlocks.flatMap { block -> block.lines.map { it.text } }) }
                .addOnFailureListener { cont.resume(null) }
        }
    } finally {
        recognizer.close()
    }
}
