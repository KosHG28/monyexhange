package app.dirhamrub.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pro: копия скриншота банка при чеке — чтобы потом видеть, откуда взялись суммы. Хранится только на телефоне,
 * в папке приложения (в резервные копии не попадает), уменьшенной до 1600 px по длинной стороне.
 * Файл — «<id карты>-<вид чека>.jpg»: на каждый вид чека карты — последний скриншот.
 */
data class ReceiptFile(val file: File, val revision: Long)

object ReceiptShots {
    private const val MAX_SIDE = 1600

    private fun dir(context: Context): File = File(context.noBackupFilesDir, "receipt-shots").apply { mkdirs() }

    /** Вид чека для имени файла: покупка в валюте [spend] или пополнение. */
    fun purchaseKind(spendCode: String): String = "buy-$spendCode"
    const val TRANSFER_KIND = "topup"

    fun file(context: Context, cardId: String, kind: String): File = File(dir(context), "${safe(cardId)}-$kind.jpg")

    /** Все сохранённые скриншоты карты, новые первыми. */
    fun list(context: Context, cardId: String): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("${safe(cardId)}-") }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** File metadata is acquired off-main, not during composition of image thumbnails. */
    suspend fun entries(context: Context, cardId: String): List<ReceiptFile> = withContext(Dispatchers.IO) {
        list(context, cardId).map { ReceiptFile(it, it.lastModified()) }
    }

    /** Уменьшить и сохранить скриншот [uri] к чеку; null — не получилось прочитать картинку. */
    suspend fun save(context: Context, uri: Uri, cardId: String, kind: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ReceiptImages.decode(context, uri, MAX_SIDE) ?: return@runCatching null
            try {
                val out = file(context, cardId, kind)
                val atomic = AtomicFile(out)
                val stream = atomic.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream))
                    atomic.finishWrite(stream)
                    out
                } catch (error: Exception) {
                    atomic.failWrite(stream)
                    throw error
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    fun deleteTransfer(context: Context, cardId: String) {
        AtomicFile(file(context, cardId, TRANSFER_KIND)).delete()
    }

    /** Карту удалили — её скриншоты тоже. */
    fun deleteCard(context: Context, cardId: String) {
        list(context, cardId).forEach { it.delete() }
    }

    /** Скриншоты карт, которых больше нет (удалили, или новую карту не сохранили). */
    fun cleanup(context: Context, cardIds: Set<String>, olderThanMillis: Long = System.currentTimeMillis()) {
        val keep = cardIds.map(::safe).toSet()
        dir(context).listFiles()?.forEach { f ->
            if (f.lastModified() < olderThanMillis && keep.none { f.name.startsWith("$it-") }) f.delete()
        }
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_]"), "_")
}
