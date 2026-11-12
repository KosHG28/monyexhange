package app.dirhamrub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pro: копия скриншота банка при чеке — чтобы потом видеть, откуда взялись суммы. Хранится только на телефоне,
 * в папке приложения (в резервные копии не попадает), уменьшенной до 1600 px по длинной стороне.
 * Файл — «<id карты>-<вид чека>.jpg»: на каждый вид чека карты — последний скриншот.
 */
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

    /** Уменьшить и сохранить скриншот [uri] к чеку; null — не получилось прочитать картинку. */
    suspend fun save(context: Context, uri: Uri, cardId: String, kind: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val out = file(context, cardId, kind)
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bitmap.recycle()
            out
        }.getOrNull()
    }

    /** Карту удалили — её скриншоты тоже. */
    fun deleteCard(context: Context, cardId: String) {
        list(context, cardId).forEach { it.delete() }
    }

    /** Скриншоты карт, которых больше нет (удалили, или новую карту не сохранили). */
    fun cleanup(context: Context, cardIds: Set<String>) {
        val keep = cardIds.map(::safe).toSet()
        dir(context).listFiles()?.forEach { f ->
            if (keep.none { f.name.startsWith("$it-") }) f.delete()
        }
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_]"), "_")
}
